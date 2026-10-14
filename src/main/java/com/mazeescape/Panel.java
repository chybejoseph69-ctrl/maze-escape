package com.mazeescape;

import java.util.List;
import javafx.scene.Node;

/**
 * A screen's visible content plus its buttons. Keeps track of which button is
 * highlighted so the keyboard and the mouse both work. With columns > 1 the
 * buttons form a grid (used by the level select).
 */
public final class Panel {

    public final Node node;
    private final List<PixelButton> buttons;
    private final int columns;
    private int index = 0;

    public Panel(Node node, List<PixelButton> buttons) {
        this(node, buttons, 1);
    }

    public Panel(Node node, List<PixelButton> buttons, int columns) {
        this.node = node;
        this.buttons = buttons;
        this.columns = columns;
        for (int i = 0; i < buttons.size(); i++) {
            final int buttonIndex = i;
            buttons.get(i).setOnMouseEntered(e -> select(buttonIndex));
        }
        refresh();
    }

    public void select(int newIndex) {
        index = newIndex;
        refresh();
    }

    /** dx / dy are -1, 0 or 1: left/right and up/down. */
    public void move(int dx, int dy) {
        int n = buttons.size();
        if (n == 0) {
            return;
        }
        if (columns <= 1) {
            if (dy != 0) {
                index = (index + dy + n) % n;
            }
        } else if (dx != 0) {
            index = (index + dx + n) % n;
        } else if (dy > 0) {
            int next = index + columns;
            if (next >= n) {
                next = (index == n - 1) ? 0 : n - 1;
            }
            index = next;
        } else if (dy < 0) {
            int next = index - columns;
            if (next < 0) {
                next = n - 1;
            }
            index = next;
        }
        refresh();
    }

    public void activate() {
        if (!buttons.isEmpty()) {
            buttons.get(index).fire();
        }
    }

    public void refresh() {
        for (int i = 0; i < buttons.size(); i++) {
            buttons.get(i).setSelected(i == index);
        }
    }
}
