package com.fitlab.backend.dto;

import com.fitlab.backend.domain.SavedOutfit;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SavedOutfitDto(
        UUID id,
        UUID shirtId,
        String shirtName,
        String shirtImageUrl,
        UUID bottomId,
        String bottomName,
        String bottomImageUrl,
        UUID shoesId,
        String shoesName,
        String shoesImageUrl,
        double score,
        List<String> reasons,
        int likeCount,
        long commentCount,
        Instant createdAt
) {
    public static SavedOutfitDto from(SavedOutfit outfit, long commentCount) {
        return new SavedOutfitDto(
                outfit.getId(),
                outfit.getShirtId(), outfit.getShirtName(), outfit.getShirtImageUrl(),
                outfit.getBottomId(), outfit.getBottomName(), outfit.getBottomImageUrl(),
                outfit.getShoesId(), outfit.getShoesName(), outfit.getShoesImageUrl(),
                outfit.getScore(),
                outfit.getReasons(),
                outfit.getLikeCount(),
                commentCount,
                outfit.getCreatedAt()
        );
    }
}
