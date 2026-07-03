package com.volleyballgame;

import javafx.animation.AnimationTimer;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import java.io.File;
import java.util.Random;

/**
 * 游戏核心引擎 v7 - 三种难度 + AI犯错机制
 */
public class GameCore {

    /** 难度等级 */
    public enum Difficulty { EASY, NORMAL, HARD }

    enum State { WAITING, PLAYING, SCORE, GAME_OVER }
    private State state = State.WAITING;

    private Difficulty difficulty = Difficulty.NORMAL;

    static final double NET_Y = 9.0, NET_H = 2.43;
    static final int WIN_SCORE = 5;
    static final double BALL_G = 4.0;

    PlayerData[] myTeam = new PlayerData[3];
    PlayerData[] enemyTeam = new PlayerData[3];
    PlayerData[] allPlayers = new PlayerData[6];
    PlayerData controlledPlayer;

    double ballX, ballY, ballZ, ballVx, ballVy, ballVz;
    boolean ballInPlay = false, ballLanded = false;
    int lastTouchedByTeam = -1;

    int myScore = 0, enemyScore = 0;
    int servingTeam = 0;
    double scoreTimer = 0;
    static final double SCORE_PAUSE = 2.0;
    boolean servePending = true;

    boolean keyW, keyA, keyS, keyD, keySpace;
    boolean jumpConsumed;
    double speedV = 3.0;
    boolean passClicked, spikeClicked;
    double mouseWorldX = 4.5, mouseWorldY = 9.0;

    private GameRenderer rend;
    private Main ui;
    private Scene scene;
    private Random rnd = new Random();
    private AnimationTimer loop;

    private int myAssigned = -1, enemyAssigned = -1;

    public double netHitX = 4.5;
    public double netHitTimer = 0;

    /** 玩家名字 */
    public String playerName;
    /** 最近得分方 (-1=无, 0=我方, 1=敌方) 用于裁判挥手 */
    public int lastScorer = -1;
    /** 裁判挥手计时器 */
    public double refArmTimer = 0;
    /** 加油气泡计时器 */
    public double cheerTimer = 0;
    /** 当前气泡文字 */
    public String currentCheer = "";
    /** 气泡坐标 */
    public double cheerX = 0, cheerY = 0;
    
    private static final String[] CHEER_WIN_TEXTS = {
        "%s太厉害了！", "%s勇敢飞，我们永相随！", "%s你是最棒的！",
        "%s打败对面啊！", "%s不愧是排球之神！", "%s好球！",
        "%s无敌！", "%s冲啊！", "%s必胜！"
    };
    private static final String[] CHEER_LOSE_TEXTS = {
        "%s再加把劲！", "%s你一定可以，相信自己！", "%s别放弃啊！",
        "%s别打假球啊！", "%s加油！", "%s不要气馁！",
        "%s下次一定行！", "%s稳住心态！", "%s我们喜欢你！"
    };

    // 等距投影参数(与GameRenderer一致)
    static final double SCALE = 36.0, COS30 = 0.8660254, SIN30 = 0.5;
    static final int OX = 200, OY = 530;

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

    public void init() {
        myTeam[0] = new PlayerData(0, 0, 4.5, 7);
        myTeam[1] = new PlayerData(1, 0, 2.0, 4);
        myTeam[2] = new PlayerData(2, 0, 7.0, 4);
        enemyTeam[0] = new PlayerData(3, 1, 4.5, 11);
        enemyTeam[1] = new PlayerData(4, 1, 2.0, 14);
        enemyTeam[2] = new PlayerData(5, 1, 7.0, 14);
        allPlayers[0]=myTeam[0]; allPlayers[1]=myTeam[1]; allPlayers[2]=myTeam[2];
        allPlayers[3]=enemyTeam[0]; allPlayers[4]=enemyTeam[1]; allPlayers[5]=enemyTeam[2];
        pickRandomPlayer(); resetBall(); state = State.WAITING;
        speedV = 4.0;
    }

