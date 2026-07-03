package com.volleyballgame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import java.io.File;

/**
 * 应用主入口 - 管理登录界面→模式选择→正式游戏的全流程
 * 
 * 界面流程：
 *   loginScene(输入名字) → modeScene(选难度+开始BGM) → gameScene(排球游戏)
 * 
 * 正式游戏界面的UI文本(在窗口最上方分行居中显示，不遮挡场地)：
 *   选手: xxx  (金色)
 *   控制: 前排 (青色)
 *   我方 0-0 敌方 (白色)
 *   速度 V: 4.0 (黄色)
 *   按空格键开始比赛 (绿色)
 * 
 * BGM：排球少年OP2《FLY HIGH!!》，从模式选择界面开始循环播放(音量35%)
 * 退出游戏或按ESC时停止BGM
 */
public class Main extends Application {

    /** 窗口尺寸(像素) */
    static final int W = 1280, H = 720;

    /** 游戏界面UI文本对象(由GameCore更新内容) */
    Text scoreText, speedText, stateText, playerText, modeText;

    /** 玩家登录时输入的名字，显示在气泡中 */
    String playerName = "玩家";

    /** BGM播放器(JavaFX MediaPlayer，循环播放) */
    MediaPlayer bgmPlayer;

    /** 计算每行UI文本的Y坐标(从28px开始，每行间距18px) */
    static double PC(int row) { return 28 + row * 18; }

    /** 难度枚举转中文名 */
    static String difficultyName(GameCore.Difficulty d) {
        if (d == GameCore.Difficulty.EASY) return "简单";
        if (d == GameCore.Difficulty.HARD) return "困难";
        return "普通";
    }

    @Override
    public void start(Stage stage) {
        // 预加载BGM(不播放，等进入模式选择界面再播放)
        try {
            File mp3 = new File("src/main/resources/bgm.mp3");
            if (mp3.exists()) {
                bgmPlayer = new MediaPlayer(new Media(mp3.toURI().toString()));
                bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE); // 无限循环
                bgmPlayer.setVolume(0.35); // 35%音量
            }
        } catch (Exception ex) { System.out.println("BGM加载失败: " + ex.getMessage()); }

        // ===== 登录界面 =====
        VBox loginBox = new VBox(20); // 垂直布局，间距20px
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setStyle("-fx-background-color: #1a1a2e;");

        Text loginTitle = new Text("🏐 3v3 排球小游戏");
        loginTitle.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        loginTitle.setFill(Color.WHITE);

        Text loginSub = new Text("请输入你的选手名");
        loginSub.setFont(Font.font("Arial", 20));
        loginSub.setFill(Color.rgb(200, 200, 220));

        TextField nameField = new TextField("玩家"); // 默认名"玩家"
        nameField.setMaxWidth(300);
        nameField.setFont(Font.font("Arial", 22));
        nameField.setStyle("-fx-background-color: #2a2a4e; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 8;");

        Button loginBtn = new Button("🚀 进入游戏");
        loginBtn.setFont(Font.font("Arial", 20));
        loginBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-padding: 12 40; -fx-background-radius: 10;");

        Text errorText = new Text(""); // 显示验证错误
        errorText.setFill(Color.rgb(255, 100, 100));

        loginBox.getChildren().addAll(loginTitle, loginSub, nameField, loginBtn, errorText);
        Scene loginScene = new Scene(loginBox, W, H);

        // ===== 模式选择界面 =====
        VBox modeBox = new VBox(22);
        modeBox.setAlignment(Pos.CENTER);
        modeBox.setStyle("-fx-background-color: #1a1a2e;");

        Text mt = new Text("🏐 3v3 排球小游戏");
        mt.setFont(Font.font("Arial", FontWeight.BOLD, 36)); mt.setFill(Color.WHITE);
        Text ms = new Text("请选择游戏模式");
        ms.setFont(Font.font("Arial", 22)); ms.setFill(Color.rgb(200, 200, 220));

        Button btnEasy = btn("🟢 简单模式", "#4CAF50");
        Button btnNormal = btn("🟡 普通模式", "#FF9800");
        Button btnHard = btn("🔴 困难模式", "#F44336");

        Button backBtn = new Button("↩ 返回修改名字");
        backBtn.setFont(Font.font("Arial", 14));
        backBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 8;");

        modeBox.getChildren().addAll(mt, ms, btnEasy, btnNormal, btnHard, backBtn);
        Scene modeScene = new Scene(modeBox, W, H);

