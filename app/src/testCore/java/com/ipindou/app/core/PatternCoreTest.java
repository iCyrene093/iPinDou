package com.ipindou.app.core;

public final class PatternCoreTest {
    public static void main(String[] args) {
        testMirrors();
        testTransparentCounts();
        testOutlineRejectsTransparent();
        testColorIndexValidation();
    }

    private static void testMirrors() {
        BeadPattern pattern = new BeadPattern(3, 2, new int[] { 1, 2, 3, 4, 5, 6 });
        assertArrayEquals(new int[] { 3, 2, 1, 6, 5, 4 }, pattern.mirroredHorizontal().copyIndexes(), "horizontal mirror");
        assertArrayEquals(new int[] { 4, 5, 6, 1, 2, 3 }, pattern.mirroredVertical().copyIndexes(), "vertical mirror");
    }

    private static void testTransparentCounts() {
        int transparent = BeadPalette.transparentIndex();
        BeadPattern pattern = new BeadPattern(3, 2, new int[] { 1, transparent, 2, transparent, 3, 4 });
        assertEquals(4, pattern.nonTransparentCount(), "non-transparent bead count");
        assertEquals(2, pattern.countOf(transparent), "transparent count");
    }

    private static void testOutlineRejectsTransparent() {
        BeadPattern pattern = new BeadPattern(2, 2, new int[] { 1, 1, BeadPalette.transparentIndex(), 1 });
        assertThrows(() -> PatternGenerator.withOutline(pattern, BeadPalette.transparentIndex()), "transparent outline rejection");
    }

    private static void testColorIndexValidation() {
        BeadPattern pattern = new BeadPattern(1, 1);
        assertThrows(() -> pattern.set(0, 0, -1), "negative color index rejection");
        assertThrows(() -> pattern.fill(BeadPalette.colors().length), "out-of-range color index rejection");
    }

    private static void assertArrayEquals(int[] expected, int[] actual, String label) {
        if (expected.length != actual.length) throw new AssertionError(label + " length mismatch");
        for (int i = 0; i < expected.length; i++) {
            if (expected[i] != actual[i]) throw new AssertionError(label + " mismatch at " + i + ": expected " + expected[i] + ", got " + actual[i]);
        }
    }

    private static void assertEquals(int expected, int actual, String label) {
        if (expected != actual) throw new AssertionError(label + ": expected " + expected + ", got " + actual);
    }

    private static void assertThrows(Runnable action, String label) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError(label + ": expected IllegalArgumentException");
    }
}
