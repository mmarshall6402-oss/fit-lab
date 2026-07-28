package com.fitlab.backend.repository;

import com.fitlab.backend.domain.ItemProfileAffinity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ItemProfileAffinityRepository extends JpaRepository<ItemProfileAffinity, UUID> {
    /** Unlike the inherited deleteById, a derived delete-by query never throws when nothing matches -
     * most items won't have a cached row yet, so invalidation call sites can call this unconditionally. */
    void deleteByItemId(UUID itemId);
}