    private void pickRandomPlayer() {
        if (controlledPlayer != null) controlledPlayer.isHumanControlled = false;
        int idx = rnd.nextInt(3);
        controlledPlayer = myTeam[idx];
        controlledPlayer.isHumanControlled = true;
        String[] names = {"前排", "后排左", "后排右"};
        ui.playerText.setText("控制: " + names[idx]);
    }

    public void handleKeyDown(KeyCode code) {
        if (code == KeyCode.W) keyW = true;
        else if (code == KeyCode.A) keyA = true;
        else if (code == KeyCode.S) keyS = true;
        else if (code == KeyCode.D) keyD = true;
        else if (code == KeyCode.SPACE) {
            if (!keySpace) { keySpace = true; jumpConsumed = false; }
        }
        else if (code.isDigitKey()) {
            int v = Integer.parseInt(code.getName());
            speedV = Math.max(1, Math.min(10, v));
        }
    }

    public void handleKeyUp(KeyCode code) {
        if (code == KeyCode.W) keyW = false;
        else if (code == KeyCode.A) keyA = false;
        else if (code == KeyCode.S) keyS = false;
        else if (code == KeyCode.D) keyD = false;
        else if (code == KeyCode.SPACE) { keySpace = false; jumpConsumed = false; }
    }

    /** ✅ 等距投影逆变换：屏幕坐标→世界坐标(地面z=0) */
    public void mouseMoved(double sx, double sy) {
        // 等距投影: sx=OX+(X+Y)*COS30*SCALE, sy=OY+(X-Y)*SIN30*SCALE-Z*SCALE
        // 逆变换(地面z=0): 
        //   A = (sx-OX)/(COS30*SCALE) = X+Y
        //   B = (sy-OY)/(SIN30*SCALE) = X-Y
        //   X = (A+B)/2, Y = (A-B)/2
        double A = (sx - OX) / (COS30 * SCALE);
        double B = (sy - OY) / (SIN30 * SCALE);
        mouseWorldX = clamp((A + B) / 2.0, 0, 9);
        mouseWorldY = clamp((A - B) / 2.0, 0, 18);
    }

    public void start() {
        loop = new AnimationTimer() {
            private long last = 0;
            public void handle(long now) {
                if (last == 0) { last = now; return; }
                double dt = (now - last) / 1_000_000_000.0;
                if (dt > 0.05) dt = 0.05;
                last = now;
                update(dt);
                try {
                    rend.render(GameCore.this);
                } catch (Exception ex) {
                    System.err.println("渲染异常: " + ex.getMessage());
                    ex.printStackTrace();
                }
            }
        };
        loop.start();
    }

    private void update(double dt) {
        if (netHitTimer > 0) {
            netHitTimer -= dt;
            if (netHitTimer < 0) netHitTimer = 0;
        }
        if (refArmTimer > 0) {
            refArmTimer -= dt;
            if (refArmTimer <= 0) { refArmTimer = 0; lastScorer = -1; }
        }
        if (cheerTimer > 0) {
            cheerTimer -= dt;
            if (cheerTimer <= 0) { cheerTimer = 0; currentCheer = ""; }
        }

        switch (state) {
            case WAITING:
                if (keySpace) { consumeJump(); startGame(); }
                break;
            case PLAYING:
                updatePlaying(dt);
                break;
            case SCORE:
                scoreTimer += dt;
                if (scoreTimer >= SCORE_PAUSE) {
                    if (myScore >= WIN_SCORE || enemyScore >= WIN_SCORE) {
                        state = State.GAME_OVER;
                        ui.stateText.setText(myScore >= WIN_SCORE ? "我方获胜! 空格重来" : "敌方获胜! 空格重来");
                    } else {
                        startRound();
                        state = State.PLAYING;
                    }
                }
                break;
            case GAME_OVER:
                if (keySpace) { consumeJump(); myScore = enemyScore = 0; ui.scoreText.setText("我方 0 - 0 敌方"); startRound(); state = State.PLAYING; }
                break;
        }
        ui.speedText.setText("速度 V: " + speedV);
    }

