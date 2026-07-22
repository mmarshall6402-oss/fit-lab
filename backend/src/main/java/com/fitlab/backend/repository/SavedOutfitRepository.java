package com.fitlab.backend.repository;

import com.fitlab.backend.domain.SavedOutfit;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SavedOutfitRepository extends JpaRepository<SavedOutfit, UUID> {
    List<SavedOutfit> findAllByOrderByLikeCountDescCreatedAtDesc();
}
