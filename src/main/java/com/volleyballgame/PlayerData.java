package com.volleyballgame;

/**
 * 球员数据结构 - 管理单个球员的所有状态：坐标、朝向、跳跃、挥臂、击球检测
 * 
 * 核心职责：
 *   1. 存储球员逻辑坐标(x,y,z)和朝向(facingX, facingY)
 *   2. 身体平滑旋转(12 rad/s)从当前角度插值到目标角度
 *   3. 跳跃物理(PLAYER_GRAVITY=8m/s², JUMP_HEIGHT=1.2m, vz0≈4.38m/s)
 *   4. 挥臂动画(传/扣两种，持续0.3秒)
 *   5. 击球条件检测(3D距离+120°前方扇形角)
 *   6. AI犯错冷却(aiBlunderCooldown, 0.5秒内不再尝试击球)
 * 
 * 击球半径(可重载)：
 *   AI默认 = 0.9m
 *   玩家传球 = 1.05m  (调用 canHit(bx,by,bz,1.05))
 *   玩家扣球 = 1.35m  (调用 canHit(bx,by,bz,1.35))
 * 
 * 手臂建模参数：
 *   肩膀高度(转动点) = 1.8m (距地面)
 *   臂长 = 0.6m
 *   检测中心离地 = 1.0m (HIT_CENTER_H)
 */
public class PlayerData {
    /** 球员编号(0~5) */
    public int index;
    /** 所属队伍(0=我方, 1=敌方) */
    public int team;
    /** 是否由人类玩家控制(可用于AI行为区分) */
    public boolean isHumanControlled = false;

    // ==================== 位置与朝向 ====================
    /** 世界坐标X(场地宽度, 0~9m) */
    public double x;
    /** 世界坐标Y(场地长度, 0~18m) */
    public double y;
    /** 离地高度Z(垂直向上, 地面=0) */
    public double z;
    /** 朝向单位向量的X分量(指向正对方向) */
    public double facingX = 0;
    /** 朝向单位向量的Y分量(默认我方+Y、敌方-Y) */
    public double facingY = 1;
    /** 垂直速度(跳跃时>0, 落地=0) */
    public double vz = 0;

    // ==================== 身体旋转插值(丝滑转身) ====================
    /** 当前渲染用的身体角度(弧度)，每帧平滑插值到targetBodyAngle */
    public double bodyAngle = 0;
    /** 目标角度(facingX/facingY的atan2值) */
    public double targetBodyAngle = 0;
    /** 上一帧的角度(备用) */
    public double prevBodyAngle = 0;

    // ==================== 挥臂系统 ====================
    /** 当前挥臂类型(0=无, 1=传球/顺时针, 2=扣球/逆时针) */
    public int swingType = 0;
    /** 挥臂动画剩余时间(秒) */
    public double swingTimer = 0;
    /** 挥臂总持续时间(秒) */
    static final double SWING_DURATION = 0.3;

    // ==================== 跑动动画 ====================
    /** 跑动计时器(用于腿部摆动动画) */
    public double runTimer = 0;
    /** 是否正在移动 */
    public boolean isMoving = false;

    // ==================== 物理常量 ====================
    /** 球员水平移动速度(m/s) */
    public static final double MOVE_SPEED = 5.0;
    /** 击球检测中心离地高度(m) */
    static final double HIT_CENTER_H = 1.0;
    /** AI击球检测半径(m) */
    static final double HIT_RADIUS = 0.9;
    /** 球员跳跃专用重力(m/s²)，独立于球的BALL_G */
    public static final double PLAYER_GRAVITY = 8.0;
    /** 球的重力(m/s²)，仅作参考 */
    public static final double BALL_GRAVITY = 4.0;

    // ==================== 跳跃参数 ====================
    /** 跳跃最高高度(m) */
    static final double JUMP_HEIGHT = 1.2;
    /** 跳跃初速度(m/s)，由公式 vz0=√(2gh)=√(2×8×1.2)≈4.38 计算 */
    public static final double JUMP_VZ = Math.sqrt(2 * PLAYER_GRAVITY * JUMP_HEIGHT);

    // ==================== 默认站位 ====================
    /** 默认站位X(三角形阵型) */
    public double defX;
    /** 默认站位Y */
    public double defY;

    // ==================== AI犯错冷却系统 ====================
    /** AI犯错冷却计时器(秒)，>0时该AI不会尝试击球(已决定放弃这次机会) */
    public double aiBlunderCooldown = 0;

    /**
     * 构造函数
     * @param idx  球员编号(0~5)
     * @param team 队伍(0=我方, 1=敌方)
     * @param x    初始默认X坐标
     * @param y    初始默认Y坐标
     */
    public PlayerData(int idx, int team, double x, double y) {
        this.index = idx; this.team = team;
        this.x = this.defX = x;
        this.y = this.defY = y;
        this.z = 0;
        // 默认朝向：我方看+Y(敌方方向)，敌方看-Y(我方方向)
        this.facingX = 0;
        this.facingY = (team == 0) ? 1 : -1;
        this.bodyAngle = Math.atan2(facingX, facingY);
        this.targetBodyAngle = bodyAngle;
        this.prevBodyAngle = bodyAngle;
    }

