package com.fitlab.backend.repository;

import com.fitlab.backend.domain.StyleProfileEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface StyleProfileRepository extends JpaRepository<StyleProfileEntity, UUID> {
}
