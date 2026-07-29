package com.fitlab.backend.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SaveOutfitRequest(
        @NotNull UUID shirtId,
        @NotNull UUID bottomId,
        @NotNull UUID shoesId
) {
}
