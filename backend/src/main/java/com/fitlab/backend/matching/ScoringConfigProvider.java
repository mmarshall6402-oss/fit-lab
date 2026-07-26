package com.fitlab.backend.matching;

/**
 * Strategy seam for reading the current scoring configuration. TagMatcher and
 * OutfitScoringService depend on this instead of a concrete source so they
 * work identically whether the config comes from the database (production)
 * or an in-memory default (tests, or any non-Spring-managed usage).
 */
public interface ScoringConfigProvider {
    ScoringConfig current();
}
