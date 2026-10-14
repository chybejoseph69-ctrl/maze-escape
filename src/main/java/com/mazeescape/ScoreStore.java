package com.mazeescape;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Saves the best score and level progress in small text files in your user folder:
 * C:\Users\YourName\.mazeescape\
 */
public final class ScoreStore {

    private static final Path DIR = Paths.get(System.getProperty("user.home"), ".mazeescape");

    private ScoreStore() { }

    /** Returns the saved best score, or 0 if nothing has been saved yet. */
    public static int load() {
        return Math.max(0, readInt("best_score.txt", 0));
    }

    public static void save(int score) {
        writeInt("best_score.txt", score);
    }

    /** How many levels are unlocked (at least 1). */
    public static int loadUnlocked() {
        return Math.max(1, readInt("progress.txt", 1));
    }

    public static void saveUnlocked(int count) {
        writeInt("progress.txt", count);
    }

    private static int readInt(String fileName, int fallback) {
        try {
            Path file = DIR.resolve(fileName);
            if (Files.exists(file)) {
                return Integer.parseInt(Files.readString(file, StandardCharsets.UTF_8).trim());
            }
        } catch (IOException | NumberFormatException e) {
            System.err.println("Could not read " + fileName + ": " + e.getMessage());
        }
        return fallback;
    }

    private static void writeInt(String fileName, int value) {
        try {
            Files.createDirectories(DIR);
            Files.writeString(DIR.resolve(fileName), Integer.toString(value), StandardCharsets.UTF_8);
        } catch (IOException e) {
            System.err.println("Could not save " + fileName + ": " + e.getMessage());
        }
    }
}
