package com.fitlab.backend.service;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/** Structured-output shape asked of the model when judging one item against the user's style profile. */
record ProfileAffinitySuggestion(
        @JsonPropertyDescription("A number from -1.0 to 1.0 rating how well this item aligns with the user's style profile: +1.0 = strongly matches their likes/style tags, -1.0 = strongly resembles things they dislike, 0.0 = neutral or no clear signal")
        double affinityScore,
        @JsonPropertyDescription("One short clause explaining the score, e.g. \"matches their love of oversized silhouettes\" or \"clashes with their stated dislike of loud patterns\"; null if neutral")
        String rationale
) {
}
