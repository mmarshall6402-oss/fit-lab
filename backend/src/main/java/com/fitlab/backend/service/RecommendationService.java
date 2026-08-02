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
    public List<RecommendationDto> recommend(UUID ownerId, UUID anchorId, Category category) {
        Item anchor = itemRepository.findByIdAndOwnerId(anchorId, ownerId).orElseThrow(() -> new ItemNotFoundException(anchorId));
        return matchService.rank(ownerId, anchor, itemRepository.findByOwnerIdAndCategory(ownerId, category));
    }

    @Transactional(readOnly = true)
    public FullRecommendationDto recommendFull(UUID ownerId, UUID shirtId) {
        Item shirt = itemRepository.findByIdAndOwnerId(shirtId, ownerId).orElseThrow(() -> new ItemNotFoundException(shirtId));
        List<RecommendationDto> bottoms = matchService.rank(ownerId, shirt, itemRepository.findByOwnerIdAndCategory(ownerId, Category.BOTTOM));
        List<RecommendationDto> shoes = matchService.rank(ownerId, shirt, itemRepository.findByOwnerIdAndCategory(ownerId, Category.SHOES));
        return new FullRecommendationDto(ItemDto.from(shirt), bottoms, shoes);
    }
}
