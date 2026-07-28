package com.fitlab.backend.dto;

import com.fitlab.backend.matching.ScoringConfig;

import java.util.Set;

public record ScoringConfigDto(
        double colorWeight,
        double vibeWeight,
        int neutralColorThreshold,
        Set<String> neutralColors,
        boolean sharedVibeReasonEnabled,
        boolean sharedColorReasonEnabled,
        boolean neutralCounterbalanceReasonEnabled,
        double profileWeight
) {
    public static ScoringConfigDto from(ScoringConfig config) {
        return new ScoringConfigDto(
                config.colorWeight(),
                config.vibeWeight(),
                config.neutralColorThreshold(),
                config.neutralColors(),
                config.sharedVibeReasonEnabled(),
                config.sharedColorReasonEnabled(),
                config.neutralCounterbalanceReasonEnabled(),
                config.profileWeight()
        );
    }
}
