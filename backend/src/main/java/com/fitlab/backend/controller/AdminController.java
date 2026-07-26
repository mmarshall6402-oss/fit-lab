package com.fitlab.backend.controller;

import com.fitlab.backend.dto.ScoringConfigDto;
import com.fitlab.backend.dto.UpdateScoringConfigRequest;
import com.fitlab.backend.service.ScoringConfigService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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

    public AdminController(ScoringConfigService scoringConfigService) {
        this.scoringConfigService = scoringConfigService;
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
}
