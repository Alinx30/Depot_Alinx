using Toybox.WatchUi as Ui;
using Toybox.Graphics as Gfx;
using Toybox.System as Sys;
using Toybox.Time;
using Toybox.Time.Gregorian;
using Toybox.ActivityMonitor;
using Toybox.Activity;
using Toybox.Math;

// Cadran "Racing Chrono" : chronographe noir et jaune, lunette tachymètre,
// trois compteurs (batterie, fréquence cardiaque, pas) et guichet date.
// Toutes les cotes sont données pour un écran de 260 px (Fenix 6 Pro)
// et mises à l'échelle par le facteur "s".
class RacingChronoView extends Ui.WatchFace {

    // Couleurs de la palette MIP 64 couleurs
    const YELLOW = 0xFFFF00;
    const GREEN  = 0x00AA00;
    const BLUE   = 0x0055FF;
    const RED    = 0xFF0000;
    const WHITE  = 0xFFFFFF;
    const LUME   = 0xAAFFFF;
    const GREY   = 0x555555;
    const LGREY  = 0xAAAAAA;
    const BLACK  = 0x000000;

    var cx = 130;
    var cy = 130;
    var s = 1.0;
    var awake = true;
    var tachy;
    var bezelLabel = "";
    var dialLabel = "";
    var dialLabel2 = "";

    function initialize() {
        Ui.WatchFace.initialize();
        tachy = [400, 300, 240, 200, 170, 150, 135, 120, 110, 100, 90, 85, 80, 75, 70, 65];
    }

    function onLayout(dc) {
        cx = dc.getWidth() / 2;
        cy = dc.getHeight() / 2;
        s = cx / 130.0;
        bezelLabel = Ui.loadResource(Rez.Strings.BezelLabel);
        dialLabel = Ui.loadResource(Rez.Strings.DialLabel);
        dialLabel2 = Ui.loadResource(Rez.Strings.DialLabel2);
    }

    function onExitSleep() {
        awake = true;
        Ui.requestUpdate();
    }

    function onEnterSleep() {
        awake = false;
        Ui.requestUpdate();
    }

    // ---------- Outils géométriques (angle en degrés, 0 = 12 h, sens horaire)

    function polar(ox, oy, a, rad) {
        var t = Math.toRadians(a);
        return [ox + rad * s * Math.sin(t), oy - rad * s * Math.cos(t)];
    }

    function tick(dc, ox, oy, a, r1, r2) {
        var p1 = polar(ox, oy, a, r1);
        var p2 = polar(ox, oy, a, r2);
        dc.drawLine(p1[0], p1[1], p2[0], p2[1]);
    }

    // pts : points dans le repère de l'aiguille (y négatif = vers la pointe)
    function fillHand(dc, ox, oy, a, pts, color) {
        var t = Math.toRadians(a);
        var sn = Math.sin(t);
        var cs = Math.cos(t);
        var out = new [pts.size()];
        for (var i = 0; i < pts.size(); i++) {
            var x = pts[i][0] * s;
            var y = pts[i][1] * s;
            out[i] = [ox + x * cs - y * sn, oy + x * sn + y * cs];
        }
        dc.setColor(color, Gfx.COLOR_TRANSPARENT);
        dc.fillPolygon(out);
    }

    function text(dc, x, y, font, str, color) {
        dc.setColor(color, Gfx.COLOR_TRANSPARENT);
        dc.drawText(x, y, font, str, Gfx.TEXT_JUSTIFY_CENTER | Gfx.TEXT_JUSTIFY_VCENTER);
    }

    // ---------- Rendu

    function onUpdate(dc) {
        var clock = Sys.getClockTime();

        dc.setColor(BLACK, BLACK);
        dc.clear();

        drawBezel(dc);
        drawFlange(dc);
        drawIndices(dc);
        drawBatteryDial(dc);
        drawHeartDial(dc);
        drawStepsDial(dc);
        drawDateBlock(dc);
        drawHands(dc, clock);
    }

    // Lunette tachymètre
    function drawBezel(dc) {
        dc.setPenWidth(2);
        dc.setColor(GREY, Gfx.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, 129 * s);
        dc.setPenWidth(1);

        for (var i = 0; i < tachy.size(); i++) {
            var a = 21600.0 / tachy[i];          // (3600 / T) secondes * 6°
            // rapproché du centre sur les côtés pour que le texte horizontal tienne
            var p = polar(cx, cy, a, 120 - 5 * Math.sin(Math.toRadians(a)).abs());
            text(dc, p[0], p[1], Gfx.FONT_XTINY, tachy[i].toString(), WHITE);
        }

        // Pointillés entre 65 et 400, interrompus par le texte du haut
        dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
        for (var a = -22; a <= 46; a += 6) {
            if (a > -16 && a < 16) {
                continue;
            }
            var p = polar(cx, cy, a, 120.5);
            dc.fillCircle(p[0], p[1], 1.5 * s);
        }
        text(dc, cx, cy - 120.5 * s, Gfx.FONT_XTINY, bezelLabel, YELLOW);
    }

