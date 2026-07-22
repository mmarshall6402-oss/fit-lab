package com.fitlab.backend.repository;

import com.fitlab.backend.domain.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CommentRepository extends JpaRepository<Comment, UUID> {
    List<Comment> findBySavedOutfitIdOrderByCreatedAtAsc(UUID savedOutfitId);

    long countBySavedOutfitId(UUID savedOutfitId);
}
