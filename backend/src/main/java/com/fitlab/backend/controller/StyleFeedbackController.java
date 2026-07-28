package com.fitlab.backend.controller;

import com.fitlab.backend.dto.StyleFeedbackDto;
import com.fitlab.backend.dto.StyleProfileDto;
import com.fitlab.backend.dto.SubmitItemFeedbackRequest;
import com.fitlab.backend.dto.SubmitOutfitFeedbackRequest;
import com.fitlab.backend.service.StyleFeedbackService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
public class StyleFeedbackController {

    private final StyleFeedbackService styleFeedbackService;

    public StyleFeedbackController(StyleFeedbackService styleFeedbackService) {
        this.styleFeedbackService = styleFeedbackService;
    }

    @PostMapping("/items/{id}/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public StyleFeedbackDto submitItemFeedback(@PathVariable UUID id, @Valid @RequestBody SubmitItemFeedbackRequest request) {
        return styleFeedbackService.submitForItem(id, request.rawText(), request.inputMethod(), request.sentiment());
    }

    @PostMapping("/outfit/feedback")
    @ResponseStatus(HttpStatus.CREATED)
    public StyleFeedbackDto submitOutfitFeedback(@Valid @RequestBody SubmitOutfitFeedbackRequest request) {
        return styleFeedbackService.submitForOutfit(
                request.shirtId(), request.bottomId(), request.shoesId(),
                request.rawText(), request.inputMethod(), request.sentiment());
    }

    @GetMapping("/style-profile")
    public StyleProfileDto getProfile() {
        return styleFeedbackService.getProfile();
    }

    @GetMapping("/style-profile/history")
    public List<StyleFeedbackDto> getHistory() {
        return styleFeedbackService.getHistory();
    }
}
