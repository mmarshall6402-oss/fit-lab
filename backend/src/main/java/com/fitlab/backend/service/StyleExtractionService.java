package com.fitlab.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.fitlab.backend.domain.FeedbackTarget;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Extracts structured style preferences (likes, style tags, normalized
 * reasoning) from a user's free-text explanation of why they like an item or
 * an outfit, via the Claude Messages API (text-only, no image involved).
 *
 * Entirely optional: with no ANTHROPIC_API_KEY set, isEnabled() is false and
 * callers skip this service without attempting a network call. A model or
 * network failure is caught and logged - it never blocks a feedback submit,
 * since the user's raw text is always worth keeping even if extraction fails.
 */
@Service
public class StyleExtractionService {

    private static final Logger log = LoggerFactory.getLogger(StyleExtractionService.class);

    private final String model;
    private final AnthropicClient client;

    public StyleExtractionService(@Value("${fitlab.ai.style-model:claude-opus-5}") String model) {
        this.model = model;
        this.client = isApiKeyConfigured() ? AnthropicOkHttpClient.fromEnv() : null;
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** Empty when disabled, rawText is blank, or the API call fails. */
    public Optional<StyleExtractionSuggestion> extract(String rawText, FeedbackTarget target) {
        if (client == null || rawText == null || rawText.isBlank()) {
            return Optional.empty();
        }

        try {
            StructuredMessageCreateParams<StyleExtractionSuggestion> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(512L)
                    .outputConfig(StyleExtractionSuggestion.class)
                    .addUserMessage(promptFor(rawText, target))
                    .build();

            StructuredMessage<StyleExtractionSuggestion> response = client.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(StructuredTextBlock::text);
        } catch (RuntimeException e) {
            log.warn("Style preference extraction failed, keeping raw feedback only: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String promptFor(String rawText, FeedbackTarget target) {
        String subject = target == FeedbackTarget.ITEM ? "a specific clothing item" : "a full outfit combination";
        return "A user of a wardrobe-matching app just explained why they like " + subject + ". "
                + "Their words: \"" + rawText + "\". "
                + "Extract concrete things they say they like, style/vibe tags implied by their reasoning "
                + "(short lowercase tags in the same vocabulary as clothing \"vibes\" like street, clean, minimal, "
                + "grunge, luxury, sporty), and restate their core reasoning as one normalized sentence.";
    }

    private boolean isApiKeyConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }
}
