package com.fitlab.backend.controller;

import com.fitlab.backend.dto.SaveOutfitRequest;
import com.fitlab.backend.dto.SavedOutfitDto;
import com.fitlab.backend.security.CurrentUser;
import com.fitlab.backend.service.SavedOutfitService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class SavedOutfitController {

    private final SavedOutfitService savedOutfitService;

    public SavedOutfitController(SavedOutfitService savedOutfitService) {
        this.savedOutfitService = savedOutfitService;
    }

    @PostMapping("/outfit/saved")
    @ResponseStatus(HttpStatus.CREATED)
    public SavedOutfitDto save(@Valid @RequestBody SaveOutfitRequest request) {
        return savedOutfitService.save(CurrentUser.id(), request.shirtId(), request.bottomId(), request.shoesId());
    }

    @GetMapping("/outfit/saved")
    public List<SavedOutfitDto> getAll() {
        return savedOutfitService.getAll(CurrentUser.id());
    }

    @DeleteMapping("/outfit/saved/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        savedOutfitService.delete(CurrentUser.id(), id);
    }
}
