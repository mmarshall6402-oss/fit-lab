package com.fitlab.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.util.Set;

/**
 * Inbound admin payload for live-calibrating outfit scoring. All fields are
 * required so a save always writes a complete, self-consistent config.
 */
public record UpdateScoringConfigRequest(
        @PositiveOrZero double colorWeight,
        @PositiveOrZero double vibeWeight,
        @PositiveOrZero int neutralColorThreshold,
        @NotNull Set<String> neutralColors,
        boolean sharedVibeReasonEnabled,
        boolean sharedColorReasonEnabled,
        boolean neutralCounterbalanceReasonEnabled,
        @PositiveOrZero double profileWeight
) {
}
