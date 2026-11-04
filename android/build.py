#!/usr/bin/env python3
"""Build the Fioles Enchantées APK without the Android SDK.

Needs (in --tools dir): android.jar (API 33 platform), dx.jar (dalvik-dx), apksig.jar.
Needs on PATH: javac, java, keytool.

The binary AndroidManifest.xml and resources.arsc are written directly by this
script, so aapt/aapt2 are not required.
"""
import argparse
import os
import shutil
import struct
import subprocess
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)

PACKAGE = "com.alinx.fioles"
APP_NAME = "Fioles Enchantées"
VERSION_CODE = 2
VERSION_NAME = "1.1"
MIN_SDK = 24  # v2-only signature (APK Signature Scheme v2 needs Android 7.0+)
TARGET_SDK = 36

DENSITIES = {"mdpi": 160, "hdpi": 240, "xhdpi": 320, "xxhdpi": 480, "xxxhdpi": 640}
ICON_ID = 0x7F010000  # @mipmap/ic_launcher  (package 0x7f, type 1, entry 0)

# Public framework attribute ids (android.R.attr.*)
ATTR = {
    "label": 0x01010001,
    "icon": 0x01010002,
    "name": 0x01010003,
    "exported": 0x01010010,
    "screenOrientation": 0x0101001E,
    "configChanges": 0x0101001F,
    "minSdkVersion": 0x0101020C,
    "versionCode": 0x0101021B,
    "versionName": 0x0101021C,
    "targetSdkVersion": 0x01010270,
    "hardwareAccelerated": 0x010102D3,
}

T_REF, T_STRING, T_INT_DEC, T_INT_HEX, T_BOOL = 0x01, 0x03, 0x10, 0x11, 0x12
ANDROID_NS = "http://schemas.android.com/apk/res/android"


# --------------------------------------------------------------------------
# Binary chunk helpers
# --------------------------------------------------------------------------
def pad4(b):
    return b + b"\0" * (-len(b) % 4)


