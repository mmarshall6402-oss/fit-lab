package com.fitlab.backend.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

/**
 * Cached result of judging how well one item aligns with the user's style
 * profile (see ProfileAffinityService) - a plain UUID keyed to Item.id, not a
 * JPA relation, following the same convention StyleFeedback already uses for
 * itemId, so this cache and the item catalog stay independently lifecycled.
 *
 * computedForFeedbackCount is the staleness marker: it's compared against
 * StyleProfileEntity.feedbackCount at read time, so a row is treated as stale
 * (and recomputed) the moment any new feedback changes the profile, without
 * needing an explicit invalidation pass over every cached row.
 */
@Entity
@Table(name = "item_profile_affinity")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ItemProfileAffinity {

    @Id
    private UUID itemId;

    /** -1.0 (resembles what the user dislikes) to +1.0 (matches what they like); 0.0 = neutral/unknown. */
    @Column(nullable = false)
    private double affinityScore;

    @Column(columnDefinition = "TEXT")
    private String rationale;

    @Column(nullable = false)
    private int computedForFeedbackCount;

    @Column(nullable = false)
    private Instant computedAt;
}
