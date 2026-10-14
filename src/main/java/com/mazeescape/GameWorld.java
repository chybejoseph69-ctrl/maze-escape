package com.mazeescape;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javafx.scene.paint.Color;

/**
 * All the game rules for ONE level: player movement, collisions, items, traps,
 * enemies, lives, score and the countdown. It does no drawing; Renderer reads from it.
 */
public class GameWorld {

    public enum Result { RUNNING, WON, OUT_OF_LIVES, OUT_OF_TIME }

    public static final int TILE = 32;
    public static final int MAX_LIVES = 3;

    public static final int KEY_POINTS = 500;
    public static final int COIN_POINTS = 100;
    public static final int CHEST_POINTS = 400;
    public static final int SECRET_POINTS = 150;
    public static final int TRAP_PENALTY = 50;
    public static final int SECOND_POINTS = 10;
    public static final int CRYSTAL_SECONDS = 15;

    private static final double HALF = 10;               // half the width of the player's hitbox
    private static final double SPEED = 150;             // pixels per second
    private static final double WEB_SLOW = 0.45;         // speed multiplier while standing in a web
    private static final double ASSIST = 9;              // corner help: how close to a gap counts as "lined up"
    private static final double INVULNERABLE_TIME = 1.5; // seconds of protection after a hit
    private static final double PULSE_CYCLE = 2.4;       // seconds for one in/out cycle of pop-up spikes
    private static final double FIRE_CYCLE = 3.0;        // seconds for one cycle of a fire vent
    private static final double ENEMY_HIT = 17;          // how close an enemy must be to hurt you
    private static final double ENEMY_HALF = 8;          // half the size of an enemy's collision box
    private static final double CHASE_RANGE = 170;       // stalkers wake up inside this distance

    public final Level level;
    public final char[][] grid;
    public int layoutVersion;   // goes up when a wall changes so the renderer redraws it

    // player
    public double x;
    public double y;
    public boolean facingLeft;
    public boolean moving;
    public boolean slowed;
    public double walkClock;

    // stats
    public int lives;
    public int score;
    public int keys;
    public int coins;
    public int chests;
    public int secretsFound;
    public int trapPenalty;
    public int timeBonus;
    public double timeLeft;

    // timers for effects
    public double invulnerable;
    public double shake;
    public double animTime;
    private double lockedMessageCooldown;

    public final List<Particle> particles = new ArrayList<>();
    public final List<FloatingText> texts = new ArrayList<>();
    public final List<Enemy> enemies = new ArrayList<>();
    private final Random random = new Random();

    /** A tiny square of colour that flies outwards and fades. */
    public static class Particle {
        public double x, y, vx, vy, life, maxLife, size;
        public Color color;
    }

    /** Text that floats upwards and fades, e.g. "+100". */
    public static class FloatingText {
        public double x, y, life;
        public String text;
        public Color color;
    }

    /** A wraith (patrols a straight corridor) or a stalker (chases the player). */
    public static class Enemy {
        public double x, y;
        public double dx, dy;       // patrol direction, each -1, 0 or 1
        public double speed;
        public double phase;        // makes each enemy bob out of step
        public boolean chaser;
        public boolean awake;       // true while a stalker is chasing
    }

    public GameWorld(Level level, int startLives, int startScore) {
        this.level = level;
        this.grid = level.copyGrid();
        this.lives = startLives;
        this.score = startScore;
        this.timeLeft = level.timeLimit;
        this.x = level.startCol * TILE + TILE / 2.0;
        this.y = level.startRow * TILE + TILE / 2.0;

        for (int[] spawn : level.enemies) {
            Enemy e = new Enemy();
            e.x = spawn[0] * TILE + TILE / 2.0;
            e.y = spawn[1] * TILE + TILE / 2.0;
            e.phase = spawn[0] * 0.9 + spawn[1] * 1.7;
            char type = (char) spawn[2];
            if (type == Level.STALKER) {
                e.chaser = true;
                e.speed = 58 + 6 * Math.max(0, level.number - 8);
            } else {
                e.speed = 48 + 7 * Math.max(0, level.number - 5);
                if (type == Level.PATROL_H) {
                    e.dx = 1;
                } else {
                    e.dy = 1;
                }
            }
            enemies.add(e);
        }
    }

    public boolean exitUnlocked() {
        return keys >= level.keysRequired;
    }

    /**
     * Pop-up spikes: 0 = hidden, 1 = warning (about to pop up), 2 = up and dangerous.
     * The renderer uses the same method so what you see always matches what hurts.
     */
    public static int pulseState(double time, int col, int row) {
        double phase = (time + col * 0.37 + row * 0.61) % PULSE_CYCLE;
        if (phase < 1.5) {
            return 0;
        }
        return phase < 1.9 ? 1 : 2;
    }

