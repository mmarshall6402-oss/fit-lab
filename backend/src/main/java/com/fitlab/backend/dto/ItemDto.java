package com.fitlab.backend.dto;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;

import java.util.Set;
import java.util.UUID;

public record ItemDto(
        UUID id,
        String name,
        Category category,
        String imageUrl,
        Set<String> colors,
        Set<String> vibes
) {
    public static ItemDto from(Item item) {
        return new ItemDto(
                item.getId(),
                item.getName(),
                item.getCategory(),
                item.getImageUrl(),
                item.getColors(),
                item.getVibes()
        );
    }
}
