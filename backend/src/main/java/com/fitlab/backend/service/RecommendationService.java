package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.FullRecommendationDto;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.RecommendationDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class RecommendationService {

    private final ItemRepository itemRepository;
    private final MatchService matchService;

    public RecommendationService(ItemRepository itemRepository, MatchService matchService) {
        this.itemRepository = itemRepository;
        this.matchService = matchService;
    }

    @Transactional(readOnly = true)
    public List<RecommendationDto> recommend(UUID anchorId, Category category) {
        Item anchor = itemRepository.findById(anchorId).orElseThrow(() -> new ItemNotFoundException(anchorId));
        return matchService.rank(anchor, itemRepository.findByCategory(category));
    }

    @Transactional(readOnly = true)
    public FullRecommendationDto recommendFull(UUID shirtId) {
        Item shirt = itemRepository.findById(shirtId).orElseThrow(() -> new ItemNotFoundException(shirtId));
        List<RecommendationDto> bottoms = matchService.rank(shirt, itemRepository.findByCategory(Category.BOTTOM));
        List<RecommendationDto> shoes = matchService.rank(shirt, itemRepository.findByCategory(Category.SHOES));
        return new FullRecommendationDto(ItemDto.from(shirt), bottoms, shoes);
    }
}
