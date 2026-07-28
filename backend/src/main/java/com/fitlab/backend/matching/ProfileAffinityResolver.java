package com.fitlab.backend.matching;

import com.fitlab.backend.domain.Item;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Strategy seam for judging how well items align with the user's style
 * profile. Always resolves in a single batch over every distinct item a
 * caller is about to touch - never called per-pair or inside a combinatorial
 * loop, since a real implementation may need one AI call per cache miss.
 */
public interface ProfileAffinityResolver {
    /** -1.0 (resembles disliked traits) to +1.0 (matches liked traits) per item id; missing entries mean neutral. */
    Map<UUID, Double> resolve(Collection<Item> items);
}
