package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private final ItemRepository itemRepository;

    public ItemService(ItemRepository itemRepository) {
        this.itemRepository = itemRepository;
    }

    @Transactional(readOnly = true)
    public List<ItemDto> findAll(Category category) {
        List<Item> items = category == null ? itemRepository.findAll() : itemRepository.findByCategory(category);
        return items.stream().map(ItemDto::from).toList();
    }

    @Transactional
    public ItemDto create(CreateItemRequest request) {
        return ItemDto.from(itemRepository.save(toEntity(request)));
    }

    @Transactional
    public List<ItemDto> importAll(List<CreateItemRequest> requests) {
        List<Item> items = requests.stream().map(this::toEntity).toList();
        return itemRepository.saveAll(items).stream().map(ItemDto::from).toList();
    }

    @Transactional
    public void delete(UUID id) {
        if (!itemRepository.existsById(id)) {
            throw new ItemNotFoundException(id);
        }
        itemRepository.deleteById(id);
    }

    @Transactional
    public ItemDto setImageUrl(UUID id, String imageUrl) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        item.setImageUrl(imageUrl);
        return ItemDto.from(itemRepository.save(item));
    }

    /** Ids are always generated server-side; any id in the request is discarded. Tags are normalized to lowercase. */
    private Item toEntity(CreateItemRequest request) {
        return Item.builder()
                .id(UUID.randomUUID())
                .name(request.name())
                .category(request.category())
                .imageUrl(request.imageUrl())
                .colors(normalize(request.colors()))
                .vibes(normalize(request.vibes()))
                .build();
    }

    private Set<String> normalize(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
    }
}
