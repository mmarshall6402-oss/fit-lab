package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.FeedbackTarget;
import com.fitlab.backend.domain.InputMethod;
import com.fitlab.backend.domain.Item;
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
 * Captures raw style feedback (why a user likes an item or an outfit combo),
 * extracts structured preferences from it, and merges those into a single
 * evolving style profile. Store-and-display only for now: nothing here feeds
 * back into TagMatcher/OutfitScoringService/ScoringConfig - that's explicit
 * future work.
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
    public StyleFeedbackDto submitForItem(UUID itemId, String rawText, InputMethod inputMethod) {
        requireItem(itemId, null);
        return submit(FeedbackTarget.ITEM, itemId, null, null, null, rawText, inputMethod);
    }

    @Transactional
    public StyleFeedbackDto submitForOutfit(UUID shirtId, UUID bottomId, UUID shoesId, String rawText, InputMethod inputMethod) {
        requireItem(shirtId, Category.SHIRT);
        requireItem(bottomId, Category.BOTTOM);
        requireItem(shoesId, Category.SHOES);
        return submit(FeedbackTarget.OUTFIT, null, shirtId, bottomId, shoesId, rawText, inputMethod);
    }

    @Transactional(readOnly = true)
    public StyleProfileDto getProfile() {
        return StyleProfileDto.from(loadOrInitializeProfile());
    }

    @Transactional(readOnly = true)
    public List<StyleFeedbackDto> getHistory() {
        return feedbackRepository.findAllByOrderByCreatedAtDesc().stream().map(StyleFeedbackDto::from).toList();
    }

    private StyleFeedbackDto submit(
            FeedbackTarget target, UUID itemId, UUID shirtId, UUID bottomId, UUID shoesId,
            String rawText, InputMethod inputMethod
    ) {
        Optional<StyleExtractionSuggestion> suggestion = extractionService.extract(rawText, target);

        StyleFeedback feedback = StyleFeedback.builder()
                .id(UUID.randomUUID())
                .target(target)
                .itemId(itemId)
                .shirtId(shirtId)
                .bottomId(bottomId)
                .shoesId(shoesId)
                .inputMethod(inputMethod)
                .rawText(rawText)
                .extractionSucceeded(suggestion.isPresent())
                .extractedLikes(suggestion.map(s -> normalize(s.likes())).orElseGet(HashSet::new))
                .extractedStyleTags(suggestion.map(s -> normalize(s.style())).orElseGet(HashSet::new))
                .extractedReasoning(suggestion.map(StyleExtractionSuggestion::reasoning).orElse(null))
                .createdAt(Instant.now())
                .build();
        feedback = feedbackRepository.save(feedback);

        suggestion.ifPresent(s -> mergeIntoProfile(target, s));

        return StyleFeedbackDto.from(feedback);
    }

    private void mergeIntoProfile(FeedbackTarget target, StyleExtractionSuggestion suggestion) {
        StyleProfileEntity profile = loadOrInitializeProfile();

        profile.getLikes().addAll(normalize(suggestion.likes()));
        profile.getStyleTags().addAll(normalize(suggestion.style()));

        String label = target == FeedbackTarget.ITEM ? "[item] " : "[outfit] ";
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

    private StyleProfileEntity loadOrInitializeProfile() {
        return profileRepository.findById(StyleProfileEntity.SINGLETON_ID)
                .orElseGet(() -> profileRepository.save(StyleProfileEntity.builder()
                        .id(StyleProfileEntity.SINGLETON_ID)
                        .reasoningSummary("")
                        .updatedAt(Instant.now())
                        .build()));
    }

    private void requireItem(UUID id, Category expectedCategory) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
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
