package com.still.launcher;

public final class GestureRulesTest {
    public static void main(String[] args) {
        check(GestureRules.swipe(5, -100, 80, 250, false) == GestureRules.UP, "up");
        check(GestureRules.swipe(-5, 100, 80, 250, false) == GestureRules.DOWN, "down");
        check(GestureRules.swipe(0, 79, 80, 100, false) == GestureRules.NONE, "small movement");
        check(GestureRules.swipe(120, 90, 80, 100, false) == GestureRules.NONE, "horizontal");
        check(GestureRules.swipe(100, 100, 80, 100, false) == GestureRules.NONE, "diagonal");
        check(GestureRules.swipe(0, 200, 80, 1000, false) == GestureRules.NONE, "long press drag");
        check(GestureRules.swipe(0, 200, 80, 100, true) == GestureRules.NONE, "multi touch");
        check(GestureRules.swipe(0, -80, 80, 600, false) == GestureRules.UP, "boundary");
        System.out.println("PASS: 8 swipe classification checks");
    }
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
    }
}
