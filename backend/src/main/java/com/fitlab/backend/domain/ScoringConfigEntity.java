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

import java.util.HashSet;
import java.util.Set;

/**
 * Singleton row (fixed id) holding the live-editable scoring configuration.
 * There is exactly one row ever; it's loaded once at startup and updated in
 * place from the admin dashboard.
 */
@Entity
@Table(name = "scoring_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ScoringConfigEntity {

    public static final long SINGLETON_ID = 1L;

    @Id
    private Long id;

    @Column(nullable = false)
    private double colorWeight;

    @Column(nullable = false)
    private double vibeWeight;

    @Column(nullable = false)
    private double profileWeight;

    @Column(nullable = false)
    private int neutralColorThreshold;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "scoring_config_neutral_colors", joinColumns = @JoinColumn(name = "scoring_config_id"))
    @Column(name = "color")
    private Set<String> neutralColors = new HashSet<>();

    @Column(nullable = false)
    private boolean sharedVibeReasonEnabled;

    @Column(nullable = false)
    private boolean sharedColorReasonEnabled;

    @Column(nullable = false)
    private boolean neutralCounterbalanceReasonEnabled;
}
