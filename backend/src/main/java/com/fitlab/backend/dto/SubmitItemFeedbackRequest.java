package com.fitlab.backend.dto;

import com.fitlab.backend.domain.InputMethod;
import com.fitlab.backend.domain.Sentiment;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SubmitItemFeedbackRequest(
        @NotBlank String rawText,
        @NotNull InputMethod inputMethod,
        @NotNull Sentiment sentiment
) {
}