    // Réhaut : graduation des minutes / secondes
    function drawFlange(dc) {
        for (var i = 0; i < 60; i++) {
            if (i % 5 == 0) {
                dc.setPenWidth(3);
                dc.setColor(YELLOW, Gfx.COLOR_TRANSPARENT);
                tick(dc, cx, cy, i * 6, 97, 107);
            } else {
                dc.setPenWidth(1);
                dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
                tick(dc, cx, cy, i * 6, 102, 107);
            }
        }
        dc.setPenWidth(1);
        dc.setColor(GREY, Gfx.COLOR_TRANSPARENT);
        dc.drawCircle(cx, cy, 95 * s);
    }

    // Index bâtons luminescents (12, 3, 6 et 9 h sont occupés par les compteurs)
    function drawIndices(dc) {
        var hours = [1, 2, 4, 5, 7, 8, 10, 11];
        for (var i = 0; i < hours.size(); i++) {
            var a = hours[i] * 30;
            dc.setPenWidth((8 * s).toNumber());
            dc.setColor(GREY, Gfx.COLOR_TRANSPARENT);
            tick(dc, cx, cy, a, 69, 91);
            dc.setPenWidth((5 * s).toNumber());
            dc.setColor(LUME, Gfx.COLOR_TRANSPARENT);
            tick(dc, cx, cy, a, 70.5, 89.5);
        }
        dc.setPenWidth(1);
    }

    // Compteur de 12 h : batterie (0-100 %)
    function drawBatteryDial(dc) {
        var ox = cx;
        var oy = cy - 50 * s;
        dc.setColor(GREY, Gfx.COLOR_TRANSPARENT);
        dc.drawCircle(ox, oy, 30 * s);
        for (var i = 0; i < 40; i++) {
            if (i % 10 == 0) {
                dc.setPenWidth(2);
                dc.setColor(YELLOW, Gfx.COLOR_TRANSPARENT);
                tick(dc, ox, oy, i * 9, 22, 29);
            } else {
                dc.setPenWidth(1);
                dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
                tick(dc, ox, oy, i * 9, 26, 29);
            }
        }
        dc.setPenWidth(1);
        var p = polar(ox, oy, 90, 15);
        text(dc, p[0], p[1], Gfx.FONT_XTINY, "25", WHITE);
        p = polar(ox, oy, 180, 15);
        text(dc, p[0], p[1], Gfx.FONT_XTINY, "50", WHITE);
        p = polar(ox, oy, 270, 15);
        text(dc, p[0], p[1], Gfx.FONT_XTINY, "75", WHITE);

        var bat = Sys.getSystemStats().battery;
        var color = bat < 15 ? RED : YELLOW;
        fillHand(dc, ox, oy, bat * 3.6, [[-1.5, 5], [1.5, 5], [1, -25], [-1, -25]], color);
        dc.setColor(color, Gfx.COLOR_TRANSPARENT);
        dc.fillCircle(ox, oy, 3 * s);
    }

    // Compteur de 9 h : fréquence cardiaque
    function drawHeartDial(dc) {
        var ox = cx - 52 * s;
        var oy = cy;
        dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
        for (var i = 0; i < 48; i++) {
            var p = polar(ox, oy, i * 7.5, 27);
            dc.fillCircle(p[0], p[1], 1);
        }
        dc.setColor(GREY, Gfx.COLOR_TRANSPARENT);
        dc.drawCircle(ox, oy, 30 * s);

        var hr = getHeartRate();
        text(dc, ox, oy - 4 * s, Gfx.FONT_SMALL, hr == null ? "--" : hr.toString(), YELLOW);
        text(dc, ox, oy + 14 * s, Gfx.FONT_XTINY, "BPM", LGREY);
    }

    function getHeartRate() {
        var info = Activity.getActivityInfo();
        if (info != null && info.currentHeartRate != null) {
            return info.currentHeartRate;
        }
        if (ActivityMonitor has :getHeartRateHistory) {
            var sample = ActivityMonitor.getHeartRateHistory(1, true).next();
            if (sample != null && sample.heartRate != ActivityMonitor.INVALID_HR_SAMPLE) {
                return sample.heartRate;
            }
        }
        return null;
    }

