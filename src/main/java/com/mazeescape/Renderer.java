package com.mazeescape;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javafx.geometry.VPos;
import javafx.scene.SnapshotParameters;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.ArcType;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;

/**
 * Draws everything onto the Canvas: the dungeon, items, traps, enemies, player,
 * lighting, HUD and the menu backdrop. The walls and floor rarely change, so
 * they are painted once into an image and reused every frame (and repainted
 * only when a secret wall opens).
 */
public class Renderer {

    public static final int WIDTH = 800;
    public static final int HEIGHT = 600;
    public static final int HUD_HEIGHT = 56;

    private static final int T = GameWorld.TILE;

    // ---- palette
    private static final Color BG = Color.web("#0b1220");
    private static final Color TEAL = Color.web("#3fc4b8");
    private static final Color GOLD = Color.web("#e8b93c");
    private static final Color GOLD_DARK = Color.web("#a67c1c");
    private static final Color GREEN = Color.web("#43e58f");
    private static final Color RED = Color.web("#e04545");
    private static final Color ORANGE = Color.web("#ff9a3c");
    private static final Color HEART_RED = Color.web("#d94452");

    private static final Color FLOOR_A = Color.web("#15212b");
    private static final Color FLOOR_B = Color.web("#17252f");
    private static final Color FLOOR_LINE = Color.web("#101a22");
    private static final Color FLOOR_SPECK = Color.web("#22343f");

    private static final Color[] WALL_COLORS = {
        Color.web("#2d3139"), Color.web("#2a2e35"), Color.web("#30343c")
    };
    private static final Color MORTAR = Color.web("#1c1f25");
    private static final Color WALL_LIGHT = Color.web("#4a515c");
    private static final Color WALL_SHADOW = Color.web("#14171b");
    private static final Color MOSS = Color.web("#3d6a3a");

    // ---- player sprite (12 x 12 "pixels", each drawn 2 x 2 screen pixels)
    private static final String[] BODY = {
        "...hhhhhh...",
        "..hhhhhhhh..",
        "..hssssssh..",
        "..sesssses..",
        "..ssssssss..",
        "..stttttts..",
        ".sdttttttds.",
        "..dggggggd..",
        "..dttttttd.."
    };
    private static final String[] LEGS_A = { "...tt..tt...", "...bb..bb...", "..bbb..bbb.." };
    private static final String[] LEGS_B = { "....tttt....", "....bbbb....", "...bbbbbb..." };

    private static final Color HAIR = Color.web("#6b3f1f");
    private static final Color SKIN = Color.web("#e8b98a");
    private static final Color EYE = Color.web("#161616");
    private static final Color TUNIC = Color.web("#2fb5a8");
    private static final Color TUNIC_DARK = Color.web("#1d7f78");
    private static final Color BELT = Color.web("#d9a93a");
    private static final Color BOOT = Color.web("#4a3320");

    // ---- enemy sprite (10 x 10 "pixels"); the last two rows wiggle between A and B
    private static final String[] GHOST = {
        "...wwww...",
        "..wwwwww..",
        ".wwwwwwww.",
        ".wddwwddw.",
        ".wddwwddw.",
        ".wwwwwwww.",
        ".wwwwwwww.",
        ".wwwwwwww."
    };
    private static final String[] GHOST_FEET_A = { ".ww.ww.ww.", ".w..w..w.." };
    private static final String[] GHOST_FEET_B = { ".w.ww.ww.w", "..w..w..w." };

    // ---- fonts
    private static final Font FONT_HUD = Font.font("Consolas", FontWeight.BOLD, 22);
    private static final Font FONT_HUD_SMALL = Font.font("Consolas", FontWeight.BOLD, 14);
    private static final Font FONT_BANNER = Font.font("Consolas", FontWeight.BOLD, 15);

    private static final double[] SPIKE_X = { 10, 22, 10, 22 };
    private static final double[] SPIKE_Y = { 11, 11, 23, 23 };

    private final GraphicsContext g;
    private final Image menuLayer;
    private Image mazeLayer;
    private int builtVersion;
    private final List<int[]> torches = new ArrayList<>();

    public Renderer(GraphicsContext g) {
        this.g = g;
        this.menuLayer = snapshot(WIDTH, HEIGHT, this::paintMenuBackdrop);
    }

    // =====================================================================
    //  Level preparation
    // =====================================================================

    /** Called once whenever a level starts: picks torch positions and paints the static layer. */
    public void prepare(GameWorld w) {
        torches.clear();
        for (int r = 0; r < w.level.rows - 1; r++) {
            for (int c = 0; c < w.level.cols; c++) {
                if (w.grid[r][c] == Level.WALL && !isWallChar(w.grid[r + 1][c]) && hash(c, r) % 10 == 0) {
                    torches.add(new int[] { c, r });
                }
            }
        }
        buildLayer(w);
    }

