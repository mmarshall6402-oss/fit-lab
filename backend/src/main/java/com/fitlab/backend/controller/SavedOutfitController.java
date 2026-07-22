package com.fitlab.backend.controller;

import com.fitlab.backend.dto.CommentDto;
import com.fitlab.backend.dto.CreateCommentRequest;
import com.fitlab.backend.dto.SaveOutfitRequest;
import com.fitlab.backend.dto.SavedOutfitDto;
import com.fitlab.backend.service.SavedOutfitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The community gallery: save a completed outfit, like it, comment on it. No login required. */
@RestController
@RequestMapping("/fits")
public class SavedOutfitController {

    private final SavedOutfitService savedOutfitService;

    public SavedOutfitController(SavedOutfitService savedOutfitService) {
        this.savedOutfitService = savedOutfitService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SavedOutfitDto save(@Valid @RequestBody SaveOutfitRequest request) {
        return savedOutfitService.save(request);
    }

    @GetMapping
    public List<SavedOutfitDto> list() {
        return savedOutfitService.listAll();
    }

    @GetMapping("/{id}")
    public SavedOutfitDto get(@PathVariable UUID id) {
        return savedOutfitService.get(id);
    }

    @PostMapping("/{id}/like")
    public SavedOutfitDto like(@PathVariable UUID id) {
        return savedOutfitService.like(id);
    }

    @GetMapping("/{id}/comments")
    public List<CommentDto> comments(@PathVariable UUID id) {
        return savedOutfitService.listComments(id);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public CommentDto addComment(@PathVariable UUID id, @Valid @RequestBody CreateCommentRequest request) {
        return savedOutfitService.addComment(id, request);
    }
}
