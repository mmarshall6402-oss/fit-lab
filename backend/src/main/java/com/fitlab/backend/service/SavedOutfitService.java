package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.domain.SavedOutfit;
import com.fitlab.backend.dto.SavedOutfitDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.exception.SavedOutfitNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import com.fitlab.backend.repository.SavedOutfitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Persists a user's explicit "save this fit" action - a specific
 * shirt+bottom+shoes combination and its score at save time. Independent of
 * StyleFeedbackService: saving a fit needs no reasoning text, just a record
 * you can browse later.
 */
@Service
public class SavedOutfitService {

    private final SavedOutfitRepository savedOutfitRepository;
    private final ItemRepository itemRepository;
    private final OutfitScoringService scoringService;

    public SavedOutfitService(
            SavedOutfitRepository savedOutfitRepository,
            ItemRepository itemRepository,
            OutfitScoringService scoringService
    ) {
        this.savedOutfitRepository = savedOutfitRepository;
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
    }

    @Transactional
    public SavedOutfitDto save(UUID ownerId, UUID shirtId, UUID bottomId, UUID shoesId) {
        Item shirt = getByCategory(ownerId, shirtId, Category.SHIRT);
        Item bottom = getByCategory(ownerId, bottomId, Category.BOTTOM);
        Item shoes = getByCategory(ownerId, shoesId, Category.SHOES);

        // Score is recomputed server-side, never trusted from the client, and
        // snapshotted here rather than recomputed on read - see class javadoc.
        double score = scoringService.holisticScore(ownerId, shirt, bottom, shoes);

        SavedOutfit saved = savedOutfitRepository.save(SavedOutfit.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .shirtId(shirtId)
                .bottomId(bottomId)
                .shoesId(shoesId)
                .score(score)
                .createdAt(Instant.now())
                .build());

        return SavedOutfitDto.from(saved);
    }

    @Transactional(readOnly = true)
    public List<SavedOutfitDto> getAll(UUID ownerId) {
        return savedOutfitRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream()
                .map(SavedOutfitDto::from).toList();
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        SavedOutfit outfit = savedOutfitRepository.findByIdAndOwnerId(id, ownerId)
                .orElseThrow(() -> new SavedOutfitNotFoundException(id));
        savedOutfitRepository.delete(outfit);
    }

    private Item getByCategory(UUID ownerId, UUID id, Category expected) {
        Item item = itemRepository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new ItemNotFoundException(id));
        if (item.getCategory() != expected) {
            throw new IllegalArgumentException(expected + " id does not refer to a " + expected + " item: " + id);
        }
        return item;
    }
}
