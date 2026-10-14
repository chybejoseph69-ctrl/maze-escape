package com.mazeescape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import javafx.geometry.HPos;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.VPos;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

/**
 * Builds every menu-style screen (main menu, level select, how to play, settings,
 * pause, level complete, game over, victory). Each method returns a Panel.
 * The buttons simply call methods on the MazeEscape class.
 */
public class Screens {

    private static final Color GOLD = Color.web("#e8b93c");
    private static final Color TEAL = Color.web("#3fc4b8");
    private static final Color LIGHT = Color.web("#d5e6ea");
    private static final Color MUTED = Color.web("#8fa8b0");
    private static final Color RED = Color.web("#e24848");
    private static final Color GREEN = Color.web("#43e58f");

    private static final String PANEL_STYLE =
            "-fx-background-color: rgba(9, 18, 30, 0.93);"
            + "-fx-border-color: #3a8f9a;"
            + "-fx-border-width: 3;"
            + "-fx-background-radius: 4;"
            + "-fx-border-radius: 4;";

    private final MazeEscape app;

    public Screens(MazeEscape app) {
        this.app = app;
    }

    // ------------------------------------------------------------------ main menu

    public Panel mainMenu(int best) {
        Label maze = title("MAZE", 58, GOLD);
        Label escape = title("ESCAPE", 58, GOLD);
        Label tagline = label("Explore  \u2022  Collect  \u2022  Escape", 14, TEAL);

        PixelButton play = button("PLAY", 260, app::startNewRun);
        PixelButton levels = button("LEVELS", 260, app::openLevels);
        PixelButton how = button("HOW TO PLAY", 260, app::openHowTo);
        PixelButton settings = button("SETTINGS", 260, app::openSettings);
        PixelButton quit = button("QUIT", 260, app::quit);

        Label bestLabel = label("BEST SCORE  " + score(best), 16, GOLD);

        VBox box = box(10, false);
        box.getChildren().addAll(maze, escape, tagline, gap(6), play, levels, how, settings, quit,
                gap(2), bestLabel);
        return new Panel(box, List.of(play, levels, how, settings, quit));
    }

    // ------------------------------------------------------------------ level select

    public Panel levelSelect(int unlocked) {
        GridPane grid = new GridPane();
        grid.setHgap(12);
        grid.setVgap(12);
        grid.setAlignment(Pos.CENTER);

        List<PixelButton> buttons = new ArrayList<>();
        for (int i = 0; i < Levels.count(); i++) {
            Level level = Levels.get(i);
            boolean open = i < unlocked;
            String text = "LEVEL " + level.number + "\n" + (open ? level.name : "LOCKED");
            PixelButton b = new PixelButton(text, 124, 66, 12);
            b.setLocked(!open);
            final int index = i;
            b.setOnAction(e -> {
                if (index < unlocked) {
                    app.startFromLevel(index);
                }
            });
            grid.add(b, i % 5, i / 5);
            buttons.add(b);
        }

        PixelButton back = button("BACK", 260, app::goBack);
        buttons.add(back);

        Label hint = body("Finish a level to unlock the next one.", 13, MUTED);

        VBox box = box(16, true);
        box.getChildren().addAll(title("SELECT LEVEL", 36, TEAL), hint, grid, back);
        return new Panel(box, buttons, 5);
    }

    // ------------------------------------------------------------------ how to play

    public Panel howTo() {
        String[][] rows = {
            {"MOVE", "WASD or the Arrow Keys. Pause with SPACE, ESC or the II button."},
            {"GOAL", "Collect every gold key, then reach the exit before time runs out."},
            {"EXIT", "Locked doors glow red. Once you hold all the keys the door turns green."},
            {"LOOT", "Coins give 100. Hidden chests give 400. Push into cracked walls to find secret rooms."},
            {"TRAPS", "Spikes cost a life. Pop-up spikes and fire vents flash before they strike. Faint cracks on the floor can hide spikes."},
            {"FOES", "Wraiths patrol corridors. Stalkers chase you when you get close. Webs slow you down."},
            {"HELP", "Hearts restore a life. Blue crystals add 15 seconds."},
            {"SCORE", "Finish with time to spare for a bigger bonus."}
        };

        GridPane grid = new GridPane();
        grid.setHgap(22);
        grid.setVgap(9);
        for (int i = 0; i < rows.length; i++) {
            Label key = label(rows[i][0], 14, GOLD);
            key.setMinWidth(66);
            Label text = body(rows[i][1], 14, LIGHT);
            text.setWrapText(true);
            text.setPrefWidth(470);
            text.setMinHeight(Region.USE_PREF_SIZE);
            GridPane.setValignment(key, VPos.TOP);
            grid.add(key, 0, i);
            grid.add(text, 1, i);
        }

        PixelButton back = button("BACK", 260, app::goBack);

        VBox box = box(14, true);
        box.getChildren().addAll(title("HOW TO PLAY", 34, TEAL), grid, back);
        return new Panel(box, List.of(back));
    }

