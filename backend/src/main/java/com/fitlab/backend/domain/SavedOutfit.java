package com.fitlab.backend.domain;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A community-saved outfit. Item name/image are snapshotted at save time (not a live FK to
 * Item) so a saved fit still displays correctly even if the underlying catalog item is later
 * edited or deleted.
 */
@Entity
@Table(name = "saved_outfits")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SavedOutfit {

    @Id
    private UUID id;

    private UUID shirtId;
    private String shirtName;
    private String shirtImageUrl;

    private UUID bottomId;
    private String bottomName;
    private String bottomImageUrl;

    private UUID shoesId;
    private String shoesName;
    private String shoesImageUrl;

    private double score;

    @Builder.Default
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "saved_outfit_reasons", joinColumns = @JoinColumn(name = "saved_outfit_id"))
    @OrderColumn(name = "reason_order")
    @Column(name = "reason")
    private List<String> reasons = new ArrayList<>();

    @Builder.Default
    private int likeCount = 0;

    private Instant createdAt;
}
