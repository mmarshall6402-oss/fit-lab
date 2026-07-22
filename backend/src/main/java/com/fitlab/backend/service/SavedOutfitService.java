package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Comment;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.domain.SavedOutfit;
import com.fitlab.backend.dto.CommentDto;
import com.fitlab.backend.dto.CreateCommentRequest;
import com.fitlab.backend.dto.SaveOutfitRequest;
import com.fitlab.backend.dto.SavedOutfitDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.exception.SavedOutfitNotFoundException;
import com.fitlab.backend.repository.CommentRepository;
import com.fitlab.backend.repository.ItemRepository;
import com.fitlab.backend.repository.SavedOutfitRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The community gallery: anyone can save a completed outfit, like it, and comment on it.
 * No accounts - matches the rest of the app being open/no-login. Item name/image are
 * snapshotted at save time so a saved fit keeps rendering correctly even if the underlying
 * catalog item is later edited or deleted.
 */
@Service
public class SavedOutfitService {

    private final SavedOutfitRepository savedOutfitRepository;
    private final CommentRepository commentRepository;
    private final ItemRepository itemRepository;
    private final OutfitScoringService scoringService;

    public SavedOutfitService(SavedOutfitRepository savedOutfitRepository, CommentRepository commentRepository,
                               ItemRepository itemRepository, OutfitScoringService scoringService) {
        this.savedOutfitRepository = savedOutfitRepository;
        this.commentRepository = commentRepository;
        this.itemRepository = itemRepository;
        this.scoringService = scoringService;
    }

    @Transactional
    public SavedOutfitDto save(SaveOutfitRequest request) {
        Item shirt = getByCategory(request.shirtId(), Category.SHIRT);
        Item bottom = getByCategory(request.bottomId(), Category.BOTTOM);
        Item shoes = getByCategory(request.shoesId(), Category.SHOES);

        SavedOutfit outfit = SavedOutfit.builder()
                .id(UUID.randomUUID())
                .shirtId(shirt.getId()).shirtName(shirt.getName()).shirtImageUrl(shirt.getImageUrl())
                .bottomId(bottom.getId()).bottomName(bottom.getName()).bottomImageUrl(bottom.getImageUrl())
                .shoesId(shoes.getId()).shoesName(shoes.getName()).shoesImageUrl(shoes.getImageUrl())
                .score(scoringService.holisticScore(shirt, bottom, shoes))
                .reasons(scoringService.reasons(shirt, bottom, shoes))
                .likeCount(0)
                .createdAt(Instant.now())
                .build();

        return toDto(savedOutfitRepository.save(outfit));
    }

    @Transactional(readOnly = true)
    public List<SavedOutfitDto> listAll() {
        return savedOutfitRepository.findAllByOrderByLikeCountDescCreatedAtDesc().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public SavedOutfitDto get(UUID id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public SavedOutfitDto like(UUID id) {
        SavedOutfit outfit = getOrThrow(id);
        outfit.setLikeCount(outfit.getLikeCount() + 1);
        return toDto(savedOutfitRepository.save(outfit));
    }

    @Transactional(readOnly = true)
    public List<CommentDto> listComments(UUID savedOutfitId) {
        getOrThrow(savedOutfitId);
        return commentRepository.findBySavedOutfitIdOrderByCreatedAtAsc(savedOutfitId).stream()
                .map(CommentDto::from).toList();
    }

    @Transactional
    public CommentDto addComment(UUID savedOutfitId, CreateCommentRequest request) {
        getOrThrow(savedOutfitId);
        String author = (request.author() == null || request.author().isBlank())
                ? "Anonymous" : request.author().trim();
        Comment comment = new Comment(UUID.randomUUID(), savedOutfitId, author, request.body().trim(), Instant.now());
        return CommentDto.from(commentRepository.save(comment));
    }

    private SavedOutfit getOrThrow(UUID id) {
        return savedOutfitRepository.findById(id).orElseThrow(() -> new SavedOutfitNotFoundException(id));
    }

    private Item getByCategory(UUID id, Category expected) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        if (item.getCategory() != expected) {
            throw new IllegalArgumentException(expected + " id does not refer to a " + expected + " item: " + id);
        }
        return item;
    }

    private SavedOutfitDto toDto(SavedOutfit outfit) {
        long commentCount = commentRepository.countBySavedOutfitId(outfit.getId());
        return SavedOutfitDto.from(outfit, commentCount);
    }
}
