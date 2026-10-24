package com.asp.platform.catalogue.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/// Spreads a package price over its items, so the work order lines add up to exactly the package price.
public final class PackagePricing {

    private PackagePricing() {
    }

    /// Splits `packageCents` in proportion to `weights` (each item's own price times its quantity), rounding half up
    /// and putting the remainder on the last share. When all weights are zero the price is split evenly.
    ///
    /// @return one share per weight, in the same order, adding up to `packageCents`
    public static List<Long> allocate(long packageCents, List<Long> weights) {
        return allocate(packageCents, weights, Collections.nCopies(weights.size(), 1));
    }

    /// Like [#allocate(long, List)], but tries to make each share a whole multiple of its `granularity` (the quantity
    /// of a product, so its unit price comes out in whole cents). Each share is rounded half up to the nearest
    /// multiple of its granularity; the remainder goes on the last share whose granularity is 1 (a service or a single
    /// product), or on the last share when there is none, which then may not divide evenly (see [#split]).
    /// With every granularity 1 this is the plain proportional split.
    ///
    /// @return one share per weight, in the same order, adding up to `packageCents`
    public static List<Long> allocate(long packageCents, List<Long> weights, List<Integer> granularities) {
        if (weights.isEmpty()) {
            throw new IllegalArgumentException("Nothing to allocate to");
        }
        if (granularities.size() != weights.size() || granularities.stream().anyMatch(g -> g < 1)) {
            throw new IllegalArgumentException("One granularity of at least 1 per weight");
        }
        int sink = granularities.lastIndexOf(1);
        if (sink < 0) {
            sink = weights.size() - 1;
        }
        long total = weights.stream().mapToLong(Long::longValue).sum();
        BigDecimal price = BigDecimal.valueOf(packageCents);
        Long[] shares = new Long[weights.size()];
        long given = 0;
        for (int i = 0; i < weights.size(); i++) {
            if (i == sink) {
                continue;
            }
            long g = granularities.get(i);
            BigDecimal units = total == 0
                    ? price.divide(BigDecimal.valueOf(weights.size() * g), 0, RoundingMode.HALF_UP)
                    : price.multiply(BigDecimal.valueOf(weights.get(i)))
                            .divide(BigDecimal.valueOf(total).multiply(BigDecimal.valueOf(g)), 0,
                                    RoundingMode.HALF_UP);
            long left = packageCents - given;
            long cents = Math.min(units.longValueExact() * g, left - left % g);
            shares[i] = cents;
            given += cents;
        }
        shares[sink] = packageCents - given;
        return List.of(shares);
    }

    /// Puts a share on lines of whole cents per unit: one portion when it divides evenly by the quantity, otherwise
    /// two (some units one cent dearer), which add up to exactly the share.
    ///
    /// @return one or two portions whose quantities add up to `quantity` and whose totals add up to `shareCents`
    public static List<Portion> split(long shareCents, int quantity) {
        if (quantity < 1 || shareCents < 0) {
            throw new IllegalArgumentException("Quantity must be positive and the share not negative");
        }
        long unit = shareCents / quantity;
        int dearer = (int) (shareCents % quantity);
        List<Portion> portions = new ArrayList<>(2);
        portions.add(new Portion(quantity - dearer, unit));
        if (dearer > 0) {
            portions.add(new Portion(dearer, unit + 1));
        }
        return List.copyOf(portions);
    }

    /// `quantity` units at `unitCents` each.
    public record Portion(int quantity, long unitCents) {
    }
}
