"""Aperçu PNG du cadran Racing Chrono (reprend la géométrie de RacingChronoView.mc).
Usage : python3 tools/preview.py [sortie.png] [taille_icone_sortie.png]"""
import math, sys
from PIL import Image, ImageDraw, ImageFont

SS = 4                      # suréchantillonnage
W = 260
YELLOW, GREEN, BLUE = "#FFFF00", "#00AA00", "#0055FF"
WHITE, LUME, GREY, LGREY, BLACK = "#FFFFFF", "#AAFFFF", "#555555", "#AAAAAA", "#000000"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
XTINY = ImageFont.truetype(BOLD, 11 * SS)
SMALL = ImageFont.truetype(BOLD, 18 * SS)

img = Image.new("RGB", (W * SS, W * SS), BLACK)
d = ImageDraw.Draw(img)
cx = cy = 130

def P(v): return v * SS
def polar(ox, oy, a, r):
    t = math.radians(a); return (ox + r * math.sin(t), oy - r * math.cos(t))
def line(p1, p2, c, w=1): d.line([P(p1[0]), P(p1[1]), P(p2[0]), P(p2[1])], fill=c, width=max(1, int(w * SS)))
def tick(ox, oy, a, r1, r2, c, w=1): line(polar(ox, oy, a, r1), polar(ox, oy, a, r2), c, w)
def circle(x, y, r, c, fill=True, w=1):
    box = [P(x - r), P(y - r), P(x + r), P(y + r)]
    d.ellipse(box, fill=c) if fill else d.ellipse(box, outline=c, width=int(w * SS))
def text(x, y, s, font, c): d.text((P(x), P(y)), s, font=font, fill=c, anchor="mm")
def hand(ox, oy, a, pts, c):
    t = math.radians(a); sn, cs = math.sin(t), math.cos(t)
    d.polygon([(P(ox + x * cs - y * sn), P(oy + x * sn + y * cs)) for x, y in pts], fill=c)

# lunette
circle(cx, cy, 129, GREY, False, 2)
for T in [400, 300, 240, 200, 170, 150, 135, 120, 110, 100, 90, 85, 80, 75, 70, 65]:
    a = 21600 / T; x, y = polar(cx, cy, a, 120 - 5 * abs(math.sin(math.radians(a)))); text(x, y, str(T), XTINY, WHITE)
for a in range(-22, 47, 6):
    if -16 < a < 16: continue
    x, y = polar(cx, cy, a, 120.5); circle(x, y, 1.5, LGREY)
text(cx, cy - 120.5, "LEGEND", XTINY, YELLOW)
# réhaut
for i in range(60):
    if i % 5 == 0: tick(cx, cy, i * 6, 97, 107, YELLOW, 3)
    else: tick(cx, cy, i * 6, 102, 107, LGREY, 1)
circle(cx, cy, 95, GREY, False)
# index
for h in [1, 2, 4, 5, 7, 8, 10, 11]:
    tick(cx, cy, h * 30, 69, 91, GREY, 8); tick(cx, cy, h * 30, 70.5, 89.5, LUME, 5)
# batterie (12 h)
ox, oy = cx, cy - 50
circle(ox, oy, 30, GREY, False)
for i in range(40):
    if i % 10 == 0: tick(ox, oy, i * 9, 22, 29, YELLOW, 2)
    else: tick(ox, oy, i * 9, 26, 29, LGREY)
for a, s in [(90, "25"), (180, "50"), (270, "75")]:
    x, y = polar(ox, oy, a, 15); text(x, y, s, XTINY, WHITE)
hand(ox, oy, 78 * 3.6, [(-1.5, 5), (1.5, 5), (1, -25), (-1, -25)], YELLOW); circle(ox, oy, 3, YELLOW)
# cardio (9 h)
ox, oy = cx - 52, cy
for i in range(48):
    x, y = polar(ox, oy, i * 7.5, 27); circle(x, y, 1, LGREY)
