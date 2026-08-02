package com.fitlab.backend.repository;

import com.fitlab.backend.domain.StyleFeedback;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StyleFeedbackRepository extends JpaRepository<StyleFeedback, UUID> {
    List<StyleFeedback> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
}
