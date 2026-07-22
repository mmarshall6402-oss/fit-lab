package com.fitlab.backend.controller;

import com.fitlab.backend.dto.OutfitDto;
import com.fitlab.backend.service.OutfitService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class OutfitController {

    private final OutfitService outfitService;

    public OutfitController(OutfitService outfitService) {
        this.outfitService = outfitService;
    }

    @GetMapping("/outfit/build")
    public OutfitDto build(@RequestParam UUID anchorId) {
        return outfitService.buildBest(anchorId);
    }
}