circle(ox, oy, 30, GREY, False)
text(ox, oy - 4, "62", SMALL, YELLOW); text(ox, oy + 14, "BPM", XTINY, LGREY)
# pas (6 h)
ox, oy = cx, cy + 52
for i in range(60): tick(ox, oy, i * 6, 27.5 if i % 5 == 0 else 28.5, 31, YELLOW, 2 if i % 5 == 0 else 1)
for yy in range(-26, 27):
    w = math.sqrt(26 * 26 - yy * yy)
    c = GREEN if -12 <= yy < -5 else BLUE if 5 <= yy < 12 else YELLOW
    line((ox - w, oy + yy), (ox + w + 1, oy + yy), c)
for i, s in enumerate(["2", "4", "6", "8", "10"]):
    x, y = polar(ox, oy, (i + 1) * 60, 18); text(x, y, s, XTINY, BLACK)
hand(ox, oy, 6400 * 360 / 12000, [(-1.5, 5), (1.5, 5), (1, -23), (-1, -23)], BLACK); circle(ox, oy, 3, BLACK)
# bloc date (3 h)
ox = cx + 60
text(ox, cy - 24, "RACING", XTINY, WHITE); text(ox, cy + 24, "AUTOMATIC", XTINY, WHITE)
dow, day = "LUN", "9"
w1 = d.textlength(dow, font=XTINY) / SS + 6; w2 = d.textlength(day, font=SMALL) / SS
x0 = ox - (w1 + 8 + w2) / 2
d.rectangle([P(x0), P(cy - 9), P(x0 + w1), P(cy + 9)], outline=WHITE, width=SS)
text(x0 + w1 / 2, cy, dow, XTINY, WHITE)
line((x0 + w1 + 4, cy - 9), (x0 + w1 + 4, cy + 9), LGREY)
d.text((P(x0 + w1 + 8), P(cy)), day, font=SMALL, fill=WHITE, anchor="lm")
# aiguilles 10:09:37
hA, mA, sA = (10 + 9 / 60) * 30, 9 * 6 + 3.7, 37 * 6
hand(cx, cy, hA, [(-2.5, 12), (2.5, 12), (2.5, -18), (-2.5, -18)], LGREY)
hand(cx, cy, hA, [(-6, -16), (6, -16), (5.5, -54), (0, -60), (-5.5, -54)], GREY)
hand(cx, cy, hA, [(-4, -19), (4, -19), (3.5, -52), (0, -56), (-3.5, -52)], LUME)
hand(cx, cy, mA, [(-2, 14), (2, 14), (2, -30), (-2, -30)], LGREY)
hand(cx, cy, mA, [(-5, -28), (5, -28), (4.5, -83), (0, -89), (-4.5, -83)], GREY)
hand(cx, cy, mA, [(-3, -31), (3, -31), (2.5, -81), (0, -85), (-2.5, -81)], LUME)
hand(cx, cy, sA, [(-1.5, 24), (1.5, 24), (1, -106), (-1, -106)], YELLOW)
x, y = polar(cx, cy, sA + 180, 18); circle(x, y, 4, YELLOW); circle(cx, cy, 6, YELLOW); circle(cx, cy, 2, BLACK)

# masque rond (écran de la montre)
mask = Image.new("L", img.size, 0); ImageDraw.Draw(mask).ellipse([0, 0, img.size[0] - 1, img.size[1] - 1], fill=255)
out = Image.new("RGB", img.size, "#202020"); out.paste(img, (0, 0), mask)
out.resize((W * 2, W * 2), Image.LANCZOS).save(sys.argv[1] if len(sys.argv) > 1 else "preview.png")
if len(sys.argv) > 2:
    icon = Image.new("RGBA", img.size, (0, 0, 0, 0)); icon.paste(img, (0, 0), mask)
    icon.resize((40, 40), Image.LANCZOS).save(sys.argv[2])
