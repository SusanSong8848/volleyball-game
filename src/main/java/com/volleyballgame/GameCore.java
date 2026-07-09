package com.volleyballgame;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.util.Random;

/**
 * 游戏核心引擎 - 管理整个游戏的逻辑状态机、AI决策、物理更新和得分判定
 * 
 * 职责：
 *   1. 管理游戏状态(WAITING→PLAYING→SCORE→GAME_OVER)
 *   2. 每帧更新(updatePlaying)所有实体：人类移动、AI决策、球员物理、球的运动和碰撞
 *   3. 得分检测(球落地/出界)和裁判系统(挥手+哨声+气泡)
 *   4. AI分层决策(接球者分配→跑位→击球→目标搜索)
 *   5. 难度系统(简单/普通/困难，控制AI犯错率)
 * 
 * 物理参数：
 *   网球高度=2.43m，在Y=9处
 *   球重力=g_player(4m/s²)，球员跳跃重力独立(8m/s²)
 *   球员移速=5m/s，跳跃高度=1.2m
 *   先得5分获胜
 */
public class GameCore {

    // ==================== 难度与状态枚举 ====================
    /** 难度等级：简单(EASY,我方8%,敌方45%) / 普通(NORMAL,双方20%) / 困难(HARD,我方30%,敌方6%) */
    public enum Difficulty { EASY, NORMAL, HARD }

    /** 游戏状态机：WAITING(等待开球)→PLAYING(进行中)→SCORE(得分暂停2秒)→GAME_OVER(比赛结束) */
    enum State { WAITING, PLAYING, SCORE, GAME_OVER }
    private State state = State.WAITING;

    /** 当前选择的难度模式 */
    private Difficulty difficulty = Difficulty.NORMAL;

    // ==================== 场地常量 ====================
    /** 网线Y坐标，将场地分为我方半场(0~9)和对方半场(9~18) */
    static final double NET_Y = 9.0;
    /** 网球高度(m) */
    static final double NET_H = 2.43;
    /** 获胜分数阈值 */
    static final int WIN_SCORE = 5;
    /** 球的重力加速度(m/s²)，每帧vz减此值 */
    static final double BALL_G = 4.0;

    // ==================== 球员数组 ====================
    /** 我方3名球员：index 0=前排(4.5,7), 1=后排左(2,4), 2=后排右(7,4) */
    PlayerData[] myTeam = new PlayerData[3];
    /** 敌方3名球员：index 0=前排(4.5,11), 1=后排左(2,14), 2=后排右(7,14) */
    PlayerData[] enemyTeam = new PlayerData[3];
    /** 所有6名球员的引用数组，便于遍历 */
    PlayerData[] allPlayers = new PlayerData[6];
    /** 当前由人类玩家控制的球员引用 */
    PlayerData controlledPlayer;

    // ==================== 球的状态 ====================
    /** 球的逻辑坐标(X,Y,Z)，Z轴垂直向上 */
    double ballX, ballY, ballZ;
    /** 球的速度分量(Vx,Vy,Vz)，Vx/Vy水平恒定，Vz受重力影响 */
    double ballVx, ballVy, ballVz;
    /** 球是否在空中飞行(未被击中且未落地) */
    boolean ballInPlay = false;
    /** 球是否已落地(防止重复触发得分检测) */
    boolean ballLanded = false;
    /** 最后触球的队伍(-1=无, 0=我方, 1=敌方)，用于出界判罚 */
    int lastTouchedByTeam = -1;

    // ==================== 比分与发球 ====================
    /** 双方比分 */
    int myScore = 0, enemyScore = 0;
    /** 当前发球方(0=我方, 1=敌方) */
    int servingTeam = 0;
    /** 得分后暂停计时器(秒)，达到SCORE_PAUSE后进入下一回合 */
    double scoreTimer = 0;
    /** 得分暂停时长(秒) */
    static final double SCORE_PAUSE = 2.0;
    /** 是否等待发球 */
    boolean servePending = true;

    // ==================== 玩家输入状态 ====================
    /** WASD移动键是否按下 */
    boolean keyW, keyA, keyS, keyD;
    /** 空格键是否按下 */
    boolean keySpace;
    /** 跳跃是否已被消费(按下空格后直到松开才可再次跳跃) */
    boolean jumpConsumed;
    /** 玩家自定义的击球水平速度(通过0~9键调整，默认4m/s) */
    double speedV = 3.0;
    /** 鼠标左键(传球)和右键(扣球)是否被点击 */
    boolean passClicked, spikeClicked;
    /** 鼠标指针对应的世界坐标(等距投影逆变换计算) */
    double mouseWorldX = 4.5, mouseWorldY = 9.0;

