package com.fitlab.backend.service;

import com.fitlab.backend.domain.Category;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

class ImageTaggingServiceTest {

    @Test
    void disabledWithNoApiKeyConfiguredAndNeverCallsOut() {
        assumeThat(System.getenv("ANTHROPIC_API_KEY")).as("test env should not carry a real API key").isNullOrEmpty();

        ImageTaggingService service = new ImageTaggingService("claude-opus-5");

        assertThat(service.isEnabled()).isFalse();
        assertThat(service.suggestTags(new byte[] {1, 2, 3}, "image/png", "shirt", Category.SHIRT)).isEmpty();
    }
}
