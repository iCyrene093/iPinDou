package com.ipindou.app.core;

public final class BeadPalette {
    private static final String[][] MARD_291 = new String[][] {
        {"A", "FAF4C8 FFFFD5 FEFF8B FBED56 F4D738 FEAC4C FE8B4C FFDA45 FF995B F77C31 FFDD99 FE9F72 FFC365 FD543D FFF365 FFFF9F FFE36E FEBE7D FD7C72 FFD568 FFE395 F4F57D E6C9B7 F7F8A2 FFD67D FFC830"},
        {"B", "E6EE31 63F347 9EF780 5DE035 35E352 65E2A6 3DAF80 1C9C4F 27523A 95D3C2 5D722A 166F41 CAEB7B ADE946 2E5132 C5ED9C 9BB13A E6EE49 24B88C C2F0CC 156A6B 0B3C43 303A21 EEFCA5 4E846D 8D7A35 CCE1AF 9EE5B9 C5E254 E2FCB1 B0E792 9CAB5A"},
        {"C", "E8FFE7 A9F9FC A0E2FB 41CCFF 01ACEB 50AAF0 3677D2 0F54C0 324BCA 3EBCE2 28DDDE 1C334D CDE8FF D5FDFF 22C4C6 1557A8 04D1F6 1D3344 1887A2 176DAF BEDDFF 67B4BE C8E2FF 7CC4FF A9E5E5 3CAED8 D3DFFA BBCFED 34488E"},
        {"D", "AEB4F2 858EDD 2F54AF 182A84 B843C5 AC7BDE 8854B3 E2D3FF D5B9F8 361851 B9BAE1 DE9AD4 B90095 8B279B 2F1F90 E3E1EE C4D4F6 A45EC7 D8C3D7 9C32B2 9A009B 333A95 EBDAFC 7786E5 494FC7 DFC2F8"},
        {"E", "FDD3CC FEC0DF FFB7E7 E8649E F551A2 F13D74 C63478 FFDBE9 E970CC D33793 FCDDD2 F78FC3 B5006D FFD1BA F8C7C9 FFF3EB FFE2EA FFC7DB FEBAD5 D8C7D1 BD9DA1 B785A1 937A8D E1BCE8"},
        {"F", "FD957B FC3D46 F74941 FC283C E7002F 943630 971937 BC0028 E2677A 8A4526 5A2121 FD4E6A F35744 FFA9AD D30022 FEC2A6 E69C79 D37C46 C1444A CD9391 F7B4C6 FDC0D0 F67E66 E698AA E54B4F"},
        {"G", "FFE2CE FFC4AA F4C3A5 E1B383 EDB045 E99C17 9D5B3E 753832 E6B483 D98C39 E0C593 FFC890 B7714A 8D614C FCF9E0 F2D9BA 78524B FFE4CC E07935 A94023 B88558"},
        {"H", "FDFBFF FEFFFF B6B1BA 89858C 48464E 2F2B2F 000000 E7D6DB EDEDED EEE9EA CECDD5 FFF5ED F5ECD2 CFD7D3 98A6A8 1D1414 F1EDED FFFDF0 F6EFE2 949FA3 FFFBE1 CACAD4 9A9D94"},
        {"M", "BCC6B8 8AA386 697D80 E3D2BC D0CCAA B0A782 B4A497 B38281 A58767 C5B2BC 9F7594 644749 D19066 C77362 757D78"},
        {"P", "FCF7F8 B0A9AC AFDCAB FEA49F EE8C3E 5FD0A7 EB9270 F0D958 D9D9D9 D9C7EA F3ECC9 E6EEF2 AACBEF 337680 668575 FEBF45 FEA324 FEB89F FFFEEC FEBECF ECBEBF E4A89F A56268"},
        {"Q", "F2A5E8 E9EC91 FFFF00 FFEBFA 76CEDE"},
        {"R", "D50D21 F92F83 FD8324 F8EC31 35C75B 238891 19779D 1A60C3 9A56B4 FFDB4C FFEBFA D8D5CE 55514C 9FE4DF 77CEE9 3ECFCA 4A867A 7FCD9D CDE55D E8C7B4 AD6F3C 6C372F FEB872 F3C1C0 C9675E D293BE EA8CB1 9C87D6"},
        {"T", "FFFFFF"},
        {"Y", "FD6FB4 FEB481 D7FAA0 8BDBFA E987EA"},
        {"ZG", "DAABB3 D6AA87 C1BD8D 96869F 8490A6 94BFE2 E2A9D2 AB91C0"}
    };

