package com.volleyballgame;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import java.util.Random;

/**
 * 2D Canvas 渲染器 - 等距45°投影 + 完整场景元素绘制
 * 
 * 核心职责：
 *   1. 等距投影(世界坐标→屏幕坐标)：p(x,y,z) → (sx,sy)
 *   2. 场地绘制：黄木地板+白线边框+球网+深绿外围
 *   3. 球员绘制：360°头部+身体旋转+球衣+挥臂动画+跑动
 *   4. 球绘制：黄蓝Mikasa排球纹理+近大远小阴影+高度数值
 *   5. 裁判绘制：黑衣黑帽+绿色高椅+哨子+挥手动画
 *   6. 观众席绘制：四侧阶梯看台+多排绿色椅子+彩色观众
 *   7. 加油气泡绘制：白色圆角矩形+三角指针+浮动动画
 * 
 * 等距投影公式：
 *   sx = OX + (X+Y)×cos30°×SCALE
 *   sy = OY + (X-Y)×sin30°×SCALE - Z×SCALE
 *   参数：SCALE=36px/m, OX=200, OY=530
 * 
 * 渲染顺序(从后到前)：
 *   观众席+观众(最底层) → 裁判 → 气泡 → 敌方球员(半透明)
 *   → 球网后半 → 对方半场球 → 球网前半 → 我方球员 → 我方半场球 → 光环(最顶层)
 */
public class GameRenderer {

    private GraphicsContext g;

    // ==================== 等距投影常量 ====================
    /** 缩放比例(px/m)，将世界米转换为屏幕像素 */
    static final double SCALE = 36.0;
    /** cos30°≈0.866，等距投影X分量系数 */
    static final double COS30 = 0.8660254;
    /** sin30°=0.5，等距投影Y分量系数 */
    static final double SIN30 = 0.5;
    /** 投影原点X坐标(屏幕像素) */
    static final int OX = 200;
    /** 投影原点Y坐标(屏幕像素) */
    static final int OY = 530;

    // ==================== 观众数据 ====================
    /** 预计算约200名观众的位置和颜色，游戏启动时初始化一次 */
    private static double[][] spectatorData = null;
    /** 观众衣服颜色(7种鲜艳颜色) */
    private static final Color[] SPECTATOR_COLORS = {
        Color.rgb(220, 60, 60), Color.rgb(60, 140, 60), Color.rgb(60, 60, 200),
        Color.rgb(220, 180, 40), Color.rgb(220, 120, 40), Color.rgb(160, 60, 200), Color.rgb(60, 180, 180)
    };

    public GameRenderer(GraphicsContext g, int w, int h) { this.g = g; initSpectators(); }

    // ==================== 等距投影函数 ====================
    /**
     * 世界坐标(X,Y,Z) → 屏幕坐标(sx,sy)
     * 等距45°投影：X轴+Y轴都旋转并缩放，Z轴垂直向上
     */
    public static double[] p(double x, double y, double z) {
        return new double[]{
            OX + (x + y) * COS30 * SCALE,
            OY + (x - y) * SIN30 * SCALE - z * SCALE
        };
    }

    /** 世界坐标旋转：球员局部坐标转为世界坐标(用于身体旋转建模) */
    private double[] pw(PlayerData pl, double lx, double ly, double z) {
        return p(pl.x + lx * pl.facingY + ly * pl.facingX,
                 pl.y - lx * pl.facingX + ly * pl.facingY, z);
    }

    // ==================== 主渲染函数 ====================
    /** 渲染完整一帧(由GameCore的AnimationTimer每帧调用) */
    public void render(GameCore core) {
        g.setGlobalAlpha(1.0);
        // 深蓝色背景(代表天空/体育馆天花板)
        g.setFill(Color.rgb(20, 35, 70));
        g.fillRect(0, 0, 1280, 720);
        drawCourt();

        // ==== 渲染顺序：观众席(底层) → 裁判 → 气泡 → 球场内部(顶层) ====
        drawSpectators(core);   // 观众席+椅子+观众(场外，最底层)
        drawReferee(core);      // 裁判+椅子(场外中线左侧)
        drawCheerBubble(core);  // 加油气泡(场外观众席上方)

        // ==== 球场内部元素(从上到下) ====
        // 敌方球员(半透明，模拟被球网遮挡)
        PlayerData[] enSorted = core.enemyTeam.clone();
        java.util.Arrays.sort(enSorted, (a, b) -> Double.compare(b.y, a.y)); // 后排先画
        g.setGlobalAlpha(0.72);
        for (PlayerData pl : enSorted) drawPlayer(pl);
        g.setGlobalAlpha(1.0);

        drawNetBack(core); // 球网后半部分(地面底座)

        // 球在对方半场时画在网后(低于网高则半透明)
        if (core.ballY > 9.0) {
            if (core.ballZ < 2.43) g.setGlobalAlpha(0.7);
            drawBall(core);
            g.setGlobalAlpha(1.0);
        }

        drawNetFront(core); // 球网前半部分(网面+网线+立柱)

        // 我方球员(全透明，不被球网遮挡)
        PlayerData[] mySorted = core.myTeam.clone();
        java.util.Arrays.sort(mySorted, (a, b) -> Double.compare(b.y, a.y));
        for (PlayerData pl : mySorted) drawPlayer(pl);

        // 球在我方半场时画在最上层
        if (core.ballY <= 9.0) drawBall(core);

        // 操控光环(金色双层脉冲圆环，最顶层)
        for (PlayerData pl : core.allPlayers) {
            if (pl.isHumanControlled) {
                double[] s = p(pl.x, pl.y, pl.z + 2.3); // 头顶上方
                // 外层固定环
                g.setStroke(Color.YELLOW); g.setLineWidth(4);
                g.strokeOval(s[0] - 16, s[1] - 16, 32, 32);
                // 内层呼吸环(周期性缩放)
                g.setLineWidth(1.5);
                g.setStroke(Color.rgb(255, 255, 100, 0.7));
                double pulse = Math.sin(System.currentTimeMillis() / 150.0) * 3;
                g.strokeOval(s[0] - 10 - pulse, s[1] - 10 - pulse, 20 + pulse * 2, 20 + pulse * 2);
            }
        }
    }