    // ==================== 击球检测(核心判定逻辑) ====================

    /**
     * 标准击球检测(AI使用，半径=HIT_RADIUS=0.9m)
     * 条件1：球到身体中心(x,y,z+1.0)的3D距离 ≤ 半径
     * 条件2：球在球员前方120°扇形内(dp≥-0.866≈cos150°)
     */
    public boolean canHit(double bx, double by, double bz) {
        return canHit(bx, by, bz, HIT_RADIUS);
    }

    /**
     * 自定义半径的击球检测(玩家使用更大半径)
     * @param bx ball-X
     * @param by ball-Y
     * @param bz ball-Z
     * @param radius 自定义击球半径(玩家传球1.05m,扣球1.35m)
     */
    public boolean canHit(double bx, double by, double bz, double radius) {
        // 身体检测中心：离地1m处
        double cx = x, cy = y, cz = z + HIT_CENTER_H;
        double dx = bx - cx, dy = by - cy, dz = bz - cz;
        double dist = Math.sqrt(dx*dx + dy*dy + dz*dz);
        if (dist > radius) return false; // 距离条件不满足
        // 前方角度检测：投影到水平面计算朝向点积
        double hLen = Math.sqrt(dx*dx + dy*dy);
        if (hLen < 0.001) return true; // 球在头顶正上方
        double dp = facingX * (dx / hLen) + facingY * (dy / hLen);
        // dp ∈ [-0.866, 0.866] 对应角度[150°, 30°]
        return dp >= -0.866 && dp <= 0.866;
    }

    // ==================== 挥臂控制 ====================
    /** 开始挥臂动画(spike=true→扣球/逆时针, false→传球/顺时针) */
    public void startSwing(boolean spike) {
        swingType = spike ? 2 : 1;
        swingTimer = SWING_DURATION; // 重置0.3秒倒计时
    }

    // ==================== 状态更新 ====================
    /** 每帧更新(由GameCore.updatePlaying调用) */
    public void updatePhysics(double dt) {
        // 跳跃物理
        if (z > 0 || vz > 0) {
            vz -= PLAYER_GRAVITY * dt; // 重力减速
            z += vz * dt;
            if (z <= 0) { z = 0; vz = 0; } // 落地
        }
        // 递减挥臂计时器和犯错冷却
        updateSwing(dt);
        // 平滑身体旋转
        smoothBodyAngle(dt);
        // 空中不跑动
        if (Math.abs(vz) > 0.1 || z > 0.01) runTimer = 0;
    }

    /** 递减挥臂计时器和AI犯错冷却 */
    public void updateSwing(double dt) {
        if (swingTimer > 0) {
            swingTimer -= dt;
            if (swingTimer <= 0) { swingType = 0; swingTimer = 0; }
        }
        if (aiBlunderCooldown > 0) {
            aiBlunderCooldown -= dt;
            if (aiBlunderCooldown < 0) aiBlunderCooldown = 0;
        }
    }

    /** 更新朝向向量并同步更新目标身体角度 */
    public void updateFacing(double fx, double fy) {
        this.facingX = fx;
        this.facingY = fy;
        this.targetBodyAngle = Math.atan2(fx, fy);
    }

    /**
     * 平滑转身插值(丝滑旋转效果)
     * 角速度 = 12 rad/s (约0.13秒转180°)
     * 自动处理角度环绕(±π边界)
     */
    public void smoothBodyAngle(double dt) {
        prevBodyAngle = bodyAngle;
        double diff = targetBodyAngle - bodyAngle;
        // 标准化差值到[-π, π]
        while (diff > Math.PI) diff -= 2 * Math.PI;
        while (diff < -Math.PI) diff += 2 * Math.PI;
        double step = dt * 12.0;
        if (Math.abs(diff) <= step) {
            bodyAngle = targetBodyAngle; // 接近目标，直接对齐
        } else {
            bodyAngle += Math.signum(diff) * step; // 逐步旋转
        }
        // 保持在[-π, π]范围内
        while (bodyAngle > Math.PI) bodyAngle -= 2 * Math.PI;
        while (bodyAngle < -Math.PI) bodyAngle += 2 * Math.PI;
    }

    // ==================== 重置与跳跃 ====================
    /** 重置所有状态到默认(得分后回合重置) */
    public void resetToDefault() {
        x = defX; y = defY; z = 0; vz = 0;
        runTimer = 0; isMoving = false;
        facingX = 0; facingY = (team == 0) ? 1 : -1;
        bodyAngle = Math.atan2(facingX, facingY);
        targetBodyAngle = bodyAngle;
        prevBodyAngle = bodyAngle;
        swingType = 0; swingTimer = 0;
        aiBlunderCooldown = 0;
    }

    /** 执行跳跃(只有在地面时才能起跳) */
    public void jump() {
        if (z <= 0.01) vz = JUMP_VZ;
    }
}