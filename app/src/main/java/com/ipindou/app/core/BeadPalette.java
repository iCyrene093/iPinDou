package com.ipindou.app.core;

public final class BeadPalette {
    private static final BeadColor[] COLORS = new BeadColor[] {
        new BeadColor("S01", "Artkal White", 0xFFFFFFFF), new BeadColor("S02", "Artkal Burning Sand", 0xFFFFA38B),
        new BeadColor("S03", "Artkal Tangerine", 0xFFFF8200), new BeadColor("S04", "Artkal Orange", 0xFFFA4616),
        new BeadColor("S05", "Artkal Tall Poppy", 0xFFEE2737), new BeadColor("S06", "Artkal Raspberry Pink", 0xFFEF64A2),
        new BeadColor("S07", "Artkal Gray", 0xFF97999B), new BeadColor("S08", "Artkal Emerald", 0xFF26D07C),
        new BeadColor("S09", "Artkal Dark Green", 0xFF007371), new BeadColor("S10", "Artkal Baby Blue", 0xFF56B7E6),
        new BeadColor("S11", "Artkal Dark Blue", 0xFF0050B5), new BeadColor("S12", "Artkal Pastel Lavender", 0xFF9063CD),
        new BeadColor("S13", "Artkal Black", 0xFF000000), new BeadColor("S14", "Artkal Sandstorm", 0xFFFDDA24),
        new BeadColor("S15", "Artkal Redwood", 0xFFA72B2A), new BeadColor("S16", "Artkal Brown", 0xFF674736),
        new BeadColor("S17", "Artkal Light Brown", 0xFF7B4D35), new BeadColor("S18", "Artkal Sand", 0xFFEAA794),
        new BeadColor("S19", "Artkal Bubble Gum", 0xFFF8C1B8), new BeadColor("S20", "Artkal Green", 0xFF249E6B),
        new BeadColor("S21", "Artkal Pastel Green", 0xFF93C90E), new BeadColor("S22", "Artkal Purple", 0xFF483698),
        new BeadColor("S23", "Artkal Royal Purple", 0xFF7D55C7), new BeadColor("S24", "Artkal True Blue", 0xFF1164C9),
        new BeadColor("CT1", "Artkal Clear", 0x00FFFFFF)
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