    private void startGame() {
        myScore = enemyScore = 0;
        ui.scoreText.setText("我方 0 - 0 敌方");
        servingTeam = rnd.nextBoolean() ? 0 : 1;
        startRound();
        state = State.PLAYING;
    }

    private void startRound() {
        for (PlayerData p : allPlayers) p.resetToDefault();
        resetBall();
        ballLanded = false;
        scoreTimer = 0;
        speedV = 4.0;
        pickRandomPlayer();
        servePending = true;
        netHitTimer = 0;
        ui.stateText.setText("比赛进行中");
    }

    private void resetBall() {
        ballX = 4.5; ballY = 1.0; ballZ = 2.0;
        ballVx = ballVy = ballVz = 0;
        ballInPlay = false;
    }

    private void updatePlaying(double dt) {
        if (servePending && !ballInPlay) { doServe(); servePending = false; }

        // 1. 玩家朝向(鼠标跟踪)
        updateFacing();

        // 2. 人类移动
        updateHumanMove(dt);

        // 3. AI决策
        updateAI(dt);

        // 4. 物理+转身
        for (PlayerData p : allPlayers) {
            p.updatePhysics(dt);
        }

        // 5. 挥臂
        checkHumanSwing();

        // 6. 击球检测
        checkHumanHit();
        checkAIHit();

        // 7. 球运动
        if (ballInPlay) {
            checkNet(dt);
            ballVz -= BALL_G * dt;
            ballX += ballVx * dt;
            ballY += ballVy * dt;
            ballZ += ballVz * dt;
            if (ballZ <= 0.1) { ballZ = 0.1; ballInPlay = false; checkScore(); }
        }
    }

    private void doServe() {
        PlayerData sv = (servingTeam == 0) ? myTeam[2] : enemyTeam[2];
        ballX = sv.x;
        ballY = sv.y + sv.facingY * 0.5;
        ballZ = 2.0;
        // 发球速度随机: 3.5 ± 1.0 m/s
        double serveSpeed = 3.5 + (rnd.nextDouble() - 0.5) * 2.0;
        // 发球方向: 默认朝向 ± 15° (约0.26 rad)
        double baseAngle = Math.atan2(sv.facingX, sv.facingY);
        double angleVariation = (rnd.nextDouble() - 0.5) * Math.toRadians(30.0); // ±15°
        double serveAngle = baseAngle + angleVariation;
        double serveDirX = Math.sin(serveAngle);
        double serveDirY = Math.cos(serveAngle);
        ballVx = serveDirX * serveSpeed;
        ballVy = serveDirY * serveSpeed;
        ballVz = 5.0 + rnd.nextDouble() * 1.0; // 竖直速度也有微调
        ballInPlay = true;
        lastTouchedByTeam = sv.team;
    }

