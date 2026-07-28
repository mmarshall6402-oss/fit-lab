package com.fitlab.backend.controller;

import com.fitlab.backend.dto.ScoringConfigDto;
import com.fitlab.backend.dto.UpdateScoringConfigRequest;
import com.fitlab.backend.service.ItemService;
import com.fitlab.backend.service.ScoringConfigService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only endpoints for live-calibrating outfit scoring. Every route here
 * is gated by AdminAuthFilter (X-Admin-Token header) - none of it is reachable
 * without the shared admin token.
 */
@RestController
@RequestMapping("/admin")
public class AdminController {

    private final ScoringConfigService scoringConfigService;
    private final ItemService itemService;

    public AdminController(ScoringConfigService scoringConfigService, ItemService itemService) {
        this.scoringConfigService = scoringConfigService;
        this.itemService = itemService;
    }

    @GetMapping("/scoring-config")
    public ScoringConfigDto getScoringConfig() {
        return ScoringConfigDto.from(scoringConfigService.current());
    }

    @PutMapping("/scoring-config")
    public ScoringConfigDto updateScoringConfig(@Valid @RequestBody UpdateScoringConfigRequest request) {
        return ScoringConfigDto.from(scoringConfigService.update(request));
    }

    @PostMapping("/scoring-config/reset")
    public ScoringConfigDto resetScoringConfig() {
        return ScoringConfigDto.from(scoringConfigService.resetToDefaults());
    }

    /** Deletes every item in the catalog. Irreversible - items must be re-added or re-imported afterward. */
    @DeleteMapping("/items")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void wipeItems() {
        itemService.deleteAll();
    }
}