    // ==================== 场地绘制 ====================
    /** 绘制排球场：黄地板+白线+外围绿草地 */
    private void drawCourt() {
        // 外围深绿"草地"
        double[][] grass = {p(-3, -2, -0.01), p(12, -2, -0.01), p(12, 20, -0.01), p(-3, 20, -0.01)};
        g.setFill(Color.rgb(35, 90, 45)); fillPoly(grass);
        // 我方半场(浅黄)
        fillRect(0, 0, 9, 9, Color.rgb(230, 175, 70));
        // 对方半场(稍深黄)
        fillRect(0, 9, 9, 18, Color.rgb(210, 155, 50));
        // 白色边框
        g.setStroke(Color.WHITE); g.setLineWidth(2.5);
        double[][] b = {p(0, 0, 0), p(9, 0, 0), p(9, 18, 0), p(0, 18, 0)};
        strokePoly(b, true);
        // 中线(球网投影)
        g.strokeLine(p(0, 9, 0.01)[0], p(0, 9, 0.01)[1], p(9, 9, 0.01)[0], p(9, 9, 0.01)[1]);
        // 进攻线(Y=3和Y=15)
        g.setLineWidth(1.5); g.setStroke(Color.rgb(235, 235, 235));
        g.strokeLine(p(0, 3, 0.01)[0], p(0, 3, 0.01)[1], p(9, 3, 0.01)[0], p(9, 3, 0.01)[1]);
        g.strokeLine(p(0, 15, 0.01)[0], p(0, 15, 0.01)[1], p(9, 15, 0.01)[0], p(9, 15, 0.01)[1]);
    }

    // ==================== 球网绘制 ====================
    /** 球网后半部分(立柱底面，先画) */
    private void drawNetBack(GameCore core) {
        double[] bl = p(0, 9, 0), br = p(9, 9, 0);
        double[] tl = p(0, 9, 2.43), tr = p(9, 9, 2.43);
        g.setStroke(Color.rgb(90, 90, 95)); g.setLineWidth(6);
        g.strokeLine(bl[0], bl[1], tl[0], tl[1]); // 左侧立柱
        g.strokeLine(br[0], br[1], tr[0], tr[1]); // 右侧立柱
    }

    /** 球网前半部分(网面+横线+纵线+立柱顶部) */
    private void drawNetFront(GameCore core) {
        double h = 2.43;
        double[] bl = p(0, 9, 0), br = p(9, 9, 0);
        double[] tl = p(0, 9, h), tr = p(9, 9, h);

        // 球网形变：碰网时产生0.5m凹陷动画
        double dent = 0;
        if (core.netHitTimer > 0) dent = (core.netHitTimer / 0.3) * 0.5;

        // 半透明网面(平行四边形)
        g.setFill(Color.rgb(200, 200, 230, 0.35));
        if (dent > 0.01) {
            double dx = core.netHitX;
            double[][] dpoly = {p(0, 9, 0), p(dx - 0.6, 9, 0), p(dx, 9 - dent, 2.43 * (dent / 0.5)),
                                p(dx + 0.6, 9, 0), p(9, 9, 0), p(9, 9, h), p(0, 9, h)};
            fillPoly(dpoly);
        } else {
            fillPoly(new double[][]{bl, br, tr, tl});
        }

        // 底部边线
        g.setStroke(Color.rgb(180, 180, 190, 0.5)); g.setLineWidth(1.5);
        g.strokeLine(bl[0], bl[1], br[0], br[1]);
        // 顶部粗线
        g.setStroke(Color.rgb(240, 240, 245, 0.8)); g.setLineWidth(5);
        g.strokeLine(tl[0], tl[1], tr[0], tr[1]);
        // 5条横线(水平分隔)
        for (int i = 1; i <= 5; i++) {
            double hh = h * i / 6;
            g.setStroke(Color.rgb(200, 200, 220, 0.35)); g.setLineWidth(1);
            g.strokeLine(p(0, 9, hh)[0], p(0, 9, hh)[1], p(9, 9, hh)[0], p(9, 9, hh)[1]);
        }
        // 8条纵线(垂直分隔)
        g.setStroke(Color.rgb(200, 200, 220, 0.2)); g.setLineWidth(0.8);
        for (int i = 1; i <= 8; i++) {
            double xx = i;
            g.strokeLine(p(xx, 9, 0.05)[0], p(xx, 9, 0.05)[1], p(xx, 9, h - 0.05)[0], p(xx, 9, h - 0.05)[1]);
        }
        // 两根立柱顶部球体(白色小球)
        double[] ptl = p(0, 9, h + 0.1), ptr = p(9, 9, h + 0.1);
        g.setFill(Color.rgb(240, 240, 240));
        g.fillOval(ptl[0] - 5, ptl[1] - 5, 10, 10);
        g.fillOval(ptr[0] - 5, ptr[1] - 5, 10, 10);
        // 红色天线(斜向外的两根线)
        g.setStroke(Color.rgb(200, 60, 60)); g.setLineWidth(2);
        g.strokeLine(ptl[0], ptl[1], ptl[0] - 12, ptl[1] - 30);
        g.strokeLine(ptr[0], ptr[1], ptr[0] + 12, ptr[1] - 30);
    }

