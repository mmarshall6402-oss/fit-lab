package com.fitlab.backend.dto;

import java.util.List;

public record OutfitDto(
        ItemDto shirt,
        ItemDto bottom,
        ItemDto shoes,
        double score,
        List<String> reasons
) {
}
