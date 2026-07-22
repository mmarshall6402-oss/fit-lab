package com.fitlab.backend.matching;

import com.fitlab.backend.domain.Item;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;

/**
 * Scores two items by tag overlap: shared colors count more than shared vibes.
 * Stateless and side-effect free so it can be reused for both pairwise
 * recommendations and outfit cohesion scoring.
 */
@Service
public class TagMatcher implements Matcher {

    public static final double W_COLOR = 3;
    public static final double W_VIBE = 2;

    @Override
    public double score(Item a, Item b) {
        return overlap(a.getColors(), b.getColors()) * W_COLOR
                + overlap(a.getVibes(), b.getVibes()) * W_VIBE;
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
