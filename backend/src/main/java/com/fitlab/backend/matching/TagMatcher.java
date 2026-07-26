package com.fitlab.backend.matching;

import com.fitlab.backend.domain.Item;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Scores two items by tag overlap: shared colors count more than shared vibes
 * by default, but the exact weights are live-tunable via ScoringConfigProvider
 * (backed by the admin dashboard) instead of being fixed constants.
 * Stateless and side-effect free so it can be reused for both pairwise
 * recommendations and outfit cohesion scoring.
 */
@Service
public class TagMatcher implements Matcher {

    private final ScoringConfigProvider configProvider;

    /** Falls back to hardcoded defaults for callers outside the Spring context (e.g. plain unit tests). */
    public TagMatcher() {
        this(ScoringConfig::defaults);
    }

    @Autowired
    public TagMatcher(ScoringConfigProvider configProvider) {
        this.configProvider = configProvider;
    }

    @Override
    public double score(Item a, Item b) {
        ScoringConfig config = configProvider.current();
        return overlap(a.getColors(), b.getColors()) * config.colorWeight()
                + overlap(a.getVibes(), b.getVibes()) * config.vibeWeight();
    }

    private int overlap(Set<String> x, Set<String> y) {
        if (x == null || y == null || x.isEmpty() || y.isEmpty()) {
            return 0;
        }
        Set<String> intersection = new HashSet<>(x);
        intersection.retainAll(y);
        return intersection.size();
    }
}
