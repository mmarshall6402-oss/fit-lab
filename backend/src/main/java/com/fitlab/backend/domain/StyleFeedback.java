package com.fitlab.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
import java.util.UUID;

/**
 * A user's raw, in-their-own-words explanation of why they like an item or an
 * outfit combo, plus whatever structured preferences were extracted from it.
 * Item/outfit ids are stored as plain UUIDs rather than JPA relations so this
 * feedback - and the taste signal it represents - survives the referenced
 * item(s) later being deleted from the catalog.
 */
@Entity
@Table(name = "style_feedback")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StyleFeedback {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FeedbackTarget target;

    /** Set when target == ITEM. */
    private UUID itemId;

    /** Set together when target == OUTFIT. */
    private UUID shirtId;
    private UUID bottomId;
    private UUID shoesId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InputMethod inputMethod;

    /** Whether this feedback explains what the user likes or what doesn't work for them. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Sentiment sentiment;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawText;

    /** Populated only when sentiment == LIKE. */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_feedback_likes", joinColumns = @JoinColumn(name = "style_feedback_id"))
    @Column(name = "like_value")
    private Set<String> extractedLikes = new HashSet<>();

    /** Populated only when sentiment == DISLIKE. */
    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_feedback_dislikes", joinColumns = @JoinColumn(name = "style_feedback_id"))
    @Column(name = "dislike_value")
    private Set<String> extractedDislikes = new HashSet<>();

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "style_feedback_style_tags", joinColumns = @JoinColumn(name = "style_feedback_id"))
    @Column(name = "style_tag")
    private Set<String> extractedStyleTags = new HashSet<>();

    @Column(columnDefinition = "TEXT")
    private String extractedReasoning;

    @Column(nullable = false)
    private boolean extractionSucceeded;

    @Column(nullable = false)
    private Instant createdAt;
}
