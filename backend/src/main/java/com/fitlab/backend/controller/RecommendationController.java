package com.fitlab.backend.controller;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.dto.FullRecommendationDto;
import com.fitlab.backend.dto.RecommendationDto;
import com.fitlab.backend.service.RecommendationService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class RecommendationController {

    private final RecommendationService recommendationService;

    public RecommendationController(RecommendationService recommendationService) {
        this.recommendationService = recommendationService;
    }

    @GetMapping("/recommend")
    public List<RecommendationDto> recommend(@RequestParam UUID anchorId, @RequestParam Category category) {
        return recommendationService.recommend(anchorId, category);
    }

    @GetMapping("/recommend/full")
    public FullRecommendationDto recommendFull(@RequestParam UUID shirtId) {
        return recommendationService.recommendFull(shirtId);
    }
}
