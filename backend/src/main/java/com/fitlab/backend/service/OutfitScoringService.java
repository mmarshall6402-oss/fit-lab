package com.fitlab.backend.service;

import com.fitlab.backend.domain.Item;
import com.fitlab.backend.matching.Matcher;
import com.fitlab.backend.matching.ProfileAffinityResolver;
import com.fitlab.backend.matching.ScoringConfig;
import com.fitlab.backend.matching.ScoringConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Scores a full outfit (shirt + bottom + shoes) by combining all three pairwise
 * Matcher edges into one 0-100 cohesion score, plus a personal style-profile
 * affinity nudge, and derives honest, rule-based explanations from the tag
 * signals. Every reason is a direct readout of colors/vibes actually shared
 * (or deliberately not shared) by the chosen pieces - the profile affinity
 * itself isn't explained in the reasons list. Weights, the neutral-color
 * list, and which reason types are active all come from a live
 * ScoringConfigProvider (the admin dashboard) so calibration takes effect
 * without a redeploy.
 */
@Service
public class OutfitScoringService {

    private final Matcher matcher;
    private final ScoringConfigProvider configProvider;
    private final ProfileAffinityResolver profileAffinityResolver;

    /** Falls back to hardcoded defaults and neutral profile affinity for callers outside the Spring context (e.g. plain unit tests). */
    public OutfitScoringService(Matcher matcher) {
        this(matcher, ScoringConfig::defaults, items -> Map.of());
    }

    @Autowired
    public OutfitScoringService(Matcher matcher, ScoringConfigProvider configProvider, ProfileAffinityResolver profileAffinityResolver) {
        this.matcher = matcher;
        this.configProvider = configProvider;
        this.profileAffinityResolver = profileAffinityResolver;
    }

    /**
     * Average of the three pairwise edges, each normalized to its own achievable
     * max, plus a profile-affinity nudge, scaled to 0-100. Resolves affinity for
     * just these three items - fine for one-off scoring, but callers scoring
     * many combinations (e.g. OutfitService.buildBest()) should batch-resolve
     * affinity once via ProfileAffinityResolver and call the 4-arg overload
     * instead, to avoid repeated cache lookups/AI calls inside a hot loop.
     */
    public double holisticScore(Item shirt, Item bottom, Item shoes) {
        Map<UUID, Double> affinity = profileAffinityResolver.resolve(List.of(shirt, bottom, shoes));
        return holisticScore(shirt, bottom, shoes, affinity);
    }

    /** Pure math, no I/O - affinityByItemId must already contain every item touched (missing entries count as neutral). */
    public double holisticScore(Item shirt, Item bottom, Item shoes, Map<UUID, Double> affinityByItemId) {
        double shirtBottom = normalizedPairScore(shirt, bottom);
        double shirtShoes = normalizedPairScore(shirt, shoes);
        double bottomShoes = normalizedPairScore(bottom, shoes);
        double avgPairwise = (shirtBottom + shirtShoes + bottomShoes) / 3.0;

        double avgAffinity = (affinityByItemId.getOrDefault(shirt.getId(), 0.0)
                + affinityByItemId.getOrDefault(bottom.getId(), 0.0)
                + affinityByItemId.getOrDefault(shoes.getId(), 0.0)) / 3.0;

        ScoringConfig config = configProvider.current();
        double denom = config.colorWeight() + config.vibeWeight();
        // profileWeight is defined in the same raw units as colorWeight/vibeWeight, so it's expressed
        // here as a share of the pairwise 0..1 scale - if an admin raises colorWeight/vibeWeight to make
        // tag overlap matter more, the profile nudge shrinks proportionally unless profileWeight also rises.
        double affinityFraction = denom <= 0 ? 0.0 : (config.profileWeight() / denom) * avgAffinity;

        double combined = Math.max(0.0, Math.min(1.0, avgPairwise + affinityFraction));
        return Math.round(combined * 1000.0) / 10.0; // one decimal place
    }

    /**
     * Raw score divided by the best score this pair could possibly achieve given
     * how many colors/vibes each item actually has. Adapts to each item's tag
     * count instead of assuming a fixed catalog-wide cap.
     */
    double normalizedPairScore(Item a, Item b) {
        double raw = matcher.score(a, b);
        double max = maxPossiblePairScore(a, b);
        if (max <= 0) {
            return 0;
        }
        return Math.min(1.0, raw / max);
    }

    private double maxPossiblePairScore(Item a, Item b) {
        ScoringConfig config = configProvider.current();
        int maxColorOverlap = Math.min(a.getColors().size(), b.getColors().size());
        int maxVibeOverlap = Math.min(a.getVibes().size(), b.getVibes().size());
        return maxColorOverlap * config.colorWeight() + maxVibeOverlap * config.vibeWeight();
    }

    public List<String> reasons(Item shirt, Item bottom, Item shoes) {
        ScoringConfig config = configProvider.current();
        List<String> reasons = new ArrayList<>();

        if (config.sharedVibeReasonEnabled()) {
            intersectAll(shirt.getVibes(), bottom.getVibes(), shoes.getVibes()).stream()
                    .findFirst()
                    .ifPresent(vibe -> reasons.add("all three pieces share the '" + vibe + "' vibe"));
        }

        if (config.sharedColorReasonEnabled()) {
            intersectAll(shirt.getColors(), bottom.getColors(), shoes.getColors()).stream()
                    .findFirst()
                    .ifPresent(color -> reasons.add("shirt, bottom, and shoes are unified by the color '" + color + "'"));
        }

        if (config.neutralCounterbalanceReasonEnabled() && shirt.getColors().size() >= config.neutralColorThreshold()) {
            bottom.getColors().stream()
                    .filter(config.neutralColors()::contains)
                    .findFirst()
                    .ifPresent(neutral -> reasons.add(
                            "shirt brings " + shirt.getColors().size() + " colors, so the bottom stays neutral ('" + neutral + "')"));

            shoes.getColors().stream()
                    .filter(config.neutralColors()::contains)
                    .findFirst()
                    .ifPresent(neutral -> reasons.add(
                            "shirt brings " + shirt.getColors().size() + " colors, so the shoes stay neutral ('" + neutral + "')"));
        }

        if (reasons.isEmpty()) {
            reasons.add("no strong shared colors or vibes - this is simply the best-scoring combination available");
        }

        return reasons;
    }

    private Set<String> intersectAll(Set<String> a, Set<String> b, Set<String> c) {
        Set<String> result = new HashSet<>(a);
        result.retainAll(b);
        result.retainAll(c);
        return result;
    }
}
