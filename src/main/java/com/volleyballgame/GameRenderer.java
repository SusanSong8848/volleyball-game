package com.volleyballgame;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.Random;

/**
 * 2D Canvas 渲染器 - 含裁判、观众席、加油气泡
 */
public class GameRenderer {

    private GraphicsContext g;
    static final double SCALE = 36.0, COS30 = 0.8660254, SIN30 = 0.5;
    static final int OX = 200, OY = 530;

    // 观众数据（预生成80名观众位置和颜色）
    private static double[][] spectatorData = null;
    private static final Color[] SPECTATOR_COLORS = {
        Color.rgb(220, 60, 60), Color.rgb(60, 140, 60), Color.rgb(60, 60, 200),
        Color.rgb(220, 180, 40), Color.rgb(220, 120, 40), Color.rgb(160, 60, 200), Color.rgb(60, 180, 180)
    };

    public GameRenderer(GraphicsContext g, int w, int h) { this.g = g; initSpectators(); }

    public static double[] p(double x, double y, double z) {
        return new double[]{
            OX + (x + y) * COS30 * SCALE,
            OY + (x - y) * SIN30 * SCALE - z * SCALE
        };
    }

    public void render(GameCore core) {
        g.setGlobalAlpha(1.0);
        g.setFill(Color.rgb(20, 35, 70));
        g.fillRect(0, 0, 1280, 720);
        drawCourt();

        // 渲染顺序：观众席(最底层) → 裁判 → 场内元素(最上层) → 气泡
        // 观众席和裁判在球场外围，不应遮挡场内
        drawSpectators(core);

        // 裁判（中线左侧，场外）
        drawReferee(core);

        // 加油气泡（上半部分可在场地之上，先画球场再画气泡覆盖）
        drawCheerBubble(core);

        // === 场内元素（最上层） ===
        // 敌方球员
        PlayerData[] enSorted = core.enemyTeam.clone();
        java.util.Arrays.sort(enSorted, (a, b) -> Double.compare(b.y, a.y));
        g.setGlobalAlpha(0.72);
        for (PlayerData pl : enSorted) drawPlayer(pl);
        g.setGlobalAlpha(1.0);

        drawNetBack(core);

        if (core.ballY > 9.0) {
            if (core.ballZ < 2.43) g.setGlobalAlpha(0.7);
            drawBall(core);
            g.setGlobalAlpha(1.0);
        }

        drawNetFront(core);

        PlayerData[] mySorted = core.myTeam.clone();
        java.util.Arrays.sort(mySorted, (a, b) -> Double.compare(b.y, a.y));
        for (PlayerData pl : mySorted) drawPlayer(pl);

        if (core.ballY <= 9.0) drawBall(core);

        // 光环（最顶层场内元素）
        for (PlayerData pl : core.allPlayers) {
            if (pl.isHumanControlled) {
                double[] s = p(pl.x, pl.y, pl.z + 2.3);
                g.setStroke(Color.YELLOW); g.setLineWidth(4);
                g.strokeOval(s[0] - 16, s[1] - 16, 32, 32);
                g.setLineWidth(1.5);
                g.setStroke(Color.rgb(255, 255, 100, 0.7));
                double pulse = Math.sin(System.currentTimeMillis() / 150.0) * 3;
                g.strokeOval(s[0] - 10 - pulse, s[1] - 10 - pulse, 20 + pulse * 2, 20 + pulse * 2);
            }
        }
    }

    private void drawCourt() {
        double[][] grass = {p(-3, -2, -0.01), p(12, -2, -0.01), p(12, 20, -0.01), p(-3, 20, -0.01)};
        g.setFill(Color.rgb(35, 90, 45)); fillPoly(grass);
        fillRect(0, 0, 9, 9, Color.rgb(230, 175, 70));
        fillRect(0, 9, 9, 18, Color.rgb(210, 155, 50));
        g.setStroke(Color.WHITE); g.setLineWidth(2.5);
        double[][] b = {p(0, 0, 0), p(9, 0, 0), p(9, 18, 0), p(0, 18, 0)};
        strokePoly(b, true);
        g.strokeLine(p(0, 9, 0.01)[0], p(0, 9, 0.01)[1], p(9, 9, 0.01)[0], p(9, 9, 0.01)[1]);
        g.setLineWidth(1.5); g.setStroke(Color.rgb(235, 235, 235));
        g.strokeLine(p(0, 3, 0.01)[0], p(0, 3, 0.01)[1], p(9, 3, 0.01)[0], p(9, 3, 0.01)[1]);
        g.strokeLine(p(0, 15, 0.01)[0], p(0, 15, 0.01)[1], p(9, 15, 0.01)[0], p(9, 15, 0.01)[1]);
    }

    private void drawNetBack(GameCore core) {
        double[] bl = p(0, 9, 0), br = p(9, 9, 0);
        double[] tl = p(0, 9, 2.43), tr = p(9, 9, 2.43);
        g.setStroke(Color.rgb(90, 90, 95)); g.setLineWidth(6);
        g.strokeLine(bl[0], bl[1], tl[0], tl[1]);
        g.strokeLine(br[0], br[1], tr[0], tr[1]);
    }

