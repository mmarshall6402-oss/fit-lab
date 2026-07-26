package com.fitlab.backend.repository;

import com.fitlab.backend.domain.Attachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AttachmentRepository extends JpaRepository<Attachment, UUID> {
    List<Attachment> findByItemId(UUID itemId);
}
