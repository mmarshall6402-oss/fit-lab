package com.fitlab.backend.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fitlab.backend.domain.Category;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.Set;

/**
 * Inbound item payload. Any "id" field present in the JSON (e.g. from a
 * copy-pasted export) is intentionally ignored - ids are always generated
 * server-side.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CreateItemRequest(
        @NotBlank String name,
        @NotNull Category category,
        String imageUrl,
        Set<String> colors,
        Set<String> vibes
) {
}