    // ==================== 球员绘制(最复杂的渲染逻辑) ====================
    /**
     * 绘制单个球员：阴影→腿→短裤→躯干→球衣号码→肩膀→手臂→脖子→头部
     * 所有部位通过pw()实现围绕朝向中心轴的旋转
     * 正面(facing→镜头)显示完整五官，背面显示后脑勺头发
     */
    private void drawPlayer(PlayerData pl) {
        // 身体各部位高度(Z坐标)
        double fz = pl.z, ah = fz + 0.15, kh = fz + 0.55, hh = fz + 1.0;
        double ch = fz + 1.45, sh = fz + 1.8, nh = fz + 1.92, hd = fz + 2.07;

        boolean my = (pl.team == 0);
        // 球衣颜色：我方蓝色，敌方红色
        Color jersey = my ? Color.rgb(50, 80, 220) : Color.rgb(220, 50, 50);
        Color jd = my ? Color.rgb(30, 55, 160) : Color.rgb(160, 30, 30); // 背面暗色
        Color shorts = my ? Color.rgb(20, 40, 120) : Color.rgb(130, 20, 20);
        Color shoe = Color.rgb(35, 35, 40), skin = Color.rgb(250, 210, 170);

        // 地面椭圆阴影
        double[] s = p(pl.x, pl.y, 0.02);
        g.setFill(Color.rgb(0, 0, 0, 0.3)); g.fillOval(s[0] - 11, s[1] - 6, 22, 8);

        // 身体朝向分量(cos=正面程度, sin=侧面程度)
        double ba = pl.bodyAngle;
        double cosBA = Math.cos(ba), sinBA = Math.sin(ba);
        double ff = Math.max(0, -cosBA); // 正面面对镜头程度
        double bf = Math.max(0, cosBA);  // 背面朝向镜头程度
        double sf = Math.abs(sinBA);     // 侧面程度

        // 跑动时的腿部摆动(只有在地面且无跳跃时)
        double rc = pl.runTimer * 14.0, loL = 0, loR = 0;
        if (pl.isMoving && Math.abs(pl.vz) < 0.1 && pl.z < 0.01) {
            loL = Math.sin(rc) * 0.16;          // 左腿摆动(+X)
            loR = Math.sin(rc + Math.PI) * 0.16; // 右腿摆动(反相位)
        }

        // 关键关节点(使用pw进行身体旋转)
        double[] hip = p(pl.x, pl.y, hh); // 臀部中心
        double[] kl = pw(pl, -0.07 + loL, 0, kh), kr = pw(pl, 0.07 + loR, 0, kh); // 双膝
        double[] al = pw(pl, -0.09 + loL * 2, 0, ah), ar = pw(pl, 0.09 + loR * 2, 0, ah); // 双踝
        double[] sl = pw(pl, -0.11 + loL * 2, 0, fz + 0.03), sr = pw(pl, 0.11 + loR * 2, 0, fz + 0.03); // 双脚

        // 绘制短裤(大腿)
        g.setStroke(shorts); g.setLineWidth(6);
        g.strokeLine(hip[0], hip[1], kl[0], kl[1]);
        g.strokeLine(hip[0], hip[1], kr[0], kr[1]);
        // 绘制小腿(肤色)
        g.setStroke(skin); g.setLineWidth(5);
        g.strokeLine(kl[0], kl[1], al[0], al[1]);
        g.strokeLine(kr[0], kr[1], ar[0], ar[1]);
        // 绘制鞋子(深色)
        g.setStroke(shoe); g.setLineWidth(7);
        g.strokeLine(al[0], al[1], sl[0], sl[1]);
        g.strokeLine(ar[0], ar[1], sr[0], sr[1]);

        // 臀部横向线
        double[] hl = pw(pl, -0.13, 0, hh), hr = pw(pl, 0.13, 0, hh);
        g.setStroke(shorts); g.setLineWidth(9);
        g.strokeLine(hl[0], hl[1], hr[0], hr[1]);

        // 躯干(根据朝向微调偏移，正面躯干稍靠前)
        double td = 0.05 * (bf - ff);
        double[] chest = p(pl.x - td * pl.facingX, pl.y - td * pl.facingY, ch); // 胸部
        double[] shoulder = p(pl.x - td * pl.facingX, pl.y - td * pl.facingY, sh); // 肩膀
        // 球衣颜色：正面亮，背面暗
        Color bc = (ff > 0.4) ? jersey : jd;
        g.setStroke(bc); g.setLineWidth(10);
        g.strokeLine(hip[0], hip[1], chest[0], chest[1]); // 腰→胸
        g.setLineWidth(9);
        g.strokeLine(chest[0], chest[1], shoulder[0], shoulder[1]); // 胸→肩

        // 球衣号码(仅在正面面对镜头时显示)
        if (ff > 0.5) {
            g.setFill(Color.WHITE);
            g.setFont(javafx.scene.text.Font.font("Arial", 10));
            g.fillText("" + (pl.index % 3 + 1), chest[0] - 3, chest[1] + 4);
        }

        // 肩膀横线(宽度随视角动态变化：正面最宽，侧面最窄)
        double sw = 0.23 * (0.7 + 0.3 * (ff + sf));
        double[] ll = pw(pl, -sw, 0, sh), lr = pw(pl, sw, 0, sh);
        g.setStroke(bc); g.setLineWidth(6);
        g.strokeLine(ll[0], ll[1], lr[0], lr[1]);

        // 绘制手臂(包含挥臂动画)
        drawArms(pl, sh, ll, lr, skin, jersey);

        // 脖子(肤色)
        double[] neck = p(pl.x - td * 0.5 * pl.facingX, pl.y - td * 0.5 * pl.facingY, nh);
        g.setStroke(skin); g.setLineWidth(3.5);
        g.strokeLine(shoulder[0], shoulder[1], neck[0], neck[1]);

        // 头部(含360°旋转五官)
        double[] head = p(pl.x - td * 0.3 * pl.facingX, pl.y - td * 0.3 * pl.facingY, hd);
        drawHead(pl, head, 10.0, ff, bf, sf, sinBA);
    }

