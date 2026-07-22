package com.fitlab.backend.matching;

import com.fitlab.backend.domain.Item;

/**
 * Strategy seam for item-to-item compatibility scoring. TagMatcher is the only
 * implementation today; an EmbeddingMatcher/HybridMatcher could be added later
 * behind this same interface without touching callers.
 */
public interface Matcher {
    double score(Item a, Item b);
}
