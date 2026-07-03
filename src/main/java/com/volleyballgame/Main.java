package com.volleyballgame;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.geometry.Pos;
import javafx.util.Duration;
import java.io.File;

/**
 * 排球小游戏 - 登录→模式选择→游戏
 */
public class Main extends Application {

    static final int W = 1280, H = 720;

    Text scoreText, speedText, stateText, playerText, modeText;
    String playerName = "玩家";
    MediaPlayer bgmPlayer;

    /** 每行文本的Y坐标 */
    static double PC(int row) { return 28 + row * 18; }
    static String difficultyName(GameCore.Difficulty d) {
        if (d == GameCore.Difficulty.EASY) return "简单";
        if (d == GameCore.Difficulty.HARD) return "困难";
        return "普通";
    }

    @Override
    public void start(Stage stage) {
        // 预加载BGM
        try {
            File mp3 = new File("src/main/resources/bgm.mp3");
            if (mp3.exists()) {
                bgmPlayer = new MediaPlayer(new Media(mp3.toURI().toString()));
                bgmPlayer.setCycleCount(MediaPlayer.INDEFINITE);
                bgmPlayer.setVolume(0.35);
            }
        } catch (Exception ex) { System.out.println("BGM: " + ex.getMessage()); }

        // ===== 登录界面 =====
        VBox loginBox = new VBox(20);
        loginBox.setAlignment(Pos.CENTER);
        loginBox.setStyle("-fx-background-color: #1a1a2e;");
        Text loginTitle = new Text("🏐 3v3 排球小游戏");
        loginTitle.setFont(Font.font("Arial", FontWeight.BOLD, 42));
        loginTitle.setFill(Color.WHITE);
        Text loginSub = new Text("请输入你的选手名");
        loginSub.setFont(Font.font("Arial", 20));
        loginSub.setFill(Color.rgb(200, 200, 220));
        TextField nameField = new TextField("玩家");
        nameField.setMaxWidth(300); nameField.setFont(Font.font("Arial", 22));
        nameField.setStyle("-fx-background-color: #2a2a4e; -fx-text-fill: white; -fx-padding: 10; -fx-background-radius: 8;");
        Button loginBtn = new Button("🚀 进入游戏");
        loginBtn.setFont(Font.font("Arial", 20));
        loginBtn.setStyle("-fx-background-color: #FF9800; -fx-text-fill: white; -fx-padding: 12 40; -fx-background-radius: 10;");
        Text errorText = new Text("");
        errorText.setFill(Color.rgb(255, 100, 100));
        loginBox.getChildren().addAll(loginTitle, loginSub, nameField, loginBtn, errorText);
        Scene loginScene = new Scene(loginBox, W, H);

        // ===== 模式选择 =====
        VBox modeBox = new VBox(22);
        modeBox.setAlignment(Pos.CENTER);
        modeBox.setStyle("-fx-background-color: #1a1a2e;");
        Text mt = new Text("🏐 3v3 排球小游戏"); mt.setFont(Font.font("Arial", FontWeight.BOLD, 36)); mt.setFill(Color.WHITE);
        Text ms = new Text("请选择游戏模式"); ms.setFont(Font.font("Arial", 22)); ms.setFill(Color.rgb(200, 200, 220));
        Button btnEasy = btn("🟢 简单模式", "#4CAF50");
        Button btnNormal = btn("🟡 普通模式", "#FF9800");
        Button btnHard = btn("🔴 困难模式", "#F44336");
        Button backBtn = new Button("↩ 返回修改名字");
        backBtn.setFont(Font.font("Arial", 14));
        backBtn.setStyle("-fx-background-color: #555; -fx-text-fill: white; -fx-padding: 8 20; -fx-background-radius: 8;");
        modeBox.getChildren().addAll(mt, ms, btnEasy, btnNormal, btnHard, backBtn);
        Scene modeScene = new Scene(modeBox, W, H);

        loginBtn.setOnAction(e -> {
            String name = nameField.getText().trim();
            if (name.isEmpty()) { errorText.setText("名字不能为空！"); return; }
            if (name.length() > 10) { errorText.setText("名字太长了！"); return; }
            playerName = name;
            stage.setScene(modeScene);
            try { if (bgmPlayer != null) bgmPlayer.play(); } catch (Exception ex) { }
        });
        btnEasy.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.EASY));
        btnNormal.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.NORMAL));
        btnHard.setOnAction(e -> startGame(stage, modeScene, GameCore.Difficulty.HARD));
        backBtn.setOnAction(e -> { stage.setScene(loginScene); });

        stage.setScene(loginScene);
        stage.setTitle("3v3 Volleyball - 登录");
        stage.setMinWidth(900); stage.setMinHeight(600);
        stage.show();
    }

    private Button btn(String t, String c) {
        Button b = new Button(t); b.setFont(Font.font("Arial", 20));
        b.setStyle("-fx-background-color: " + c + "; -fx-text-fill: white; -fx-padding: 12 45; -fx-background-radius: 10;");
        return b;
    }

    private void startGame(Stage stage, Scene modeScene, GameCore.Difficulty difficulty) {
        Canvas canvas = new Canvas(W, H);
        GraphicsContext g = canvas.getGraphicsContext2D();

        // UI 信息分行放在弹窗界面最上方，居中，不遮挡场地
        modeText = new Text("选手: " + playerName);
        modeText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        modeText.setStyle("-fx-fill: #FFD700;");
        modeText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        modeText.setX(0); modeText.setWrappingWidth(W); modeText.setY(PC(0));

        playerText = new Text("");
        playerText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        playerText.setStyle("-fx-fill: cyan;");
        playerText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        playerText.setX(0); playerText.setWrappingWidth(W); playerText.setY(PC(1));

        scoreText = new Text("我方 0 - 0 敌方");
        scoreText.setFont(Font.font("Arial", FontWeight.BOLD, 20));
        scoreText.setStyle("-fx-fill: white;");
        scoreText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        scoreText.setX(0); scoreText.setWrappingWidth(W); scoreText.setY(PC(2));

        speedText = new Text("速度 V: 3.0");
        speedText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        speedText.setStyle("-fx-fill: yellow;");
        speedText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        speedText.setX(0); speedText.setWrappingWidth(W); speedText.setY(PC(3));

        stateText = new Text("按空格键开始游戏");
        stateText.setFont(Font.font("Arial", FontWeight.BOLD, 13));
        stateText.setStyle("-fx-fill: lime;");
        stateText.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        stateText.setX(0); stateText.setWrappingWidth(W); stateText.setY(PC(4));

        GameRenderer rend = new GameRenderer(g, W, H);

        javafx.scene.Group uiGroup = new javafx.scene.Group();
        uiGroup.setMouseTransparent(true);
        uiGroup.getChildren().addAll(scoreText, speedText, stateText, playerText, modeText);

        Pane root = new Pane();
        root.setStyle("-fx-background-color: #1a1a2e;");
        root.getChildren().addAll(canvas, uiGroup);

        Scene scene = new Scene(root, W, H);

        GameCore core = new GameCore(rend, this, scene, difficulty, playerName);
        core.init();

        scene.setOnKeyPressed(e -> {
            core.handleKeyDown(e.getCode());
            if (e.getCode() == KeyCode.ESCAPE) {
                stage.setScene(modeScene);
            }
        });
        scene.setOnKeyReleased(e -> core.handleKeyUp(e.getCode()));
        scene.setOnMouseMoved(e -> core.mouseMoved(e.getSceneX(), e.getSceneY()));
        scene.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) core.passClicked = true;
            else if (e.getButton() == MouseButton.SECONDARY) core.spikeClicked = true;
        });

        core.start();
        stage.setScene(scene);
        stage.setTitle("3v3 Volleyball");
    }

    public void stopBGM() {
        if (bgmPlayer != null) bgmPlayer.stop();
    }

    public static void main(String[] args) { launch(args); }
}