        // ===== 登录按钮逻辑：验证名字→跳转模式选择→开始BGM =====
        loginBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorText.setText("名字不能为空！"); return; }
            if (name.length() > 10) { errorText.setText("名字太长了！"); return; }
            playerName = name;
            stage.setScene(modeScene);
            // 进入模式选择界面后开始播放BGM
            try { if (bgmPlayer != null) bgmPlayer.play(); } catch (Exception ex) { }
        });

        // ===== 模式选择按钮逻辑 =====
        btnEasy.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.EASY));
        btnNormal.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.NORMAL));
        btnHard.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.HARD));

        // 返回修改名字
        backBtn.setOnAction(e -> { stage.setScene(loginScene); });

        stage.setScene(loginScene);
        stage.setTitle("3v3 Volleyball - 登录");
        stage.setMinWidth(900); stage.setMinHeight(600);
        stage.show();
    }

    /** 创建模式选择按钮(统一样式) */
    private Button btn(String t, String c) {
        Button b = new Button(t);
        b.setFont(Font.font("Arial", 20));
        b.setStyle("-fx-background-color: " + c + "; -fx-text-fill: white; -fx-padding: 12 45; -fx-background-radius: 10;");
        return b;
    }

    /**
     * 启动正式游戏
     * 创建Canvas+GameRenderer+GameCore，绑定键盘/鼠标事件，启动游戏循环
     * 使用Pane(非StackPane)作为容器，支持Text.setX/setY绝对定位
     */
    private void startGame(Stage stage, Scene modeScene, GameCore.Difficulty difficulty) {
        Canvas canvas = new Canvas(W, H);
        GraphicsContext g = canvas.getGraphicsContext2D();

        // ===== UI文本(窗口最上方分行居中) =====
        modeText = new Text("选手: " + playerName);
        modeText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        modeText.setStyle("-fx-fill: #FFD700;"); // 金色
        modeText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        modeText.setX(0); modeText.setWrappingWidth(W); modeText.setY(PC(0));

        playerText = new Text("");
        playerText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        playerText.setStyle("-fx-fill: cyan;"); // 青色
        playerText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        playerText.setX(0); playerText.setWrappingWidth(W); playerText.setY(PC(1));

        scoreText = new Text("我方 0 - 0 敌方");
        scoreText.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        scoreText.setStyle("-fx-fill: white;");
        scoreText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        scoreText.setX(0); scoreText.setWrappingWidth(W); scoreText.setY(PC(2));

        speedText = new Text("速度 V: 3.0");
        speedText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        speedText.setStyle("-fx-fill: yellow;"); // 黄色
        speedText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        speedText.setX(0); speedText.setWrappingWidth(W); scoreText.setY(PC(3));

        stateText = new Text("按空格键开始游戏");
        stateText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        stateText.setStyle("-fx-fill: lime;"); // 绿色
        stateText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        stateText.setX(0); stateText.setWrappingWidth(W); stateText.setY(PC(4));

        // ===== 渲染器和核心引擎 =====
        GameRenderer rend = new GameRenderer(g, W, H);

        // UI组(鼠标穿透，不阻挡Canvas交互)
        javafx.scene.Group uiGroup = new javafx.scene.Group();
        uiGroup.setMouseTransparent(true);
        uiGroup.getChildren().addAll(scoreText, speedText, stateText, playerText, modeText);

        // Pane支持绝对定位(StackPane会忽略setX/setY)
        Pane root = new Pane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.getChildren().addAll(canvas, uiGroup);

        Scene scene = new Scene(root, W, H);

        GameCore core = new GameCore(rend, this, scene, difficulty, playerName);
        core.init();

        // ===== 键盘/鼠标事件绑定 =====
        scene.setOnKeyPressed(e -> {
            core.handleKeyDown(e.getCode());
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.setScene(modeScene); // ESC返回模式选择
            }
        });
        scene.setOnKeyReleased(e -> core.handleKeyUp(e.getCode()));
        scene.setOnMouseMoved(e -> core.mouseMoved(e.getSceneX(), e.getSceneY()));
        scene.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) core.passClicked = true;    // 左键→传球
            else if (e.getButton() == MouseButton.SECONDARY) core.spikeClicked = true; // 右键→扣球
        });

        core.start(); // 启动游戏循环
        stage.setScene(scene);
        stage.setTitle("3v3 Volleyball");
    }

    /** 停止BGM(ESC或退出时调用) */
    public void stopBGM() {
        if (bgmPlayer != null) bgmPlayer.stop();
    }

    public static void main(String[] args) { launch(args); }
}