    // Compteur de 6 h : pas (0 à 12 000), fond à bandes jaune / vert / bleu
    function drawStepsDial(dc) {
        var ox = cx;
        var oy = cy + 52 * s;

        // anneau de graduations
        dc.setColor(YELLOW, Gfx.COLOR_TRANSPARENT);
        for (var i = 0; i < 60; i++) {
            dc.setPenWidth(i % 5 == 0 ? 2 : 1);
            tick(dc, ox, oy, i * 6, i % 5 == 0 ? 27.5 : 28.5, 31);
        }
        dc.setPenWidth(1);

        // disque à bandes, tracé ligne par ligne
        var rp = (26 * s).toNumber();
        for (var yy = -rp; yy <= rp; yy++) {
            var y = yy / s;
            var w = Math.sqrt(rp * rp - yy * yy);
            var c = YELLOW;
            if (y >= -12 && y < -5) {
                c = GREEN;
            } else if (y >= 5 && y < 12) {
                c = BLUE;
            }
            dc.setColor(c, Gfx.COLOR_TRANSPARENT);
            dc.drawLine(ox - w, oy + yy, ox + w + 1, oy + yy);
        }

        var labels = ["2", "4", "6", "8", "10"];
        for (var i = 0; i < 5; i++) {
            var p = polar(ox, oy, (i + 1) * 60, 18);
            text(dc, p[0], p[1], Gfx.FONT_XTINY, labels[i], BLACK);
        }

        var steps = 0;
        var info = ActivityMonitor.getInfo();
        if (info != null && info.steps != null) {
            steps = info.steps;
        }
        var a = steps * 360.0 / 12000;
        if (a > 359) {
            a = 359;
        }
        fillHand(dc, ox, oy, a, [[-1.5, 5], [1.5, 5], [1, -23], [-1, -23]], BLACK);
        dc.setColor(BLACK, Gfx.COLOR_TRANSPARENT);
        dc.fillCircle(ox, oy, 3 * s);
    }

    // Bloc texte côté 3 h : libellé, [JOUR] | date, libellé
    function drawDateBlock(dc) {
        var ox = cx + 60 * s;
        var now = Gregorian.info(Time.now(), Time.FORMAT_MEDIUM);
        var dow = now.day_of_week.toString().toUpper();
        if (dow.length() > 3) {
            dow = dow.substring(0, 3);
        }
        var day = now.day.toString();

        text(dc, ox, cy - 24 * s, Gfx.FONT_XTINY, dialLabel, WHITE);
        text(dc, ox, cy + 24 * s, Gfx.FONT_XTINY, dialLabel2, WHITE);

        var w1 = dc.getTextWidthInPixels(dow, Gfx.FONT_XTINY) + 6;
        var w2 = dc.getTextWidthInPixels(day, Gfx.FONT_SMALL);
        var x0 = ox - (w1 + 8 + w2) / 2;
        var h = (18 * s).toNumber();

        dc.setColor(WHITE, Gfx.COLOR_TRANSPARENT);
        dc.drawRectangle(x0, cy - h / 2, w1, h);
        text(dc, x0 + w1 / 2, cy, Gfx.FONT_XTINY, dow, WHITE);
        dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
        dc.drawLine(x0 + w1 + 4, cy - h / 2, x0 + w1 + 4, cy + h / 2);
        dc.setColor(WHITE, Gfx.COLOR_TRANSPARENT);
        dc.drawText(x0 + w1 + 8, cy, Gfx.FONT_SMALL, day, Gfx.TEXT_JUSTIFY_LEFT | Gfx.TEXT_JUSTIFY_VCENTER);
    }

    function drawHands(dc, clock) {
        var hAngle = ((clock.hour % 12) + clock.min / 60.0) * 30;
        var mAngle = clock.min * 6 + clock.sec * 0.1;

        // Heures
        fillHand(dc, cx, cy, hAngle, [[-2.5, 12], [2.5, 12], [2.5, -18], [-2.5, -18]], LGREY);
        fillHand(dc, cx, cy, hAngle, [[-6, -16], [6, -16], [5.5, -54], [0, -60], [-5.5, -54]], GREY);
        fillHand(dc, cx, cy, hAngle, [[-4, -19], [4, -19], [3.5, -52], [0, -56], [-3.5, -52]], LUME);

        // Minutes
        fillHand(dc, cx, cy, mAngle, [[-2, 14], [2, 14], [2, -30], [-2, -30]], LGREY);
        fillHand(dc, cx, cy, mAngle, [[-5, -28], [5, -28], [4.5, -83], [0, -89], [-4.5, -83]], GREY);
        fillHand(dc, cx, cy, mAngle, [[-3, -31], [3, -31], [2.5, -81], [0, -85], [-2.5, -81]], LUME);

        if (awake) {
            // Trotteuse jaune (uniquement quand la montre est "réveillée")
            var sAngle = clock.sec * 6;
            fillHand(dc, cx, cy, sAngle, [[-1.5, 24], [1.5, 24], [1, -106], [-1, -106]], YELLOW);
            var p = polar(cx, cy, sAngle + 180, 18);
            dc.fillCircle(p[0], p[1], 4 * s);
            dc.fillCircle(cx, cy, 6 * s);
        } else {
            dc.setColor(LGREY, Gfx.COLOR_TRANSPARENT);
            dc.fillCircle(cx, cy, 5 * s);
        }
        dc.setColor(BLACK, Gfx.COLOR_TRANSPARENT);
        dc.fillCircle(cx, cy, 2 * s);
    }
}
