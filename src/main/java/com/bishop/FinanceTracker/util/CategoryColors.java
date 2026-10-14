package com.bishop.FinanceTracker.util;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Picks category chart colours that stay as visually distinct as possible: each new colour is the
 * candidate whose nearest already-used colour is furthest away in OKLab (a perceptual colour space).
 */
public final class CategoryColors {

    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");

    // Hue every 10°, three saturation/brightness tiers that read on both light and dark backgrounds.
    // Saturation stays high so nothing lands near the grey used for "Other".
    private static final float[][] TIERS = {{0.70f, 0.85f}, {0.50f, 0.95f}, {0.85f, 0.60f}};
    private static final List<String> CANDIDATES = buildCandidates();

    private CategoryColors() {
    }

    public static String mostDistinct(Collection<String> used) {
        List<double[]> usedLab = used.stream()
                .filter(Objects::nonNull)
                .filter(c -> HEX.matcher(c).matches())
                .map(CategoryColors::oklab)
                .toList();
        String best = CANDIDATES.get(0);
        double bestDistance = -1;
        for (String candidate : CANDIDATES) {
            double[] lab = oklab(candidate);
            double nearest = usedLab.stream().mapToDouble(u -> distance(u, lab)).min().orElse(Double.MAX_VALUE);
            if (nearest > bestDistance) {
                bestDistance = nearest;
                best = candidate;
            }
        }
        return best;
    }

    private static List<String> buildCandidates() {
        List<String> out = new ArrayList<>();
        for (float[] tier : TIERS) {
            for (int hue = 0; hue < 360; hue += 10) {
                out.add(String.format("#%06x", Color.HSBtoRGB(hue / 360f, tier[0], tier[1]) & 0xFFFFFF));
            }
        }
        return out;
    }

    static double[] oklab(String hex) {
        int rgb = Integer.parseInt(hex.substring(1), 16);
        double r = linear((rgb >> 16) & 0xFF);
        double g = linear((rgb >> 8) & 0xFF);
        double b = linear(rgb & 0xFF);
        double l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
        double m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
        double s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
        return new double[]{
                0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
                1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
                0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s
        };
    }

    private static double linear(int channel) {
        double c = channel / 255.0;
        return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
    }

    static double distance(double[] a, double[] b) {
        return Math.sqrt(Math.pow(a[0] - b[0], 2) + Math.pow(a[1] - b[1], 2) + Math.pow(a[2] - b[2], 2));
    }
}
