package com.fitlab.backend.dto;

import com.fitlab.backend.domain.SavedOutfit;

import java.time.Instant;
import java.util.UUID;

public record SavedOutfitDto(
        UUID id,
        UUID shirtId,
        UUID bottomId,
        UUID shoesId,
        double score,
        Instant createdAt
) {
    public static SavedOutfitDto from(SavedOutfit entity) {
        return new SavedOutfitDto(
                entity.getId(),
                entity.getShirtId(),
                entity.getBottomId(),
                entity.getShoesId(),
                entity.getScore(),
                entity.getCreatedAt()
        );
    }
}