    /** Fire vents: 0 = quiet, 1 = warning (smoke and glow), 2 = burning. */
    public static int fireState(double time, int col, int row) {
        double phase = (time + col * 0.53 + row * 0.29) % FIRE_CYCLE;
        if (phase < 1.6) {
            return 0;
        }
        return phase < 2.1 ? 1 : 2;
    }

    /** Advances the game by dt seconds. Called once per frame while playing. */
    public Result update(double dt, boolean left, boolean right, boolean up, boolean down) {
        animTime += dt;
        timeLeft -= dt;
        invulnerable = Math.max(0, invulnerable - dt);
        shake = Math.max(0, shake - dt);
        lockedMessageCooldown = Math.max(0, lockedMessageCooldown - dt);

        slowed = tileAtPixel(x, y) == Level.WEB;

        double dx = (right ? 1 : 0) - (left ? 1 : 0);
        double dy = (down ? 1 : 0) - (up ? 1 : 0);
        moving = dx != 0 || dy != 0;

        if (moving) {
            double length = Math.sqrt(dx * dx + dy * dy);
            double step = SPEED * (slowed ? WEB_SLOW : 1.0) * dt;
            dx = dx / length * step;
            dy = dy / length * step;
            if (dx < 0) {
                facingLeft = true;
            } else if (dx > 0) {
                facingLeft = false;
            }
            walkClock += dt;
            moveX(dx, dy == 0);
            moveY(dy, dx == 0);
        } else {
            walkClock = 0;
        }

        updateEnemies(dt);
        updateEffects(dt);
        checkTouches(left, right, up, down);

        Result result = checkTile();
        if (result != Result.RUNNING) {
            return result;
        }
        result = checkEnemies();
        if (result != Result.RUNNING) {
            return result;
        }
        if (timeLeft <= 0) {
            timeLeft = 0;
            return Result.OUT_OF_TIME;
        }
        return Result.RUNNING;
    }

    // ------------------------------------------------------------------ player movement

    private void moveX(double dx, boolean allowAssist) {
        if (dx == 0) {
            return;
        }
        double newX = x + dx;
        if (!collides(newX, y)) {
            x = newX;
            return;
        }
        // Blocked: slide right up against the wall instead of stopping short.
        if (dx > 0) {
            int col = (int) ((newX + HALF) / TILE);
            x = col * TILE - HALF - 0.001;
        } else {
            int col = (int) ((newX - HALF) / TILE);
            x = (col + 1) * TILE + HALF + 0.001;
        }
        if (allowAssist) {
            double centre = (Math.floor(y / TILE) + 0.5) * TILE;
            double diff = centre - y;
            if (Math.abs(diff) < ASSIST) {
                double nudge = Math.signum(diff) * Math.min(Math.abs(diff), Math.abs(dx));
                if (!collides(x, y + nudge)) {
                    y += nudge;
                }
            }
        }
    }

    private void moveY(double dy, boolean allowAssist) {
        if (dy == 0) {
            return;
        }
        double newY = y + dy;
        if (!collides(x, newY)) {
            y = newY;
            return;
        }
        if (dy > 0) {
            int row = (int) ((newY + HALF) / TILE);
            y = row * TILE - HALF - 0.001;
        } else {
            int row = (int) ((newY - HALF) / TILE);
            y = (row + 1) * TILE + HALF + 0.001;
        }
        if (allowAssist) {
            double centre = (Math.floor(x / TILE) + 0.5) * TILE;
            double diff = centre - x;
            if (Math.abs(diff) < ASSIST) {
                double nudge = Math.signum(diff) * Math.min(Math.abs(diff), Math.abs(dy));
                if (!collides(x + nudge, y)) {
                    x += nudge;
                }
            }
        }
    }

    /** True if a player-sized box centred here would overlap a solid tile. */
    private boolean collides(double cx, double cy) {
        return solidAt(cx - HALF, cy - HALF) || solidAt(cx + HALF, cy - HALF)
                || solidAt(cx - HALF, cy + HALF) || solidAt(cx + HALF, cy + HALF);
    }

    private boolean solidAt(double worldX, double worldY) {
        char tile = tileAtPixel(worldX, worldY);
        return tile == Level.WALL || tile == Level.SECRET
                || (tile == Level.EXIT && !exitUnlocked());
    }

    private char tileAtPixel(double worldX, double worldY) {
        int col = (int) Math.floor(worldX / TILE);
        int row = (int) Math.floor(worldY / TILE);
        if (col < 0 || row < 0 || col >= level.cols || row >= level.rows) {
            return Level.WALL;
        }
        return grid[row][col];
    }

