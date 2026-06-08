package com.ipindou.app.core;

import java.util.Arrays;

public final class BeadPattern {
    public final int width;
    public final int height;
    private final int[] colorIndexes;

    public BeadPattern(int width, int height) {
        this(width, height, new int[Math.max(1, width * height)]);
    }

    public BeadPattern(int width, int height, int[] colorIndexes) {
        if (width <= 0 || height <= 0 || colorIndexes.length != width * height) throw new IllegalArgumentException("Invalid pattern size");
        for (int colorIndex : colorIndexes) validateColorIndex(colorIndex);
        this.width = width;
        this.height = height;
        this.colorIndexes = colorIndexes.clone();
    }

    public int get(int x, int y) { return colorIndexes[y * width + x]; }
    public void set(int x, int y, int colorIndex) { validateColorIndex(colorIndex); colorIndexes[y * width + x] = colorIndex; }
    public int[] copyIndexes() { return colorIndexes.clone(); }

    public BeadPattern mirroredHorizontal() {
        int[] out = new int[colorIndexes.length];
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) out[y * width + (width - 1 - x)] = get(x, y);
        return new BeadPattern(width, height, out);
    }

    public BeadPattern mirroredVertical() {
        int[] out = new int[colorIndexes.length];
        for (int y = 0; y < height; y++) System.arraycopy(colorIndexes, y * width, out, (height - 1 - y) * width, width);
        return new BeadPattern(width, height, out);
    }

    public int countOf(int colorIndex) {
        int count = 0;
        for (int index : colorIndexes) if (index == colorIndex) count++;
        return count;
    }

    public int nonTransparentCount() { return colorIndexes.length - countOf(BeadPalette.transparentIndex()); }

    public void fill(int colorIndex) { validateColorIndex(colorIndex); Arrays.fill(colorIndexes, colorIndex); }

    private void validateColorIndex(int colorIndex) {
        if (colorIndex < 0 || colorIndex >= BeadPalette.colors().length) throw new IllegalArgumentException("Invalid color index: " + colorIndex);
    }
}
