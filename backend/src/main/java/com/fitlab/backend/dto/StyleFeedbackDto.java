package com.fitlab.backend.dto;

import com.fitlab.backend.domain.FeedbackTarget;
import com.fitlab.backend.domain.InputMethod;
import com.fitlab.backend.domain.Sentiment;
import com.fitlab.backend.domain.StyleFeedback;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record StyleFeedbackDto(
        UUID id,
        FeedbackTarget target,
        UUID itemId,
        UUID shirtId,
        UUID bottomId,
        UUID shoesId,
        InputMethod inputMethod,
        Sentiment sentiment,
        String rawText,
        boolean extractionSucceeded,
        List<String> extractedLikes,
        List<String> extractedDislikes,
        List<String> extractedStyleTags,
        String extractedReasoning,
        Instant createdAt
) {
    public static StyleFeedbackDto from(StyleFeedback entity) {
        return new StyleFeedbackDto(
                entity.getId(),
                entity.getTarget(),
                entity.getItemId(),
                entity.getShirtId(),
                entity.getBottomId(),
                entity.getShoesId(),
                entity.getInputMethod(),
                entity.getSentiment(),
                entity.getRawText(),
                entity.isExtractionSucceeded(),
                List.copyOf(entity.getExtractedLikes()),
                List.copyOf(entity.getExtractedDislikes()),
                List.copyOf(entity.getExtractedStyleTags()),
                entity.getExtractedReasoning(),
                entity.getCreatedAt()
        );
    }
}
