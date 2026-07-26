package com.fitlab.backend.matching;

import java.util.Set;

/**
 * Live-tunable knobs behind outfit scoring. Immutable snapshot handed out by a
 * ScoringConfigProvider so callers never see a config mutate mid-calculation.
 */
public record ScoringConfig(
        double colorWeight,
        double vibeWeight,
        int neutralColorThreshold,
        Set<String> neutralColors,
        boolean sharedVibeReasonEnabled,
        boolean sharedColorReasonEnabled,
        boolean neutralCounterbalanceReasonEnabled
) {
    public static ScoringConfig defaults() {
        return new ScoringConfig(
                3.0,
                2.0,
                3,
                Set.of("black", "white", "grey", "gray", "beige", "tan", "navy", "cream", "khaki"),
                true,
                true,
                true
        );
    }
}
