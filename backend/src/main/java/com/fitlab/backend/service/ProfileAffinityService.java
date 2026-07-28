package com.fitlab.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.domain.ItemProfileAffinity;
import com.fitlab.backend.domain.StyleProfileEntity;
import com.fitlab.backend.matching.ProfileAffinityResolver;
import com.fitlab.backend.repository.ItemProfileAffinityRepository;
import com.fitlab.backend.repository.StyleProfileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Judges how well each item aligns with the user's style profile, via the
 * Claude Messages API (text-only, one call per cache miss). Results are
 * cached per item, versioned against StyleProfileEntity.feedbackCount so a
 * row is treated as stale - and recomputed - the moment any new feedback
 * changes the profile, without an explicit invalidation pass over every row.
 *
 * Always resolves in a single batch over every distinct item a caller is
 * about to touch (see ProfileAffinityResolver) - callers like
 * OutfitService.buildBest() brute-force many shirt x bottom x shoes combos,
 * so calling this per-combination instead of once per distinct item would
 * turn a handful of AI calls into potentially thousands per request.
 *
 * Entirely optional and fail-soft, exactly like ImageTaggingService/
 * StyleExtractionService: with no ANTHROPIC_API_KEY set, or no profile yet,
 * every item resolves to a neutral 0.0 with zero network/DB calls.
 */
@Service
public class ProfileAffinityService implements ProfileAffinityResolver {

    private static final Logger log = LoggerFactory.getLogger(ProfileAffinityService.class);

    private final String model;
    private final AnthropicClient client;
    private final ItemProfileAffinityRepository affinityRepository;
    private final StyleProfileRepository profileRepository;

    public ProfileAffinityService(
            @Value("${fitlab.ai.affinity-model:claude-opus-5}") String model,
            ItemProfileAffinityRepository affinityRepository,
            StyleProfileRepository profileRepository
    ) {
        this.model = model;
        this.client = isApiKeyConfigured() ? AnthropicOkHttpClient.fromEnv() : null;
        this.affinityRepository = affinityRepository;
        this.profileRepository = profileRepository;
    }

    public boolean isEnabled() {
        return client != null;
    }

    @Override
    @Transactional
    public Map<UUID, Double> resolve(Collection<Item> items) {
        Map<UUID, Item> distinct = new LinkedHashMap<>();
        for (Item item : items) {
            distinct.put(item.getId(), item);
        }
        if (distinct.isEmpty()) {
            return Map.of();
        }

        Optional<StyleProfileEntity> profileOpt = profileRepository.findById(StyleProfileEntity.SINGLETON_ID);
        if (!isEnabled() || profileOpt.isEmpty() || profileOpt.get().getFeedbackCount() == 0) {
            return neutralMap(distinct.keySet());
        }
        StyleProfileEntity profile = profileOpt.get();

        Map<UUID, ItemProfileAffinity> cached = new LinkedHashMap<>();
        for (ItemProfileAffinity row : affinityRepository.findAllById(distinct.keySet())) {
            cached.put(row.getItemId(), row);
        }

        Map<UUID, Double> result = new LinkedHashMap<>();
        for (Map.Entry<UUID, Item> entry : distinct.entrySet()) {
            UUID id = entry.getKey();
            ItemProfileAffinity row = cached.get(id);
            if (row != null && row.getComputedForFeedbackCount() == profile.getFeedbackCount()) {
                result.put(id, row.getAffinityScore());
            } else {
                result.put(id, computeOne(entry.getValue(), profile));
            }
        }
        return result;
    }

    private Map<UUID, Double> neutralMap(Collection<UUID> ids) {
        Map<UUID, Double> neutral = new LinkedHashMap<>();
        for (UUID id : ids) {
            neutral.put(id, 0.0);
        }
        return neutral;
    }

    /** Always persists a cache row for the current feedback generation, even on failure, so a flaky
     * API call doesn't get retried on every request until the next feedback submission. */
    private double computeOne(Item item, StyleProfileEntity profile) {
        double score = 0.0;
        String rationale = null;
        try {
            StructuredMessageCreateParams<ProfileAffinitySuggestion> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(256L)
                    .outputConfig(ProfileAffinitySuggestion.class)
                    .addUserMessage(promptFor(item, profile))
                    .build();

            StructuredMessage<ProfileAffinitySuggestion> response = client.messages().create(params);
            Optional<ProfileAffinitySuggestion> suggestion = response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(StructuredTextBlock::text);
            if (suggestion.isPresent()) {
                score = clamp(suggestion.get().affinityScore());
                rationale = suggestion.get().rationale();
            }
        } catch (RuntimeException e) {
            log.warn("Profile affinity check failed for item {}, treating as neutral: {}", item.getId(), e.getMessage());
        }

        affinityRepository.save(ItemProfileAffinity.builder()
                .itemId(item.getId())
                .affinityScore(score)
                .rationale(rationale)
                .computedForFeedbackCount(profile.getFeedbackCount())
                .computedAt(Instant.now())
                .build());
        return score;
    }

    private double clamp(double value) {
        return Math.max(-1.0, Math.min(1.0, value));
    }

    private String promptFor(Item item, StyleProfileEntity profile) {
        return "A wardrobe app is judging how well one clothing item fits a user's established style profile. "
                + "Item: name=\"" + item.getName() + "\", colors=" + item.getColors() + ", vibes=" + item.getVibes() + ". "
                + "Things the user says they like: " + profile.getLikes() + ". "
                + "Things they say don't work for them: " + profile.getDislikes() + ". "
                + "Their broader style tags: " + profile.getStyleTags() + ". "
                + "Their own words, most recent first: " + profile.getReasoningSummary() + ". "
                + "Rate how well this specific item aligns with this profile - penalize traits that resemble what "
                + "they dislike, reward traits that resemble what they like - on a -1.0 to 1.0 scale.";
    }

    private boolean isApiKeyConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }
}