    private void drawNetFront(GameCore core) {
        double h = 2.43;
        double[] bl = p(0, 9, 0), br = p(9, 9, 0);
        double[] tl = p(0, 9, h), tr = p(9, 9, h);
        double dent = 0;
        if (core.netHitTimer > 0) dent = (core.netHitTimer / 0.3) * 0.5;

        g.setFill(Color.rgb(200, 200, 230, 0.35));
        if (dent > 0.01) {
            double dx = core.netHitX;
            double[][] dpoly = {p(0, 9, 0), p(dx - 0.6, 9, 0), p(dx, 9 - dent, 2.43 * (dent / 0.5)), p(dx + 0.6, 9, 0), p(9, 9, 0), p(9, 9, h), p(0, 9, h)};
            fillPoly(dpoly);
        } else { fillPoly(new double[][]{bl, br, tr, tl}); }

        g.setStroke(Color.rgb(180, 180, 190, 0.5)); g.setLineWidth(1.5);
        g.strokeLine(bl[0], bl[1], br[0], br[1]);
        g.setStroke(Color.rgb(240, 240, 245, 0.8)); g.setLineWidth(5);
        g.strokeLine(tl[0], tl[1], tr[0], tr[1]);
        for (int i = 1; i <= 5; i++) {
            double hh = h * i / 6;
            g.setStroke(Color.rgb(200, 200, 220, 0.35)); g.setLineWidth(1);
            g.strokeLine(p(0, 9, hh)[0], p(0, 9, hh)[1], p(9, 9, hh)[0], p(9, 9, hh)[1]);
        }
        g.setStroke(Color.rgb(200, 200, 220, 0.2)); g.setLineWidth(0.8);
        for (int i = 1; i <= 8; i++) {
            double xx = i;
            g.strokeLine(p(xx, 9, 0.05)[0], p(xx, 9, 0.05)[1], p(xx, 9, h - 0.05)[0], p(xx, 9, h - 0.05)[1]);
        }
        double[] ptl = p(0, 9, h + 0.1), ptr = p(9, 9, h + 0.1);
        g.setFill(Color.rgb(240, 240, 240));
        g.fillOval(ptl[0] - 5, ptl[1] - 5, 10, 10); g.fillOval(ptr[0] - 5, ptr[1] - 5, 10, 10);
        g.setStroke(Color.rgb(200, 60, 60)); g.setLineWidth(2);
        g.strokeLine(ptl[0], ptl[1], ptl[0] - 12, ptl[1] - 30);
        g.strokeLine(ptr[0], ptr[1], ptr[0] + 12, ptr[1] - 30);
    }

    private void drawPlayer(PlayerData pl) {
        double fz = pl.z, ah = fz + 0.15, kh = fz + 0.55, hh = fz + 1.0;
        double ch = fz + 1.45, sh = fz + 1.8, nh = fz + 1.92, hd = fz + 2.07;
        boolean my = (pl.team == 0);
        Color jersey = my ? Color.rgb(50, 80, 220) : Color.rgb(220, 50, 50);
        Color jd = my ? Color.rgb(30, 55, 160) : Color.rgb(160, 30, 30);
        Color shorts = my ? Color.rgb(20, 40, 120) : Color.rgb(130, 20, 20);
        Color shoe = Color.rgb(35, 35, 40), skin = Color.rgb(250, 210, 170);

        double[] s = p(pl.x, pl.y, 0.02);
        g.setFill(Color.rgb(0, 0, 0, 0.3)); g.fillOval(s[0] - 11, s[1] - 6, 22, 8);

        double ba = pl.bodyAngle;
        double cosBA = Math.cos(ba), sinBA = Math.sin(ba);
        double ff = Math.max(0, -cosBA), bf = Math.max(0, cosBA), sf = Math.abs(sinBA);

        double rc = pl.runTimer * 14.0, loL = 0, loR = 0;
        if (pl.isMoving && Math.abs(pl.vz) < 0.1 && pl.z < 0.01) {
            loL = Math.sin(rc) * 0.16; loR = Math.sin(rc + Math.PI) * 0.16;
        }

        double[] hip = p(pl.x, pl.y, hh);
        double[] kl = pw(pl, -0.07 + loL, 0, kh), kr = pw(pl, 0.07 + loR, 0, kh);
        double[] al = pw(pl, -0.09 + loL * 2, 0, ah), ar = pw(pl, 0.09 + loR * 2, 0, ah);
        double[] sl = pw(pl, -0.11 + loL * 2, 0, fz + 0.03), sr = pw(pl, 0.11 + loR * 2, 0, fz + 0.03);

        g.setStroke(shorts); g.setLineWidth(6);
        g.strokeLine(hip[0], hip[1], kl[0], kl[1]); g.strokeLine(hip[0], hip[1], kr[0], kr[1]);
        g.setStroke(skin); g.setLineWidth(5);
        g.strokeLine(kl[0], kl[1], al[0], al[1]); g.strokeLine(kr[0], kr[1], ar[0], ar[1]);
        g.setStroke(shoe); g.setLineWidth(7);
        g.strokeLine(al[0], al[1], sl[0], sl[1]); g.strokeLine(ar[0], ar[1], sr[0], sr[1]);

        double[] hl = pw(pl, -0.13, 0, hh), hr = pw(pl, 0.13, 0, hh);
        g.setStroke(shorts); g.setLineWidth(9); g.strokeLine(hl[0], hl[1], hr[0], hr[1]);

        double td = 0.05 * (bf - ff);
        double[] chest = p(pl.x - td * pl.facingX, pl.y - td * pl.facingY, ch);
        double[] shoulder = p(pl.x - td * pl.facingX, pl.y - td * pl.facingY, sh);
        Color bc = (ff > 0.4) ? jersey : jd;
        g.setStroke(bc); g.setLineWidth(10);
        g.strokeLine(hip[0], hip[1], chest[0], chest[1]); g.setLineWidth(9);
        g.strokeLine(chest[0], chest[1], shoulder[0], shoulder[1]);

        if (ff > 0.5) {
            g.setFill(Color.WHITE); g.setFont(javafx.scene.text.Font.font("Arial", 10));
            g.fillText("" + (pl.index % 3 + 1), chest[0] - 3, chest[1] + 4);
        }

        double sw = 0.23 * (0.7 + 0.3 * (ff + sf));
        double[] ll = pw(pl, -sw, 0, sh), lr = pw(pl, sw, 0, sh);
        g.setStroke(bc); g.setLineWidth(6); g.strokeLine(ll[0], ll[1], lr[0], lr[1]);

        drawArms(pl, sh, ll, lr, skin, jersey);

        double[] neck = p(pl.x - td * 0.5 * pl.facingX, pl.y - td * 0.5 * pl.facingY, nh);
        g.setStroke(skin); g.setLineWidth(3.5); g.strokeLine(shoulder[0], shoulder[1], neck[0], neck[1]);

        double[] head = p(pl.x - td * 0.3 * pl.facingX, pl.y - td * 0.3 * pl.facingY, hd);
        drawHead(pl, head, 10.0, ff, bf, sf, sinBA);
    }