    private void buildLayer(GameWorld w) {
        builtVersion = w.layoutVersion;
        mazeLayer = snapshot(w.level.cols * T, w.level.rows * T, gc -> paintMazeLayer(gc, w));
    }

    private static Image snapshot(int w, int h, Consumer<GraphicsContext> painter) {
        Canvas canvas = new Canvas(w, h);
        painter.accept(canvas.getGraphicsContext2D());
        SnapshotParameters params = new SnapshotParameters();
        params.setFill(Color.TRANSPARENT);
        WritableImage image = new WritableImage(w, h);
        canvas.snapshot(params, image);
        return image;
    }

    private static int hash(int c, int r) {
        int h = c * 73856093 ^ r * 19349663;
        h ^= (h >>> 13);
        return h & 0x7fffffff;
    }

    // =====================================================================
    //  Static layer: floor, walls, cracks, torch brackets
    // =====================================================================

    private static boolean isWallChar(char ch) {
        return ch == Level.WALL || ch == Level.EXIT || ch == Level.SECRET;
    }

    private static boolean wallAt(char[][] grid, int c, int r) {
        if (r < 0 || c < 0 || r >= grid.length || c >= grid[0].length) {
            return true;
        }
        return isWallChar(grid[r][c]);
    }

    private void paintMazeLayer(GraphicsContext gc, GameWorld w) {
        char[][] grid = w.grid;
        for (int r = 0; r < w.level.rows; r++) {
            for (int c = 0; c < w.level.cols; c++) {
                char tile = grid[r][c];
                if (isWallChar(tile)) {
                    paintWall(gc, grid, c, r, tile == Level.SECRET);
                } else {
                    paintFloor(gc, grid, c, r);
                }
            }
        }
        for (int[] torch : torches) {
            if (grid[torch[1]][torch[0]] != Level.WALL) {
                continue;
            }
            double x = torch[0] * T;
            double y = torch[1] * T;
            gc.setFill(Color.web("#4a3a28"));
            gc.fillRect(x + 13, y + 12, 6, 14);
            gc.setFill(Color.web("#6a5538"));
            gc.fillRect(x + 10, y + 9, 12, 5);
        }
    }

    private static void paintBrick(GraphicsContext gc, double x, double y, int h) {
        gc.setFill(WALL_COLORS[h % WALL_COLORS.length]);
        gc.fillRect(x, y, T, T);
        gc.setFill(MORTAR);
        gc.fillRect(x, y + 15, T, 2);
        gc.fillRect(x + 15, y, 2, 15);
        gc.fillRect(x + 7, y + 17, 2, 15);
        gc.fillRect(x + 23, y + 17, 2, 15);
    }

    private static void paintWall(GraphicsContext gc, char[][] grid, int c, int r, boolean cracked) {
        double x = c * T;
        double y = r * T;
        int h = hash(c, r);
        paintBrick(gc, x, y, h);

        if (!wallAt(grid, c, r - 1)) {
            gc.setFill(WALL_LIGHT);
            gc.fillRect(x, y, T, 3);
        }
        if (!wallAt(grid, c, r + 1)) {
            gc.setFill(WALL_SHADOW);
            gc.fillRect(x, y + T - 4, T, 4);
        }
        if (!wallAt(grid, c - 1, r)) {
            gc.setFill(WALL_LIGHT);
            gc.fillRect(x, y, 2, T);
        }
        if (!wallAt(grid, c + 1, r)) {
            gc.setFill(WALL_SHADOW);
            gc.fillRect(x + T - 2, y, 2, T);
        }
        if (h % 6 == 0 && !wallAt(grid, c, r + 1)) {
            gc.setFill(MOSS);
            gc.fillRect(x + 4, y + T - 8, 5, 4);
            gc.fillRect(x + 10, y + T - 6, 4, 3);
            gc.fillRect(x + 20, y + T - 7, 6, 3);
        }
        if (cracked) {
            // A thin dark crack: the only clue that this wall hides a secret.
            gc.setStroke(Color.rgb(6, 8, 12, 0.85));
            gc.setLineWidth(1.5);
            gc.strokeLine(x + 9, y + 3, x + 14, y + 12);
            gc.strokeLine(x + 14, y + 12, x + 11, y + 19);
            gc.strokeLine(x + 11, y + 19, x + 17, y + 29);
            gc.strokeLine(x + 14, y + 12, x + 21, y + 15);
        }
    }