    /**
     * 绘制360°全方位头部：正面全五官，背面后脑勺头发，侧面单眼+耳朵
     * @param ff 正面面对镜头程度(0~1)
     * @param bf 背面程度(0~1)
     * @param sf 侧面程度(0~1)
     * @param sd sinBA的正负表示左右方向
     */
    private void drawHead(PlayerData pl, double[] head, double r, double ff, double bf, double sf, double sd) {
        Color skin = Color.rgb(250, 210, 170), skinS = Color.rgb(220, 180, 145); // 肤色/阴影
        Color hair = Color.rgb(45, 28, 18), hairH = Color.rgb(62, 40, 28); // 头发
        double hx = head[0], hy = head[1];

        // 肤色底圆
        g.setFill(skin); g.fillOval(hx - r, hy - r + 1, r * 2, r * 2 - 1);

        if (bf > 0.2) {
            // ==== 背面 ====
            g.setFill(hair); g.fillOval(hx - r * 1.05, hy - r * 1.05, r * 2.1, r * 2.1); // 头发全覆盖
            g.setFill(hairH); g.fillOval(hx - r * 0.5, hy - r * 1.0, r * 1.8, r * 1.4); // 头发高光
            g.setFill(skinS); g.fillOval(hx - 3, hy + r - 2, 6, 7); // 后颈部
            if (sf > 0.4) { g.setFill(skin); g.fillOval((sd > 0 ? hx + r - 1 : hx - r - 3), hy - r + 3, 6, 9); } // 侧面耳
        } else if (ff > 0.2) {
            // ==== 正面：完整五官 ====
            g.setFill(hair); g.fillArc(hx - r - 1, hy - r - 2, r * 2 + 2, r * 1.6, 0, 180, javafx.scene.shape.ArcType.ROUND); // 上部头发
            g.fillOval(hx - r - 2, hy - r, 7, r * 2); g.fillOval(hx + r - 5, hy - r, 7, r * 2); // 两侧头发
            g.setStroke(hairH); g.setLineWidth(1);
            for (int i = -3; i <= 3; i++) g.strokeLine(hx + i * 2.0, hy - r - 1, hx + i * 2.0, hy - r + 5); // 刘海
            double fv = ff;
            if (fv > 0.3) { // 眉毛
                g.setStroke(Color.rgb(45, 28, 18, fv * 0.9)); g.setLineWidth(2.5);
                g.strokeLine(hx - 6, hy - 2, hx - 1, hy - 3); g.strokeLine(hx + 1, hy - 3, hx + 6, hy - 2);
            }
            if (fv > 0.4) { // 双眼(白色底+瞳孔+高光)
                double ey = hy - 0.5;
                g.setFill(Color.rgb(250, 250, 250)); g.fillOval(hx - 6, ey - 2, 7, 5); g.fillOval(hx, ey - 2, 7, 5);
                g.setFill(Color.rgb(20, 20, 20)); g.fillOval(hx - 4, ey - 1, 3.5, 3.5); g.fillOval(hx + 1.5, ey - 1, 3.5, 3.5);
                g.setFill(Color.WHITE); g.fillOval(hx - 3, ey - 0.5, 1.5, 1.5); g.fillOval(hx + 2.5, ey - 0.5, 1.5, 1.5);
            }
            if (fv > 0.5) { // 鼻子+微笑线+嘴
                g.setStroke(Color.rgb(210, 160, 130, fv * 0.7)); g.setLineWidth(1.5);
                g.strokeLine(hx - 0.5, hy + 1, hx - 1.5, hy + 5); g.strokeLine(hx + 0.5, hy + 1, hx + 1.5, hy + 5);
                g.strokeLine(hx - 1.5, hy + 5, hx + 1.5, hy + 5);
            }
            if (fv > 0.5) { // 嘴巴
                g.setStroke(Color.rgb(190, 100, 85, fv * 0.8)); g.setLineWidth(2);
                g.strokeLine(hx - 3, hy + 7, hx + 3, hy + 7);
            }
            if (sf > 0.15) { g.setFill(skin); if (sd > 0) { g.fillOval(hx + r - 2, hy - r + 4, 6, 9); } else { g.fillOval(hx - r - 3, hy - r + 4, 6, 9); } } // 侧面耳
        } else {
            // ==== 侧面 ====
            g.setFill(hair); g.fillOval((sd > 0 ? hx - r : hx - 3), hy - r - 1, r + 4, r * 2 + 2); // 半边头发
            g.setFill(skin);
            if (sd > 0) { g.fillOval(hx + 1, hy - r, r * 2 - 3, r * 2); } else { g.fillOval(hx - r, hy - r, r * 2 - 3, r * 2); }
            // 单眼
            double ex = (sd > 0) ? hx + 5 : hx - 4;
            g.setFill(Color.WHITE); g.fillOval(ex, hy - 3, 5, 4);
            g.setFill(Color.rgb(20, 20, 20)); g.fillOval(ex + 1, hy - 1.5, 2.5, 2.5);
            // 鼻子轮廓
            if (sd > 0) g.strokeLine(hx + r, hy + 1, hx + r + 2, hy + 4);
            else g.strokeLine(hx - r, hy + 1, hx - r - 2, hy + 4);
            // 耳朵
            g.setFill(skin); g.fillOval((sd > 0 ? hx + r - 2 : hx - r - 4), hy - r + 4, 6, 9);
        }
        // 后颈部(不面对镜头时)
        if (ff < 0.3) { g.setFill(skinS); g.fillOval(hx - 3, hy + r - 3, 6, 6); }
    }

