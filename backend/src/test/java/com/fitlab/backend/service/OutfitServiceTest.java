package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.exception.InsufficientCatalogException;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.matching.TagMatcher;
import com.fitlab.backend.repository.ItemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OutfitServiceTest {

    @Mock
    private ItemRepository itemRepository;

    private final OutfitScoringService scoringService = new OutfitScoringService(new TagMatcher());

    private Item item(String name, Category category, Set<String> colors, Set<String> vibes) {
        return Item.builder().id(UUID.randomUUID()).name(name).category(category).colors(colors).vibes(vibes).build();
    }

    @Test
    void picksTheHighestScoringCombinationAcrossAllCandidates() {
        Item anchorShirt = item("anchor shirt", Category.SHIRT, Set.of("black", "red"), Set.of("street"));
        Item strongBottom = item("strong bottom", Category.BOTTOM, Set.of("black", "red"), Set.of("street"));
        Item weakBottom = item("weak bottom", Category.BOTTOM, Set.of("green"), Set.of());
        Item strongShoes = item("strong shoes", Category.SHOES, Set.of("black"), Set.of("street"));
        Item weakShoes = item("weak shoes", Category.SHOES, Set.of("yellow"), Set.of());

        OutfitService outfitService = new OutfitService(itemRepository, scoringService);
        when(itemRepository.findById(anchorShirt.getId())).thenReturn(Optional.of(anchorShirt));
        when(itemRepository.findByCategory(Category.BOTTOM)).thenReturn(List.of(strongBottom, weakBottom));
        when(itemRepository.findByCategory(Category.SHOES)).thenReturn(List.of(strongShoes, weakShoes));

        OutfitDto outfit = outfitService.buildBest(anchorShirt.getId());

        assertThat(outfit.shirt().id()).isEqualTo(anchorShirt.getId());
        assertThat(outfit.bottom().id()).isEqualTo(strongBottom.getId());
        assertThat(outfit.shoes().id()).isEqualTo(strongShoes.getId());
        assertThat(outfit.score()).isEqualTo(100.0);
        assertThat(outfit.reasons()).isNotEmpty();
    }

    @Test
    void throwsWhenAnchorDoesNotExist() {
        OutfitService outfitService = new OutfitService(itemRepository, scoringService);
        UUID missingId = UUID.randomUUID();
        when(itemRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> outfitService.buildBest(missingId))
                .isInstanceOf(ItemNotFoundException.class);
    }

    @Test
    void throwsWhenACategoryHasNoItemsToChooseFrom() {
        Item anchorShirt = item("anchor shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        OutfitService outfitService = new OutfitService(itemRepository, scoringService);
        when(itemRepository.findById(anchorShirt.getId())).thenReturn(Optional.of(anchorShirt));
        when(itemRepository.findByCategory(Category.BOTTOM)).thenReturn(List.of());

        assertThatThrownBy(() -> outfitService.buildBest(anchorShirt.getId()))
                .isInstanceOf(InsufficientCatalogException.class);
    }
}
