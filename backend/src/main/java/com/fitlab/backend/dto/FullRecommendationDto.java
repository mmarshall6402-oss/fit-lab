package com.fitlab.backend.dto;

import java.util.List;

/** Response for /recommend/full: a shirt plus ranked bottoms and shoes. */
public record FullRecommendationDto(
        ItemDto shirt,
        List<RecommendationDto> bottoms,
        List<RecommendationDto> shoes
) {
}