    // ==================== 依赖引用 ====================
    private GameRenderer rend;
    private Main ui;
    private Scene scene;
    /** 随机数生成器(没有种子，真随机) */
    private Random rnd = new Random();
    /** JavaFX游戏循环定时器 */
    private AnimationTimer loop;

    /** 我方和敌方被分配为接球者的球员索引(-1=无人) */
    private int myAssigned = -1, enemyAssigned = -1;

    // ==================== 球网碰撞动画 ====================
    /** 球碰网的X坐标(用于形变动画的中心点) */
    public double netHitX = 4.5;
    /** 网形变动画计时器(秒)，>0时绘制凹陷效果 */
    public double netHitTimer = 0;

    // ==================== 裁判与气泡系统 ====================
    /** 玩家登录时输入的名字，显示在加油气泡中 */
    public String playerName;
    /** 最近得分方(-1=无, 0=我方, 1=敌方)，用于裁判挥手方向 */
    public int lastScorer = -1;
    /** 裁判挥手动画计时器(秒)，递减到0后手臂放下 */
    public double refArmTimer = 0;
    /** 加油气泡显示计时器(秒) */
    public double cheerTimer = 0;
    /** 当前气泡显示的文字内容 */
    public String currentCheer = "";
    /** 气泡出现的世界坐标 */
    public double cheerX = 0, cheerY = 0;

    /** 我方得分时的夸赞鼓励语(9种)，{0}会被替换为玩家名 */
    private static final String[] CHEER_WIN_TEXTS = {
        "%s太厉害了！", "%s勇敢飞，我们永相随！", "%s你是最棒的！",
        "%s打败对面啊！", "%s不愧是排球之神！", "%s好球！",
        "%s无敌！", "%s冲啊！", "%s必胜！"
    };
    /** 敌方得分时的安慰勉励语(9种) */
    private static final String[] CHEER_LOSE_TEXTS = {
        "%s再加把劲！", "%s你一定可以，相信自己！", "%s别放弃啊！",
        "%s别打假球啊！", "%s加油！", "%s不要气馁！",
        "%s下次一定行！", "%s稳住心态！", "%s我们喜欢你！"
    };

    // ==================== 等距投影参数(与GameRenderer一致) ====================
    /** 缩放比例(px/m)，将世界米转换为屏幕像素 */
    static final double SCALE = 36.0;
    /** cos30°≈0.866，用于等距投影X分量 */
    static final double COS30 = 0.8660254;
    /** sin30°=0.5，用于等距投影Y分量 */
    static final double SIN30 = 0.5;
    /** 投影原点X(屏幕坐标) */
    static final int OX = 200;
    /** 投影原点Y(屏幕坐标) */
    static final int OY = 530;

    /** 构造函数(默认难度普通、默认名"玩家") */
    public GameCore(GameRenderer rend, Main ui, Scene scene) {
        this(rend, ui, scene, Difficulty.NORMAL, "玩家");
    }

    public GameCore(GameRenderer rend, Main ui, Scene scene, Difficulty difficulty) {
        this(rend, ui, scene, difficulty, "玩家");
    }

    public GameCore(GameRenderer rend, Main ui, Scene scene, Difficulty difficulty, String playerName) {
        this.rend = rend; this.ui = ui; this.scene = scene;
        this.difficulty = difficulty;
        this.playerName = playerName;
    }

    /**
     * 初始化游戏：创建两队球员、随机分配操控角色、重置球
     * 调用时机：场景创建后调用一次
     */
    public void init() {
        // 创建我方3名球员(team=0)
        myTeam[0] = new PlayerData(0, 0, 4.5, 7);   // 前排(靠近球网)
        myTeam[1] = new PlayerData(1, 0, 2.0, 4);   // 后排左
        myTeam[2] = new PlayerData(2, 0, 7.0, 4);   // 后排右
        // 创建敌方3名球员(team=1)
        enemyTeam[0] = new PlayerData(3, 1, 4.5, 11);
        enemyTeam[1] = new PlayerData(4, 1, 2.0, 14);
        enemyTeam[2] = new PlayerData(5, 1, 7.0, 14);
        // 汇总到allPlayers数组
        allPlayers[0]=myTeam[0]; allPlayers[1]=myTeam[1]; allPlayers[2]=myTeam[2];
        allPlayers[3]=enemyTeam[0]; allPlayers[4]=enemyTeam[1]; allPlayers[5]=enemyTeam[2];
        pickRandomPlayer(); resetBall(); state = State.WAITING;
        speedV = 4.0; // 默认击球水平速度
    }

