package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final ImageTaggingService imageTaggingService;

    public ItemService(ItemRepository itemRepository, ImageTaggingService imageTaggingService) {
        this.itemRepository = itemRepository;
        this.imageTaggingService = imageTaggingService;
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
    public ItemDto update(UUID id, CreateItemRequest request) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        item.setName(request.name());
        item.setCategory(request.category());
        item.setColors(normalize(request.colors()));
        item.setVibes(normalize(request.vibes()));
        return ItemDto.from(itemRepository.save(item));
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

    /**
     * Fills colors/vibes from the photo when (and only when) the item currently has
     * neither - never overwrites tags the user already entered. Untagged items can't
     * share a color/vibe with anything, so they always score 0 in outfit matching;
     * this is what makes bulk-uploaded photos matchable without manual tagging.
     */
    @Transactional
    public ItemDto autoTagIfUntagged(UUID id, byte[] imageBytes, String contentType) {
        Item item = itemRepository.findById(id).orElseThrow(() -> new ItemNotFoundException(id));
        if (!item.getColors().isEmpty() || !item.getVibes().isEmpty()) {
            return ItemDto.from(item);
        }
        imageTaggingService.suggestTags(imageBytes, contentType, item.getName(), item.getCategory())
                .ifPresent(suggestion -> {
                    item.setColors(normalize(toSet(suggestion.colors())));
                    item.setVibes(normalize(toSet(suggestion.vibes())));
                });
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

    private Set<String> toSet(List<String> values) {
        return values == null ? Set.of() : new HashSet<>(values);
    }

    private Set<String> normalize(Set<String> tags) {
        if (tags == null) {
            return Set.of();
        }
        return tags.stream().map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
    }
}