    private double[] pw(PlayerData pl, double lx, double ly, double z) {
        return p(pl.x + lx * pl.facingY + ly * pl.facingX, pl.y - lx * pl.facingX + ly * pl.facingY, z);
    }

    private void drawHead(PlayerData pl, double[] head, double r, double ff, double bf, double sf, double sd) {
        Color skin = Color.rgb(250, 210, 170), skinS = Color.rgb(220, 180, 145);
        Color hair = Color.rgb(45, 28, 18), hairH = Color.rgb(62, 40, 28);
        double hx = head[0], hy = head[1];
        g.setFill(skin); g.fillOval(hx - r, hy - r + 1, r * 2, r * 2 - 1);

        if (bf > 0.2) {
            g.setFill(hair); g.fillOval(hx - r * 1.05, hy - r * 1.05, r * 2.1, r * 2.1);
            g.setFill(hairH); g.fillOval(hx - r * 0.5, hy - r * 1.0, r * 1.8, r * 1.4);
            g.setFill(skinS); g.fillOval(hx - 3, hy + r - 2, 6, 7);
            if (sf > 0.4) { g.setFill(skin); g.fillOval((sd > 0 ? hx + r - 1 : hx - r - 3), hy - r + 3, 6, 9); }
        } else if (ff > 0.2) {
            g.setFill(hair); g.fillArc(hx - r - 1, hy - r - 2, r * 2 + 2, r * 1.6, 0, 180, javafx.scene.shape.ArcType.ROUND);
            g.fillOval(hx - r - 2, hy - r, 7, r * 2); g.fillOval(hx + r - 5, hy - r, 7, r * 2);
            g.setStroke(hairH); g.setLineWidth(1);
            for (int i = -3; i <= 3; i++) g.strokeLine(hx + i * 2.0, hy - r - 1, hx + i * 2.0, hy - r + 5);
            double fv = ff;
            if (fv > 0.3) { g.setStroke(Color.rgb(45, 28, 18, fv * 0.9)); g.setLineWidth(2.5); g.strokeLine(hx - 6, hy - 2, hx - 1, hy - 3); g.strokeLine(hx + 1, hy - 3, hx + 6, hy - 2); }
            if (fv > 0.4) { double ey = hy - 0.5; g.setFill(Color.rgb(250, 250, 250)); g.fillOval(hx - 6, ey - 2, 7, 5); g.fillOval(hx, ey - 2, 7, 5); g.setFill(Color.rgb(20, 20, 20)); g.fillOval(hx - 4, ey - 1, 3.5, 3.5); g.fillOval(hx + 1.5, ey - 1, 3.5, 3.5); g.setFill(Color.WHITE); g.fillOval(hx - 3, ey - 0.5, 1.5, 1.5); g.fillOval(hx + 2.5, ey - 0.5, 1.5, 1.5); }
            if (fv > 0.5) { g.setStroke(Color.rgb(210, 160, 130, fv * 0.7)); g.setLineWidth(1.5); g.strokeLine(hx - 0.5, hy + 1, hx - 1.5, hy + 5); g.strokeLine(hx + 0.5, hy + 1, hx + 1.5, hy + 5); g.strokeLine(hx - 1.5, hy + 5, hx + 1.5, hy + 5); }
            if (fv > 0.5) { g.setStroke(Color.rgb(190, 100, 85, fv * 0.8)); g.setLineWidth(2); g.strokeLine(hx - 3, hy + 7, hx + 3, hy + 7); }
            if (sf > 0.15) { g.setFill(skin); if (sd > 0) { g.fillOval(hx + r - 2, hy - r + 4, 6, 9); } else { g.fillOval(hx - r - 3, hy - r + 4, 6, 9); } }
        } else {
            g.setFill(hair); g.fillOval((sd > 0 ? hx - r : hx - 3), hy - r - 1, r + 4, r * 2 + 2);
            g.setFill(skin);
            if (sd > 0) { g.fillOval(hx + 1, hy - r, r * 2 - 3, r * 2); } else { g.fillOval(hx - r, hy - r, r * 2 - 3, r * 2); }
            double ex = (sd > 0) ? hx + 5 : hx - 4; g.setFill(Color.WHITE); g.fillOval(ex, hy - 3, 5, 4); g.setFill(Color.rgb(20, 20, 20)); g.fillOval(ex + 1, hy - 1.5, 2.5, 2.5);
            if (sd > 0) g.strokeLine(hx + r, hy + 1, hx + r + 2, hy + 4); else g.strokeLine(hx - r, hy + 1, hx - r - 2, hy + 4);
            g.setFill(skin); g.fillOval((sd > 0 ? hx + r - 2 : hx - r - 4), hy - r + 4, 6, 9);
        }
        if (ff < 0.3) { g.setFill(skinS); g.fillOval(hx - 3, hy + r - 3, 6, 6); }
    }