    // ------------------------------------------------------------------ enemies

    private boolean solidForEnemy(double worldX, double worldY) {
        char tile = tileAtPixel(worldX, worldY);
        return tile == Level.WALL || tile == Level.SECRET || tile == Level.EXIT;
    }

    private boolean enemyCollides(double cx, double cy) {
        return solidForEnemy(cx - ENEMY_HALF, cy - ENEMY_HALF) || solidForEnemy(cx + ENEMY_HALF, cy - ENEMY_HALF)
                || solidForEnemy(cx - ENEMY_HALF, cy + ENEMY_HALF) || solidForEnemy(cx + ENEMY_HALF, cy + ENEMY_HALF);
    }

    private void updateEnemies(double dt) {
        for (Enemy e : enemies) {
            if (e.chaser) {
                updateStalker(e, dt);
            } else {
                updatePatrol(e, dt);
            }
        }
    }

    /** Wraiths walk in a straight line and turn around when they reach a wall. */
    private void updatePatrol(Enemy e, double dt) {
        double nextX = e.x + e.dx * e.speed * dt;
        double nextY = e.y + e.dy * e.speed * dt;
        if (solidForEnemy(nextX + e.dx * ENEMY_HALF, nextY + e.dy * ENEMY_HALF)) {
            e.dx = -e.dx;
            e.dy = -e.dy;
        } else {
            e.x = nextX;
            e.y = nextY;
        }
    }

    /** Stalkers sleep until you get close, then head straight for you (and get stuck behind walls). */
    private void updateStalker(Enemy e, double dt) {
        double toX = x - e.x;
        double toY = y - e.y;
        e.awake = Math.hypot(toX, toY) < CHASE_RANGE;
        if (!e.awake) {
            return;
        }
        double step = e.speed * dt;
        double stepX = Math.signum(toX) * step;
        double stepY = Math.signum(toY) * step;
        if (Math.abs(toX) > Math.abs(toY)) {
            if (!tryMoveEnemy(e, stepX, 0)) {
                tryMoveEnemy(e, 0, stepY);
            }
        } else {
            if (!tryMoveEnemy(e, 0, stepY)) {
                tryMoveEnemy(e, stepX, 0);
            }
        }
    }

    private boolean tryMoveEnemy(Enemy e, double dx, double dy) {
        if (dx == 0 && dy == 0) {
            return false;
        }
        if (enemyCollides(e.x + dx, e.y + dy)) {
            return false;
        }
        e.x += dx;
        e.y += dy;
        return true;
    }

    private Result checkEnemies() {
        if (invulnerable > 0) {
            return Result.RUNNING;
        }
        for (Enemy e : enemies) {
            if (Math.hypot(e.x - x, e.y - y) < ENEMY_HIT) {
                return hurtPlayer("CAUGHT! -1 LIFE");
            }
        }
        return Result.RUNNING;
    }

    // ------------------------------------------------------------------ tiles and items

    /** Pushing into a locked exit shows a message; pushing into a cracked wall opens it. */
    private void checkTouches(boolean left, boolean right, boolean up, boolean down) {
        if (!moving) {
            return;
        }
        double reach = HALF + 3;
        if (left) {
            touch(x - reach, y);
        }
        if (right) {
            touch(x + reach, y);
        }
        if (up) {
            touch(x, y - reach);
        }
        if (down) {
            touch(x, y + reach);
        }
    }

    private void touch(double worldX, double worldY) {
        char tile = tileAtPixel(worldX, worldY);
        int col = (int) Math.floor(worldX / TILE);
        int row = (int) Math.floor(worldY / TILE);
        if (tile == Level.SECRET) {
            grid[row][col] = Level.FLOOR;
            layoutVersion++;
            secretsFound++;
            score += SECRET_POINTS;
            double cx = col * TILE + TILE / 2.0;
            double cy = row * TILE + TILE / 2.0;
            burst(cx, cy, Color.web("#9aa3ad"), 22);
            shake = Math.max(shake, 0.25);
            addText("SECRET FOUND! +" + SECRET_POINTS, cx, cy - 16, Color.web("#ffe27a"));
        } else if (tile == Level.EXIT && !exitUnlocked() && lockedMessageCooldown <= 0) {
            lockedMessageCooldown = 1.2;
            addText("LOCKED! KEYS " + keys + "/" + level.keysRequired, x, y - 18, Color.web("#ff7070"));
        }
    }

