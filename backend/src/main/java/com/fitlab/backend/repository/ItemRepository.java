package com.fitlab.backend.repository;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByOwnerId(UUID ownerId);

    List<Item> findByOwnerIdAndCategory(UUID ownerId, Category category);

    Optional<Item> findByIdAndOwnerId(UUID id, UUID ownerId);
}
