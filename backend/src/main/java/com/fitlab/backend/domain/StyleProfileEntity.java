package com.fitlab.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * Singleton row (fixed id) holding the user's evolving style profile, built up
 * by merging structured preferences extracted from each piece of style
 * feedback. There is exactly one row ever, following the same pattern as
 * ScoringConfigEntity. Store-and-display only for now - nothing here feeds
 * back into TagMatcher/OutfitScoringService/ScoringConfig; that's future work.
 */
@Entity
@Table(name = "style_profile")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StyleProfileEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_profile_likes", joinColumns = @JoinColumn(name = "style_profile_id"))
    @Column(name = "like_value")
    private Set<String> likes = new HashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_profile_style_tags", joinColumns = @JoinColumn(name = "style_profile_id"))
    @Column(name = "style_tag")
    private Set<String> styleTags = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String reasoningSummary;

    @Column(nullable = false)
    private int feedbackCount;

    @Column(nullable = false)
    private Instant updatedAt;
}