def string_pool(strings):
    """UTF-16 ResStringPool chunk."""
    offsets, data = [], b""
    for s in strings:
        offsets.append(len(data))
        u = s.encode("utf-16-le")
        data += struct.pack("<H", len(u) // 2) + u + b"\0\0"
    data = pad4(data)
    header_size = 28
    strings_start = header_size + 4 * len(strings)
    body = b"".join(struct.pack("<I", o) for o in offsets) + data
    return struct.pack("<HHIIIIII", 0x0001, header_size, header_size + len(body),
                       len(strings), 0, 0, strings_start, 0) + body


# --------------------------------------------------------------------------
# AndroidManifest.xml (binary XML)
# --------------------------------------------------------------------------
def build_manifest():
    # (tag, [(attrName, type, value)], children)
    activity = ("activity", [
        ("name", T_STRING, PACKAGE + ".MainActivity"),
        ("exported", T_BOOL, True),
        ("screenOrientation", T_INT_DEC, 1),           # portrait
        ("configChanges", T_INT_HEX, 0x04A0 | 0x0D00),  # orientation|keyboardHidden|screenSize|smallestScreenSize|screenLayout
    ], [("intent-filter", [], [
        ("action", [("name", T_STRING, "android.intent.action.MAIN")], []),
        ("category", [("name", T_STRING, "android.intent.category.LAUNCHER")], []),
    ])])
    application = ("application", [
        ("label", T_STRING, APP_NAME),
        ("icon", T_REF, ICON_ID),
        ("hardwareAccelerated", T_BOOL, True),
    ], [activity])
    manifest = ("manifest", [
        ("versionCode", T_INT_DEC, VERSION_CODE),
        ("versionName", T_STRING, VERSION_NAME),
        ("package", T_STRING, PACKAGE),
    ], [
        ("uses-sdk", [("minSdkVersion", T_INT_DEC, MIN_SDK), ("targetSdkVersion", T_INT_DEC, TARGET_SDK)], []),
        application,
    ])

    # Attribute names with resource ids must come first in the pool (resource map).
    attr_names = list(ATTR.keys())
    strings = list(attr_names)

    def idx(s):
        if s not in strings:
            strings.append(s)
        return strings.index(s)

    # Pre-register every string so indices are stable.
    def collect(node):
        tag, attrs, kids = node
        idx(tag)
        for n, t, v in attrs:
            idx(n)
            if t == T_STRING:
                idx(v)
        for k in kids:
            collect(k)
    idx("android"); idx(ANDROID_NS)
    collect(manifest)

    NO = 0xFFFFFFFF
    chunks = []
    chunks.append(struct.pack("<HHIII", 0x0100, 16, 24, 1, NO) + struct.pack("<II", idx("android"), idx(ANDROID_NS)))

    def emit(node, line=[2]):
        tag, attrs, kids = node
        # Android requires attributes sorted by resource id; non-android attrs (package) last.
        attrs = sorted(attrs, key=lambda a: ATTR.get(a[0], 0xFFFFFFFF))
        ab = b""
        for n, t, v in attrs:
            ns = idx(ANDROID_NS) if n in ATTR else NO
            if t == T_STRING:
                raw, data = idx(v), idx(v)
            elif t == T_BOOL:
                raw, data = NO, 0xFFFFFFFF if v else 0
            else:
                raw, data = NO, v
            ab += struct.pack("<IIIHBBI", ns, idx(n), raw, 8, 0, t, data)
        body = struct.pack("<IIHHHHHH", NO, idx(tag), 0x14, 0x14, len(attrs), 0, 0, 0) + ab
        chunks.append(struct.pack("<HHIII", 0x0102, 16, 16 + len(body), line[0], NO) + body)
        line[0] += 1
        for k in kids:
            emit(k)
        chunks.append(struct.pack("<HHIII", 0x0103, 16, 24, line[0], NO) + struct.pack("<II", NO, idx(tag)))
        line[0] += 1

    emit(manifest)
    chunks.append(struct.pack("<HHIII", 0x0101, 16, 24, 99, NO) + struct.pack("<II", idx("android"), idx(ANDROID_NS)))

    pool = string_pool(strings)
    resmap_body = b"".join(struct.pack("<I", ATTR[n]) for n in attr_names)
    resmap = struct.pack("<HHI", 0x0180, 8, 8 + len(resmap_body)) + resmap_body
    body = pool + resmap + b"".join(chunks)
    return struct.pack("<HHI", 0x0003, 8, 8 + len(body)) + body


# --------------------------------------------------------------------------
# resources.arsc : a single resource, @mipmap/ic_launcher, in every density
# --------------------------------------------------------------------------
def build_arsc():
    dens = list(DENSITIES.items())
    paths = ["res/mipmap-%s-v4/ic_launcher.png" % d for d, _ in dens]
    global_pool = string_pool(paths)
    type_pool = string_pool(["mipmap"])
    key_pool = string_pool(["ic_launcher"])

    # typeSpec: one entry, varies by density (CONFIG_DENSITY = 0x0100)
    spec = struct.pack("<HHIBBHI", 0x0202, 16, 20, 1, 0, 0, 1) + struct.pack("<I", 0x0100)

    types = b""
    for i, (_, dpi) in enumerate(dens):
        config = bytearray(64)
        struct.pack_into("<I", config, 0, 64)
        struct.pack_into("<H", config, 14, dpi)   # density
        struct.pack_into("<H", config, 24, 4)     # sdkVersion (v4, implied by density)
        header_size = 20 + 64
        offsets = struct.pack("<I", 0)
        entry = struct.pack("<HHI", 8, 0, 0) + struct.pack("<HBBI", 8, 0, T_STRING, i)
        entries_start = header_size + len(offsets)
        size = entries_start + len(entry)
        types += struct.pack("<HHIBBHII", 0x0201, header_size, size, 1, 0, 0, 1, entries_start) + bytes(config) + offsets + entry

    name = PACKAGE.encode("utf-16-le").ljust(256, b"\0")
    pkg_header_size = 288
    type_strings_off = pkg_header_size
    key_strings_off = type_strings_off + len(type_pool)
    pkg_body = type_pool + key_pool + spec + types
    pkg = struct.pack("<HHII", 0x0200, pkg_header_size, pkg_header_size + len(pkg_body), 0x7F) + name + \
        struct.pack("<IIIII", type_strings_off, 1, key_strings_off, 1, 0) + pkg_body

    body = global_pool + pkg
    return struct.pack("<HHII", 0x0002, 12, 12 + len(body), 1) + body


# --------------------------------------------------------------------------
# Zip writer with 4-byte alignment for stored entries (zipalign equivalent)
# --------------------------------------------------------------------------
def write_aligned_zip(out, entries):
    """entries: list of (name, bytes, compress)."""
    with zipfile.ZipFile(out, "w") as z:
        for name, data, compress in entries:
            zi = zipfile.ZipInfo(name, date_time=(2008, 1, 1, 0, 0, 0))
            zi.create_system = 0
            if compress:
                zi.compress_type = zipfile.ZIP_DEFLATED
            else:
                zi.compress_type = zipfile.ZIP_STORED
                # local header = 30 + len(name) + len(extra); data must start 4-aligned
                offset = z.fp.tell() + 30 + len(name.encode())
                padding = (-offset) % 4
                zi.extra = b"\0" * padding
            z.writestr(zi, data)


def run(cmd, **kw):
    print("+", " ".join(cmd))
    subprocess.check_call(cmd, **kw)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tools", required=True, help="dir with android.jar, dx.jar, apksig.jar")
    ap.add_argument("--keystore", default=os.path.join(HERE, "fioles.p12"))
    ap.add_argument("--storepass", default="fioles123")
    ap.add_argument("--out", default=os.path.join(ROOT, "FiolesEnchantees.apk"))
    a = ap.parse_args()

    tools = os.path.abspath(a.tools)
    android_jar = os.path.join(tools, "android.jar")
    build = os.path.join(HERE, "build")
    shutil.rmtree(build, ignore_errors=True)
    os.makedirs(os.path.join(build, "classes"))

    # 1. Java -> .class -> classes.dex
    srcs = [os.path.join(dp, f) for dp, _, fs in os.walk(os.path.join(HERE, "src")) for f in fs if f.endswith(".java")]
    run(["javac", "-source", "8", "-target", "8", "-nowarn", "-Xlint:-options",
         "-bootclasspath", android_jar, "-d", os.path.join(build, "classes")] + srcs)
    run(["java", "-cp", os.path.join(tools, "dx.jar"), "com.android.dx.command.Main", "--dex",
         "--min-sdk-version=%d" % MIN_SDK, "--output=" + os.path.join(build, "classes.dex"),
         os.path.join(build, "classes")])

    # 2. Signing tool
    run(["javac", "-nowarn", "-cp", os.path.join(tools, "apksig.jar"), "-d", build, os.path.join(HERE, "tools", "Sign.java")])
    if not os.path.exists(a.keystore):
        run(["keytool", "-genkeypair", "-keystore", a.keystore, "-storetype", "PKCS12",
             "-storepass", a.storepass, "-keypass", a.storepass, "-alias", "fioles",
             "-keyalg", "RSA", "-keysize", "2048", "-validity", "10000",
             "-dname", "CN=Fioles Enchantees, O=Alinx, C=FR"])

    # 3. Package
    entries = [
        ("AndroidManifest.xml", build_manifest(), True),
        ("resources.arsc", build_arsc(), False),
        ("classes.dex", open(os.path.join(build, "classes.dex"), "rb").read(), True),
        ("assets/index.html", open(os.path.join(ROOT, "index.html"), "rb").read(), True),
    ]
    for d in DENSITIES:
        png = open(os.path.join(HERE, "res", "mipmap-" + d, "ic_launcher.png"), "rb").read()
        entries.append(("res/mipmap-%s-v4/ic_launcher.png" % d, png, False))
    unsigned = os.path.join(build, "unsigned.apk")
    write_aligned_zip(unsigned, entries)

    # 4. Sign (v2)
    run(["java", "--add-exports", "java.base/sun.security.x509=ALL-UNNAMED",
         "--add-exports", "java.base/sun.security.pkcs=ALL-UNNAMED",
         "--add-exports", "java.base/sun.security.util=ALL-UNNAMED",
         "-cp", os.pathsep.join([os.path.join(tools, "apksig.jar"), build]), "Sign",
         unsigned, a.out, a.keystore, a.storepass, "fioles"])
    print("APK:", a.out, os.path.getsize(a.out), "bytes")


if __name__ == "__main__":
    sys.exit(main())
