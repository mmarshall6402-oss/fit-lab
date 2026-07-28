package com.fitlab.backend.dto;

import com.fitlab.backend.domain.StyleProfileEntity;

import java.time.Instant;
import java.util.List;

public record StyleProfileDto(
        List<String> likes,
        List<String> styleTags,
        String reasoningSummary,
        int feedbackCount,
        Instant updatedAt
) {
    public static StyleProfileDto from(StyleProfileEntity entity) {
        return new StyleProfileDto(
                List.copyOf(entity.getLikes()),
                List.copyOf(entity.getStyleTags()),
                entity.getReasoningSummary() == null ? "" : entity.getReasoningSummary(),
                entity.getFeedbackCount(),
                entity.getUpdatedAt()
        );
    }
}
