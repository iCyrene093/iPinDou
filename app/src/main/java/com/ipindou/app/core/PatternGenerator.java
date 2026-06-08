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
        int seed = dominantEdgeColor(pixels, width, height);
        int tolerance = 38;
        boolean[] seen = new boolean[pixels.length];
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        for (int x = 0; x < width; x++) { enqueueIfBg(pixels, width, x, 0, seed, tolerance, seen, queue); enqueueIfBg(pixels, width, x, height - 1, seed, tolerance, seen, queue); }
        for (int y = 0; y < height; y++) { enqueueIfBg(pixels, width, 0, y, seed, tolerance, seen, queue); enqueueIfBg(pixels, width, width - 1, y, seed, tolerance, seen, queue); }
        while (!queue.isEmpty()) {
            int p = queue.removeFirst();
            pixels[p] = 0x00FFFFFF;
            int x = p % width, y = p / width;
            if (x > 0) enqueueIfBg(pixels, width, x - 1, y, seed, tolerance, seen, queue);
            if (x + 1 < width) enqueueIfBg(pixels, width, x + 1, y, seed, tolerance, seen, queue);
            if (y > 0) enqueueIfBg(pixels, width, x, y - 1, seed, tolerance, seen, queue);
            if (y + 1 < height) enqueueIfBg(pixels, width, x, y + 1, seed, tolerance, seen, queue);
        }
    }

    private static void enqueueIfBg(int[] pixels, int width, int x, int y, int seed, int tolerance, boolean[] seen, ArrayDeque<Integer> queue) {
        int p = y * width + x;
        if (seen[p]) return;
        seen[p] = true;
        if (distance(pixels[p], seed) <= tolerance) queue.addLast(p);
    }

    private static int dominantEdgeColor(int[] pixels, int width, int height) {
        long r = 0, g = 0, b = 0, count = 0;
        for (int x = 0; x < width; x++) { int top = pixels[x], bottom = pixels[(height - 1) * width + x]; r += ((top >>> 16) & 0xff) + ((bottom >>> 16) & 0xff); g += ((top >>> 8) & 0xff) + ((bottom >>> 8) & 0xff); b += (top & 0xff) + (bottom & 0xff); count += 2; }
        for (int y = 1; y + 1 < height; y++) { int left = pixels[y * width], right = pixels[y * width + width - 1]; r += ((left >>> 16) & 0xff) + ((right >>> 16) & 0xff); g += ((left >>> 8) & 0xff) + ((right >>> 8) & 0xff); b += (left & 0xff) + (right & 0xff); count += 2; }
        return 0xff000000 | ((int)(r / count) << 16) | ((int)(g / count) << 8) | (int)(b / count);
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
