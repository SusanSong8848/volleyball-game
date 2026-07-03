package com.volleyballgame;

public class PlayerData {
    public int index;
    public int team;
    public boolean isHumanControlled = false;

    public double x, y, z;
    public double facingX = 0, facingY = 1;
    public double vz = 0;

    // 身体转向插值
    public double bodyAngle = 0;       // 当前渲染用的角度(弧度)
    public double targetBodyAngle = 0; // 目标角度
    public double prevBodyAngle = 0;

    public int swingType = 0;
    public double swingTimer = 0;
    static final double SWING_DURATION = 0.3;

    public double runTimer = 0;

    public static final double MOVE_SPEED = 5.0;
    static final double HIT_CENTER_H = 1.0;
    static final double HIT_RADIUS = 0.9;
    public static final double PLAYER_GRAVITY = 5.0;
    public static final double BALL_GRAVITY = 4.0;

    // 跳跃参数: 最高1.2m, g_player=5.0, vz0=sqrt(2*5*1.2)=√12≈3.464
    static final double JUMP_HEIGHT = 1.2;
    public static final double JUMP_VZ = Math.sqrt(2 * PLAYER_GRAVITY * JUMP_HEIGHT);

    public double defX, defY;
    public boolean isMoving = false;
    // AI犯错冷却: >0时不会尝试击球(相当于这次机会放弃了)
    public double aiBlunderCooldown = 0;

    public PlayerData(int idx, int team, double x, double y) {
        this.index = idx; this.team = team;
        this.x = this.defX = x; this.y = this.defY = y; this.z = 0;
        this.facingX = 0; this.facingY = (team == 0) ? 1 : -1;
        this.bodyAngle = Math.atan2(facingX, facingY);
        this.targetBodyAngle = bodyAngle;
        this.prevBodyAngle = bodyAngle;
    }

    /** 击球检测: 球在身体前方120°扇形范围内且距离≤0.9m */
    public boolean canHit(double bx, double by, double bz) {
        return canHit(bx, by, bz, HIT_RADIUS);
    }

    /** 击球检测(自定义半径): 玩家可用更大半径 */
    public boolean canHit(double bx, double by, double bz, double radius) {
        double cx = x, cy = y, cz = z + HIT_CENTER_H;
        double dx = bx - cx, dy = by - cy, dz = bz - cz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist > radius) return false;
        double hLen = Math.sqrt(dx*dx + dy*dy);
        if (hLen < 0.001) return true;
        double dp = facingX * (dx / hLen) + facingY * (dy / hLen);
        return dp >= -0.866 && dp <= 0.866;
    }

    public void startSwing(boolean spike) {
        swingType = spike ? 2 : 1;
        swingTimer = SWING_DURATION;
    }

    public void updateSwing(double dt) {
        if (swingTimer > 0) {
            swingTimer -= dt;
            if (swingTimer <= 0) { swingType = 0; swingTimer = 0; }
        }
        // 犯错冷却递减
        if (aiBlunderCooldown > 0) {
            aiBlunderCooldown -= dt;
            if (aiBlunderCooldown < 0) aiBlunderCooldown = 0;
        }
    }

    /** 更新朝向时同步更新目标身体角度 */
    public void updateFacing(double fx, double fy) {
        this.facingX = fx;
        this.facingY = fy;
        this.targetBodyAngle = Math.atan2(fx, fy);
    }

    /** 平滑转身插值，每帧调用, 12 rad/s */
    public void smoothBodyAngle(double dt) {
        prevBodyAngle = bodyAngle;
        double diff = targetBodyAngle - bodyAngle;
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        double step = dt * 12.0;
        if (Math.abs(diff) <= step) {
            bodyAngle = targetBodyAngle;
        } else {
            bodyAngle += Math.signum(diff) * step;
        }
        while (bodyAngle > Math.PI) bodyAngle -= 2 * Math.PI;
        while (bodyAngle < -Math.PI) bodyAngle += 2 * Math.PI;
    }

    public void resetToDefault() {
        x = defX; y = defY; z = 0; vz = 0; runTimer = 0; isMoving = false;
        facingX = 0; facingY = (team == 0) ? 1 : -1;
        bodyAngle = Math.atan2(facingX, facingY);
        targetBodyAngle = bodyAngle;
        prevBodyAngle = bodyAngle;
        swingType = 0; swingTimer = 0;
        aiBlunderCooldown = 0;
    }

    public void jump() {
        if (z <= 0.01) vz = JUMP_VZ;
    }

    public void updatePhysics(double dt) {
        if (z > 0 || vz > 0) {
            vz -= PLAYER_GRAVITY * dt;
            z += vz * dt;
            if (z <= 0) { z = 0; vz = 0; }
        }
        updateSwing(dt);
        smoothBodyAngle(dt);
        if (Math.abs(vz) > 0.1 || z > 0.01) runTimer = 0;
    }
}