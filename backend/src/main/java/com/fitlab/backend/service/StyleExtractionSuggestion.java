package com.fitlab.backend.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/** Structured-output shape asked of the model when extracting style preferences from free-text reasoning. */
record StyleExtractionSuggestion(
        @JsonPropertyDescription("1-5 short lowercase phrases naming specific things the user says they like, e.g. \"oversized fits\", \"contrast stitching\", \"the color combo\"")
        List<String> likes,
        @JsonPropertyDescription("1-5 lowercase style/vibe tags implied by the reasoning, using the same vocabulary as clothing vibes, e.g. street, clean, minimal, grunge, luxury, sporty")
        List<String> style,
        @JsonPropertyDescription("One normalized sentence restating the user's core reasoning in clear, third-person style-profile language")
        String reasoning
) {
}
