package com.ipindou.app.core;

import java.util.ArrayDeque;

public final class PatternGenerator {
    private PatternGenerator() {}

    public static BeadPattern fromPixels(int[] pixels, int sourceWidth, int sourceHeight, int beadWidth, int beadHeight, boolean removeBackground) {
        int[] work = pixels.clone();
        if (removeBackground) removeEdgeConnectedBackground(work, sourceWidth, sourceHeight);
        int[] indexes = new int[beadWidth * beadHeight];
        for (int gy = 0; gy < beadHeight; gy++) {
            int y0 = gy * sourceHeight / beadHeight;
            int y1 = Math.max(y0 + 1, (gy + 1) * sourceHeight / beadHeight);
            for (int gx = 0; gx < beadWidth; gx++) {
                int x0 = gx * sourceWidth / beadWidth;
                int x1 = Math.max(x0 + 1, (gx + 1) * sourceWidth / beadWidth);
                indexes[gy * beadWidth + gx] = BeadPalette.nearestIndex(average(work, sourceWidth, x0, y0, x1, y1), removeBackground);
            }
        }
        return new BeadPattern(beadWidth, beadHeight, indexes);
    }

    private static int average(int[] pixels, int width, int x0, int y0, int x1, int y1) {
        long a = 0, r = 0, g = 0, b = 0, count = 0;
        for (int y = y0; y < y1; y++) for (int x = x0; x < x1; x++) {
            int c = pixels[y * width + x];
            int alpha = (c >>> 24) & 0xff;
            if (alpha < 16) continue;
            a += alpha; r += (c >>> 16) & 0xff; g += (c >>> 8) & 0xff; b += c & 0xff; count++;
        }
        if (count == 0) return 0x00FFFFFF;
        return ((int)(a / count) << 24) | ((int)(r / count) << 16) | ((int)(g / count) << 8) | (int)(b / count);
    }

    public static BeadPattern withOutline(BeadPattern pattern, int outlineColorIndex) {
        int transparent = BeadPalette.transparentIndex();
        if (outlineColorIndex < 0 || outlineColorIndex >= transparent) throw new IllegalArgumentException("Outline color must be an opaque palette index");
        int[] indexes = pattern.copyIndexes();
        for (int y = 0; y < pattern.height; y++) {
            for (int x = 0; x < pattern.width; x++) {
                int current = pattern.get(x, y);
                if (current == transparent) {
                    if (touchesOpaque(pattern, x, y, transparent)) indexes[y * pattern.width + x] = outlineColorIndex;
                } else if (current != outlineColorIndex && shouldAddInteriorOutline(pattern, x, y, transparent)) {
                    indexes[y * pattern.width + x] = outlineColorIndex;
                }
            }
        }
        return new BeadPattern(pattern.width, pattern.height, indexes);
    }

    private static boolean shouldAddInteriorOutline(BeadPattern pattern, int x, int y, int transparent) {
        int currentArgb = BeadPalette.colorAt(pattern.get(x, y)).argb;
        int currentLuminance = luminance(currentArgb);
        boolean darkerSideOfStrongEdge = false;
        int strongEdges = 0;
        int[] dx = { -1, 1, 0, 0 };
        int[] dy = { 0, 0, -1, 1 };
        for (int i = 0; i < dx.length; i++) {
            int nx = x + dx[i], ny = y + dy[i];
            if (nx < 0 || nx >= pattern.width || ny < 0 || ny >= pattern.height) continue;
            int neighbor = pattern.get(nx, ny);
            if (neighbor == transparent) continue;
            int neighborArgb = BeadPalette.colorAt(neighbor).argb;
            int neighborLuminance = luminance(neighborArgb);
            int colorGap = distance(currentArgb, neighborArgb);
            int lightGap = Math.abs(currentLuminance - neighborLuminance);
            if (colorGap >= 105 || lightGap >= 42) {
                strongEdges++;
                if (currentLuminance <= neighborLuminance + 8) darkerSideOfStrongEdge = true;
            }
        }
        return darkerSideOfStrongEdge && strongEdges <= 2;
    }

