package com.fitlab.backend.controller;

import com.fitlab.backend.domain.Category;
import com.fitlab.backend.dto.CreateItemRequest;
import com.fitlab.backend.dto.ItemDto;
import com.fitlab.backend.service.ImageStorageService;
import com.fitlab.backend.service.ItemService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/items")
public class ItemController {

    private final ItemService itemService;
    private final ImageStorageService imageStorageService;

    public ItemController(ItemService itemService, ImageStorageService imageStorageService) {
        this.itemService = itemService;
        this.imageStorageService = imageStorageService;
    }

    @GetMapping
    public List<ItemDto> findAll(@RequestParam(required = false) Category category) {
        return itemService.findAll(category);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ItemDto create(@Valid @RequestBody CreateItemRequest request) {
        return itemService.create(request);
    }

    @PostMapping("/import")
    @ResponseStatus(HttpStatus.CREATED)
    public List<ItemDto> importAll(@Valid @RequestBody List<@Valid CreateItemRequest> requests) {
        return itemService.importAll(requests);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        itemService.delete(id);
    }

    @PostMapping("/{id}/image")
    public ItemDto uploadImage(@PathVariable UUID id, @RequestParam MultipartFile file) {
        String imageUrl = imageStorageService.store(id, file);
        return itemService.setImageUrl(id, imageUrl);
    }
}