    /** 从我方3人中随机选1人作为玩家操控角色 */
    private void pickRandomPlayer() {
        if (controlledPlayer != null) controlledPlayer.isHumanControlled = false;
        int idx = rnd.nextInt(3);
        controlledPlayer = myTeam[idx];
        controlledPlayer.isHumanControlled = true;
        // 更新UI显示哪个位置被控制
        String[] names = {"前排", "后排左", "后排右"};
        ui.playerText.setText("控制: " + names[idx]);
    }

    // ==================== 键盘输入处理 ====================
    /** 处理键盘按下事件 */
    public void handleKeyDown(KeyCode code) {
        if (code == KeyCode.W) keyW = true;
        else if (code == KeyCode.A) keyA = true;
        else if (code == KeyCode.S) keyS = true;
        else if (code == KeyCode.D) keyD = true;
        else if (code == KeyCode.SPACE) {
            if (!keySpace) { keySpace = true; jumpConsumed = false; } // 新按下时才允许跳跃
        }
        else if (code.isDigitKey()) {
            int v = Integer.parseInt(code.getName()); // 0~9键设置水平速度
            speedV = Math.max(1, Math.min(10, v));    // 限制在1~10 m/s
        }
    }

    /** 处理键盘释放事件 */
    public void handleKeyUp(KeyCode code) {
        if (code == KeyCode.W) keyW = false;
        else if (code == KeyCode.A) keyA = false;
        else if (code == KeyCode.S) keyS = false;
        else if (code == KeyCode.D) keyD = false;
        else if (code == KeyCode.SPACE) { keySpace = false; jumpConsumed = false; }
    }

    /**
     * 等距投影逆变换：将屏幕坐标(sx, sy)转换为世界坐标(X, Y)
     * 公式推导：
     *   sx = OX + (X+Y)×COS30×SCALE  →  A = (sx-OX)/(COS30×SCALE) = X+Y
     *   sy = OY + (X-Y)×SIN30×SCALE  →  B = (sy-OY)/(SIN30×SCALE) = X-Y
     *   解方程组：X=(A+B)/2, Y=(A-B)/2
     */
    public void mouseMoved(double sx, double sy) {
        double A = (sx - OX) / (COS30 * SCALE);
        double B = (sy - OY) / (SIN30 * SCALE);
        mouseWorldX = clamp((A + B) / 2.0, 0, 9);
        mouseWorldY = clamp((A - B) / 2.0, 0, 18);
    }

