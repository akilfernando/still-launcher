package com.still.launcher;

final class GestureRules {
    static final int NONE = 0, UP = 1, DOWN = 2;
    static int swipe(float dx, float dy, float threshold, long elapsed, boolean multiplePointers) {
        if (multiplePointers || elapsed > 600 || Math.abs(dy) < threshold
            || Math.abs(dy) < Math.abs(dx) * 1.5f) return NONE;
        return dy < 0 ? UP : DOWN;
    }
}
