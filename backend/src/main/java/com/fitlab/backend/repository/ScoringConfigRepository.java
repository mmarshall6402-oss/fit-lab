package com.fitlab.backend.repository;

import com.fitlab.backend.domain.ScoringConfigEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScoringConfigRepository extends JpaRepository<ScoringConfigEntity, Long> {
}
