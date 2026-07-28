package com.fitlab.backend.service;

import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.dto.RecommendationDto;
import com.fitlab.backend.matching.Matcher;
import com.fitlab.backend.matching.ProfileAffinityResolver;
import com.fitlab.backend.matching.ScoringConfig;
import com.fitlab.backend.matching.ScoringConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Ranks candidate items against an anchor item using the injected Matcher
 * strategy, nudged by how well each candidate aligns with the user's style
 * profile (see ProfileAffinityResolver). Affinity is resolved once per call
 * in a single batch over every candidate, never per-candidate - a category
 * can hold dozens of items, and each one otherwise risks its own cache
 * lookup/AI call.
 */
@Service
public class MatchService {

    private final Matcher matcher;
    private final ScoringConfigProvider configProvider;
    private final ProfileAffinityResolver profileAffinityResolver;

    /** Falls back to hardcoded defaults and neutral profile affinity for callers outside the Spring context (e.g. plain unit tests). */
    public MatchService(Matcher matcher) {
        this(matcher, ScoringConfig::defaults, items -> Map.of());
    }

    @Autowired
    public MatchService(Matcher matcher, ScoringConfigProvider configProvider, ProfileAffinityResolver profileAffinityResolver) {
        this.matcher = matcher;
        this.configProvider = configProvider;
        this.profileAffinityResolver = profileAffinityResolver;
    }

    public List<RecommendationDto> rank(Item anchor, List<Item> candidates) {
        Map<UUID, Double> affinity = profileAffinityResolver.resolve(candidates);
        double profileWeight = configProvider.current().profileWeight();

        return candidates.stream()
                .filter(candidate -> !candidate.getId().equals(anchor.getId()))
                .map(candidate -> new RecommendationDto(
                        ItemDto.from(candidate),
                        matcher.score(anchor, candidate) + profileWeight * affinity.getOrDefault(candidate.getId(), 0.0)))
                .sorted(Comparator.comparingDouble(RecommendationDto::score).reversed())
                .toList();
    }
}
