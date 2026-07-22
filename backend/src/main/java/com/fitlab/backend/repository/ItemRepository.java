package com.fitlab.backend.repository;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ItemRepository extends JpaRepository<Item, UUID> {
    List<Item> findByCategory(Category category);
}
