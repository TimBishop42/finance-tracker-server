package com.bishop.FinanceTracker.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class CategoryColorsTest {

    @Test
    public void twentyCategoriesGetUniqueWellSeparatedColours() {
        List<String> used = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            String next = CategoryColors.mostDistinct(used);
            assertTrue(next.matches("#[0-9a-f]{6}"), next);
            assertTrue(!used.contains(next), "duplicate " + next);
            used.add(next);
        }
        double closest = Double.MAX_VALUE;
        for (int i = 0; i < used.size(); i++) {
            for (int j = i + 1; j < used.size(); j++) {
                closest = Math.min(closest,
                        CategoryColors.distance(CategoryColors.oklab(used.get(i)), CategoryColors.oklab(used.get(j))));
            }
        }
        // ~0.1 in OKLab is a clearly visible difference; greedy max-min should stay well clear of near-duplicates.
        assertTrue(closest > 0.08, "closest pair only " + closest);
    }

    @Test
    public void ignoresNullAndMalformedExistingColours() {
        List<String> used = new ArrayList<>();
        used.add(null);
        used.add("not-a-colour");
        assertTrue(CategoryColors.mostDistinct(used).startsWith("#"));
    }
}