    // ------------------------------------------------------------------ settings

    public Panel settings() {
        PixelButton shake = toggle("SCREEN SHAKE", () -> Settings.screenShake, v -> Settings.screenShake = v);
        PixelButton lighting = toggle("LIGHTING", () -> Settings.lighting, v -> Settings.lighting = v);
        PixelButton unlock = toggle("UNLOCK ALL LEVELS", () -> Settings.unlockAll, v -> Settings.unlockAll = v);
        PixelButton back = button("BACK", 380, app::goBack);

        VBox box = box(14, true);
        box.getChildren().addAll(title("SETTINGS", 36, TEAL), gap(6), shake, lighting, unlock, gap(6), back);
        return new Panel(box, List.of(shake, lighting, unlock, back));
    }

    // ------------------------------------------------------------------ pause

    public Panel pause() {
        PixelButton resume = button("RESUME", 300, app::resume);
        PixelButton restart = button("RESTART LEVEL", 300, app::restartLevel);
        PixelButton settings = button("SETTINGS", 300, app::openSettings);
        PixelButton menu = button("MAIN MENU", 300, app::showMainMenu);

        VBox box = box(12, true);
        box.getChildren().addAll(title("PAUSED", 44, LIGHT), gap(6), resume, restart, settings, menu);
        return new Panel(box, List.of(resume, restart, settings, menu));
    }

    // ------------------------------------------------------------------ level complete

    public Panel levelComplete(GameWorld w) {
        GridPane grid = new GridPane();
        grid.setHgap(30);
        grid.setVgap(8);

        int row = 0;
        addRow(grid, row++, "Time Remaining", clock(w.timeLeft), "+" + w.timeBonus, LIGHT, GREEN);
        addRow(grid, row++, "Keys Collected", w.keys + "/" + w.level.keysRequired,
                "+" + (w.keys * GameWorld.KEY_POINTS), LIGHT, GREEN);
        addRow(grid, row++, "Coins Collected", w.coins + "/" + w.level.coinsTotal,
                "+" + (w.coins * GameWorld.COIN_POINTS), LIGHT, GREEN);
        if (w.level.chestsTotal > 0) {
            addRow(grid, row++, "Hidden Treasure", w.chests + "/" + w.level.chestsTotal,
                    "+" + (w.chests * GameWorld.CHEST_POINTS), LIGHT, GREEN);
        }
        if (w.level.secretsTotal > 0) {
            addRow(grid, row++, "Secrets Found", w.secretsFound + "/" + w.level.secretsTotal,
                    "+" + (w.secretsFound * GameWorld.SECRET_POINTS), LIGHT, GREEN);
        }
        if (w.trapPenalty > 0) {
            addRow(grid, row++, "Trap Penalty", "", "-" + w.trapPenalty, LIGHT, Color.web("#e07070"));
        }

        Region line = new Region();
        line.setMinHeight(2);
        line.setPrefHeight(2);
        line.setStyle("-fx-background-color: #3a8f9a;");
        grid.add(line, 0, row++, 3, 1);

        Label totalName = label("TOTAL SCORE", 22, GOLD);
        Label totalValue = label(score(w.score), 22, GOLD);
        grid.add(totalName, 0, row, 2, 1);
        grid.add(totalValue, 2, row);
        GridPane.setHalignment(totalValue, HPos.RIGHT);

        PixelButton next = button("CONTINUE", 300, app::continueToNextLevel);

        VBox box = box(14, true);
        box.getChildren().addAll(title("LEVEL COMPLETE!", 38, GOLD), grid, next);
        return new Panel(box, List.of(next));
    }

    // ------------------------------------------------------------------ game over

