package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.exception.InsufficientCatalogException;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
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
    private final Random random;

    @Autowired
    public OutfitService(ItemRepository itemRepository, OutfitScoringService scoringService) {
        this(itemRepository, scoringService, new Random());
    }

    /** Package-private: lets tests inject a Random with a fixed nextInt() to make tie-breaking deterministic. */
    OutfitService(ItemRepository itemRepository, OutfitScoringService scoringService, Random random) {
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
        this.random = random;
    }

    public OutfitDto buildBest(UUID anchorId) {
        Item anchor = itemRepository.findById(anchorId).orElseThrow(() -> new ItemNotFoundException(anchorId));

        List<Item> shirts = candidatesFor(Category.SHIRT, anchor);
        List<Item> bottoms = candidatesFor(Category.BOTTOM, anchor);
        List<Item> shoes = candidatesFor(Category.SHOES, anchor);

        // Collect every combo tied for the best score instead of keeping only the
        // first one hit in catalog order - a strict ">" comparison here always
        // favored whichever item came first in findByCategory()'s result order,
        // so ties (common when tags are sparse) silently always resolved the same
        // way regardless of which shirt/bottom was picked. Breaking ties randomly
        // fixes that without changing what counts as "best".
        List<Item[]> bestCombos = new ArrayList<>();
        double bestScore = -1;

        for (Item shirt : shirts) {
            for (Item bottom : bottoms) {
                for (Item shoe : shoes) {
                    double score = scoringService.holisticScore(shirt, bottom, shoe);
                    if (score > bestScore) {
                        bestScore = score;
                        bestCombos.clear();
                        bestCombos.add(new Item[] { shirt, bottom, shoe });
                    } else if (score == bestScore) {
                        bestCombos.add(new Item[] { shirt, bottom, shoe });
                    }
                }
            }
        }

        Item[] chosen = bestCombos.get(random.nextInt(bestCombos.size()));
        Item bestShirt = chosen[0];
        Item bestBottom = chosen[1];
        Item bestShoes = chosen[2];

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
