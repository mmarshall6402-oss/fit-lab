package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Comment;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.domain.SavedOutfit;
import com.fitlab.backend.dto.CommentDto;
import com.fitlab.backend.dto.CreateCommentRequest;
import com.fitlab.backend.dto.SaveOutfitRequest;
import com.fitlab.backend.dto.SavedOutfitDto;
import com.fitlab.backend.exception.SavedOutfitNotFoundException;
import com.fitlab.backend.matching.TagMatcher;
import com.fitlab.backend.repository.CommentRepository;
import com.fitlab.backend.repository.ItemRepository;
import com.fitlab.backend.repository.SavedOutfitRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SavedOutfitServiceTest {

    @Mock
    private SavedOutfitRepository savedOutfitRepository;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private ItemRepository itemRepository;

    private final OutfitScoringService scoringService = new OutfitScoringService(new TagMatcher());

    private Item item(String name, Category category, Set<String> colors, Set<String> vibes) {
        return Item.builder().id(UUID.randomUUID()).name(name).category(category)
                .imageUrl("/uploads/" + name + ".jpg").colors(colors).vibes(vibes).build();
    }

    private SavedOutfitService service() {
        return new SavedOutfitService(savedOutfitRepository, commentRepository, itemRepository, scoringService);
    }

    @Test
    void savesASnapshotOfTheOutfitAtSaveTime() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black"), Set.of("street"));
        Item bottom = item("bottom", Category.BOTTOM, Set.of("black"), Set.of("street"));
        Item shoes = item("shoes", Category.SHOES, Set.of("black"), Set.of("street"));
        when(itemRepository.findById(shirt.getId())).thenReturn(Optional.of(shirt));
        when(itemRepository.findById(bottom.getId())).thenReturn(Optional.of(bottom));
        when(itemRepository.findById(shoes.getId())).thenReturn(Optional.of(shoes));
        when(savedOutfitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countBySavedOutfitId(any())).thenReturn(0L);

        SavedOutfitDto dto = service().save(new SaveOutfitRequest(shirt.getId(), bottom.getId(), shoes.getId()));

        assertThat(dto.shirtName()).isEqualTo("shirt");
        assertThat(dto.shirtImageUrl()).isEqualTo(shirt.getImageUrl());
        assertThat(dto.score()).isEqualTo(100.0);
        assertThat(dto.likeCount()).isZero();
        assertThat(dto.reasons()).isNotEmpty();
    }

    @Test
    void rejectsAnIdFromTheWrongCategory() {
        Item shirt = item("shirt", Category.SHIRT, Set.of("black"), Set.of());
        Item anotherShirt = item("shirt2", Category.SHIRT, Set.of("black"), Set.of());
        when(itemRepository.findById(shirt.getId())).thenReturn(Optional.of(shirt));
        when(itemRepository.findById(anotherShirt.getId())).thenReturn(Optional.of(anotherShirt));

        assertThatThrownBy(() -> service().save(new SaveOutfitRequest(shirt.getId(), anotherShirt.getId(), UUID.randomUUID())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void likeIncrementsCount() {
        SavedOutfit outfit = SavedOutfit.builder().id(UUID.randomUUID()).likeCount(3).reasons(List.of()).build();
        when(savedOutfitRepository.findById(outfit.getId())).thenReturn(Optional.of(outfit));
        when(savedOutfitRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(commentRepository.countBySavedOutfitId(any())).thenReturn(0L);

        SavedOutfitDto dto = service().like(outfit.getId());

        assertThat(dto.likeCount()).isEqualTo(4);
    }

    @Test
    void likeThrowsWhenOutfitMissing() {
        UUID missingId = UUID.randomUUID();
        when(savedOutfitRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().like(missingId)).isInstanceOf(SavedOutfitNotFoundException.class);
    }

    @Test
    void addCommentDefaultsAuthorToAnonymousWhenBlank() {
        SavedOutfit outfit = SavedOutfit.builder().id(UUID.randomUUID()).reasons(List.of()).build();
        when(savedOutfitRepository.findById(outfit.getId())).thenReturn(Optional.of(outfit));
        ArgumentCaptor<Comment> captor = ArgumentCaptor.forClass(Comment.class);
        when(commentRepository.save(captor.capture())).thenAnswer(inv -> inv.getArgument(0));

        CommentDto dto = service().addComment(outfit.getId(), new CreateCommentRequest("fire fit", "  "));

        assertThat(dto.author()).isEqualTo("Anonymous");
        assertThat(captor.getValue().getSavedOutfitId()).isEqualTo(outfit.getId());
    }

    @Test
    void addCommentThrowsWhenOutfitMissing() {
        UUID missingId = UUID.randomUUID();
        when(savedOutfitRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().addComment(missingId, new CreateCommentRequest("hi", "me")))
                .isInstanceOf(SavedOutfitNotFoundException.class);
    }
}