    private static void paintFloor(GraphicsContext gc, char[][] grid, int c, int r) {
        double x = c * T;
        double y = r * T;
        int h = hash(c, r);
        gc.setFill((h & 1) == 0 ? FLOOR_A : FLOOR_B);
        gc.fillRect(x, y, T, T);
        gc.setFill(FLOOR_LINE);
        gc.fillRect(x, y, T, 1);
        gc.fillRect(x, y, 1, T);
        if (h % 5 == 0) {
            int ox = (h >> 3) % 20 + 4;
            int oy = (h >> 7) % 20 + 4;
            gc.setFill(FLOOR_SPECK);
            gc.fillRect(x + ox, y + oy, 2, 2);
            gc.fillRect(x + ox + 5, y + oy + 3, 1, 1);
        }
        if (wallAt(grid, c, r - 1)) {
            gc.setFill(Color.rgb(0, 0, 0, 0.30));
            gc.fillRect(x, y, T, 7);
        }
        if (wallAt(grid, c - 1, r)) {
            gc.setFill(Color.rgb(0, 0, 0, 0.18));
            gc.fillRect(x, y, 4, T);
        }
    }

    // =====================================================================
    //  Game frame
    // =====================================================================

    public void drawGame(GameWorld w, double time) {
        if (w.layoutVersion != builtVersion) {
            buildLayer(w);   // a secret wall opened
        }

        g.setFill(BG);
        g.fillRect(0, 0, WIDTH, HEIGHT);

        g.save();
        g.translate(0, HUD_HEIGHT);
        if (w.shake > 0 && Settings.screenShake) {
            double amount = w.shake * 14;
            g.translate((Math.random() - 0.5) * amount, (Math.random() - 0.5) * amount);
        }

        g.drawImage(mazeLayer, 0, 0);
        drawTorches(time);
        drawTiles(w, time);
        drawEnemies(w, time);
        drawPlayer(w);
        drawParticles(w);
        drawLighting(w);
        drawFloatingTexts(w);
        drawBanner(w);
        g.restore();

        drawHud(w, time);
    }

