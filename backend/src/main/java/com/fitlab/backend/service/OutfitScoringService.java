package com.fitlab.backend.service;

import com.fitlab.backend.domain.Item;
import com.fitlab.backend.matching.Matcher;
import com.fitlab.backend.matching.ScoringConfig;
import com.fitlab.backend.matching.ScoringConfigProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Scores a full outfit (shirt + bottom + shoes) by combining all three pairwise
 * Matcher edges into one 0-100 cohesion score, and derives honest, rule-based
 * explanations from the same tag signals. No ML - every reason is a direct
 * readout of colors/vibes actually shared (or deliberately not shared) by the
 * chosen pieces. Weights, the neutral-color list, and which reason types are
 * active all come from a live ScoringConfigProvider (the admin dashboard) so
 * calibration takes effect without a redeploy.
 */
@Service
public class OutfitScoringService {

    private final Matcher matcher;
    private final ScoringConfigProvider configProvider;

    /** Falls back to hardcoded defaults for callers outside the Spring context (e.g. plain unit tests). */
    public OutfitScoringService(Matcher matcher) {
        this(matcher, ScoringConfig::defaults);
    }

    @Autowired
    public OutfitScoringService(Matcher matcher, ScoringConfigProvider configProvider) {
        this.matcher = matcher;
        this.configProvider = configProvider;
    }

    /** Average of the three pairwise edges, each normalized to its own achievable max, scaled to 0-100. */
    public double holisticScore(Item shirt, Item bottom, Item shoes) {
        double shirtBottom = normalizedPairScore(shirt, bottom);
        double shirtShoes = normalizedPairScore(shirt, shoes);
        double bottomShoes = normalizedPairScore(bottom, shoes);
        double average = (shirtBottom + shirtShoes + bottomShoes) / 3.0;
        return Math.round(average * 1000.0) / 10.0; // one decimal place
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
