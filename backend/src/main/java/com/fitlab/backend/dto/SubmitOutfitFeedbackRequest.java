package com.fitlab.backend.dto;

import com.fitlab.backend.domain.InputMethod;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitOutfitFeedbackRequest(
        @NotNull UUID shirtId,
        @NotNull UUID bottomId,
        @NotNull UUID shoesId,
        @NotBlank String rawText,
        @NotNull InputMethod inputMethod
) {
}
