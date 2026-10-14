package com.mazeescape;

import java.util.EnumSet;
import java.util.Set;
import javafx.animation.AnimationTimer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

/**
 * Maze Escape: the application entry point.
 *
 * This class owns the window, the ONE game loop, keyboard input and the "state"
 * the game is in (menu, playing, paused...). The rules live in GameWorld, the
 * drawing lives in Renderer and the menu screens live in Screens.
 */
public class MazeEscape extends Application {

    private enum State {
        MENU, LEVEL_SELECT, HOW_TO, SETTINGS, PLAYING, PAUSED, LEVEL_COMPLETE, GAME_OVER, VICTORY
    }

    private final Set<KeyCode> held = EnumSet.noneOf(KeyCode.class);

    private GraphicsContext gc;
    private Renderer renderer;
    private Screens screens;
    private StackPane overlay;
    private PixelButton pauseButton;
    private Panel currentPanel;

    private State state = State.MENU;
    private State settingsReturn = State.MENU;

    private GameWorld world;
    private int levelIndex;
    private int runLives;        // lives the player had when the current level started
    private int runScore;        // score the player had when the current level started
    private int bestScore;
    private int unlockedLevels;  // how many levels the level select lets you open

    private double time;         // seconds since launch, used for animation
    private long lastNanos;