    /** 启动游戏主循环(JavaFX AnimationTimer，约60fps) */
    public void start() {
        loop = new AnimationTimer() {
            private long last = 0;
            public void handle(long now) {
                if (last == 0) { last = now; return; }
                double dt = (now - last) / 1_000_000_000.0; // 纳秒转秒
                if (dt > 0.05) dt = 0.05; // 防止卡帧导致大跳跃
                last = now;
                update(dt); // 更新游戏逻辑
                try {
                    rend.render(GameCore.this); // 渲染一帧
                } catch (Exception ex) {
                    System.err.println("渲染异常: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        };
        loop.start();
    }

    /** 每帧更新入口：先递减动画计时器，再根据状态调度 */
    private void update(double dt) {
        // 递减网形变动画计时器
        if (netHitTimer > 0) {
            netHitTimer -= dt;
            if (netHitTimer < 0) netHitTimer = 0;
        }
        // 递减裁判挥手计时器
        if (refArmTimer > 0) {
            refArmTimer -= dt;
            if (refArmTimer <= 0) { refArmTimer = 0; lastScorer = -1; }
        }
        // 递减气泡显示计时器
        if (cheerTimer > 0) {
            cheerTimer -= dt;
            if (cheerTimer <= 0) { cheerTimer = 0; currentCheer = ""; }
        }

        // 状态机调度
        switch (state) {
            case WAITING:
                if (keySpace) { consumeJump(); startGame(); } // 按空格开始
                break;
            case PLAYING:
                updatePlaying(dt); // 核心游戏逻辑
                break;
            case SCORE:
                scoreTimer += dt;
                if (scoreTimer >= SCORE_PAUSE) { // 暂停2秒后
                    if (myScore >= WIN_SCORE || enemyScore >= WIN_SCORE) {
                        state = State.GAME_OVER; // 有人胜出
                        ui.stateText.setText(myScore >= WIN_SCORE ? "我方获胜! 空格重来" : "敌方获胜! 空格重来");
                    } else {
                        startRound(); // 下一回合
                        state = State.PLAYING;
                    }
                }
                break;
            case GAME_OVER:
                if (keySpace) { // 按空格重新开始
                    consumeJump();
                    myScore = enemyScore = 0;
                    ui.scoreText.setText("我方 0 - 0 敌方");
                    startRound();
                    state = State.PLAYING;
                }
                break;
        }
        // 每帧更新UI显示当前速度
        ui.speedText.setText("速度 V: " + speedV);
    }

    /** 开始新比赛(WAITING→PLAYING) */
    private void startGame() {
        myScore = enemyScore = 0;
        ui.scoreText.setText("我方 0 - 0 敌方");
        servingTeam = rnd.nextBoolean() ? 0 : 1; // 随机决定首发球权
        startRound();
        state = State.PLAYING;
    }

    /** 开始新一回合(得分后或比赛开始) */
    private void startRound() {
        for (PlayerData p : allPlayers) p.resetToDefault(); // 所有人回原位
        resetBall();
        ballLanded = false;
        scoreTimer = 0;
        speedV = 4.0;
        pickRandomPlayer(); // 重新随机分配操控角色
        servePending = true;
        netHitTimer = 0;
        ui.stateText.setText("比赛进行中");
    }

    /** 重置球到初始位置(不在飞行中) */
    private void resetBall() {
        ballX = 4.5; ballY = 1.0; ballZ = 2.0;
        ballVx = ballVy = ballVz = 0;
        ballInPlay = false;
    }

    /**
     * PLAYING状态的每帧更新(核心游戏循环)
     * 执行顺序：
     *   1. 发球(如果需要)  5. 挥臂判断
     *   2. 玩家朝向跟踪    6. 人类击球检测
     *   3. 人类移动        7. AI击球检测
     *   4. AI决策          8. 球运动+网碰撞+得分检测
     */
    private void updatePlaying(double dt) {
        if (servePending && !ballInPlay) { doServe(); servePending = false; }

        updateFacing();        // 玩家朝向始终指向鼠标
        updateHumanMove(dt);   // 人类WASD移动+跳跃
        updateAI(dt);          // AI决策(接球分配→跑位→击球)
        for (PlayerData p : allPlayers) p.updatePhysics(dt); // 物理更新
        checkHumanSwing();     // 检测鼠标点击触发挥臂
        checkHumanHit();       // 检测玩家是否击中球
        checkAIHit();          // 检测AI是否击中球

        if (ballInPlay) {      // 球在空中时更新运动+碰撞
            checkNet(dt);      // 球网碰撞检测
            ballVz -= BALL_G * dt; // 重力影响垂直速度
            ballX += ballVx * dt;
            ballY += ballVy * dt;
            ballZ += ballVz * dt;
            if (ballZ <= 0.1) { ballZ = 0.1; ballInPlay = false; checkScore(); } // 球落地
        }
    }

    /** 发球：由发球方后排右球员执行，速度/方向带随机性 */
    private void doServe() {
        PlayerData sv = (servingTeam == 0) ? myTeam[2] : enemyTeam[2]; // 后排右
        ballX = sv.x;
        ballY = sv.y + sv.facingY * 0.5; // 球在发球员前方0.5m
        ballZ = 2.0;
        double serveSpeed = 3.5 + (rnd.nextDouble() - 0.5) * 2.0; // 3.5±1.0 m/s
        double baseAngle = Math.atan2(sv.facingX, sv.facingY);
        double angleVariation = (rnd.nextDouble() - 0.5) * Math.toRadians(30.0); // ±15°
        double serveAngle = baseAngle + angleVariation;
        ballVx = Math.sin(serveAngle) * serveSpeed;
        ballVy = Math.cos(serveAngle) * serveSpeed;
        ballVz = 5.0 + rnd.nextDouble() * 1.0; // 竖直速度5~6 m/s
        ballInPlay = true;
        lastTouchedByTeam = sv.team;
    }

    /** 玩家朝向始终指向鼠标世界坐标 */
    private void updateFacing() {
        double dx = mouseWorldX - controlledPlayer.x;
        double dy = mouseWorldY - controlledPlayer.y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 0.01) controlledPlayer.updateFacing(dx / len, dy / len);
    }

    /** 人类移动：WASD绝对方向移动，空格跳跃，碰到网边自动限制 */
    private void updateHumanMove(double dt) {
        PlayerData p = controlledPlayer;
        double dist = PlayerData.MOVE_SPEED * dt;
        double mx = 0, my = 0;
        if (keyW) my += 1; if (keyS) my -= 1;
        if (keyA) mx -= 1; if (keyD) mx += 1;
        double len = Math.sqrt(mx * mx + my * my);
        if (len > 0.001) { mx = mx / len * dist; my = my / len * dist; }
        double nx = p.x + mx, ny = p.y + my;
        nx = clamp(nx, 0.3, 8.7);
        ny = clamp(ny, 0.3, 17.7);
        if (p.team == 0 && ny > NET_Y - 0.3) ny = NET_Y - 0.3; // 我方不能过网
        if (p.team == 1 && ny < NET_Y + 0.3) ny = NET_Y + 0.3; // 敌方同理
        p.x = nx; p.y = ny;
        if (keySpace && !jumpConsumed) { jumpConsumed = true; p.jump(); }
        if (len > 0.001) { p.runTimer += dt; p.isMoving = true; }
        else { p.runTimer = 0; p.isMoving = false; }
    }

    /** 检测鼠标点击触发挥臂(传球=左键,扣球=右键) */
    private void checkHumanSwing() {
        if (passClicked) { passClicked = false; controlledPlayer.startSwing(false); }
        if (spikeClicked) { spikeClicked = false; controlledPlayer.startSwing(true); }
    }

    /**
     * 检测玩家是否击中球
     * 条件：球在玩家击球范围内(传球1.05m, 扣球1.35m)且挥臂在有效时间窗口
     */
    private void checkHumanHit() {
        if (!ballInPlay) return;
        boolean spiking = (controlledPlayer.swingType == 2);
        double humanRadius = spiking ? 1.35 : 1.05; // 扣球范围更大
        if (controlledPlayer.canHit(ballX, ballY, ballZ, humanRadius)) {
            // 挥臂有效窗口：swingTimer在0.03~0.22秒之间
            if (controlledPlayer.swingType != 0 && controlledPlayer.swingTimer < 0.22 && controlledPlayer.swingTimer > 0.03) {
                hitBall(controlledPlayer, controlledPlayer.swingType == 2);
                controlledPlayer.swingTimer = 0.02;
            }
        }
    }

    /** 根据难度返回该队AI的犯错概率 */
    private double getBlunderChance(int team) {
        if (team == 0) { // 我方AI
            return (difficulty == Difficulty.EASY) ? 0.10 : (difficulty == Difficulty.NORMAL) ? 0.18 : 0.24;
        } else { // 敌方AI
            return (difficulty == Difficulty.EASY) ? 0.24 : (difficulty == Difficulty.NORMAL) ? 0.18 : 0.10;
        }
    }

    /**
     * 检测AI是否击中球(遍历所有非人类控制的球员)
     * 采用犯错冷却机制：AI首次进入击球范围时按概率决定是否犯错，
     * 犯错后冷却0.5秒不再尝试(避免每帧重试导致犯错率失效)
     */
    private void checkAIHit() {
        if (!ballInPlay) return;
        for (PlayerData p : allPlayers) {
            if (p.isHumanControlled) continue;
            // 犯错冷却中且球还在范围内：跳过
            if (p.aiBlunderCooldown > 0 && p.canHit(ballX, ballY, ballZ)) {
                p.swingType = 0; p.swingTimer = 0;
                continue;
            }
            if (p.canHit(ballX, ballY, ballZ) && p.swingType != 0) {
                if (p.aiBlunderCooldown <= 0 && rnd.nextDouble() < getBlunderChance(p.team)) {
                    p.aiBlunderCooldown = 0.5; // 犯错，冷却0.5秒
                    p.swingType = 0; p.swingTimer = 0;
                    continue;
                }
                boolean spike = (p.swingType == 2);
                aiDoHit(p, spike);
                p.swingType = 0; p.swingTimer = 0;
                break; // 每帧最多一个AI击球
            }
        }
        // 球不在了，重置所有AI犯错冷却
        if (!ballInPlay) {
            for (PlayerData p : allPlayers)
                if (!p.isHumanControlled) p.aiBlunderCooldown = 0;
        }
    }

    /**
     * 设置球的速度(被玩家或AI击中时调用)
     * 扣球时水平速度×1.5(确保球能打过网) + 竖直速度-0.4(平缓下落弧线)
     * 传球时竖直速度+5(高弧度)
     */
    private void hitBall(PlayerData p, boolean spike) {
        double vh = p.isHumanControlled ? speedV : (spike ? 5.0 : 4.0);
        if (spike) vh *= 1.5; // 扣球水平速度加50%
        ballVx = p.facingX * vh;
        ballVy = p.facingY * vh;
        ballVz = spike ? -0.4 : 5.0; // 扣球=-0.4(微向下), 传球=+5(高弧)
        lastTouchedByTeam = p.team;
    }

    /**
     * AI击球：搜索对方半场9×5网格找到最远空档点作为目标
     * 扣球速度4~7m/s随机，传球速度3~5.5m/s随机(增加不可预测性)
     */
    private void aiDoHit(PlayerData p, boolean spike) {
        double vh = spike ? (4.0 + rnd.nextDouble() * 3.0) : (3.0 + rnd.nextDouble() * 2.5);
        PlayerData[] opps = (p.team == 0) ? enemyTeam : myTeam;
        double y0 = (p.team == 0) ? 9 : 0, y1 = (p.team == 0) ? 18 : 9; // 对方半场范围
        // 9×5网格搜索距离对方球员最远的点
        double bestS = -1, bx = 4.5, by = (y0 + y1) / 2;
        for (int i = 0; i <= 8; i++)
            for (int j = 0; j <= 4; j++) {
                double tx = 0.5 + i * 1.0, ty = y0 + 0.5 + j * (y1 - y0 - 1) / 4;
                double minD = Double.MAX_VALUE;
                for (PlayerData o : opps) {
                    double dd = dist(tx, ty, o.x, o.y);
                    if (dd < minD) minD = dd;
                }
                if (minD > bestS) { bestS = minD; bx = tx; by = ty; }
            }
        // 面向目标方向
        double tdx = bx - p.x, tdy = by - p.y, tl = Math.sqrt(tdx * tdx + tdy * tdy);
        if (tl > 0.001) {
            p.updateFacing(tdx / tl, tdy / tl);
            ballVx = vh * tdx / tl;
            ballVy = vh * tdy / tl;
        } else {
            ballVx = p.facingX * vh;
            ballVy = p.facingY * vh;
        }
        ballVz = spike ? -1.0 : 5.0;
        lastTouchedByTeam = p.team;
        myAssigned = enemyAssigned = -1;
    }

    /** 球网碰撞检测：球穿越Y=9且高度<2.43m时弹回并衰减 */
    private void checkNet(double dt) {
        double prev = ballY - ballVy * dt;
        // 检测上一帧和当前帧Y坐标是否跨越球网
        if ((prev - NET_Y) * (ballY - NET_Y) < 0 && ballZ < NET_H) {
            ballY = NET_Y - (ballY - NET_Y) * 0.3; // 弹回网线附近
            ballVy = -ballVy * 0.2; // 水平速度反向并大幅衰减
            ballVz *= 0.7;          // 垂直速度衰减
            netHitX = ballX;
            netHitTimer = 0.3;      // 触发0.3秒形变动画
        }
    }

    /**
     * 得分判定：球落地时调用
     * 规则：界内(Y<9→敌方得分, Y>=9→我方得分)
     *       出界→最后触球方对方得分
     * 得分后：更新比分→裁判挥手(1.5s)→吹哨→弹窗气泡(2s)
     */
    private void checkScore() {
        if (ballLanded) return;
        ballLanded = true;
        boolean in = ballX >= 0 && ballX <= 9 && ballY >= 0 && ballY <= 18; // 是否界内
        int scorer = -1;
        if (in) {
            if (ballY < NET_Y) { enemyScore++; servingTeam = 1; scorer = 1; }
            else { myScore++; servingTeam = 0; scorer = 0; }
        } else {
            if (lastTouchedByTeam == 0) { enemyScore++; servingTeam = 1; scorer = 1; }
            else if (lastTouchedByTeam == 1) { myScore++; servingTeam = 0; scorer = 0; }
            else { // 无人触球，发球方失分
                if (servingTeam == 0) { enemyScore++; servingTeam = 1; scorer = 1; }
                else { myScore++; servingTeam = 0; scorer = 0; }
            }
        }
        // 触发裁判动画+音效+气泡
        lastScorer = scorer;
        refArmTimer = 1.5;
        playWhistle();
        cheerTimer = 2.0;
        String[] texts = (scorer == 0) ? CHEER_WIN_TEXTS : CHEER_LOSE_TEXTS;
        int idx = rnd.nextInt(texts.length);
        currentCheer = String.format(texts[idx], playerName);
        // 气泡出现在观众席区域(四侧随机)
        int side = rnd.nextInt(4);
        if (side == 0) { cheerX = 1 + rnd.nextDouble() * 7; cheerY = 18.3 + rnd.nextDouble() * 1.0; }
        else if (side == 1) { cheerX = 1 + rnd.nextDouble() * 7; cheerY = -1.0 - rnd.nextDouble() * 0.8; }
        else if (side == 2) { cheerX = -1.0 - rnd.nextDouble() * 1.2; cheerY = 2 + rnd.nextDouble() * 14; }
        else { cheerX = 9.0 + rnd.nextDouble() * 1.2; cheerY = 2 + rnd.nextDouble() * 14; }

        ui.scoreText.setText("我方 " + myScore + " - " + enemyScore + " 敌方");
        state = State.SCORE;
        scoreTimer = 0;
        ui.stateText.setText("得分!");
    }

    // ==================== AI 系统 ====================

    /** AI决策入口：分配接球者→执行AI移动/击球 */
    private void updateAI(double dt) {
        assignReceivers();
        for (PlayerData p : myTeam) {
            if (p.isHumanControlled) continue;
            boolean rec = (idxIn(p, myTeam) == myAssigned); // 是否被分配为接球者
            doAI(p, dt, rec);
        }
        for (int i = 0; i < 3; i++) {
            PlayerData p = enemyTeam[i];
            boolean rec = (i == enemyAssigned);
            doAI(p, dt, rec);
        }
    }

    /** 分配接球者：预测拦截点→算到达时间→选最快者。我方AI在球靠近玩家时不抢 */
    private void assignReceivers() {
        if (!ballInPlay) { myAssigned = enemyAssigned = -1; return; }
        double[] land = predictLand();
        if (land == null) { myAssigned = enemyAssigned = -1; return; }
        myAssigned = bestRecv(myTeam, land);
        enemyAssigned = bestRecv(enemyTeam, land);
    }

    /**
     * 预测球降到目标高度targetZ时的世界坐标
     * 解二次方程：z0 + vz0*t - 0.5*g*t² = targetZ
     */
    private double[] predictAtHeight(double targetZ) {
        double a = 0.5 * BALL_G;
        double b = -ballVz;
        double c = targetZ - ballZ;
        double disc = b * b - 4 * a * c;
        if (disc < 0) return null;
        double t = (-b + Math.sqrt(disc)) / (2 * a);
        if (t <= 0) t = (-b - Math.sqrt(disc)) / (2 * a);
        if (t <= 0) return null;
        return new double[]{ballX + ballVx * t, ballY + ballVy * t, t};
    }

    /** 预测球落地位置(Z=0) */
    private double[] predictLand() {
        if (ballZ <= 0) return new double[]{ballX, ballY};
        double disc = ballVz * ballVz + 2 * BALL_G * ballZ;
        if (disc < 0) return null;
        double t = (ballVz + Math.sqrt(disc)) / BALL_G;
        if (t <= 0) return null;
        return new double[]{ballX + ballVx * t, ballY + ballVy * t};
    }

    /**
     * 选最佳接球者：到拦截点时间最短者
     * 我方AI额外判断：球落点距人类玩家2.5m以内→AI不接球
     */
    private int bestRecv(PlayerData[] team, double[] interceptPt) {
        double best = Double.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < 3; i++) {
            if (team[i].isHumanControlled) continue;
            double d = dist(team[i].x, team[i].y, interceptPt[0], interceptPt[1]);
            double time = d / PlayerData.MOVE_SPEED + 0.08; // 加0.08s反应时间
            if (time < best) { best = time; idx = i; }
        }
        if (team[0].team == 0 && idx >= 0 && controlledPlayer != null) {
            double playerDist = dist(controlledPlayer.x, controlledPlayer.y, interceptPt[0], interceptPt[1]);
            if (playerDist < 2.5) return -1; // 球在玩家身边，AI不抢
        }
        return idx;
    }

    /**
     * 单个AI球员的决策执行
     * 优先级：接球(全速跑拦截点) → 防守(向球靠拢40%) → 回位(默认站位)
     * 只在球高度合适(0.3~3.5m)且可击中时挥臂击球
     * 球>2m且能跳着够到时触发跳跃拦截
     */
    private void doAI(PlayerData p, double dt, boolean receiver) {
        if (ballZ <= 0.2 || !ballInPlay) {
            // 球太低或不在飞行中→回默认站位
            if (dist(p.x, p.y, p.defX, p.defY) > 0.3) {
                moveToward(p, p.defX, p.defY, dt);
                updateAIFacing(p, p.defX, p.defY);
                p.runTimer += dt; p.isMoving = true;
            } else {
                p.runTimer = 0; p.isMoving = false;
                p.updateFacing(0, (p.team == 0) ? 1 : -1);
            }
            return;
        }

        double[] intercept = predictAtHeight(1.0); // 最佳击球高度拦截点
        double targetX = p.defX, targetY = p.defY;
        boolean hasValidTarget = false;

        if (ballZ > 1.2 && intercept != null) {
            targetX = intercept[0]; targetY = intercept[1];
            hasValidTarget = true;
        } else if (ballZ <= 1.2 && ballZ > 0.2) {
            targetX = ballX; targetY = ballY; // 直接跑向球
            hasValidTarget = true;
        }

        if (hasValidTarget) {
            if (receiver) {
                moveToward(p, targetX, targetY, dt); // 全速跑
            } else {
                // 非接球者：向球方向靠拢40%(保持阵型)
                double mix = 0.4;
                double tx = p.defX + (targetX - p.defX) * mix;
                double ty = p.defY + (targetY - p.defY) * mix;
                tx = clamp(tx, 0.5, 8.5);
                ty = clamp(ty, 0.5, 17.5);
                if (p.team == 0 && ty > NET_Y - 0.5) ty = NET_Y - 0.5;
                if (p.team == 1 && ty < NET_Y + 0.5) ty = NET_Y + 0.5;
                moveToward(p, tx, ty, dt);
            }
        } else {
            if (dist(p.x, p.y, p.defX, p.defY) > 0.3)
                moveToward(p, p.defX, p.defY, dt);
        }

        updateAIFacing(p, targetX, targetY);
        p.runTimer += dt; p.isMoving = true;

        // 球在合理击球高度范围内且能击中→挥臂
        if (ballZ > 0.3 && ballZ < 3.5 && p.canHit(ballX, ballY, ballZ)) {
            if (p.swingType == 0) {
                boolean spike = ballZ > 1.8 && Math.abs(p.y - NET_Y) < 1.8;
                p.startSwing(spike);
            }
        }

        // 高球近身有机会够到→跳跃
        if (ballZ > 2.0 && p.z <= 0.01 && p.canHit(ballX, ballY, ballZ + 0.5)) {
            p.jump();
        }
    }

    /** AI面向目标方向 */
    private void updateAIFacing(PlayerData p, double tx, double ty) {
        double dx = tx - p.x, dy = ty - p.y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > 0.001) p.updateFacing(dx / d, dy / d);
    }

    /** 向目标坐标移动(步长限制) */
    private void moveToward(PlayerData p, double tx, double ty, double dt) {
        double dx = tx - p.x, dy = ty - p.y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d < 0.05) return;
        double step = PlayerData.MOVE_SPEED * dt;
        if (step > d) step = d;
        p.x += dx / d * step;
        p.y += dy / d * step;
        p.x = clamp(p.x, 0.3, 8.7);
        p.y = clamp(p.y, 0.3, 17.7);
        if (p.team == 0 && p.y > NET_Y - 0.3) p.y = NET_Y - 0.3;
        if (p.team == 1 && p.y < NET_Y + 0.3) p.y = NET_Y + 0.3;
    }

    // ==================== 工具方法 ====================

    private int idxIn(PlayerData p, PlayerData[] t) {
        for (int i = 0; i < 3; i++) if (t[i] == p) return i;
        return -1;
    }

    /** 两点间欧氏距离 */
    private static double dist(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    /** 限制值在[lo, hi]范围内 */
    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /** 消费跳跃(防止按住空格连续跳跃) */
    private void consumeJump() { jumpConsumed = true; }

    /** 播放裁判哨声(使用JavaFX MediaPlayer) */
    private void playWhistle() {
        try {
            File whistleFile = new File("src/main/resources/referee39s-whistle.mp3");
            if (whistleFile.exists()) {
                MediaPlayer whistlePlayer = new MediaPlayer(new Media(whistleFile.toURI().toString()));
                whistlePlayer.setVolume(0.6);
                whistlePlayer.setOnEndOfMedia(() -> whistlePlayer.dispose());
                whistlePlayer.play();
            }
        } catch (Exception ex) {
            System.out.println("哨声播放失败: " + ex.getMessage());
        }
    }

    /** 停止BGM */
    public void stopBGM() {
        if (ui != null) ui.stopBGM();
    }
}