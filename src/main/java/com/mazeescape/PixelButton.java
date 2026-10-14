package com.mazeescape;

import javafx.scene.control.Button;
import javafx.scene.text.TextAlignment;

/** A dungeon-styled button with normal, highlighted (hover / keyboard) and locked looks. */
public class PixelButton extends Button {

    private final int fontSize;
    private boolean selected;
    private boolean locked;

    public PixelButton(String text, double width) {
        this(text, width, 44, 18);
    }

    public PixelButton(String text, double width, double height, int fontSize) {
        super(text);
        this.fontSize = fontSize;
        setPrefSize(width, height);
        setMinSize(width, height);
        setMaxSize(width, height);
        setTextAlignment(TextAlignment.CENTER);
        setFocusTraversable(false);   // we handle keyboard navigation ourselves
        applyStyle();
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
        applyStyle();
    }

    public void setLocked(boolean locked) {
        this.locked = locked;
        applyStyle();
    }

    private void applyStyle() {
        String background;
        String border;
        String text;
        String effect = "";
        if (locked) {
            background = selected ? "#1a1216" : "#0e1722";
            border = selected ? "#8a4040" : "#26323f";
            text = selected ? "#9a8080" : "#55656f";
        } else if (selected) {
            background = "#145a45";
            border = "#43e58f";
            text = "white";
            effect = "-fx-effect: dropshadow(gaussian, rgba(67,229,143,0.55), 14, 0.3, 0, 0);";
        } else {
            background = "#13273a";
            border = "#2d6f80";
            text = "#cfe8ee";
        }
        setStyle("-fx-background-color: " + background + ";"
                + "-fx-background-insets: 0;"
                + "-fx-background-radius: 3;"
                + "-fx-border-color: " + border + ";"
                + "-fx-border-width: 2;"
                + "-fx-border-radius: 3;"
                + "-fx-text-fill: " + text + ";"
                + "-fx-font-family: 'Consolas';"
                + "-fx-font-size: " + fontSize + "px;"
                + "-fx-font-weight: bold;"
                + "-fx-text-alignment: center;"
                + "-fx-cursor: " + (locked ? "default" : "hand") + ";"
                + effect);
    }
}
