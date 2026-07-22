package com.fitlab.backend.bootstrap;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.repository.ItemRepository;
import com.fitlab.backend.service.ItemService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * Loads a bundled starter catalog on first boot only (empty item table). Any
 * "id" values in the seed JSON are ignored by CreateItemRequest - fresh UUIDs
 * are always generated via ItemService.
 */
@Component
public class CatalogSeeder implements CommandLineRunner {

    private final ItemRepository itemRepository;
    private final ItemService itemService;
    private final ObjectMapper objectMapper;

    public CatalogSeeder(ItemRepository itemRepository, ItemService itemService, ObjectMapper objectMapper) {
        this.itemRepository = itemRepository;
        this.itemService = itemService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) throws Exception {
        if (itemRepository.count() > 0) {
            return;
        }
        try (InputStream in = new ClassPathResource("seed/starter-catalog.json").getInputStream()) {
            List<CreateItemRequest> seedItems = objectMapper.readValue(in, new TypeReference<>() {});
            itemService.importAll(seedItems);
        }
    }
}