    public Panel gameOver(String reason, int finalScore, int best, boolean newBest) {
        Label why = label(reason, 16, Color.web("#d6a0a0"));

        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(8);
        addScoreRow(grid, 0, "Final Score", score(finalScore), LIGHT);
        addScoreRow(grid, 1, "Best Score", score(best), GOLD);

        PixelButton retry = button("RETRY", 260, app::retryLevel);
        PixelButton menu = button("MAIN MENU", 260, app::showMainMenu);

        VBox box = box(14, true);
        box.setStyle(PANEL_STYLE.replace("#3a8f9a", "#9a3a3a"));
        box.getChildren().addAll(title("GAME OVER", 52, RED), why, grid);
        if (newBest) {
            box.getChildren().add(label("NEW BEST SCORE!", 18, GOLD));
        }
        box.getChildren().addAll(gap(4), retry, menu);
        return new Panel(box, List.of(retry, menu));
    }

    // ------------------------------------------------------------------ victory

    public Panel victory(int finalScore, int best, boolean newBest) {
        Label text = label("You escaped all " + Levels.count() + " mazes!", 16, LIGHT);

        GridPane grid = new GridPane();
        grid.setHgap(40);
        grid.setVgap(8);
        addScoreRow(grid, 0, "Final Score", score(finalScore), LIGHT);
        addScoreRow(grid, 1, "Best Score", score(best), GOLD);

        PixelButton again = button("PLAY AGAIN", 260, app::startNewRun);
        PixelButton menu = button("MAIN MENU", 260, app::showMainMenu);

        VBox box = box(14, true);
        box.setStyle(PANEL_STYLE.replace("#3a8f9a", "#3aa06a"));
        box.getChildren().addAll(title("YOU ESCAPED!", 46, GREEN), text, grid);
        if (newBest) {
            box.getChildren().add(label("NEW BEST SCORE!", 18, GOLD));
        }
        box.getChildren().addAll(gap(4), again, menu);
        return new Panel(box, List.of(again, menu));
    }

    // ------------------------------------------------------------------ small helpers

    private void addRow(GridPane grid, int row, String name, String value, String bonus,
                        Color textColor, Color bonusColor) {
        Label nameLabel = label(name, 17, textColor);
        Label valueLabel = label(value, 17, textColor);
        Label bonusLabel = label(bonus, 17, bonusColor);
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
        grid.add(bonusLabel, 2, row);
        GridPane.setHalignment(valueLabel, HPos.RIGHT);
        GridPane.setHalignment(bonusLabel, HPos.RIGHT);
    }

    private void addScoreRow(GridPane grid, int row, String name, String value, Color valueColor) {
        Label nameLabel = label(name, 20, LIGHT);
        Label valueLabel = label(value, 20, valueColor);
        grid.add(nameLabel, 0, row);
        grid.add(valueLabel, 1, row);
        GridPane.setHalignment(valueLabel, HPos.RIGHT);
    }

    private PixelButton button(String text, double width, Runnable action) {
        PixelButton b = new PixelButton(text, width);
        b.setOnAction(e -> action.run());
        return b;
    }

    private PixelButton toggle(String name, BooleanSupplier getter, Consumer<Boolean> setter) {
        PixelButton b = new PixelButton("", 380);
        Runnable refresh = () -> b.setText(name + ": " + (getter.getAsBoolean() ? "ON" : "OFF"));
        refresh.run();
        b.setOnAction(e -> {
            setter.accept(!getter.getAsBoolean());
            refresh.run();
        });
        return b;
    }

    private VBox box(double spacing, boolean framed) {
        VBox v = new VBox(spacing);
        v.setAlignment(Pos.CENTER);
        if (framed) {
            v.setStyle(PANEL_STYLE);
            v.setPadding(new Insets(26, 38, 28, 38));
        }
        v.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return v;
    }

    private Region gap(double height) {
        Region r = new Region();
        r.setMinHeight(height);
        r.setPrefHeight(height);
        return r;
    }

    private Label label(String text, double size, Color color) {
        Label l = new Label(text);
        l.setFont(Font.font("Consolas", FontWeight.BOLD, size));
        l.setTextFill(color);
        return l;
    }

    private Label body(String text, double size, Color color) {
        Label l = new Label(text);
        l.setFont(Font.font("Consolas", FontWeight.NORMAL, size));
        l.setTextFill(color);
        return l;
    }

    private Label title(String text, double size, Color color) {
        Label l = new Label(text);
        l.setFont(Font.font("Consolas", FontWeight.EXTRA_BOLD, size));
        l.setTextFill(color);
        l.setEffect(new DropShadow(0, 4, 4, Color.rgb(0, 0, 0, 0.85)));
        return l;
    }

    private static String score(int value) {
        return String.format("%05d", value);
    }

    private static String clock(double seconds) {
        int s = (int) seconds;
        return String.format("%02d:%02d", s / 60, s % 60);
    }
}