    /** ✅ 玩家朝向始终指向鼠标世界位置 */
    private void updateFacing() {
        double dx = mouseWorldX - controlledPlayer.x;
        double dy = mouseWorldY - controlledPlayer.y;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len > 0.01) {
            controlledPlayer.updateFacing(dx / len, dy / len);
        }
    }

    private void updateHumanMove(double dt) {
        PlayerData p = controlledPlayer;
        double dist = PlayerData.MOVE_SPEED * dt;

        double mx = 0, my = 0;
        if (keyW) my += 1;
        if (keyS) my -= 1;
        if (keyA) mx -= 1;
        if (keyD) mx += 1;

        double len = Math.sqrt(mx * mx + my * my);
        if (len > 0.001) {
            mx = mx / len * dist;
            my = my / len * dist;
        }

        double nx = p.x + mx, ny = p.y + my;
        nx = clamp(nx, 0.3, 8.7);
        ny = clamp(ny, 0.3, 17.7);
        if (p.team == 0 && ny > NET_Y - 0.3) ny = NET_Y - 0.3;
        if (p.team == 1 && ny < NET_Y + 0.3) ny = NET_Y + 0.3;
        p.x = nx;
        p.y = ny;

        if (keySpace && !jumpConsumed) {
            jumpConsumed = true;
            p.jump();
        }

        if (len > 0.001) {
            p.runTimer += dt;
            p.isMoving = true;
        } else {
            p.runTimer = 0;
            p.isMoving = false;
        }
    }

    private void checkHumanSwing() {
        if (passClicked) { passClicked = false; controlledPlayer.startSwing(false); }
        if (spikeClicked) { spikeClicked = false; controlledPlayer.startSwing(true); }
    }

    private void checkHumanHit() {
        if (!ballInPlay) return;
        // 玩家击球范围比AI大一点(1.05m vs 0.9m)，扣球范围更大(1.2m)
        boolean spiking = (controlledPlayer.swingType == 2);
        double humanRadius = spiking ? 1.2 : 1.05;
        if (controlledPlayer.canHit(ballX, ballY, ballZ, humanRadius)) {
            if (controlledPlayer.swingType != 0 && controlledPlayer.swingTimer < 0.22 && controlledPlayer.swingTimer > 0.03) {
                hitBall(controlledPlayer, controlledPlayer.swingType == 2);
                controlledPlayer.swingTimer = 0.02;
            }
        }
    }

    /** 根据难度获取该队AI犯错概率 */
    private double getBlunderChance(int team) {
        if (team == 0) {
            return (difficulty == Difficulty.EASY) ? 0.08 : (difficulty == Difficulty.NORMAL) ? 0.20 : 0.30;
        } else {
            return (difficulty == Difficulty.EASY) ? 0.45 : (difficulty == Difficulty.NORMAL) ? 0.20 : 0.06;
        }
    }

    /** AI击球检测：加入犯错冷却机制——一次犯错在球离开前不再重试 */
    private void checkAIHit() {
        if (!ballInPlay) return;
        for (PlayerData p : allPlayers) {
            if (p.isHumanControlled) continue;
            // 在犯错冷却中且球还在范围内→跳过
            if (p.aiBlunderCooldown > 0 && p.canHit(ballX, ballY, ballZ)) {
                p.swingType = 0; p.swingTimer = 0;
                continue;
            }
            if (p.canHit(ballX, ballY, ballZ) && p.swingType != 0) {
                // 首次进入击球范围时决定是否犯错
                if (p.aiBlunderCooldown <= 0 && rnd.nextDouble() < getBlunderChance(p.team)) {
                    // 犯错: 放弃这次击球, 冷却0.5秒
                    p.aiBlunderCooldown = 0.5;
                    p.swingType = 0; p.swingTimer = 0;
                    continue;
                }
                boolean spike = (p.swingType == 2);
                aiDoHit(p, spike);
                p.swingType = 0; p.swingTimer = 0;
                break;
            }
        }
        
        // 球离开所有AI范围，重置犯错冷却
        if (!ballInPlay) {
            for (PlayerData p : allPlayers) {
                if (!p.isHumanControlled) p.aiBlunderCooldown = 0;
            }
        }
    }

    private void hitBall(PlayerData p, boolean spike) {
        double vh = p.isHumanControlled ? speedV : (spike ? 5.0 : 4.0);
        if (spike) vh *= 1.5; // 扣球水平速度x1.5，确保球能打过网
        ballVx = p.facingX * vh;
        ballVy = p.facingY * vh;
        ballVz = spike ? -1.0 : 5.0;
        lastTouchedByTeam = p.team;
    }

    private void aiDoHit(PlayerData p, boolean spike) {
        double vh = spike ? 5.0 : 4.0;
        PlayerData[] opps = (p.team == 0) ? enemyTeam : myTeam;
        double y0 = (p.team == 0) ? 9 : 0, y1 = (p.team == 0) ? 18 : 9;
        // ✅ 更精细的9×5网格搜索空档
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

    private void checkNet(double dt) {
        double prev = ballY - ballVy * dt;
        if ((prev - NET_Y) * (ballY - NET_Y) < 0 && ballZ < NET_H) {
            ballY = NET_Y - (ballY - NET_Y) * 0.3;
            ballVy = -ballVy * 0.2;
            ballVz *= 0.7;
            netHitX = ballX;
            netHitTimer = 0.3;
        }
    }

    private void checkScore() {
        if (ballLanded) return;
        ballLanded = true;
        boolean in = ballX >= 0 && ballX <= 9 && ballY >= 0 && ballY <= 18;
        int scorer = -1;
        if (in) {
            if (ballY < NET_Y) { enemyScore++; servingTeam = 1; scorer = 1; }
            else { myScore++; servingTeam = 0; scorer = 0; }
        } else {
            if (lastTouchedByTeam == 0) { enemyScore++; servingTeam = 1; scorer = 1; }
            else if (lastTouchedByTeam == 1) { myScore++; servingTeam = 0; scorer = 0; }
            else {
                if (servingTeam == 0) { enemyScore++; servingTeam = 1; scorer = 1; }
                else { myScore++; servingTeam = 0; scorer = 0; }
            }
        }
        // 裁判挥手
        lastScorer = scorer;
        refArmTimer = 1.5;
        // 吹哨
        playWhistle();
        // 加油气泡（我方得分用鼓励语，敌方得分用安慰语）
        cheerTimer = 2.0;
        String[] texts = (scorer == 0) ? CHEER_WIN_TEXTS : CHEER_LOSE_TEXTS;
        int idx = rnd.nextInt(texts.length);
        currentCheer = String.format(texts[idx], playerName);
        // 气泡出现在观众席区域（场外），不在场内
        int side = rnd.nextInt(4);
        if (side == 0) { // 上侧观众席
            cheerX = 1 + rnd.nextDouble() * 7;
            cheerY = 18.3 + rnd.nextDouble() * 1.0;
        } else if (side == 1) { // 下侧观众席
            cheerX = 1 + rnd.nextDouble() * 7;
            cheerY = -1.0 - rnd.nextDouble() * 0.8;
        } else if (side == 2) { // 左侧观众席
            cheerX = -1.0 - rnd.nextDouble() * 1.2;
            cheerY = 2 + rnd.nextDouble() * 14;
        } else { // 右侧观众席
            cheerX = 9.0 + rnd.nextDouble() * 1.2;
            cheerY = 2 + rnd.nextDouble() * 14;
        }
        
        ui.scoreText.setText("我方 " + myScore + " - " + enemyScore + " 敌方");
        state = State.SCORE;
        scoreTimer = 0;
        ui.stateText.setText("得分!");
    }

    // ===== AI =====
    private void updateAI(double dt) {
        assignReceivers();

        for (PlayerData p : myTeam) {
            if (p.isHumanControlled) continue;
            boolean rec = (idxIn(p, myTeam) == myAssigned);
            doAI(p, dt, rec);
        }
        for (int i = 0; i < 3; i++) {
            PlayerData p = enemyTeam[i];
            boolean rec = (i == enemyAssigned);
            doAI(p, dt, rec);
        }
    }

    /** ✅ 更好的接球者分配: 考虑到达时间和反应时间 */
    private void assignReceivers() {
        if (!ballInPlay) { myAssigned = enemyAssigned = -1; return; }
        double[] land = predictLand();
        if (land == null) { myAssigned = enemyAssigned = -1; return; }
        myAssigned = bestRecv(myTeam, land);
        enemyAssigned = bestRecv(enemyTeam, land);
    }

    /** 计算球降到目标高度targetZ时的位置（拦截点） */
    private double[] predictAtHeight(double targetZ) {
        // vz(t) = vz0 - g*t, z(t) = z0 + vz0*t - 0.5*g*t^2
        // 解: 0.5*g*t^2 - vz0*t + (targetZ - ballZ) = 0
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

    /** 预测球落地位置 */
    private double[] predictLand() {
        if (ballZ <= 0) return new double[]{ballX, ballY};
        double disc = ballVz * ballVz + 2 * BALL_G * ballZ;
        if (disc < 0) return null;
        double t = (ballVz + Math.sqrt(disc)) / BALL_G;
        if (t <= 0) return null;
        return new double[]{ballX + ballVx * t, ballY + ballVy * t};
    }

    /** 选最佳接球者: 计算谁到拦截点最快。我方AI不抢玩家附近的球。 */
    private int bestRecv(PlayerData[] team, double[] interceptPt) {
        double best = Double.MAX_VALUE;
        int idx = -1;
        for (int i = 0; i < 3; i++) {
            if (team[i].isHumanControlled) continue;
            double d = dist(team[i].x, team[i].y, interceptPt[0], interceptPt[1]);
            double time = d / PlayerData.MOVE_SPEED + 0.08;
            if (time < best) { best = time; idx = i; }
        }
        // 如果我方AI，检查球落点是否靠近人类玩家，是的话AI不去抢
        if (team[0].team == 0 && idx >= 0 && controlledPlayer != null) {
            double playerDist = dist(controlledPlayer.x, controlledPlayer.y, interceptPt[0], interceptPt[1]);
            // 球落点在玩家附近2.5m以内，AI不抢
            if (playerDist < 2.5) {
                return -1;
            }
        }
        return idx;
    }

    /** 智能AI: 核心逻辑 - 跑向击球高度拦截点，不在原地跳 */
    private void doAI(PlayerData p, double dt, boolean receiver) {
        // 关键: 球在0.2m以下或球已落地时不跳跃、不追落地处
        if (ballZ <= 0.2 || !ballInPlay) {
            // 球太低或不在飞行中，回默认位置
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

        // 计算球在 z=1.0m (最佳击球高度) 时的拦截位置
        double[] intercept = predictAtHeight(1.0);
        double[] land = predictLand();

        double targetX = p.defX, targetY = p.defY;
        boolean hasValidTarget = false;

        if (ballZ > 1.2 && intercept != null) {
            // 球还高，跑向 z=1.0m 拦截点
            targetX = intercept[0]; targetY = intercept[1];
            hasValidTarget = true;
        } else if (ballZ <= 1.2 && ballZ > 0.2) {
            // 球已低于击球高度，直接跑向球当前位置（尽量接近）
            targetX = ballX; targetY = ballY;
            hasValidTarget = true;
        }

        // 如果是接球者，精确跑向拦截点；否则防守性靠拢
        if (hasValidTarget) {
            if (receiver) {
                // 接球者: 全速跑向拦截点
                moveToward(p, targetX, targetY, dt);
            } else {
                // 非接球者: 以阵型位置为基准，向球方向适度靠拢
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
            // 无有效目标，回默认位置
            if (dist(p.x, p.y, p.defX, p.defY) > 0.3) {
                moveToward(p, p.defX, p.defY, dt);
            }
        }

        // 面向目标
        updateAIFacing(p, targetX, targetY);
        p.runTimer += dt;
        p.isMoving = true;

        // 检查是否能击球（只有球还在合适高度时才击球，不跳跃）
        if (ballZ > 0.3 && ballZ < 3.5 && p.canHit(ballX, ballY, ballZ)) {
            if (p.swingType == 0) {
                boolean spike = ballZ > 1.8 && Math.abs(p.y - NET_Y) < 1.8;
                p.startSwing(spike);
            }
        }

        // 只在高球近身且有机会够到时才跳跃（球>2m且距离<1.5m时跳）
        if (ballZ > 2.0 && p.z <= 0.01 && p.canHit(ballX, ballY, ballZ + 0.5)) {
            p.jump();
        }
    }

    private void updateAIFacing(PlayerData p, double tx, double ty) {
        double dx = tx - p.x, dy = ty - p.y;
        double d = Math.sqrt(dx * dx + dy * dy);
        if (d > 0.001) {
            p.updateFacing(dx / d, dy / d);
        }
    }

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

    private int idxIn(PlayerData p, PlayerData[] t) {
        for (int i = 0; i < 3; i++) if (t[i] == p) return i;
        return -1;
    }

    private static double dist(double x1, double y1, double x2, double y2) {
        double dx = x1 - x2, dy = y1 - y2;
        return Math.sqrt(dx * dx + dy * dy);
    }

    private static double clamp(double v, double lo, double hi) { return Math.max(lo, Math.min(hi, v)); }

    private void consumeJump() { jumpConsumed = true; }

    /** 播放裁判哨声 */
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
