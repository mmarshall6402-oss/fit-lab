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
 * A user's explicit "save this fit" action - a specific shirt+bottom+shoes
 * combination and its score at the moment it was saved. Item ids are stored
 * as plain UUIDs rather than JPA relations, same convention as StyleFeedback,
 * so a saved fit survives one of its items later being deleted from the
 * catalog. The score is a snapshot, not recomputed on read: scoring weights
 * are live-tunable via the admin dashboard, so a saved score should reflect
 * what it scored when saved, not drift silently as calibration changes.
 */
@Entity
@Table(name = "saved_outfit")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedOutfit {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private UUID shirtId;

    @Column(nullable = false)
    private UUID bottomId;

    @Column(nullable = false)
    private UUID shoesId;

    @Column(nullable = false)
    private double score;

    @Column(nullable = false)
    private Instant createdAt;
}
