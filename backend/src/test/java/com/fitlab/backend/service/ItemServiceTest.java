package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.repository.ItemProfileAffinityRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    @Mock
    private ImageTaggingService imageTaggingService;

    @Mock
    private ItemProfileAffinityRepository affinityRepository;

    private final UUID owner = UUID.randomUUID();

    private ItemService itemService() {
        return new ItemService(itemRepository, imageTaggingService, affinityRepository);
    }

    private Item item(Set<String> colors, Set<String> vibes) {
        return Item.builder()
                .id(UUID.randomUUID())
                .name("wick tee")
                .category(Category.SHIRT)
                .colors(colors)
                .vibes(vibes)
                .build();
    }

    @Test
    void fillsTagsFromSuggestionWhenItemHasNoTags() {
        Item untagged = item(Set.of(), Set.of());
        when(itemRepository.findByIdAndOwnerId(untagged.getId(), owner)).thenReturn(Optional.of(untagged));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageTaggingService.suggestTags(any(), any(), any(), any()))
                .thenReturn(Optional.of(new TagSuggestion(List.of("Black", "White"), List.of("Street"))));

        ItemDto result = itemService().autoTagIfUntagged(owner, untagged.getId(), new byte[] {1, 2, 3}, "image/png");

        assertThat(result.colors()).containsExactlyInAnyOrder("black", "white");
        assertThat(result.vibes()).containsExactly("street");
    }

    @Test
    void skipsTaggingWhenItemAlreadyHasColors() {
        Item tagged = item(Set.of("beige"), Set.of());
        when(itemRepository.findByIdAndOwnerId(tagged.getId(), owner)).thenReturn(Optional.of(tagged));

        ItemDto result = itemService().autoTagIfUntagged(owner, tagged.getId(), new byte[] {1}, "image/png");

        assertThat(result.colors()).containsExactly("beige");
        verify(imageTaggingService, never()).suggestTags(any(), any(), any(), any());
    }

    @Test
    void skipsTaggingWhenItemAlreadyHasVibes() {
        Item tagged = item(Set.of(), Set.of("clean"));
        when(itemRepository.findByIdAndOwnerId(tagged.getId(), owner)).thenReturn(Optional.of(tagged));

        itemService().autoTagIfUntagged(owner, tagged.getId(), new byte[] {1}, "image/png");

        verify(imageTaggingService, never()).suggestTags(any(), any(), any(), any());
    }

    @Test
    void leavesItemUntaggedWhenSuggestionServiceReturnsEmpty() {
        Item untagged = item(Set.of(), Set.of());
        when(itemRepository.findByIdAndOwnerId(untagged.getId(), owner)).thenReturn(Optional.of(untagged));
        when(itemRepository.save(any(Item.class))).thenAnswer(inv -> inv.getArgument(0));
        when(imageTaggingService.suggestTags(any(), any(), any(), any())).thenReturn(Optional.empty());

        ItemDto result = itemService().autoTagIfUntagged(owner, untagged.getId(), new byte[] {1}, "image/png");

        assertThat(result.colors()).isEmpty();
        assertThat(result.vibes()).isEmpty();
    }
}