    @Override
    public void start(Stage stage) {
        bestScore = ScoreStore.load();
        unlockedLevels = Math.min(Levels.count(), ScoreStore.loadUnlocked());

        Canvas canvas = new Canvas(Renderer.WIDTH, Renderer.HEIGHT);
        gc = canvas.getGraphicsContext2D();
        gc.setImageSmoothing(false);
        renderer = new Renderer(gc);
        screens = new Screens(this);

        overlay = new StackPane();
        overlay.setMouseTransparent(true);

        // The on-screen pause button, in the top-right corner of the HUD.
        pauseButton = new PixelButton("II", 44, 34, 16);
        pauseButton.setVisible(false);
        pauseButton.setOnAction(e -> {
            if (state == State.PLAYING) {
                pause();
            }
        });
        pauseButton.setOnMouseEntered(e -> pauseButton.setSelected(true));
        pauseButton.setOnMouseExited(e -> pauseButton.setSelected(false));
        StackPane.setAlignment(pauseButton, Pos.TOP_RIGHT);
        StackPane.setMargin(pauseButton, new Insets(11, 12, 0, 0));

        StackPane root = new StackPane(canvas, overlay, pauseButton);
        root.setStyle("-fx-background-color: #0b1220;");

        Scene scene = new Scene(root, Renderer.WIDTH, Renderer.HEIGHT, Color.web("#0b1220"));
        scene.addEventFilter(KeyEvent.KEY_PRESSED, this::onKeyPressed);
        scene.addEventFilter(KeyEvent.KEY_RELEASED, e -> held.remove(e.getCode()));

        // Losing focus: forget held keys and pause so the player does not run into spikes.
        stage.focusedProperty().addListener((obs, wasFocused, isFocused) -> {
            if (!isFocused) {
                held.clear();
                if (state == State.PLAYING) {
                    pause();
                }
            }
        });

        stage.setTitle("Maze Escape");
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();

        showMainMenu();

        // The one and only game loop. It is created once here and never again,
        // so restarting a level can never stack up extra timers.
        AnimationTimer loop = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (lastNanos == 0) {
                    lastNanos = now;
                }
                double dt = Math.min((now - lastNanos) / 1_000_000_000.0, 0.05);
                lastNanos = now;
                tick(dt);
            }
        };
        loop.start();
    }

    // =====================================================================
    //  Game loop
    // =====================================================================

    private void tick(double dt) {
        time += dt;

        if (state == State.PLAYING && world != null) {
            GameWorld.Result result = world.update(dt, isLeft(), isRight(), isUp(), isDown());
            if (result == GameWorld.Result.WON) {
                onLevelWon();
            } else if (result == GameWorld.Result.OUT_OF_LIVES) {
                onGameOver("You ran out of lives!");
            } else if (result == GameWorld.Result.OUT_OF_TIME) {
                onGameOver("Time ran out!");
            }
        }

        pauseButton.setVisible(state == State.PLAYING);
        render();
    }

    private void render() {
        if (showsGame()) {
            renderer.drawGame(world, time);
            switch (state) {
                case PAUSED, SETTINGS -> renderer.drawTint(Color.rgb(4, 8, 14, 0.62));
                case LEVEL_COMPLETE -> renderer.drawTint(Color.rgb(6, 30, 40, 0.65));
                case GAME_OVER -> renderer.drawTint(Color.rgb(70, 8, 8, 0.65));
                case VICTORY -> renderer.drawTint(Color.rgb(6, 40, 24, 0.65));
                default -> { }
            }
        } else {
            renderer.drawMenu(time);
        }
    }

    private boolean showsGame() {
        if (world == null) {
            return false;
        }
        return switch (state) {
            case MENU, LEVEL_SELECT, HOW_TO -> false;
            case SETTINGS -> settingsReturn == State.PAUSED;
            default -> true;
        };
    }

    // =====================================================================
    //  Keyboard
    // =====================================================================

    private boolean isLeft() {
        return held.contains(KeyCode.A) || held.contains(KeyCode.LEFT);
    }

    private boolean isRight() {
        return held.contains(KeyCode.D) || held.contains(KeyCode.RIGHT);
    }

    private boolean isUp() {
        return held.contains(KeyCode.W) || held.contains(KeyCode.UP);
    }

    private boolean isDown() {
        return held.contains(KeyCode.S) || held.contains(KeyCode.DOWN);
    }

    private void onKeyPressed(KeyEvent e) {
        KeyCode key = e.getCode();
        boolean freshPress = held.add(key);   // false when the key is just auto-repeating
        e.consume();
        if (!freshPress) {
            return;
        }

        switch (state) {
            case PLAYING -> {
                if (key == KeyCode.ESCAPE || key == KeyCode.SPACE || key == KeyCode.P) {
                    pause();
                }
            }
            case PAUSED -> {
                if (key == KeyCode.ESCAPE || key == KeyCode.SPACE || key == KeyCode.P) {
                    resume();
                } else {
                    menuKey(key);
                }
            }
            case HOW_TO, SETTINGS, LEVEL_SELECT -> {
                if (key == KeyCode.ESCAPE) {
                    goBack();
                } else {
                    menuKey(key);
                }
            }
            default -> menuKey(key);
        }
    }

    private void menuKey(KeyCode key) {
        if (currentPanel == null) {
            return;
        }
        switch (key) {
            case UP, W -> currentPanel.move(0, -1);
            case DOWN, S -> currentPanel.move(0, 1);
            case LEFT, A -> currentPanel.move(-1, 0);
            case RIGHT, D -> currentPanel.move(1, 0);
            case ENTER, SPACE -> currentPanel.activate();
            default -> { }
        }
    }

    // =====================================================================
    //  Screens and game flow (the Screens buttons call these)
    // =====================================================================

    private void setPanel(Panel panel) {
        overlay.getChildren().clear();
        currentPanel = panel;
        if (panel != null) {
            overlay.getChildren().add(panel.node);
            overlay.setMouseTransparent(false);
        } else {
            overlay.setMouseTransparent(true);
        }
    }

    void showMainMenu() {
        world = null;
        state = State.MENU;
        setPanel(screens.mainMenu(bestScore));
    }

    void openLevels() {
        world = null;
        state = State.LEVEL_SELECT;
        setPanel(screens.levelSelect(effectiveUnlocked()));
    }

    void openHowTo() {
        state = State.HOW_TO;
        setPanel(screens.howTo());
    }

    void openSettings() {
        settingsReturn = state;   // remember whether we came from the main menu or the pause menu
        state = State.SETTINGS;
        setPanel(screens.settings());
    }

    /** Back button / Esc on the Level Select, How To Play and Settings screens. */
    void goBack() {
        if (state == State.SETTINGS && settingsReturn == State.PAUSED) {
            state = State.PAUSED;
            setPanel(screens.pause());
        } else {
            showMainMenu();
        }
    }

    private int effectiveUnlocked() {
        return Settings.unlockAll ? Levels.count() : unlockedLevels;
    }

    /** PLAY button: a brand new run from level 1. */
    void startNewRun() {
        startFromLevel(0);
    }

    /** Level select: a new run (3 lives, score 0) that begins at the chosen level. */
    void startFromLevel(int index) {
        levelIndex = index;
        runLives = GameWorld.MAX_LIVES;
        runScore = 0;
        startLevel();
    }

    /** Builds a fresh world for the current level. Used for starting, restarting and retrying. */
    private void startLevel() {
        Level level = Levels.get(levelIndex);
        world = new GameWorld(level, runLives, runScore);
        renderer.prepare(world);
        state = State.PLAYING;
        setPanel(null);
    }

    void restartLevel() {
        startLevel();
    }

    void retryLevel() {
        runLives = GameWorld.MAX_LIVES;
        startLevel();
    }

    void continueToNextLevel() {
        levelIndex++;
        startLevel();
    }

    private void pause() {
        state = State.PAUSED;
        setPanel(screens.pause());
    }

    void resume() {
        state = State.PLAYING;
        setPanel(null);
    }

    private void onLevelWon() {
        runLives = world.lives;
        runScore = world.score;
        unlockNextLevel();

        if (levelIndex == Levels.count() - 1) {
            boolean newBest = recordScore(world.score);
            state = State.VICTORY;
            setPanel(screens.victory(world.score, bestScore, newBest));
        } else {
            state = State.LEVEL_COMPLETE;
            setPanel(screens.levelComplete(world));
        }
    }

    private void unlockNextLevel() {
        int needed = Math.min(Levels.count(), levelIndex + 2);
        if (needed > unlockedLevels) {
            unlockedLevels = needed;
            ScoreStore.saveUnlocked(unlockedLevels);
        }
    }

    private void onGameOver(String reason) {
        boolean newBest = recordScore(world.score);
        state = State.GAME_OVER;
        setPanel(screens.gameOver(reason, world.score, bestScore, newBest));
    }

    /** Saves the score if it beats the best one. Returns true when it did. */
    private boolean recordScore(int score) {
        if (score > bestScore) {
            bestScore = score;
            ScoreStore.save(score);
            return true;
        }
        return false;
    }

    void quit() {
        Platform.exit();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
