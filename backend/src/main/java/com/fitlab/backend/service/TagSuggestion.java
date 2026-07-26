package com.fitlab.backend.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Structured-output shape asked of the vision model when auto-tagging an uploaded photo. */
record TagSuggestion(
        @JsonPropertyDescription("1-3 lowercase dominant colors, e.g. black, white, red, beige, navy")
        List<String> colors,
        @JsonPropertyDescription("1-3 lowercase style vibes, e.g. street, clean, luxury, sporty, grunge, minimal, loud, hype")
        List<String> vibes
) {
}
