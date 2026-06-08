package com.ipindou.app.core;

public final class PatternCoreTest {
    public static void main(String[] args) {
        testMirrors();
        testTransparentCounts();
        testOutlineRejectsTransparent();
        testColorIndexValidation();
        testTransparentAverageProducesEmptyCell();
        testEdgeConnectedBackgroundRemovalKeepsInterior();
        testSamplingUsesEveryTargetCell();
        testOutlineFillsTransparentNeighbor();
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

    private static void testTransparentAverageProducesEmptyCell() {
        int transparent = BeadPalette.transparentIndex();
        BeadPattern pattern = PatternGenerator.fromPixels(new int[] { 0x00ffffff, 0x00ffffff, 0x00ffffff, 0x00ffffff }, 2, 2, 1, 1, true);
        assertEquals(transparent, pattern.get(0, 0), "fully transparent source cell");
    }

    private static void testEdgeConnectedBackgroundRemovalKeepsInterior() {
        int white = 0xffffffff;
        int black = 0xff000000;
        int[] pixels = new int[] {
                white, white, white,
                white, black, white,
                white, white, white
        };
        PatternGenerator.removeEdgeConnectedBackground(pixels, 3, 3);
        int transparent = 0x00ffffff;
        assertEquals(transparent, pixels[0], "top-left edge background removal");
        assertEquals(transparent, pixels[2], "top-right edge background removal");
        assertEquals(black, pixels[4], "interior subject preservation");
        assertEquals(transparent, pixels[8], "bottom-right edge background removal");
    }

    private static void testSamplingUsesEveryTargetCell() {
        int black = BeadPalette.nearestOpaqueIndex(0xff000000);
        int white = BeadPalette.nearestOpaqueIndex(0xffffffff);
        BeadPattern pattern = PatternGenerator.fromPixels(new int[] {
                0xff000000, 0xffffffff,
                0xffffffff, 0xff000000
        }, 2, 2, 2, 2, false);
        assertEquals(black, pattern.get(0, 0), "sampled top-left cell");
        assertEquals(white, pattern.get(1, 0), "sampled top-right cell");
        assertEquals(white, pattern.get(0, 1), "sampled bottom-left cell");
        assertEquals(black, pattern.get(1, 1), "sampled bottom-right cell");
    }

    private static void testOutlineFillsTransparentNeighbor() {
        int transparent = BeadPalette.transparentIndex();
        int black = BeadPalette.nearestOpaqueIndex(0xff000000);
        int red = BeadPalette.nearestOpaqueIndex(0xffff0000);
        BeadPattern pattern = new BeadPattern(3, 3, new int[] {
                transparent, transparent, transparent,
                transparent, red, transparent,
                transparent, transparent, transparent
        });
        BeadPattern outlined = PatternGenerator.withOutline(pattern, black);
        assertEquals(red, outlined.get(1, 1), "outlined center remains subject");
        assertEquals(black, outlined.get(0, 0), "outlined diagonal neighbor");
        assertEquals(black, outlined.get(1, 0), "outlined cardinal neighbor");
        assertEquals(black, outlined.get(2, 2), "outlined opposite diagonal neighbor");
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
