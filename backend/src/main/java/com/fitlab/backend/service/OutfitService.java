package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.exception.InsufficientCatalogException;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * Builds the best-scoring complete outfit that includes a given anchor item.
 * Brute-forces every shirt x bottom x shoes combination - the catalog is small
 * enough today that this is simpler and more correct than any heuristic search.
 */
@Service
public class OutfitService {

    private final ItemRepository itemRepository;
    private final OutfitScoringService scoringService;

    public OutfitService(ItemRepository itemRepository, OutfitScoringService scoringService) {
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
    }

    public OutfitDto buildBest(UUID anchorId) {
        Item anchor = itemRepository.findById(anchorId).orElseThrow(() -> new ItemNotFoundException(anchorId));

        List<Item> shirts = candidatesFor(Category.SHIRT, anchor);
        List<Item> bottoms = candidatesFor(Category.BOTTOM, anchor);
        List<Item> shoes = candidatesFor(Category.SHOES, anchor);

        Item bestShirt = null;
        Item bestBottom = null;
        Item bestShoes = null;
        double bestScore = -1;

        for (Item shirt : shirts) {
            for (Item bottom : bottoms) {
                for (Item shoe : shoes) {
                    double score = scoringService.holisticScore(shirt, bottom, shoe);
                    if (score > bestScore) {
                        bestScore = score;
                        bestShirt = shirt;
                        bestBottom = bottom;
                        bestShoes = shoe;
                    }
                }
            }
        }

        return new OutfitDto(
                ItemDto.from(bestShirt),
                ItemDto.from(bestBottom),
                ItemDto.from(bestShoes),
                bestScore,
                scoringService.reasons(bestShirt, bestBottom, bestShoes)
        );
    }

    /** Scores a specific user-picked triple (manual mix-and-match), rather than searching the catalog. */
    public OutfitDto score(UUID shirtId, UUID bottomId, UUID shoesId) {
        Item shirt = getByCategory(shirtId, Category.SHIRT);
        Item bottom = getByCategory(bottomId, Category.BOTTOM);
        Item shoes = getByCategory(shoesId, Category.SHOES);

        return new OutfitDto(
                ItemDto.from(shirt),
                ItemDto.from(bottom),
                ItemDto.from(shoes),
                scoringService.holisticScore(shirt, bottom, shoes),
                scoringService.reasons(shirt, bottom, shoes)
        );
    }

    private Item getByCategory(UUID id, Category expected) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        if (item.getCategory() != expected) {
            throw new IllegalArgumentException(expected + " id does not refer to a " + expected + " item: " + id);
        }
        return item;
    }

    private List<Item> candidatesFor(Category category, Item anchor) {
        List<Item> candidates = anchor.getCategory() == category
                ? List.of(anchor)
                : itemRepository.findByCategory(category);
        if (candidates.isEmpty()) {
            throw new InsufficientCatalogException("No items available in category " + category + " to build an outfit");
        }
        return candidates;
    }
}