    private void drawArms(PlayerData pl, double sh, double[] sL, double[] sR, Color skin, Color jersey) {
        double al = 0.65, tk = 4.5, sp = pl.swingTimer > 0 ? 1.0 - pl.swingTimer / PlayerData.SWING_DURATION : 0;
        double[] uL = pw(pl, -0.23, 0, sh - 0.22), uR = pw(pl, 0.23, 0, sh - 0.22);
        g.setStroke(jersey); g.setLineWidth(4.5); g.strokeLine(sL[0], sL[1], uL[0], uL[1]); g.strokeLine(sR[0], sR[1], uR[0], uR[1]);
        g.setStroke(skin); g.setLineWidth(tk);
        if (pl.swingType == 1) {
            double ang = sp * 200, rad = Math.toRadians(ang - 100);
            double[] end = pw(pl, 0.23 + Math.cos(rad) * al, 0, Math.max(0, sh - 0.22 + Math.sin(rad) * al));
            g.strokeLine(uR[0], uR[1], end[0], end[1]);
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)); g.strokeLine(uL[0], uL[1], le[0], le[1]);
        } else if (pl.swingType == 2) {
            double ang = sp * 220, rad = Math.toRadians(80 - ang);
            double[] end = pw(pl, 0.23 + Math.cos(rad) * al, 0, Math.max(0, sh - 0.22 + Math.sin(rad) * al));
            g.strokeLine(uR[0], uR[1], end[0], end[1]);
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)); g.strokeLine(uL[0], uL[1], le[0], le[1]);
        } else {
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)), re = pw(pl, 0.23, 0, Math.max(0, sh - 0.22 - al));
            g.strokeLine(uL[0], uL[1], le[0], le[1]); g.strokeLine(uR[0], uR[1], re[0], re[1]);
        }
    }

    private void drawBall(GameCore core) {
        double[] pos = p(core.ballX, core.ballY, core.ballZ);
        double sx = pos[0], sy = pos[1], r = 8.0, h = core.ballZ;
        double[] gp = p(core.ballX, core.ballY, 0.02);
        double ss = 1.0 / (1.0 + h * 0.6), sa = 0.15 + 0.3 * ss;
        g.setFill(Color.rgb(0, 0, 0, sa));
        g.fillOval(gp[0] - Math.max(5, 16 * ss) / 2, gp[1] - Math.max(3, 7 * ss) / 2, Math.max(5, 16 * ss), Math.max(3, 7 * ss));
        if (h < 1.0) { g.setFill(Color.rgb(0, 0, 0, 0.25 * (1 - h))); g.fillOval(gp[0] - 5 * (1.5 - h * 0.5), gp[1] - 3, 10 * (1.5 - h * 0.5), 4); }
        if (h > 0.5) { g.setFill(Color.rgb(255, 255, 255, 0.5)); g.setFont(javafx.scene.text.Font.font("Arial", 9)); g.fillText(String.format("%.1fm", h), sx + r + 2, sy - 2); }

        g.setFill(Color.rgb(248, 248, 248)); g.fillOval(sx - r, sy - r, r * 2, r * 2);

        g.setFill(Color.rgb(255, 200, 40)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.7, sy - r * 0.9); g.quadraticCurveTo(sx, sy - r * 1.2, sx + r * 0.7, sy - r * 0.9);
        g.quadraticCurveTo(sx + r * 0.4, sy - r * 0.3, sx, sy - r * 0.1); g.quadraticCurveTo(sx - r * 0.4, sy - r * 0.3, sx - r * 0.7, sy - r * 0.9);
        g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx + r * 0.4, sy - r * 0.3); g.quadraticCurveTo(sx + r * 0.7, sy - r * 0.9, sx + r * 0.9, sy - r * 0.2);
        g.quadraticCurveTo(sx + r * 1.1, sy + r * 0.3, sx + r * 0.6, sy + r * 0.4);
        g.quadraticCurveTo(sx + r * 0.2, sy + r * 0.1, sx, sy - r * 0.1); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        g.setFill(Color.rgb(255, 200, 40)); g.save(); g.beginPath();
        g.moveTo(sx, sy - r * 0.1); g.quadraticCurveTo(sx + r * 0.2, sy + r * 0.1, sx + r * 0.6, sy + r * 0.4);
        g.quadraticCurveTo(sx + r * 0.8, sy + r * 0.8, sx + r * 0.2, sy + r * 0.9);
        g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.7, sx - r * 0.3, sy + r * 0.2); g.quadraticCurveTo(sx - r * 0.2, sy, sx, sy - r * 0.1);
        g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.3, sy + r * 0.2); g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.7, sx + r * 0.2, sy + r * 0.9);
        g.quadraticCurveTo(sx - r * 0.3, sy + r * 1.1, sx - r * 0.7, sy + r * 0.6); g.quadraticCurveTo(sx - r * 0.9, sy, sx - r * 0.7, sy - r * 0.4);
        g.quadraticCurveTo(sx - r * 0.5, sy - r * 0.1, sx - r * 0.3, sy + r * 0.2); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.7, sy - r * 0.9); g.quadraticCurveTo(sx - r * 0.4, sy - r * 0.3, sx - r * 0.5, sy);
        g.quadraticCurveTo(sx - r * 0.9, sy, sx - r * 0.9, sy - r * 0.4); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        g.setStroke(Color.rgb(50, 50, 60)); g.setLineWidth(1.8);
        g.beginPath(); g.moveTo(sx, sy - r * 0.95); g.quadraticCurveTo(sx - r * 0.5, sy - r * 0.5, sx - r * 0.6, sy); g.quadraticCurveTo(sx - r * 0.4, sy + r * 0.5, sx - r * 0.1, sy + r * 0.85); g.stroke();
        g.beginPath(); g.moveTo(sx, sy - r * 0.95); g.quadraticCurveTo(sx + r * 0.3, sy - r * 0.5, sx + r * 0.55, sy - r * 0.1); g.quadraticCurveTo(sx + r * 0.5, sy + r * 0.4, sx + r * 0.25, sy + r * 0.75); g.stroke();
        g.beginPath(); g.moveTo(sx - r * 0.65, sy - r * 0.1); g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.15, sx + r * 0.05, sy + r * 0.2); g.quadraticCurveTo(sx + r * 0.4, sy + r * 0.1, sx + r * 0.6, sy - r * 0.2); g.stroke();
        g.setStroke(Color.rgb(80, 80, 90)); g.setLineWidth(2.2); g.strokeOval(sx - r, sy - r, r * 2, r * 2);
        g.setFill(Color.rgb(255, 255, 255, 0.3)); g.fillOval(sx - r * 0.35, sy - r * 0.65, r * 0.8, r * 0.6);
    }

    private void fillPoly(double[][] pts) {
        double[] xs = new double[pts.length], ys = new double[pts.length];
        for (int i = 0; i < pts.length; i++) { xs[i] = pts[i][0]; ys[i] = pts[i][1]; }
        g.fillPolygon(xs, ys, pts.length);
    }
    private void strokePoly(double[][] pts, boolean c) {
        for (int i = 0; i < pts.length - 1; i++) g.strokeLine(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
        if (c) g.strokeLine(pts[0][0], pts[0][1], pts[pts.length - 1][0], pts[pts.length - 1][1]);
    }
    private void fillRect(double x0, double y0, double x1, double y1, Color c) {
        g.setFill(c); fillPoly(new double[][]{p(x0, y0, 0), p(x1, y0, 0), p(x1, y1, 0), p(x0, y1, 0)});
    }

    // ===================== 观众席 =====================
    private void initSpectators() {
        if (spectatorData != null) return;
        Random r = new Random(42);
        spectatorData = new double[200][5];
        // 四侧看台，每侧多排
        int idx = 0;
        // 上侧看台 (Y=18.2~19.8) 5排
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 10 && idx < 200; col++) {
                double sx = 0.5 + col * 0.9 + r.nextDouble() * 0.3;
                double sy = 18.2 + row * 0.35;
                spectatorData[idx][0] = sx;
                spectatorData[idx][1] = sy;
                spectatorData[idx][2] = row * 0.12;
                spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length);
                idx++;
            }
        }
        // 下侧看台 (Y=-1.5~-0.1) 4排
        for (int row = 0; row < 4 && idx < 200; row++) {
            for (int col = 0; col < 10 && idx < 200; col++) {
                double sx = 0.5 + col * 0.9 + r.nextDouble() * 0.3;
                double sy = -0.2 - row * 0.35;
                spectatorData[idx][0] = sx;
                spectatorData[idx][1] = sy;
                spectatorData[idx][2] = row * 0.12;
                spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length);
                idx++;
            }
        }
        // 左侧看台 (X=-1.5~-0.1) 5排
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 19 && idx < 200; col++) {
                double sx = -0.2 - row * 0.35;
                double sy = 0.5 + col * 0.95 + r.nextDouble() * 0.3;
                spectatorData[idx][0] = sx;
                spectatorData[idx][1] = sy;
                spectatorData[idx][2] = row * 0.12;
                spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length);
                idx++;
            }
        }
        // 右侧看台 (X=9.1~10.5) 5排
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 19 && idx < 200; col++) {
                double sx = 9.1 + row * 0.35;
                double sy = 0.5 + col * 0.95 + r.nextDouble() * 0.3;
                spectatorData[idx][0] = sx;
                spectatorData[idx][1] = sy;
                spectatorData[idx][2] = row * 0.12;
                spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length);
                idx++;
            }
        }
    }

    private void drawSpectators(GameCore core) {
        g.setGlobalAlpha(1.0);
        // 阶梯状看台地面（多层阶梯）
        Color standBase = Color.rgb(100, 100, 105);
        Color standStep = Color.rgb(115, 115, 120);
        int steps = 4;
        double stepH = 0.15;
        // 上侧阶梯看台
        for (int s = 0; s < steps; s++) {
            double y0 = 18.0 + s * 0.35;
            double y1 = y0 + 0.35;
            double zBase = s * stepH;
            fillRect(0, y0, 9, y1, (s % 2 == 0) ? standStep : standBase);
            // 阶梯竖面
            double[][] riser = {p(0, y1, zBase), p(9, y1, zBase), p(9, y1, zBase + stepH), p(0, y1, zBase + stepH)};
            g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
        }
        // 下侧阶梯看台
        for (int s = 0; s < steps; s++) {
            double y0 = -0.35 - s * 0.35;
            double y1 = y0 + 0.35;
            double zBase = s * stepH;
            fillRect(0, y0, 9, y1, (s % 2 == 0) ? standStep : standBase);
            double[][] riser = {p(0, y0, zBase), p(9, y0, zBase), p(9, y0, zBase + stepH), p(0, y0, zBase + stepH)};
            g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
        }
        // 左侧阶梯看台
        for (int s = 0; s < steps; s++) {
            double x0 = -0.35 - s * 0.35;
            double x1 = x0 + 0.35;
            double zBase = s * stepH;
            fillRect(x0, 0, x1, 18, (s % 2 == 0) ? standStep : standBase);
            double[][] riser = {p(x0, 0, zBase), p(x0, 18, zBase), p(x0, 18, zBase + stepH), p(x0, 0, zBase + stepH)};
            g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
        }
        // 右侧阶梯看台
        for (int s = 0; s < steps; s++) {
            double x0 = 9.0 + s * 0.35;
            double x1 = x0 + 0.35;
            double zBase = s * stepH;
            fillRect(x0, 0, x1, 18, (s % 2 == 0) ? standStep : standBase);
            double[][] riser = {p(x1, 0, zBase), p(x1, 18, zBase), p(x1, 18, zBase + stepH), p(x1, 0, zBase + stepH)};
            g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
        }

        // 画椅子 + 观众（更大）
        long time = System.currentTimeMillis();
        for (int i = 0; i < spectatorData.length; i++) {
            double[] sp = spectatorData[i];
            if (sp[0] == 0 && sp[1] == 0) continue;
            double sx = sp[0], sy = sp[1], sz = sp[2];
            Color cl = SPECTATOR_COLORS[(int) sp[3]];

            double[] chairBase = p(sx, sy, sz);
            double cx = chairBase[0], cy = chairBase[1];
            // 椅子面
            g.setFill(Color.rgb(40, 80, 40));
            g.fillRoundRect(cx - 7, cy - 9, 14, 11, 4, 4);
            // 椅背
            g.setStroke(Color.rgb(30, 60, 30));
            g.setLineWidth(2.5);
            g.strokeLine(cx - 5, cy - 9, cx - 5, cy - 16);
            g.strokeLine(cx + 5, cy - 9, cx + 5, cy - 16);
            // 椅背上横梁
            g.strokeLine(cx - 5, cy - 16, cx + 5, cy - 16);

            // 更大的观众
            double bob = Math.sin(time * 0.003 + sx * 4 + sy * 3) * 2;
            double[] sp2 = p(sx, sy, sz + 0.18 + bob * 0.04);
            double bx = sp2[0], by = sp2[1];
            // 身体
            g.setFill(cl);
            g.fillOval(bx - 5, by - 9, 10, 15);
            // 头
            g.setFill(Color.rgb(250, 210, 170));
            g.fillOval(bx - 4, by - 14, 8, 7);
        }
    }

    // ===================== 裁判 =====================
    private void drawReferee(GameCore core) {
        double rx = -0.6, ry = 9.0, rz = 1.2;
        Color greenC = Color.rgb(35, 120, 55);
        Color greenD = Color.rgb(25, 90, 40);

        // 绿色高椅 - 4条金属腿
        g.setStroke(Color.rgb(150, 150, 155));
        g.setLineWidth(3);
        double[][] legTops = {{rx-0.12, ry-0.06}, {rx+0.12, ry-0.06}, {rx-0.12, ry+0.06}, {rx+0.12, ry+0.06}};
        for (double[] lt : legTops) {
            double[] lb = p(lt[0], lt[1], 0);
            double[] ls = p(lt[0], lt[1], rz);
            g.strokeLine(lb[0], lb[1], ls[0], ls[1]);
        }
        // 椅子面（绿色）
        double[][] seatP = {p(rx-0.15, ry-0.06, rz), p(rx+0.15, ry-0.06, rz),
                            p(rx+0.15, ry+0.06, rz), p(rx-0.15, ry+0.06, rz)};
        g.setFill(greenC); fillPoly(seatP);
        g.setStroke(greenD); g.setLineWidth(1.5); strokePoly(seatP, true);
        // 椅子靠背（绿色，稍微向后倾斜）
        double backH = 0.3;
        double[][] backP = {p(rx-0.14, ry-0.06, rz), p(rx+0.14, ry-0.06, rz),
                            p(rx+0.12, ry-0.08, rz+backH), p(rx-0.12, ry-0.08, rz+backH)};
        g.setFill(greenD); fillPoly(backP);
        g.setStroke(Color.rgb(25, 80, 35)); g.setLineWidth(1.5); strokePoly(backP, true);
        // 靠背横梁
        for (int i = 0; i < 2; i++) {
            double hh = rz + backH * (0.3 + i * 0.35);
            double[] b1 = p(rx-0.13, ry-0.065, hh);
            double[] b2 = p(rx+0.13, ry-0.065, hh);
            g.setStroke(Color.rgb(25, 80, 35)); g.setLineWidth(2.5);
            g.strokeLine(b1[0], b1[1], b2[0], b2[1]);
        }

        // 黑色西服身体
        double bodyH = 0.75, bodyW = 0.14;
        double bh = rz + 0.28;
        Color blackC = Color.rgb(25, 25, 30);

        double[] bodyT = p(rx, ry, bh + bodyH);
        double[] bodyB = p(rx, ry, bh);
        g.setStroke(blackC); g.setLineWidth(8);
        g.strokeLine(bodyB[0], bodyB[1], bodyT[0], bodyT[1]);

        // 肩膀
        double[] shL = p(rx - bodyW, ry, bh + bodyH - 0.08);
        double[] shR = p(rx + bodyW, ry, bh + bodyH - 0.08);
        g.setStroke(blackC); g.setLineWidth(5);
        g.strokeLine(shL[0], shL[1], shR[0], shR[1]);

        // 头
        double hdZ = bh + bodyH + 0.12;
        double hdR = 7;
        double[] hd = p(rx, ry, hdZ);
        double hx = hd[0], hy = hd[1];

        g.setFill(Color.rgb(245, 205, 165));
        g.fillOval(hx - hdR, hy - hdR, hdR * 2, hdR * 2);

        // 黑色帽子
        g.setFill(Color.rgb(20, 20, 25));
        g.fillOval(hx - hdR - 1, hy - hdR - 3, hdR * 2 + 2, hdR * 1.2);
        g.fillRect(hx - hdR - 1, hy - hdR - 1, hdR * 2 + 2, 4);

        // 面部细节
        g.setFill(Color.WHITE);
        g.fillOval(hx - 4, hy - 3, 4, 3); g.fillOval(hx + 1, hy - 3, 4, 3);
        g.setFill(Color.rgb(20, 20, 20));
        g.fillOval(hx - 2.5, hy - 2.5, 2, 2); g.fillOval(hx + 2.5, hy - 2.5, 2, 2);
        g.setFill(Color.WHITE);
        g.fillOval(hx - 2, hy - 2.5, 0.8, 0.8); g.fillOval(hx + 3, hy - 2.5, 0.8, 0.8);
        g.setStroke(Color.rgb(200, 160, 130)); g.setLineWidth(1.2);
        g.strokeLine(hx, hy - 1, hx, hy + 1.5);
        g.setStroke(Color.rgb(180, 120, 100)); g.setLineWidth(1.5);
        g.strokeLine(hx - 2, hy + 3, hx + 2, hy + 3);
        // 哨子 + 绳子（平时挂在胸前，得分时举手到嘴边）
        boolean isScoring = core.refArmTimer > 0;
        double wX, wY, wZ;
        if (isScoring) {
            // 哨子在嘴巴附近（头中心hdZ约2.35，嘴巴在下方约-0.03）
            wX = rx; wY = ry - 0.01; wZ = hdZ - 0.06;
        } else {
            // 哨子挂在胸前
            wX = rx; wY = ry + 0.03; wZ = bh + bodyH * 0.35;
        }
        double[] whistlePos = p(wX, wY, wZ);
        double wx = whistlePos[0], wy = whistlePos[1];
        // 银灰色哨子
        g.setFill(Color.rgb(200, 200, 210));
        g.fillRoundRect(wx - 3, wy - 2, 6, 4, 2, 2);
        g.setStroke(Color.rgb(140, 140, 150)); g.setLineWidth(1);
        g.strokeRoundRect(wx - 3, wy - 2, 6, 4, 2, 2);
        // 黑色挂绳从脖子到胸口
        if (!isScoring) {
            double[] neckP = p(rx, ry - 0.01, hdZ - hdR * 0.3);
            g.setStroke(Color.rgb(30, 30, 35)); g.setLineWidth(1.2);
            g.strokeLine(neckP[0], neckP[1], wx, wy);
        }

        // 裁判挥手 - 平举指向得分方半场！
        if (core.refArmTimer > 0) {
            double progress = core.refArmTimer / 1.5;
            // 我方得分(0)→举右手平指向我方半场(-Y方向)
            // 敌方得分(1)→举左手平指向敌方半场(+Y方向)
            boolean raiseRight = (core.lastScorer == 0);
            boolean pointToMySide = (core.lastScorer == 0);
            double armAngle = Math.sin(progress * Math.PI) * 90;
            double armLen = 0.38;

            // 举的手
            double armBaseX = raiseRight ? rx + bodyW * 0.5 : rx - bodyW * 0.5;
            double armBaseZ = bh + bodyH - 0.1;
            double[] armStart = p(armBaseX, ry, armBaseZ);

            // 水平平举指向得分方半场(Y方向): 我方→-Y, 敌方→+Y
            double endY = ry + (pointToMySide ? -1 : 1) * armLen * 0.9;
            double endZ = armBaseZ + armLen * Math.sin(Math.toRadians(5));
            double[] armEnd = p(armBaseX, endY, endZ);
            g.setStroke(blackC); g.setLineWidth(4);
            g.strokeLine(armStart[0], armStart[1], armEnd[0], armEnd[1]);

            // 手
            double[] handEnd = p(armBaseX, endY + (pointToMySide ? -1 : 1) * 0.04, endZ);
            g.setFill(Color.rgb(245, 205, 165));
            g.fillOval(handEnd[0] - 3, handEnd[1] - 3, 6, 6);

            // 另一只手拿哨子到嘴边
            double otherBaseX = raiseRight ? rx - bodyW * 0.5 : rx + bodyW * 0.5;
            double[] otherStart = p(otherBaseX, ry, armBaseZ);
            double[] otherEnd = p(wX, wY + 0.01, wZ + 0.03);
            g.setStroke(blackC); g.setLineWidth(3);
            g.strokeLine(otherStart[0], otherStart[1], otherEnd[0], otherEnd[1]);
            g.setFill(Color.rgb(245, 205, 165));
            g.fillOval(otherEnd[0] - 2, otherEnd[1] - 2, 4, 4);
        } else {
            // 双臂自然下垂
            double armLen = 0.3;
            double lz = bh + bodyH - 0.05;
            double[] la = p(rx - bodyW * 0.5, ry, lz);
            double[] le = p(rx - bodyW * 0.5, ry, lz - armLen);
            double[] ra = p(rx + bodyW * 0.5, ry, lz);
            double[] re = p(rx + bodyW * 0.5, ry, lz - armLen);
            g.setStroke(blackC); g.setLineWidth(3.5);
            g.strokeLine(la[0], la[1], le[0], le[1]);
            g.strokeLine(ra[0], ra[1], re[0], re[1]);
            g.setFill(Color.rgb(245, 205, 165));
            g.fillOval(le[0] - 2, le[1] - 2, 4, 4);
            g.fillOval(re[0] - 2, re[1] - 2, 4, 4);
        }

        // 腿
        double legTopZ = bh - 0.1;
        double[] ll1 = p(rx - 0.06, ry + 0.06, legTopZ);
        double[] ll2 = p(rx - 0.07, ry + 0.08, legTopZ - 0.5);
        double[] rl1 = p(rx + 0.06, ry + 0.06, legTopZ);
        double[] rl2 = p(rx + 0.07, ry + 0.08, legTopZ - 0.5);
        g.setStroke(blackC); g.setLineWidth(4);
        g.strokeLine(ll1[0], ll1[1], ll2[0], ll2[1]);
        g.strokeLine(rl1[0], rl1[1], rl2[0], rl2[1]);
        g.setFill(Color.rgb(25, 25, 30));
        double[] sfp = p(rx - 0.07, ry + 0.08, legTopZ - 0.52);
        g.fillOval(sfp[0] - 4, sfp[1] - 2, 8, 4);
        sfp = p(rx + 0.07, ry + 0.08, legTopZ - 0.52);
        g.fillOval(sfp[0] - 4, sfp[1] - 2, 8, 4);

        // 阴影
        double[] shadow = p(rx, ry, 0.02);
        g.setFill(Color.rgb(0, 0, 0, 0.15));
        g.fillOval(shadow[0] - 8, shadow[1] - 4, 16, 6);
    }

    // ===================== 加油气泡 =====================
    private void drawCheerBubble(GameCore core) {
        if (core.cheerTimer <= 0 || core.currentCheer.isEmpty()) return;

        double[] bubbleWorld = p(core.cheerX, core.cheerY, 1.5);
        double bx = bubbleWorld[0], by = bubbleWorld[1];

        double floatY = Math.sin(core.cheerTimer * 5) * 8;
        by += floatY;

        double alpha = Math.min(1.0, core.cheerTimer / 0.3);
        if (core.cheerTimer < 0.5) alpha = core.cheerTimer / 0.5;

        String text = core.currentCheer;
        double textW = text.length() * 10;
        double bubbleW = textW + 16, bubbleH = 24;

        g.setGlobalAlpha(alpha);
        g.setFill(Color.rgb(255, 255, 255, 0.92));
        g.fillRoundRect(bx - bubbleW / 2, by - bubbleH / 2, bubbleW, bubbleH, 12, 12);

        g.setStroke(Color.rgb(180, 180, 190, alpha * 0.7));
        g.setLineWidth(1.5);
        g.strokeRoundRect(bx - bubbleW / 2, by - bubbleH / 2, bubbleW, bubbleH, 12, 12);

        double[] triX = {bx - 4, bx + 4, bx};
        double[] triY = {by + bubbleH / 2, by + bubbleH / 2, by + bubbleH / 2 + 8};
        g.setFill(Color.rgb(255, 255, 255, 0.92));
        g.fillPolygon(triX, triY, 3);

        g.setFill(Color.rgb(40, 40, 45));
        g.setFont(Font.font("Microsoft YaHei", 11));
        double tw = g.getFont().getSize() * text.length() * 0.7;
        g.fillText(text, bx - tw / 2, by + 4);

        g.setGlobalAlpha(1.0);
    }
}
