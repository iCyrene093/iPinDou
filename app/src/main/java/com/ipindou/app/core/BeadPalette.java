package com.ipindou.app.core;

public final class BeadPalette {
    private static final BeadColor[] COLORS = new BeadColor[] {
        new BeadColor("01", "White", 0xFFFFFFFF), new BeadColor("02", "Cream", 0xFFFFF4D6),
        new BeadColor("03", "Yellow", 0xFFFFD500), new BeadColor("04", "Orange", 0xFFFF8A00),
        new BeadColor("05", "Red", 0xFFD71920), new BeadColor("06", "Dark Red", 0xFF8C1D18),
        new BeadColor("07", "Pink", 0xFFFF7BAC), new BeadColor("08", "Purple", 0xFF7B4EA3),
        new BeadColor("09", "Lavender", 0xFFC6A4D9), new BeadColor("10", "Blue", 0xFF1D70B8),
        new BeadColor("11", "Light Blue", 0xFF6EC6E8), new BeadColor("12", "Turquoise", 0xFF00A7A5),
        new BeadColor("13", "Green", 0xFF159447), new BeadColor("14", "Light Green", 0xFF8CC63E),
        new BeadColor("15", "Brown", 0xFF7A4A2A), new BeadColor("16", "Tan", 0xFFD9A066),
        new BeadColor("17", "Grey", 0xFF8A8C8E), new BeadColor("18", "Black", 0xFF111111),
        new BeadColor("19", "Peach", 0xFFFFB997), new BeadColor("20", "Mint", 0xFFA8E6CF),
        new BeadColor("21", "Navy", 0xFF173B6D), new BeadColor("22", "Maroon", 0xFF5E1F2F),
        new BeadColor("23", "Olive", 0xFF7C8A2E), new BeadColor("24", "Transparent", 0x00FFFFFF)
    };

    private BeadPalette() {}

    public static BeadColor[] colors() { return COLORS.clone(); }

    public static BeadColor colorAt(int index) { return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))]; }

    public static int nearestIndex(int argb, boolean allowTransparent) {
        int alpha = (argb >>> 24) & 0xff;
        if (allowTransparent && alpha < 64) return COLORS.length - 1;
        int r = (argb >>> 16) & 0xff, g = (argb >>> 8) & 0xff, b = argb & 0xff;
        int best = 0;
        long bestDistance = Long.MAX_VALUE;
        int limit = allowTransparent ? COLORS.length : COLORS.length - 1;
        for (int i = 0; i < limit; i++) {
            int c = COLORS[i].argb;
            int cr = (c >>> 16) & 0xff, cg = (c >>> 8) & 0xff, cb = c & 0xff;
            int dr = r - cr, dg = g - cg, db = b - cb;
            long distance = 30L * dr * dr + 59L * dg * dg + 11L * db * db;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }
}