    private static final BeadColor[] COLORS = buildColors();
    private static final double[][] LAB = buildLab();
    private static final int TRANSPARENT_INDEX = COLORS.length - 1;

    private BeadPalette() {}

    public static BeadColor[] colors() { return COLORS.clone(); }

    public static int standardColorCount() { return COLORS.length - 1; }

    public static int transparentIndex() { return TRANSPARENT_INDEX; }

    public static int nearestOpaqueIndex(int argb) { return nearestIndex(argb, false); }

    public static BeadColor colorAt(int index) { return COLORS[Math.max(0, Math.min(COLORS.length - 1, index))]; }

    public static int nearestIndex(int argb, boolean allowTransparent) {
        int alpha = (argb >>> 24) & 0xff;
        if (allowTransparent && alpha < 64) return TRANSPARENT_INDEX;
        double[] target = toLab(argb);
        int best = 0;
        double bestDistance = Double.MAX_VALUE;
        int limit = standardColorCount();
        for (int i = 0; i < limit; i++) {
            double dl = target[0] - LAB[i][0];
            double da = target[1] - LAB[i][1];
            double db = target[2] - LAB[i][2];
            double chromaWeight = target[0] < 18 ? 0.72 : 1.0;
            double distance = dl * dl + chromaWeight * da * da + chromaWeight * db * db;
            if (distance < bestDistance) { bestDistance = distance; best = i; }
        }
        return best;
    }

    private static BeadColor[] buildColors() {
        BeadColor[] colors = new BeadColor[292];
        int index = 0;
        for (String[] series : MARD_291) {
            String[] hexes = series[1].split(" ");
            for (int i = 0; i < hexes.length; i++) {
                String code = series[0] + (i + 1);
                colors[index++] = new BeadColor(code, "MARD " + code, 0xff000000 | Integer.parseInt(hexes[i], 16));
            }
        }
        if (index != 291) throw new IllegalStateException("MARD palette must contain 291 colors, found " + index);
        colors[index] = new BeadColor("透明", "自动去背景", 0x00ffffff);
        return colors;
    }

    private static double[][] buildLab() {
        BeadColor[] colors = COLORS;
        double[][] lab = new double[colors.length][3];
        for (int i = 0; i < colors.length; i++) lab[i] = toLab(colors[i].argb);
        return lab;
    }

    private static double[] toLab(int argb) {
        double r = pivotRgb((argb >>> 16) & 0xff);
        double g = pivotRgb((argb >>> 8) & 0xff);
        double b = pivotRgb(argb & 0xff);
        double x = (r * 0.4124564 + g * 0.3575761 + b * 0.1804375) / 0.95047;
        double y = (r * 0.2126729 + g * 0.7151522 + b * 0.0721750);
        double z = (r * 0.0193339 + g * 0.1191920 + b * 0.9503041) / 1.08883;
        double fx = pivotXyz(x), fy = pivotXyz(y), fz = pivotXyz(z);
        return new double[] { 116.0 * fy - 16.0, 500.0 * (fx - fy), 200.0 * (fy - fz) };
    }

    private static double pivotRgb(int component) {
        double c = component / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    private static double pivotXyz(double component) {
        return component > 0.008856 ? Math.cbrt(component) : (7.787 * component) + (16.0 / 116.0);
    }
}
