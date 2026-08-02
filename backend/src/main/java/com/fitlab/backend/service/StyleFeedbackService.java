package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.FeedbackTarget;
import com.fitlab.backend.domain.InputMethod;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.domain.Sentiment;
import com.fitlab.backend.domain.StyleFeedback;
import com.fitlab.backend.domain.StyleProfileEntity;
import com.fitlab.backend.dto.StyleFeedbackDto;
import com.fitlab.backend.dto.StyleProfileDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import com.fitlab.backend.repository.StyleFeedbackRepository;
import com.fitlab.backend.repository.StyleProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Captures raw style feedback (why a user likes or dislikes an item or an
 * outfit combo), extracts structured preferences from it, and merges those
 * into a single evolving style profile. That profile feeds into scoring via
 * ProfileAffinityService, which reads it back to judge how well an item
 * aligns with what's been captured here.
 */
@Service
public class StyleFeedbackService {

    private static final int MAX_REASONING_LINES = 20;

    private final StyleFeedbackRepository feedbackRepository;
    private final StyleProfileRepository profileRepository;
    private final StyleExtractionService extractionService;
    private final ItemRepository itemRepository;

    public StyleFeedbackService(
            StyleFeedbackRepository feedbackRepository,
            StyleProfileRepository profileRepository,
            StyleExtractionService extractionService,
            ItemRepository itemRepository
    ) {
        this.feedbackRepository = feedbackRepository;
        this.profileRepository = profileRepository;
        this.extractionService = extractionService;
        this.itemRepository = itemRepository;
    }

    @Transactional
    public StyleFeedbackDto submitForItem(UUID ownerId, UUID itemId, String rawText, InputMethod inputMethod, Sentiment sentiment) {
        requireItem(ownerId, itemId, null);
        return submit(ownerId, FeedbackTarget.ITEM, itemId, null, null, null, rawText, inputMethod, sentiment);
    }

    @Transactional
    public StyleFeedbackDto submitForOutfit(
            UUID ownerId, UUID shirtId, UUID bottomId, UUID shoesId, String rawText, InputMethod inputMethod, Sentiment sentiment
    ) {
        requireItem(ownerId, shirtId, Category.SHIRT);
        requireItem(ownerId, bottomId, Category.BOTTOM);
        requireItem(ownerId, shoesId, Category.SHOES);
        return submit(ownerId, FeedbackTarget.OUTFIT, null, shirtId, bottomId, shoesId, rawText, inputMethod, sentiment);
    }

    @Transactional(readOnly = true)
    public StyleProfileDto getProfile(UUID ownerId) {
        return StyleProfileDto.from(loadOrInitializeProfile(ownerId));
    }

    @Transactional(readOnly = true)
    public List<StyleFeedbackDto> getHistory(UUID ownerId) {
        return feedbackRepository.findAllByOwnerIdOrderByCreatedAtDesc(ownerId).stream().map(StyleFeedbackDto::from).toList();
    }

    private StyleFeedbackDto submit(
            UUID ownerId, FeedbackTarget target, UUID itemId, UUID shirtId, UUID bottomId, UUID shoesId,
            String rawText, InputMethod inputMethod, Sentiment sentiment
    ) {
        Optional<StyleExtractionSuggestion> suggestion = extractionService.extract(rawText, target, sentiment);
        boolean isLike = sentiment == Sentiment.LIKE;

        StyleFeedback feedback = StyleFeedback.builder()
                .id(UUID.randomUUID())
                .ownerId(ownerId)
                .target(target)
                .itemId(itemId)
                .shirtId(shirtId)
                .bottomId(bottomId)
                .shoesId(shoesId)
                .inputMethod(inputMethod)
                .sentiment(sentiment)
                .rawText(rawText)
                .extractionSucceeded(suggestion.isPresent())
                .extractedLikes(isLike ? suggestion.map(s -> normalize(s.likes())).orElseGet(HashSet::new) : new HashSet<>())
                .extractedDislikes(!isLike ? suggestion.map(s -> normalize(s.likes())).orElseGet(HashSet::new) : new HashSet<>())
                .extractedStyleTags(suggestion.map(s -> normalize(s.style())).orElseGet(HashSet::new))
                .extractedReasoning(suggestion.map(StyleExtractionSuggestion::reasoning).orElse(null))
                .createdAt(Instant.now())
                .build();
        feedback = feedbackRepository.save(feedback);

        suggestion.ifPresent(s -> mergeIntoProfile(ownerId, target, sentiment, s));

        return StyleFeedbackDto.from(feedback);
    }

    private void mergeIntoProfile(UUID ownerId, FeedbackTarget target, Sentiment sentiment, StyleExtractionSuggestion suggestion) {
        StyleProfileEntity profile = loadOrInitializeProfile(ownerId);

        if (sentiment == Sentiment.LIKE) {
            profile.getLikes().addAll(normalize(suggestion.likes()));
        } else {
            profile.getDislikes().addAll(normalize(suggestion.likes()));
        }
        profile.getStyleTags().addAll(normalize(suggestion.style()));

        String label = "[" + target.name().toLowerCase() + "·" + sentiment.name().toLowerCase() + "] ";
        List<String> lines = new ArrayList<>();
        lines.add(label + suggestion.reasoning());
        if (profile.getReasoningSummary() != null && !profile.getReasoningSummary().isBlank()) {
            lines.addAll(List.of(profile.getReasoningSummary().split("\n")));
        }
        profile.setReasoningSummary(lines.stream().limit(MAX_REASONING_LINES).collect(Collectors.joining("\n")));

        profile.setFeedbackCount(profile.getFeedbackCount() + 1);
        profile.setUpdatedAt(Instant.now());
        profileRepository.save(profile);
    }

    private StyleProfileEntity loadOrInitializeProfile(UUID ownerId) {
        return profileRepository.findById(ownerId)
                .orElseGet(() -> profileRepository.save(StyleProfileEntity.builder()
                        .ownerId(ownerId)
                        .reasoningSummary("")
                        .updatedAt(Instant.now())
                        .build()));
    }

    private void requireItem(UUID ownerId, UUID id, Category expectedCategory) {
        Item item = itemRepository.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new ItemNotFoundException(id));
        if (expectedCategory != null && item.getCategory() != expectedCategory) {
            throw new IllegalArgumentException(expectedCategory + " id does not refer to a " + expectedCategory + " item: " + id);
        }
    }

    private Set<String> normalize(List<String> values) {
        if (values == null) {
            return new HashSet<>();
        }
        return values.stream()
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isBlank())
                .collect(Collectors.toCollection(HashSet::new));
    }
}