    /** Darkens the whole screen with a colour (used behind pause / game over panels). */
    public void drawTint(Color color) {
        g.setFill(color);
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void glow(double cx, double cy, double radius, Color color, double alpha) {
        g.setFill(new RadialGradient(0, 0, cx, cy, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.color(color.getRed(), color.getGreen(), color.getBlue(), alpha)),
                new Stop(1, Color.color(color.getRed(), color.getGreen(), color.getBlue(), 0))));
        g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    // ---- torches

    private void drawTorches(double time) {
        for (int[] torch : torches) {
            double cx = torch[0] * T + 16;
            double cy = torch[1] * T + 9;
            double flicker = 0.85 + 0.15 * Math.sin(time * 9 + torch[0] * 1.7 + torch[1])
                    + 0.05 * Math.sin(time * 23 + torch[0]);
            glow(cx, cy + 6, 78 * flicker, ORANGE, Settings.lighting ? 0.22 : 0.12);
            g.setFill(Color.web("#ff7a1a"));
            g.fillOval(cx - 5, cy + 3 - 14 * flicker, 10, 14 * flicker);
            g.setFill(Color.web("#ffd24a"));
            g.fillOval(cx - 3, cy + 3 - 8 * flicker, 6, 8 * flicker);
        }
    }

    // ---- items, traps, hazards, exit

    private void drawTiles(GameWorld w, double time) {
        for (int r = 0; r < w.level.rows; r++) {
            for (int c = 0; c < w.level.cols; c++) {
                char tile = w.grid[r][c];
                switch (tile) {
                    case Level.WEB -> drawWeb(c, r);
                    case Level.HIDDEN -> drawHiddenHint(c, r);
                    case Level.KEY -> drawKey(c, r, time);
                    case Level.COIN -> drawCoin(c, r, time);
                    case Level.CHEST -> drawChest(c, r, w, time);
                    case Level.LIFE -> drawLifeItem(c, r, time);
                    case Level.TIME -> drawTimeCrystal(c, r, time);
                    case Level.TRAP -> drawStaticSpikes(c, r);
                    case Level.PULSE -> drawPulseSpikes(c, r, w.animTime);
                    case Level.FIRE -> drawFireVent(c, r, w.animTime, time);
                    case Level.EXIT -> drawExit(c, r, w, time);
                    default -> { }
                }
            }
        }
    }

    private void drawKey(int c, int r, double time) {
        double cx = c * T + T / 2.0 - 2;
        double cy = r * T + T / 2.0 + Math.sin(time * 3 + c + r) * 2;
        glow(cx + 2, cy, 24, GOLD, 0.35);
        g.setFill(GOLD);
        g.fillOval(cx - 10, cy - 5, 10, 10);
        g.setFill(Color.web("#3a2a05"));
        g.fillOval(cx - 7, cy - 2, 4, 4);
        g.setFill(GOLD);
        g.fillRect(cx - 1, cy - 1.5, 13, 3);
        g.fillRect(cx + 7, cy + 1.5, 3, 4);
        g.fillRect(cx + 11, cy + 1.5, 3, 3);
        g.setFill(Color.web("#fff0b0"));
        g.fillRect(cx - 8, cy - 4, 3, 2);
    }

    private void drawCoin(int c, int r, double time) {
        double cx = c * T + T / 2.0;
        double cy = r * T + T / 2.0 + Math.sin(time * 3 + c * 1.3 + r) * 1.5;
        double spin = Math.abs(Math.cos(time * 2.5 + c * 0.7 + r * 1.1));
        double width = 3 + 9 * spin;
        glow(cx, cy, 15, GOLD, 0.25);
        g.setFill(GOLD_DARK);
        g.fillOval(cx - width / 2 - 1, cy - 6, width + 2, 12);
        g.setFill(GOLD);
        g.fillOval(cx - width / 2, cy - 5, width, 10);
        if (spin > 0.5) {
            g.setFill(Color.web("#fff3b8"));
            g.fillOval(cx - width / 4, cy - 3, width / 3, 4);
        }
    }

    /** Hidden chests fade in as you get close. A tiny twinkle gives them away to sharp eyes. */
    private void drawChest(int c, int r, GameWorld w, double time) {
        double cx = c * T + T / 2.0;
        double cy = r * T + T / 2.0 + 2;

        if (((int) (time * 2 + c * 3 + r)) % 4 == 0) {
            g.setFill(Color.web("#fff3b8"));
            g.fillRect(cx - 4 + (c % 3) * 4, cy - 10, 2, 2);
        }

        double distance = Math.hypot(w.x - cx, w.y - cy);
        double alpha = Math.max(0, Math.min(1, 1 - (distance - 48) / 80));
        if (alpha <= 0) {
            return;
        }
        g.save();
        g.setGlobalAlpha(alpha);
        glow(cx, cy, 26, GOLD, 0.40);
        g.setFill(Color.web("#6b4423"));
        g.fillRect(cx - 10, cy - 4, 20, 12);
        g.setFill(Color.web("#8a5a2f"));
        g.fillRect(cx - 10, cy - 9, 20, 6);
        g.setFill(GOLD);
        g.fillRect(cx - 10, cy - 3, 20, 2);
        g.fillRect(cx - 2, cy - 6, 4, 8);
        g.setFill(Color.web("#3a2a05"));
        g.fillRect(cx - 1, cy - 3, 2, 3);
        g.restore();
    }

    private void drawLifeItem(int c, int r, double time) {
        double cx = c * T + T / 2.0;
        double cy = r * T + T / 2.0 + Math.sin(time * 3 + c + r) * 2;
        glow(cx, cy, 26, Color.web("#ff5a7a"), 0.35);
        drawHeart(cx, cy, 7, HEART_RED);
    }

    private void drawTimeCrystal(int c, int r, double time) {
        double cx = c * T + T / 2.0;
        double cy = r * T + T / 2.0 + Math.sin(time * 3 + c + r) * 2;
        glow(cx, cy, 26, Color.web("#5fe0f0"), 0.35);
        g.setFill(Color.web("#2aa6c0"));
        g.fillPolygon(new double[] { cx, cx + 7, cx, cx - 7 }, new double[] { cy - 10, cy, cy + 10, cy }, 4);
        g.setFill(Color.web("#8cf0ff"));
        g.fillPolygon(new double[] { cx, cx, cx - 7 }, new double[] { cy - 10, cy + 10, cy }, 3);
    }

    private void drawWeb(int c, int r) {
        double x = c * T;
        double y = r * T;
        g.setStroke(Color.rgb(215, 230, 235, 0.55));
        g.setLineWidth(1);
        g.strokeLine(x + 2, y + 2, x + 30, y + 30);
        g.strokeLine(x + 30, y + 2, x + 2, y + 30);
        g.strokeLine(x + 16, y + 2, x + 16, y + 30);
        g.strokeLine(x + 2, y + 16, x + 30, y + 16);
        g.strokeOval(x + 9, y + 9, 14, 14);
        g.strokeOval(x + 3, y + 3, 26, 26);
    }

    /** Hidden spikes look like floor with a few very faint cracks. */
    private void drawHiddenHint(int c, int r) {
        double x = c * T;
        double y = r * T;
        g.setStroke(Color.rgb(0, 0, 0, 0.20));
        g.setLineWidth(1);
        g.strokeLine(x + 6, y + 10, x + 14, y + 15);
        g.strokeLine(x + 14, y + 15, x + 11, y + 23);
        g.strokeLine(x + 14, y + 15, x + 25, y + 12);
    }

    private void drawSpikePlate(double x, double y) {
        g.setFill(Color.web("#120e12"));
        g.fillRect(x + 3, y + 3, T - 6, T - 6);
    }

    private void drawSpike(double cx, double cy, double height, Color body, Color light) {
        g.setFill(body);
        g.fillPolygon(new double[] { cx - 4.5, cx, cx + 4.5 },
                new double[] { cy + 4, cy + 4 - height, cy + 4 }, 3);
        g.setFill(light);
        g.fillPolygon(new double[] { cx - 4.5, cx, cx },
                new double[] { cy + 4, cy + 4 - height, cy + 4 }, 3);
    }

    private void drawStaticSpikes(int c, int r) {
        double x = c * T;
        double y = r * T;
        drawSpikePlate(x, y);
        for (int i = 0; i < 4; i++) {
            drawSpike(x + SPIKE_X[i], y + SPIKE_Y[i], 11, Color.web("#a63a3a"), Color.web("#d65a5a"));
        }
    }

    private void drawPulseSpikes(int c, int r, double animTime) {
        double x = c * T;
        double y = r * T;
        int state = GameWorld.pulseState(animTime, c, r);
        drawSpikePlate(x, y);
        for (int i = 0; i < 4; i++) {
            double sx = x + SPIKE_X[i];
            double sy = y + SPIKE_Y[i];
            if (state == 2) {
                drawSpike(sx, sy, 11, Color.web("#b8502a"), Color.web("#ee8a45"));
            } else if (state == 1) {
                boolean blink = ((int) (animTime * 14)) % 2 == 0;
                g.setFill(blink ? Color.web("#ff5a4a") : Color.web("#7a2a2a"));
                g.fillOval(sx - 3, sy - 1, 6, 3);
                drawSpike(sx, sy, 3, Color.web("#8a3a2a"), Color.web("#b85a40"));
            } else {
                g.setFill(Color.web("#050405"));
                g.fillOval(sx - 3, sy - 1, 6, 3);
            }
        }
        if (state == 2) {
            glow(x + T / 2.0, y + T / 2.0, 26, RED, 0.25);
        }
    }

    private void drawFireVent(int c, int r, double animTime, double time) {
        double x = c * T;
        double y = r * T;
        double cx = x + T / 2.0;
        double cy = y + T / 2.0;
        int state = GameWorld.fireState(animTime, c, r);

        g.setFill(Color.web("#120e12"));
        g.fillRect(x + 3, y + 3, T - 6, T - 6);
        g.setFill(state == 0 ? Color.web("#2a2024") : Color.web("#7a2a12"));
        for (int i = 0; i < 4; i++) {
            g.fillRect(x + 7 + i * 6, y + 8, 3, 16);
        }

        if (state == 1) {
            boolean blink = ((int) (animTime * 10)) % 2 == 0;
            glow(cx, cy, 30, ORANGE, blink ? 0.40 : 0.18);
        } else if (state == 2) {
            glow(cx, cy, 52, ORANGE, 0.45);
            for (int i = 0; i < 3; i++) {
                double fx = x + 8 + i * 8;
                double height = 14 + 12 * Math.abs(Math.sin(time * 11 + i * 2 + c));
                g.setFill(Color.web("#ff6a1a"));
                g.fillOval(fx - 4.5, y + 27 - height, 9, height);
                g.setFill(Color.web("#ffd24a"));
                g.fillOval(fx - 2.5, y + 27 - height * 0.6, 5, height * 0.6);
            }
        }
    }

    private void drawExit(int c, int r, GameWorld w, double time) {
        double x = c * T;
        double y = r * T;
        boolean open = w.exitUnlocked();

        g.setFill(Color.web("#14181d"));
        g.fillRect(x, y, T, T);
        g.setFill(open ? Color.web("#1f8a55") : Color.web("#7a2e2e"));
        g.fillRoundRect(x + 3, y + 3, T - 6, T - 3, 12, 12);
        g.setFill(open ? Color.web("#38d985") : Color.web("#3b1517"));
        g.fillRoundRect(x + 7, y + 7, T - 14, T - 7, 8, 8);

        if (open) {
            double pulse = 0.5 + 0.5 * Math.sin(time * 4);
            glow(x + T / 2.0, y + T / 2.0, 48, GREEN, 0.28 + 0.18 * pulse);
            g.setFill(Color.web("#b8ffd8"));
            g.fillRect(x + 12, y + 10, 3, 12);
        } else {
            g.setFill(GOLD);
            g.fillRect(x + 11, y + 15, 10, 8);
            g.setStroke(GOLD);
            g.setLineWidth(2);
            g.strokeArc(x + 12, y + 8, 8, 10, 0, 180, ArcType.OPEN);
            g.setFill(Color.web("#3a2a05"));
            g.fillRect(x + 15, y + 18, 2, 3);
        }
    }

    // ---- enemies

    private void drawEnemies(GameWorld w, double time) {
        for (GameWorld.Enemy e : w.enemies) {
            double bob = Math.sin(time * 4 + e.phase) * 2;
            double left = e.x - 10;
            double top = e.y - 11 + bob;

            g.setFill(Color.rgb(0, 0, 0, 0.3));
            g.fillOval(e.x - 8, e.y + 8, 16, 5);

            Color body;
            Color eyes;
            if (e.chaser) {
                body = Color.web("#7a3f8f");
                eyes = e.awake ? Color.web("#ff4040") : Color.web("#b02a2a");
                if (e.awake) {
                    glow(e.x, e.y, 36, RED, 0.30);
                }
            } else {
                body = Color.web("#a8e6df", 0.9);
                eyes = Color.web("#1a2b30");
                glow(e.x, e.y, 28, TEAL, 0.20);
            }

            String[] feet = ((int) (time * 4 + e.phase)) % 2 == 0 ? GHOST_FEET_A : GHOST_FEET_B;
            for (int row = 0; row < 10; row++) {
                String line = row < GHOST.length ? GHOST[row] : feet[row - GHOST.length];
                for (int col = 0; col < 10; col++) {
                    char ch = line.charAt(col);
                    if (ch == 'w') {
                        g.setFill(body);
                    } else if (ch == 'd') {
                        g.setFill(eyes);
                    } else {
                        continue;
                    }
                    g.fillRect(left + col * 2, top + row * 2, 2, 2);
                }
            }
        }
    }

    // ---- player

    private void drawPlayer(GameWorld w) {
        if (w.invulnerable > 0 && ((int) (w.invulnerable * 12)) % 2 == 0) {
            return;   // blink while protected after a hit
        }
        g.setFill(Color.rgb(0, 0, 0, 0.35));
        g.fillOval(w.x - 9, w.y + 6, 18, 7);

        int frame = (w.moving && ((int) (w.walkClock * (w.slowed ? 4 : 8))) % 2 == 1) ? 1 : 0;
        double bob = frame == 1 ? -1 : 0;
        String[] legs = frame == 0 ? LEGS_A : LEGS_B;

        double left = w.x - 12;
        double top = w.y - 14 + bob;
        for (int row = 0; row < 12; row++) {
            String line = row < BODY.length ? BODY[row] : legs[row - BODY.length];
            for (int col = 0; col < 12; col++) {
                char ch = line.charAt(w.facingLeft ? 11 - col : col);
                Color color = spriteColor(ch);
                if (color != null) {
                    g.setFill(color);
                    g.fillRect(left + col * 2, top + row * 2, 2, 2);
                }
            }
        }
    }

    private static Color spriteColor(char ch) {
        switch (ch) {
            case 'h': return HAIR;
            case 's': return SKIN;
            case 'e': return EYE;
            case 't': return TUNIC;
            case 'd': return TUNIC_DARK;
            case 'g': return BELT;
            case 'b': return BOOT;
            default: return null;
        }
    }

    // ---- effects

    private void drawParticles(GameWorld w) {
        for (GameWorld.Particle p : w.particles) {
            double alpha = Math.max(0, p.life / p.maxLife);
            g.setFill(Color.color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), alpha));
            g.fillRect(p.x - p.size / 2, p.y - p.size / 2, p.size, p.size);
        }
    }

    private void drawLighting(GameWorld w) {
        glow(w.x, w.y, 80, TEAL, Settings.lighting ? 0.16 : 0.08);
        if (!Settings.lighting) {
            return;
        }
        double dark = w.level.darkness;
        double radius = 430 - 190 * dark;
        double edge = 0.50 + 0.28 * dark;
        double middle = 0.10 + 0.10 * dark;
        g.setFill(new RadialGradient(0, 0, w.x, w.y, radius, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.rgb(3, 6, 12, 0.0)),
                new Stop(0.45, Color.rgb(3, 6, 12, middle)),
                new Stop(1.0, Color.rgb(3, 6, 12, edge))));
        g.fillRect(0, 0, w.level.cols * T, w.level.rows * T);
    }

    private void drawFloatingTexts(GameWorld w) {
        g.setFont(FONT_HUD_SMALL);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        for (GameWorld.FloatingText t : w.texts) {
            double alpha = Math.min(1.0, t.life / 0.5);
            g.setFill(Color.rgb(0, 0, 0, alpha * 0.8));
            g.fillText(t.text, t.x + 1, t.y + 1);
            g.setFill(Color.color(t.color.getRed(), t.color.getGreen(), t.color.getBlue(), alpha));
            g.fillText(t.text, t.x, t.y);
        }
    }

    /** The level's hint, shown at the bottom for the first few seconds. */
    private void drawBanner(GameWorld w) {
        String hint = w.level.hint;
        double t = w.animTime;
        double lifetime = 5.0;
        if (hint == null || hint.isEmpty() || t >= lifetime) {
            return;
        }
        double alpha = Math.max(0, Math.min(1.0, Math.min(t / 0.4, (lifetime - t) / 0.8)));
        double width = hint.length() * 9.0 + 40;
        double cx = w.level.cols * T / 2.0;
        double cy = w.level.rows * T - 28;

        g.setFill(Color.rgb(5, 10, 18, 0.82 * alpha));
        g.fillRoundRect(cx - width / 2, cy - 16, width, 32, 8, 8);
        g.setStroke(Color.color(0.25, 0.77, 0.72, 0.9 * alpha));
        g.setLineWidth(2);
        g.strokeRoundRect(cx - width / 2, cy - 16, width, 32, 8, 8);
        g.setFont(FONT_BANNER);
        g.setTextAlign(TextAlignment.CENTER);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(Color.color(0.9, 0.95, 0.96, alpha));
        g.fillText(hint, cx, cy);
    }

    // =====================================================================
    //  HUD
    // =====================================================================

    private void drawHud(GameWorld w, double time) {
        g.setFill(new LinearGradient(0, 0, 0, HUD_HEIGHT, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#13202f")), new Stop(1, Color.web("#0c1522"))));
        g.fillRect(0, 0, WIDTH, HUD_HEIGHT);
        g.setFill(Color.web("#2a6f7a"));
        g.fillRect(0, HUD_HEIGHT - 3, WIDTH, 3);

        double cy = 27;

        // lives
        for (int i = 0; i < GameWorld.MAX_LIVES; i++) {
            drawHeart(24 + i * 28, cy, 9, i < w.lives ? HEART_RED : Color.web("#2a3342"));
        }

        // level name
        g.setFont(FONT_HUD_SMALL);
        g.setTextAlign(TextAlignment.LEFT);
        g.setTextBaseline(VPos.CENTER);
        g.setFill(TEAL);
        g.fillText("LEVEL " + w.level.number + " - " + w.level.name, 112, cy);

        // timer
        int seconds = (int) Math.ceil(w.timeLeft);
        Color timeColor = Color.web("#e6f2f4");
        if (seconds <= 10) {
            timeColor = ((int) (time * 3)) % 2 == 0 ? RED : Color.web("#ff9a9a");
        } else if (seconds <= 30) {
            timeColor = GOLD;
        }
        drawClockIcon(372, cy, timeColor);
        g.setFont(FONT_HUD);
        g.setFill(timeColor);
        g.fillText(String.format("%02d:%02d", seconds / 60, seconds % 60), 392, cy);

        // keys
        Color keyColor = w.exitUnlocked() ? GREEN : GOLD;
        drawKeyIcon(536, cy, keyColor);
        g.setFill(keyColor);
        g.fillText(w.keys + "/" + w.level.keysRequired, 562, cy);

        // score
        drawStarIcon(656, cy, GOLD);
        g.setFill(Color.web("#e6f2f4"));
        g.fillText(String.format("%05d", w.score), 676, cy);
    }

    private void drawHeart(double cx, double cy, double s, Color color) {
        g.setFill(color);
        g.beginPath();
        g.moveTo(cx, cy + s * 0.9);
        g.bezierCurveTo(cx - s * 1.5, cy - s * 0.2, cx - s * 0.7, cy - s * 1.1, cx, cy - s * 0.35);
        g.bezierCurveTo(cx + s * 0.7, cy - s * 1.1, cx + s * 1.5, cy - s * 0.2, cx, cy + s * 0.9);
        g.closePath();
        g.fill();
    }

    private void drawClockIcon(double cx, double cy, Color color) {
        g.setStroke(color);
        g.setLineWidth(2);
        g.strokeOval(cx - 9, cy - 8, 18, 18);
        g.strokeLine(cx, cy + 1, cx, cy - 4);
        g.strokeLine(cx, cy + 1, cx + 4, cy + 3);
        g.setFill(color);
        g.fillRect(cx - 2, cy - 12, 4, 3);
    }

    private void drawKeyIcon(double cx, double cy, Color color) {
        g.setFill(color);
        g.fillOval(cx - 11, cy - 5, 10, 10);
        g.setFill(Color.web("#0c1522"));
        g.fillOval(cx - 8, cy - 2, 4, 4);
        g.setFill(color);
        g.fillRect(cx - 2, cy - 1.5, 13, 3);
        g.fillRect(cx + 6, cy + 1.5, 3, 4);
        g.fillRect(cx + 10, cy + 1.5, 3, 3);
    }

    private void drawStarIcon(double cx, double cy, Color color) {
        double[] xs = new double[10];
        double[] ys = new double[10];
        for (int i = 0; i < 10; i++) {
            double angle = -Math.PI / 2 + i * Math.PI / 5;
            double radius = i % 2 == 0 ? 10 : 4.5;
            xs[i] = cx + Math.cos(angle) * radius;
            ys[i] = cy + Math.sin(angle) * radius;
        }
        g.setFill(color);
        g.fillPolygon(xs, ys, 10);
    }

    // =====================================================================
    //  Menu background
    // =====================================================================

    public void drawMenu(double time) {
        g.drawImage(menuLayer, 0, 0);
        glow(WIDTH / 2.0, 330, 330, Color.web("#2fc4b8"), 0.10);
        drawBigTorch(120, 300, time);
        drawBigTorch(WIDTH - 120, 300, time);

        g.setFill(Color.rgb(160, 230, 220, 0.35));
        for (int i = 0; i < 40; i++) {
            double px = (i * 97.0) % WIDTH + Math.sin(time * 0.5 + i) * 18;
            double py = HEIGHT - ((time * 12 + i * 53.0) % HEIGHT);
            g.fillRect(px, py, 2, 2);
        }

        g.setFill(new RadialGradient(0, 0, WIDTH / 2.0, HEIGHT / 2.0, 520, false, CycleMethod.NO_CYCLE,
                new Stop(0.5, Color.rgb(0, 0, 0, 0.0)),
                new Stop(1.0, Color.rgb(0, 0, 0, 0.55))));
        g.fillRect(0, 0, WIDTH, HEIGHT);
    }

    private void drawBigTorch(double cx, double cy, double time) {
        double f = 0.85 + 0.15 * Math.sin(time * 8 + cx) + 0.05 * Math.sin(time * 21 + cx * 0.3);
        glow(cx, cy - 10, 170 * f, ORANGE, 0.30);
        g.setFill(Color.web("#3a2a1a"));
        g.fillRect(cx - 5, cy + 6, 10, 46);
        g.setFill(Color.web("#5a4630"));
        g.fillRect(cx - 12, cy - 2, 24, 10);
        g.setFill(Color.web("#ff7a1a"));
        g.fillOval(cx - 11, cy + 2 - 38 * f, 22, 38 * f);
        g.setFill(Color.web("#ffd24a"));
        g.fillOval(cx - 6, cy + 2 - 22 * f, 12, 22 * f);
    }

    private void paintMenuBackdrop(GraphicsContext gc) {
        gc.setFill(new LinearGradient(0, 0, 0, HEIGHT, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#070b13")), new Stop(1, Color.web("#0f1a22"))));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        for (int r = 0; r * T < HEIGHT; r++) {
            for (int c = 0; c * T < WIDTH; c++) {
                paintBrick(gc, c * T, r * T, hash(c, r));
            }
        }
        gc.setFill(Color.rgb(3, 6, 12, 0.55));
        gc.fillRect(0, 0, WIDTH, HEIGHT);

        // the arched doorway the menu sits inside
        double ax = 215;
        double aw = 370;
        double ay = 40;
        double ah = 900;
        gc.setFill(new LinearGradient(0, ay, 0, HEIGHT, false, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0a1219")), new Stop(1, Color.web("#10303a"))));
        gc.fillRoundRect(ax, ay, aw, ah, aw, aw);

        gc.setStroke(Color.web("#3b414b"));
        gc.setLineWidth(12);
        gc.strokeRoundRect(ax, ay, aw, ah, aw, aw);
        gc.setStroke(Color.web("#59606c"));
        gc.setLineWidth(2);
        gc.strokeRoundRect(ax - 6, ay - 6, aw + 12, ah, aw + 12, aw + 12);
        gc.setStroke(Color.rgb(63, 196, 184, 0.35));
        gc.strokeRoundRect(ax + 6, ay + 6, aw - 12, ah, aw - 12, aw - 12);

        gc.setFill(MOSS);
        for (int i = 0; i < 50; i++) {
            int h = hash(i, 99);
            double x = (h % 2 == 0) ? ax - 6 + (h >> 4) % 14 : ax + aw - 8 + (h >> 4) % 14;
            double y = ay + 150 + (h >> 8) % 420;
            gc.fillRect(x, y, 3 + h % 3, 5 + (h >> 12) % 7);
        }
    }
}