    /** 绘制球员手臂(含挥臂动画)：传球=顺时针挥臂，扣球=逆时针挥臂 */
    private void drawArms(PlayerData pl, double sh, double[] sL, double[] sR, Color skin, Color jersey) {
        double al = 0.65, tk = 4.5; // 臂长，线宽
        // 挥臂进度(0=开始, 1=结束)
        double sp = pl.swingTimer > 0 ? 1.0 - pl.swingTimer / PlayerData.SWING_DURATION : 0;
        // 上臂(短袖部分)
        double[] uL = pw(pl, -0.23, 0, sh - 0.22), uR = pw(pl, 0.23, 0, sh - 0.22);
        g.setStroke(jersey); g.setLineWidth(4.5); g.strokeLine(sL[0], sL[1], uL[0], uL[1]); g.strokeLine(sR[0], sR[1], uR[0], uR[1]);
        g.setStroke(skin); g.setLineWidth(tk);

        if (pl.swingType == 1) {
            // 传球(顺时针挥右臂，从后方向前上方)
            double ang = sp * 200, rad = Math.toRadians(ang - 100);
            double[] end = pw(pl, 0.23 + Math.cos(rad) * al, 0, Math.max(0, sh - 0.22 + Math.sin(rad) * al));
            g.strokeLine(uR[0], uR[1], end[0], end[1]);
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)); g.strokeLine(uL[0], uL[1], le[0], le[1]);
        } else if (pl.swingType == 2) {
            // 扣球(逆时针挥右臂，从下方向上)
            double ang = sp * 220, rad = Math.toRadians(80 - ang);
            double[] end = pw(pl, 0.23 + Math.cos(rad) * al, 0, Math.max(0, sh - 0.22 + Math.sin(rad) * al));
            g.strokeLine(uR[0], uR[1], end[0], end[1]);
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)); g.strokeLine(uL[0], uL[1], le[0], le[1]);
        } else {
            // 自然下垂
            double[] le = pw(pl, -0.23, 0, Math.max(0, sh - 0.22 - al)), re = pw(pl, 0.23, 0, Math.max(0, sh - 0.22 - al));
            g.strokeLine(uL[0], uL[1], le[0], le[1]); g.strokeLine(uR[0], uR[1], re[0], re[1]);
        }
    }

    // ==================== 球绘制 ====================
    /** 绘制黄蓝Mikasa排球 + 近大远小阴影 + 离地高度数值 */
    private void drawBall(GameCore core) {
        double[] pos = p(core.ballX, core.ballY, core.ballZ);
        double sx = pos[0], sy = pos[1], r = 8.0, h = core.ballZ;

        // --------- 球阴影：近大远小显著变化 ---------
        double[] gp = p(core.ballX, core.ballY, 0.02); // 球正下方地面投影
        double shadowR = Math.max(3, r * (1.5 - h * 0.45)); // h=0→12px, 高空→3px
        double shadowAlpha = Math.max(0.06, 0.55 / (1.0 + h * 0.35));
        g.setFill(Color.rgb(0, 0, 0, shadowAlpha));
        g.fillOval(gp[0] - shadowR, gp[1] - shadowR * 0.5, shadowR * 2, shadowR);
        // 近地面(h<0.8m)额外加深圈
        if (h < 0.8) {
            double nearAlpha = 0.30 * (0.8 - h) / 0.8;
            double nearR = shadowR * (1.0 + (0.8 - h) / 0.8 * 0.4);
            g.setFill(Color.rgb(0, 0, 0, nearAlpha));
            g.fillOval(gp[0] - nearR, gp[1] - nearR * 0.5, nearR * 2, nearR);
        }
        // 离地高度数值(如"2.3m")
        if (h > 0.5) {
            g.setFill(Color.rgb(255, 255, 255, 0.5));
            g.setFont(javafx.scene.text.Font.font("Arial", 9));
            g.fillText(String.format("%.1fm", h), sx + r + 2, sy - 2);
        }

        // --------- 球体：白色底球 ---------
        g.setFill(Color.rgb(248, 248, 248)); g.fillOval(sx - r, sy - r, r * 2, r * 2);

        // --------- 5块黄蓝交替弧形面板(clip + fillOval) ---------
        // 第1块黄色面板(顶部)
        g.setFill(Color.rgb(255, 200, 40)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.7, sy - r * 0.9); g.quadraticCurveTo(sx, sy - r * 1.2, sx + r * 0.7, sy - r * 0.9);
        g.quadraticCurveTo(sx + r * 0.4, sy - r * 0.3, sx, sy - r * 0.1); g.quadraticCurveTo(sx - r * 0.4, sy - r * 0.3, sx - r * 0.7, sy - r * 0.9);
        g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        // 第2块蓝色面板(右上方)
        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx + r * 0.4, sy - r * 0.3); g.quadraticCurveTo(sx + r * 0.7, sy - r * 0.9, sx + r * 0.9, sy - r * 0.2);
        g.quadraticCurveTo(sx + r * 1.1, sy + r * 0.3, sx + r * 0.6, sy + r * 0.4);
        g.quadraticCurveTo(sx + r * 0.2, sy + r * 0.1, sx, sy - r * 0.1); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        // 第3块黄色面板(右下方)
        g.setFill(Color.rgb(255, 200, 40)); g.save(); g.beginPath();
        g.moveTo(sx, sy - r * 0.1); g.quadraticCurveTo(sx + r * 0.2, sy + r * 0.1, sx + r * 0.6, sy + r * 0.4);
        g.quadraticCurveTo(sx + r * 0.8, sy + r * 0.8, sx + r * 0.2, sy + r * 0.9);
        g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.7, sx - r * 0.3, sy + r * 0.2); g.quadraticCurveTo(sx - r * 0.2, sy, sx, sy - r * 0.1);
        g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        // 第4块蓝色面板(左下方)
        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.3, sy + r * 0.2); g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.7, sx + r * 0.2, sy + r * 0.9);
        g.quadraticCurveTo(sx - r * 0.3, sy + r * 1.1, sx - r * 0.7, sy + r * 0.6); g.quadraticCurveTo(sx - r * 0.9, sy, sx - r * 0.7, sy - r * 0.4);
        g.quadraticCurveTo(sx - r * 0.5, sy - r * 0.1, sx - r * 0.3, sy + r * 0.2); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        // 第5块蓝色面板(左上方)
        g.setFill(Color.rgb(25, 65, 175)); g.save(); g.beginPath();
        g.moveTo(sx - r * 0.7, sy - r * 0.9); g.quadraticCurveTo(sx - r * 0.4, sy - r * 0.3, sx - r * 0.5, sy);
        g.quadraticCurveTo(sx - r * 0.9, sy, sx - r * 0.9, sy - r * 0.4); g.closePath(); g.clip(); g.fillOval(sx - r, sy - r, r * 2, r * 2); g.restore();

        // --------- 4条接缝线(二次贝塞尔曲线) ---------
        g.setStroke(Color.rgb(50, 50, 60)); g.setLineWidth(1.8);
        g.beginPath(); g.moveTo(sx, sy - r * 0.95); g.quadraticCurveTo(sx - r * 0.5, sy - r * 0.5, sx - r * 0.6, sy);
        g.quadraticCurveTo(sx - r * 0.4, sy + r * 0.5, sx - r * 0.1, sy + r * 0.85); g.stroke();
        g.beginPath(); g.moveTo(sx, sy - r * 0.95); g.quadraticCurveTo(sx + r * 0.3, sy - r * 0.5, sx + r * 0.55, sy - r * 0.1);
        g.quadraticCurveTo(sx + r * 0.5, sy + r * 0.4, sx + r * 0.25, sy + r * 0.75); g.stroke();
        g.beginPath(); g.moveTo(sx - r * 0.65, sy - r * 0.1); g.quadraticCurveTo(sx - r * 0.3, sy + r * 0.15, sx + r * 0.05, sy + r * 0.2);
        g.quadraticCurveTo(sx + r * 0.4, sy + r * 0.1, sx + r * 0.6, sy - r * 0.2); g.stroke();
        // 外圆边框
        g.setStroke(Color.rgb(80, 80, 90)); g.setLineWidth(2.2); g.strokeOval(sx - r, sy - r, r * 2, r * 2);
        // 高光点(左上角白色斑)
        g.setFill(Color.rgb(255, 255, 255, 0.3)); g.fillOval(sx - r * 0.35, sy - r * 0.65, r * 0.8, r * 0.6);
    }

    // ==================== 辅助绘图方法 ====================
    /** 填充多边形 */
    private void fillPoly(double[][] pts) {
        double[] xs = new double[pts.length], ys = new double[pts.length];
        for (int i = 0; i < pts.length; i++) { xs[i] = pts[i][0]; ys[i] = pts[i][1]; }
        g.fillPolygon(xs, ys, pts.length);
    }

    /** 绘制多边形边框(非闭合线) */
    private void strokePoly(double[][] pts, boolean c) {
        for (int i = 0; i < pts.length - 1; i++) g.strokeLine(pts[i][0], pts[i][1], pts[i + 1][0], pts[i + 1][1]);
        if (c) g.strokeLine(pts[0][0], pts[0][1], pts[pts.length - 1][0], pts[pts.length - 1][1]); // 闭合线
    }

    /** 填充矩形(平行四边形) */
    private void fillRect(double x0, double y0, double x1, double y1, Color c) {
        g.setFill(c); fillPoly(new double[][]{p(x0, y0, 0), p(x1, y0, 0), p(x1, y1, 0), p(x0, y1, 0)});
    }

    // ==================== 观众席系统 ====================
    /** 预生成约200名观众：四侧看台，多排分布，随机颜色 */
    private void initSpectators() {
        if (spectatorData != null) return;
        Random r = new Random(42); // 固定种子确保每次运行时观众布局一致
        spectatorData = new double[200][5];
        int idx = 0;
        // 上侧看台(5排×10人)
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 10 && idx < 200; col++) {
                spectatorData[idx][0] = 0.5 + col * 0.9 + r.nextDouble() * 0.3; // X
                spectatorData[idx][1] = 18.2 + row * 0.35;                       // Y
                spectatorData[idx][2] = row * 0.12;                               // Z(阶梯高度)
                spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length);       // 颜色索引
                idx++;
            }
        }
        // 下侧看台(4排×10人)
        for (int row = 0; row < 4 && idx < 200; row++) {
            for (int col = 0; col < 10 && idx < 200; col++) {
                spectatorData[idx][0] = 0.5 + col * 0.9 + r.nextDouble() * 0.3;
                spectatorData[idx][1] = -0.2 - row * 0.35;
                spectatorData[idx][2] = row * 0.12; spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length); idx++;
            }
        }
        // 左侧看台(5排×19人)
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 19 && idx < 200; col++) {
                spectatorData[idx][0] = -0.2 - row * 0.35;
                spectatorData[idx][1] = 0.5 + col * 0.95 + r.nextDouble() * 0.3;
                spectatorData[idx][2] = row * 0.12; spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length); idx++;
            }
        }
        // 右侧看台(5排×19人)
        for (int row = 0; row < 5 && idx < 200; row++) {
            for (int col = 0; col < 19 && idx < 200; col++) {
                spectatorData[idx][0] = 9.1 + row * 0.35;
                spectatorData[idx][1] = 0.5 + col * 0.95 + r.nextDouble() * 0.3;
                spectatorData[idx][2] = row * 0.12; spectatorData[idx][3] = r.nextInt(SPECTATOR_COLORS.length); idx++;
            }
        }
    }

    /** 绘制观众席：阶梯看台+椅子+观众 */
    private void drawSpectators(GameCore core) {
        g.setGlobalAlpha(1.0);
        Color standBase = Color.rgb(100, 100, 105); // 阶梯面浅灰
        Color standStep = Color.rgb(115, 115, 120); // 阶梯面交替色
        int steps = 4;
        double stepH = 0.15; // 每级阶梯高度(m)

        // 四侧阶梯看台(阶梯面+竖面)
        for (int side = 0; side < 4; side++) {
            for (int s = 0; s < steps; s++) {
                double zBase = s * stepH;
                if (side == 0) { // 上侧
                    double y0 = 18.0 + s * 0.35, y1 = y0 + 0.35;
                    fillRect(0, y0, 9, y1, (s % 2 == 0) ? standStep : standBase);
                    double[][] riser = {p(0, y1, zBase), p(9, y1, zBase), p(9, y1, zBase + stepH), p(0, y1, zBase + stepH)};
                    g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
                } else if (side == 1) { // 下侧
                    double y0 = -0.35 - s * 0.35, y1 = y0 + 0.35;
                    fillRect(0, y0, 9, y1, (s % 2 == 0) ? standStep : standBase);
                    double[][] riser = {p(0, y0, zBase), p(9, y0, zBase), p(9, y0, zBase + stepH), p(0, y0, zBase + stepH)};
                    g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
                } else if (side == 2) { // 左侧
                    double x0 = -0.35 - s * 0.35, x1 = x0 + 0.35;
                    fillRect(x0, 0, x1, 18, (s % 2 == 0) ? standStep : standBase);
                    double[][] riser = {p(x0, 0, zBase), p(x0, 18, zBase), p(x0, 18, zBase + stepH), p(x0, 0, zBase + stepH)};
                    g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
                } else { // 右侧
                    double x0 = 9.0 + s * 0.35, x1 = x0 + 0.35;
                    fillRect(x0, 0, x1, 18, (s % 2 == 0) ? standStep : standBase);
                    double[][] riser = {p(x1, 0, zBase), p(x1, 18, zBase), p(x1, 18, zBase + stepH), p(x1, 0, zBase + stepH)};
                    g.setFill(Color.rgb(80, 80, 85)); fillPoly(riser);
                }
            }
        }

        // 绘制每个观众(绿色椅子+彩色身体+肤色头部+微幅摆动)
        long time = System.currentTimeMillis();
        for (double[] sp : spectatorData) {
            if (sp[0] == 0 && sp[1] == 0) continue;
            double sx = sp[0], sy = sp[1], sz = sp[2];
            Color cl = SPECTATOR_COLORS[(int) sp[3]];

            double[] chairBase = p(sx, sy, sz);
            double cx = chairBase[0], cy = chairBase[1];
            // 绿色椅子面
            g.setFill(Color.rgb(40, 80, 40)); g.fillRoundRect(cx - 7, cy - 9, 14, 11, 4, 4);
            // 椅背(两根竖杆+上横梁)
            g.setStroke(Color.rgb(30, 60, 30)); g.setLineWidth(2.5);
            g.strokeLine(cx - 5, cy - 9, cx - 5, cy - 16); g.strokeLine(cx + 5, cy - 9, cx + 5, cy - 16);
            g.strokeLine(cx - 5, cy - 16, cx + 5, cy - 16);

            // 观众(坐在椅子上，微幅上下摆动)
            double bob = Math.sin(time * 0.003 + sx * 4 + sy * 3) * 2;
            double[] sp2 = p(sx, sy, sz + 0.18 + bob * 0.04); // 头顶略高于椅背
            double bx = sp2[0], by = sp2[1];
            g.setFill(cl); g.fillOval(bx - 5, by - 9, 10, 15); // 身体
            g.setFill(Color.rgb(250, 210, 170)); g.fillOval(bx - 4, by - 14, 8, 7); // 头
        }
    }

    // ==================== 裁判系统 ====================
    /**
     * 绘制裁判：绿色高椅+黑衣黑帽+逼真面部+银色哨子
     * 得分时手臂平举指向得分方半场，另一手拿哨子到嘴边
     */
    private void drawReferee(GameCore core) {
        double rx = -0.6, ry = 9.0, rz = 1.2; // 裁判位置：中线左侧，坐1.2m高椅子上
        Color greenC = Color.rgb(35, 120, 55), greenD = Color.rgb(25, 90, 40); // 椅子绿色

        // ---- 绿色高椅(4条金属腿) ----
        g.setStroke(Color.rgb(150, 150, 155)); g.setLineWidth(3);
        double[][] legTops = {{rx-0.12, ry-0.06}, {rx+0.12, ry-0.06}, {rx-0.12, ry+0.06}, {rx+0.12, ry+0.06}};
        for (double[] lt : legTops) {
            double[] lb = p(lt[0], lt[1], 0); double[] ls = p(lt[0], lt[1], rz);
            g.strokeLine(lb[0], lb[1], ls[0], ls[1]);
        }
        // 绿色椅子面
        double[][] seatP = {p(rx-0.15, ry-0.06, rz), p(rx+0.15, ry-0.06, rz), p(rx+0.15, ry+0.06, rz), p(rx-0.15, ry+0.06, rz)};
        g.setFill(greenC); fillPoly(seatP);
        g.setStroke(greenD); g.setLineWidth(1.5); strokePoly(seatP, true);
        // 绿色靠背(稍微向后倾斜)+横梁
        double backH = 0.3;
        double[][] backP = {p(rx-0.14, ry-0.06, rz), p(rx+0.14, ry-0.06, rz), p(rx+0.12, ry-0.08, rz+backH), p(rx-0.12, ry-0.08, rz+backH)};
        g.setFill(greenD); fillPoly(backP);
        g.setStroke(Color.rgb(25, 80, 35)); g.setLineWidth(1.5); strokePoly(backP, true);
        for (int i = 0; i < 2; i++) { // 两条横梁
            double hh = rz + backH * (0.3 + i * 0.35);
            double[] b1 = p(rx-0.13, ry-0.065, hh), b2 = p(rx+0.13, ry-0.065, hh);
            g.setStroke(Color.rgb(25, 80, 35)); g.setLineWidth(2.5); g.strokeLine(b1[0], b1[1], b2[0], b2[1]);
        }

        // ---- 黑色西服身体 ----
        double bodyH = 0.75, bodyW = 0.14, bh = rz + 0.28;
        Color blackC = Color.rgb(25, 25, 30);
        double[] bodyT = p(rx, ry, bh + bodyH), bodyB = p(rx, ry, bh);
        g.setStroke(blackC); g.setLineWidth(8); g.strokeLine(bodyB[0], bodyB[1], bodyT[0], bodyT[1]);
        // 肩膀横线
        double[] shL = p(rx - bodyW, ry, bh + bodyH - 0.08), shR = p(rx + bodyW, ry, bh + bodyH - 0.08);
        g.setStroke(blackC); g.setLineWidth(5); g.strokeLine(shL[0], shL[1], shR[0], shR[1]);

        // ---- 头部(肤色底+黑帽+五官) ----
        double hdZ = bh + bodyH + 0.12, hdR = 7;
        double[] hd = p(rx, ry, hdZ); double hx = hd[0], hy = hd[1];
        g.setFill(Color.rgb(245, 205, 165)); g.fillOval(hx - hdR, hy - hdR, hdR * 2, hdR * 2);
        // 黑帽
        g.setFill(Color.rgb(20, 20, 25)); g.fillOval(hx - hdR - 1, hy - hdR - 3, hdR * 2 + 2, hdR * 1.2);
        g.fillRect(hx - hdR - 1, hy - hdR - 1, hdR * 2 + 2, 4);
        // 双眼+瞳孔+高光
        g.setFill(Color.WHITE); g.fillOval(hx - 4, hy - 3, 4, 3); g.fillOval(hx + 1, hy - 3, 4, 3);
        g.setFill(Color.rgb(20, 20, 20)); g.fillOval(hx - 2.5, hy - 2.5, 2, 2); g.fillOval(hx + 2.5, hy - 2.5, 2, 2);
        g.setFill(Color.WHITE); g.fillOval(hx - 2, hy - 2.5, 0.8, 0.8); g.fillOval(hx + 3, hy - 2.5, 0.8, 0.8);
        // 鼻子+嘴巴
        g.setStroke(Color.rgb(200, 160, 130)); g.setLineWidth(1.2); g.strokeLine(hx, hy - 1, hx, hy + 1.5);
        g.setStroke(Color.rgb(180, 120, 100)); g.setLineWidth(1.5); g.strokeLine(hx - 2, hy + 3, hx + 2, hy + 3);

        // ---- 哨子系统(胸前挂绳+银灰色哨子) ----
        boolean isScoring = core.refArmTimer > 0;
        double wX = rx, wY, wZ;
        if (isScoring) { wY = ry - 0.01; wZ = hdZ - 0.06; } // 得分时哨子移到嘴边
        else { wY = ry + 0.03; wZ = bh + bodyH * 0.35; }      // 平时挂在胸前
        double[] whistlePos = p(wX, wY, wZ); double wx = whistlePos[0], wy = whistlePos[1];
        g.setFill(Color.rgb(200, 200, 210)); g.fillRoundRect(wx - 3, wy - 2, 6, 4, 2, 2);
        g.setStroke(Color.rgb(140, 140, 150)); g.setLineWidth(1); g.strokeRoundRect(wx - 3, wy - 2, 6, 4, 2, 2);
        if (!isScoring) { // 非得分时显示挂绳
            double[] neckP = p(rx, ry - 0.01, hdZ - hdR * 0.3);
            g.setStroke(Color.rgb(30, 30, 35)); g.setLineWidth(1.2); g.strokeLine(neckP[0], neckP[1], wx, wy);
        }

        // ---- 手臂(得分时平举，否则自然下垂) ----
        if (core.refArmTimer > 0) {
            double progress = core.refArmTimer / 1.5;
            // 我方得分(0)→举右手平指向我方(-Y)，敌方得分(1)→举左手平指向敌方(+Y)
            boolean raiseRight = (core.lastScorer == 0), pointToMySide = (core.lastScorer == 0);
            double armLen = 0.38;
            double armBaseX = raiseRight ? rx + bodyW * 0.5 : rx - bodyW * 0.5;
            double armBaseZ = bh + bodyH - 0.1;
            double[] armStart = p(armBaseX, ry, armBaseZ);
            double endY = ry + (pointToMySide ? -1 : 1) * armLen * 0.9; // 水平平举指向得分方
            double endZ = armBaseZ + armLen * Math.sin(Math.toRadians(5));
            double[] armEnd = p(armBaseX, endY, endZ);
            g.setStroke(blackC); g.setLineWidth(4); g.strokeLine(armStart[0], armStart[1], armEnd[0], armEnd[1]);
            // 手
            double[] handEnd = p(armBaseX, endY + (pointToMySide ? -1 : 1) * 0.04, endZ);
            g.setFill(Color.rgb(245, 205, 165)); g.fillOval(handEnd[0] - 3, handEnd[1] - 3, 6, 6);
            // 另一只手拿哨子到嘴边
            double otherBaseX = raiseRight ? rx - bodyW * 0.5 : rx + bodyW * 0.5;
            double[] otherStart = p(otherBaseX, ry, armBaseZ), otherEnd = p(wX, wY + 0.01, wZ + 0.03);
            g.setStroke(blackC); g.setLineWidth(3); g.strokeLine(otherStart[0], otherStart[1], otherEnd[0], otherEnd[1]);
            g.setFill(Color.rgb(245, 205, 165)); g.fillOval(otherEnd[0] - 2, otherEnd[1] - 2, 4, 4);
        } else {
            // 自然下垂
            double armLen = 0.3, lz = bh + bodyH - 0.05;
            double[] la = p(rx - bodyW * 0.5, ry, lz), le = p(rx - bodyW * 0.5, ry, lz - armLen);
            double[] ra = p(rx + bodyW * 0.5, ry, lz), re = p(rx + bodyW * 0.5, ry, lz - armLen);
            g.setStroke(blackC); g.setLineWidth(3.5); g.strokeLine(la[0], la[1], le[0], le[1]); g.strokeLine(ra[0], ra[1], re[0], re[1]);
            g.setFill(Color.rgb(245, 205, 165)); g.fillOval(le[0] - 2, le[1] - 2, 4, 4); g.fillOval(re[0] - 2, re[1] - 2, 4, 4);
        }

        // ---- 腿部(黑色裤子+鞋子) ----
        double legTopZ = bh - 0.1;
        double[] ll1 = p(rx - 0.06, ry + 0.06, legTopZ), ll2 = p(rx - 0.07, ry + 0.08, legTopZ - 0.5);
        double[] rl1 = p(rx + 0.06, ry + 0.06, legTopZ), rl2 = p(rx + 0.07, ry + 0.08, legTopZ - 0.5);
        g.setStroke(blackC); g.setLineWidth(4); g.strokeLine(ll1[0], ll1[1], ll2[0], ll2[1]); g.strokeLine(rl1[0], rl1[1], rl2[0], rl2[1]);
        g.setFill(Color.rgb(25, 25, 30));
        double[] sfp = p(rx - 0.07, ry + 0.08, legTopZ - 0.52); g.fillOval(sfp[0] - 4, sfp[1] - 2, 8, 4);
        sfp = p(rx + 0.07, ry + 0.08, legTopZ - 0.52); g.fillOval(sfp[0] - 4, sfp[1] - 2, 8, 4);
        // 地面阴影
        double[] shadow = p(rx, ry, 0.02);
        g.setFill(Color.rgb(0, 0, 0, 0.15)); g.fillOval(shadow[0] - 8, shadow[1] - 4, 16, 6);
    }

    // ==================== 加油气泡 ====================
    /**
     * 绘制加油气泡：白色圆角矩形+三角指针+浮动动画+淡入淡出
     * 仅在cheerTimer>0时绘制
     */
    private void drawCheerBubble(GameCore core) {
        if (core.cheerTimer <= 0 || core.currentCheer.isEmpty()) return;

        double[] bubbleWorld = p(core.cheerX, core.cheerY, 1.5); // 气泡在观众席上方1.5m
        double bx = bubbleWorld[0], by = bubbleWorld[1];

        // 浮动动画(周期性上下移动)
        double floatY = Math.sin(core.cheerTimer * 5) * 8;
        by += floatY;

        // 淡入淡出(前0.3秒淡入，后0.5秒淡出)
        double alpha = Math.min(1.0, core.cheerTimer / 0.3);
        if (core.cheerTimer < 0.5) alpha = core.cheerTimer / 0.5;

        String text = core.currentCheer;
        double textW = text.length() * 10; // 粗略估算文字宽度
        double bubbleW = textW + 16, bubbleH = 24;

        g.setGlobalAlpha(alpha);
        // 白色气泡背景(圆角矩形)
        g.setFill(Color.rgb(255, 255, 255, 0.92));
        g.fillRoundRect(bx - bubbleW / 2, by - bubbleH / 2, bubbleW, bubbleH, 12, 12);
        // 边框
        g.setStroke(Color.rgb(180, 180, 190, alpha * 0.7)); g.setLineWidth(1.5);
        g.strokeRoundRect(bx - bubbleW / 2, by - bubbleH / 2, bubbleW, bubbleH, 12, 12);
        // 三角指针(指向下方观众)
        double[] triX = {bx - 4, bx + 4, bx};
        double[] triY = {by + bubbleH / 2, by + bubbleH / 2, by + bubbleH / 2 + 8};
        g.setFill(Color.rgb(255, 255, 255, 0.92)); g.fillPolygon(triX, triY, 3);
        // 文字
        g.setFill(Color.rgb(40, 40, 45));
        g.setFont(Font.font("Microsoft YaHei", 11));
        double tw = g.getFont().getSize() * text.length() * 0.7;
        g.fillText(text, bx - tw / 2, by + 4);

        g.setGlobalAlpha(1.0);
    }
}