    private static boolean touchesOpaque(BeadPattern pattern, int x, int y, int transparent) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                if (dx == 0 && dy == 0) continue;
                int nx = x + dx, ny = y + dy;
                if (nx >= 0 && nx < pattern.width && ny >= 0 && ny < pattern.height && pattern.get(nx, ny) != transparent) return true;
            }
        }
        return false;
    }

    public static void removeEdgeConnectedBackground(int[] pixels, int width, int height) {
        BackgroundSample background = dominantEdgeBackground(pixels, width, height);
        boolean[] seen = new boolean[pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) { enqueueIfBg(pixels, width, x, 0, background, seen, queue); enqueueIfBg(pixels, width, x, height - 1, background, seen, queue); }
        for (int y = 0; y < height; y++) { enqueueIfBg(pixels, width, 0, y, background, seen, queue); enqueueIfBg(pixels, width, width - 1, y, background, seen, queue); }
        while (!queue.isEmpty()) {
            int p = queue.removeFirst();
            pixels[p] = 0x00FFFFFF;
            int x = p % width, y = p / width;
            if (x > 0) enqueueIfBg(pixels, width, x - 1, y, background, seen, queue);
            if (x + 1 < width) enqueueIfBg(pixels, width, x + 1, y, background, seen, queue);
            if (y > 0) enqueueIfBg(pixels, width, x, y - 1, background, seen, queue);
            if (y + 1 < height) enqueueIfBg(pixels, width, x, y + 1, background, seen, queue);
        }
    }

    private static void enqueueIfBg(int[] pixels, int width, int x, int y, BackgroundSample background, boolean[] seen, ArrayDeque<Integer> queue) {
        int p = y * width + x;
        if (seen[p]) return;
        seen[p] = true;
        if (distance(pixels[p], background.seed) <= background.tolerance) queue.addLast(p);
    }

    private static BackgroundSample dominantEdgeBackground(int[] pixels, int width, int height) {
        int[] histogram = new int[512];
        forEachEdgePixel(pixels, width, height, color -> histogram[quantizeBackgroundBin(color)]++);
        int dominantBin = 0;
        for (int i = 1; i < histogram.length; i++) if (histogram[i] > histogram[dominantBin]) dominantBin = i;

        long[] totals = new long[4];
        final int selectedBin = dominantBin;
        forEachEdgePixel(pixels, width, height, color -> {
            if (quantizeBackgroundBin(color) == selectedBin) {
                totals[0] += (color >>> 16) & 0xff;
                totals[1] += (color >>> 8) & 0xff;
                totals[2] += color & 0xff;
                totals[3]++;
            }
        });
        if (totals[3] == 0) return new BackgroundSample(0xffffffff, 38);

        int seed = 0xff000000 | ((int)(totals[0] / totals[3]) << 16) | ((int)(totals[1] / totals[3]) << 8) | (int)(totals[2] / totals[3]);
        int[] maxClusterDistance = new int[1];
        forEachEdgePixel(pixels, width, height, color -> {
            if (quantizeBackgroundBin(color) == selectedBin) maxClusterDistance[0] = Math.max(maxClusterDistance[0], distance(color, seed));
        });
        return new BackgroundSample(seed, Math.max(38, Math.min(72, maxClusterDistance[0] + 18)));
    }

    private static int quantizeBackgroundBin(int color) {
        return (((color >>> 21) & 0x07) << 6) | (((color >>> 13) & 0x07) << 3) | ((color >>> 5) & 0x07);
    }

    private static void forEachEdgePixel(int[] pixels, int width, int height, EdgePixelConsumer consumer) {
        for (int x = 0; x < width; x++) { consumer.accept(pixels[x]); consumer.accept(pixels[(height - 1) * width + x]); }
        for (int y = 1; y + 1 < height; y++) { consumer.accept(pixels[y * width]); consumer.accept(pixels[y * width + width - 1]); }
    }

    private interface EdgePixelConsumer { void accept(int color); }

    private static final class BackgroundSample {
        final int seed;
        final int tolerance;

        BackgroundSample(int seed, int tolerance) {
            this.seed = seed;
            this.tolerance = tolerance;
        }
    }

    private static int distance(int a, int b) {
        int ar = (a >>> 16) & 0xff, ag = (a >>> 8) & 0xff, ab = a & 0xff;
        int br = (b >>> 16) & 0xff, bg = (b >>> 8) & 0xff, bb = b & 0xff;
        int dr = ar - br, dg = ag - bg, db = ab - bb;
        return (int)Math.sqrt(dr * dr + dg * dg + db * db);
    }

    private static int luminance(int argb) {
        return (((argb >>> 16) & 0xff) * 30 + ((argb >>> 8) & 0xff) * 59 + (argb & 0xff) * 11) / 100;
    }
}