    private Result checkTile() {
        int col = (int) (x / TILE);
        int row = (int) (y / TILE);
        char tile = grid[row][col];
        double centreX = col * TILE + TILE / 2.0;
        double centreY = row * TILE + TILE / 2.0;

        if (tile == Level.KEY) {
            grid[row][col] = Level.FLOOR;
            keys++;
            score += KEY_POINTS;
            burst(centreX, centreY, Color.web("#ffd34d"), 16);
            addText("+" + KEY_POINTS + " KEY", centreX, centreY - 14, Color.web("#ffd34d"));
            if (exitUnlocked()) {
                addText("EXIT UNLOCKED!", centreX, centreY - 32, Color.web("#4dffa0"));
            }
        } else if (tile == Level.COIN) {
            grid[row][col] = Level.FLOOR;
            coins++;
            score += COIN_POINTS;
            burst(centreX, centreY, Color.web("#ffe27a"), 10);
            addText("+" + COIN_POINTS, centreX, centreY - 14, Color.web("#ffe27a"));
        } else if (tile == Level.CHEST) {
            grid[row][col] = Level.FLOOR;
            chests++;
            score += CHEST_POINTS;
            burst(centreX, centreY, Color.web("#ffd34d"), 28);
            addText("TREASURE! +" + CHEST_POINTS, centreX, centreY - 16, Color.web("#ffd34d"));
        } else if (tile == Level.LIFE) {
            grid[row][col] = Level.FLOOR;
            burst(centreX, centreY, Color.web("#ff6a85"), 14);
            if (lives < MAX_LIVES) {
                lives++;
                addText("+1 LIFE", centreX, centreY - 14, Color.web("#ff6a85"));
            } else {
                score += COIN_POINTS;
                addText("+" + COIN_POINTS, centreX, centreY - 14, Color.web("#ff6a85"));
            }
        } else if (tile == Level.TIME) {
            grid[row][col] = Level.FLOOR;
            timeLeft += CRYSTAL_SECONDS;
            burst(centreX, centreY, Color.web("#5fe0f0"), 14);
            addText("+" + CRYSTAL_SECONDS + " SEC", centreX, centreY - 14, Color.web("#5fe0f0"));
        } else if (tile == Level.EXIT) {
            // The exit is only walkable once it is unlocked, so reaching it means we won.
            timeBonus = (int) timeLeft * SECOND_POINTS;
            score += timeBonus;
            return Result.WON;
        } else if (invulnerable <= 0 && tile == Level.HIDDEN) {
            grid[row][col] = Level.TRAP;   // now the spikes are visible for good
            return hurtPlayer("HIDDEN SPIKES! -1 LIFE");
        } else if (invulnerable <= 0 && isDangerous(tile, col, row)) {
            return hurtPlayer("OUCH! -1 LIFE");
        }
        return Result.RUNNING;
    }

    private boolean isDangerous(char tile, int col, int row) {
        if (tile == Level.TRAP) {
            return true;
        }
        if (tile == Level.PULSE) {
            return pulseState(animTime, col, row) == 2;
        }
        return tile == Level.FIRE && fireState(animTime, col, row) == 2;
    }

    private Result hurtPlayer(String message) {
        lives--;
        int lost = Math.min(score, TRAP_PENALTY);
        score -= lost;
        trapPenalty += lost;
        invulnerable = INVULNERABLE_TIME;
        shake = 0.35;
        burst(x, y, Color.web("#e04545"), 22);
        addText(message, x, y - 20, Color.web("#ff5a5a"));
        return lives <= 0 ? Result.OUT_OF_LIVES : Result.RUNNING;
    }

    // ------------------------------------------------------------------ effects

    private void burst(double px, double py, Color color, int count) {
        for (int i = 0; i < count; i++) {
            Particle p = new Particle();
            double angle = random.nextDouble() * Math.PI * 2;
            double speed = 40 + random.nextDouble() * 110;
            p.x = px;
            p.y = py;
            p.vx = Math.cos(angle) * speed;
            p.vy = Math.sin(angle) * speed;
            p.maxLife = 0.35 + random.nextDouble() * 0.4;
            p.life = p.maxLife;
            p.size = 2 + random.nextInt(3);
            p.color = color;
            particles.add(p);
        }
    }

    private void addText(String text, double px, double py, Color color) {
        FloatingText t = new FloatingText();
        t.text = text;
        t.x = px;
        t.y = py;
        t.life = 1.2;
        t.color = color;
        texts.add(t);
    }

    private void updateEffects(double dt) {
        for (Particle p : particles) {
            p.life -= dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            p.vx *= 0.96;
            p.vy *= 0.96;
        }
        particles.removeIf(p -> p.life <= 0);

        for (FloatingText t : texts) {
            t.life -= dt;
            t.y -= 26 * dt;
        }
        texts.removeIf(t -> t.life <= 0);
    }
}
