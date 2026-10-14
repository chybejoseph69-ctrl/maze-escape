package com.mazeescape;

import java.util.ArrayList;
import java.util.List;

/**
 * One level: its number, name, time limit, darkness, hint text and tile layout.
 * The legend for the layout characters is in Levels.java.
 */
public final class Level {

    public static final char WALL = '#';
    public static final char FLOOR = '.';
    public static final char START = 'P';
    public static final char KEY = 'K';
    public static final char COIN = 'C';
    public static final char EXIT = 'E';
    public static final char TRAP = 'T';      // spikes that are always up
    public static final char PULSE = 'S';     // spikes that pop in and out
    public static final char HIDDEN = 'H';    // hidden spikes, look like floor until stepped on
    public static final char FIRE = 'F';      // fire vent
    public static final char WEB = 'N';       // spider web, slows you down
    public static final char SECRET = 'Q';    // cracked wall, opens when you push into it
    public static final char CHEST = 'R';     // hidden treasure chest
    public static final char LIFE = 'L';      // heart, restores a life
    public static final char TIME = 'U';      // time crystal, adds seconds
    public static final char PATROL_H = 'X';  // wraith that patrols left and right
    public static final char PATROL_V = 'Y';  // wraith that patrols up and down
    public static final char STALKER = 'Z';   // enemy that chases you when you get close

    public final int number;
    public final String name;
    public final int timeLimit;   // seconds
    public final double darkness; // 0 = bright, 1 = very dark
    public final String hint;
    public final int cols;
    public final int rows;
    public final int startCol;
    public final int startRow;
    public final int keysRequired;
    public final int coinsTotal;
    public final int chestsTotal;
    public final int secretsTotal;

    /** Enemy spawn points: {col, row, type character}. */
    public final List<int[]> enemies = new ArrayList<>();

    private final char[][] layout;

    public Level(int number, String name, int timeLimit, double darkness, String hint, String[] lines) {
        this.number = number;
        this.name = name;
        this.timeLimit = timeLimit;
        this.darkness = darkness;
        this.hint = hint;
        this.rows = lines.length;
        this.cols = lines[0].length();
        this.layout = new char[rows][cols];

        int foundStartCol = -1;
        int foundStartRow = -1;
        int keys = 0;
        int coins = 0;
        int chests = 0;
        int secrets = 0;

        for (int r = 0; r < rows; r++) {
            if (lines[r].length() != cols) {
                throw new IllegalArgumentException(
                        "Level " + number + ": row " + r + " has " + lines[r].length()
                                + " characters but should have " + cols);
            }
            for (int c = 0; c < cols; c++) {
                char ch = lines[r].charAt(c);
                if (ch == START) {
                    foundStartCol = c;
                    foundStartRow = r;
                    ch = FLOOR;
                } else if (ch == PATROL_H || ch == PATROL_V || ch == STALKER) {
                    enemies.add(new int[] { c, r, ch });
                    ch = FLOOR;
                } else if (ch == KEY) {
                    keys++;
                } else if (ch == COIN) {
                    coins++;
                } else if (ch == CHEST) {
                    chests++;
                } else if (ch == SECRET) {
                    secrets++;
                }
                layout[r][c] = ch;
            }
        }

        if (foundStartCol < 0) {
            throw new IllegalArgumentException("Level " + number + " has no start tile (P)");
        }
        this.startCol = foundStartCol;
        this.startRow = foundStartRow;
        this.keysRequired = keys;
        this.coinsTotal = coins;
        this.chestsTotal = chests;
        this.secretsTotal = secrets;
    }

    /** A fresh copy of the grid, so a game can change it without touching the level itself. */
    public char[][] copyGrid() {
        char[][] copy = new char[rows][cols];
        for (int r = 0; r < rows; r++) {
            System.arraycopy(layout[r], 0, copy[r], 0, cols);
        }
        return copy;
    }
}
