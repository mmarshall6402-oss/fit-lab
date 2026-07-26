package com.fitlab.backend.service;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.fitlab.backend.domain.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;
import java.util.Optional;

/**
 * Infers colors/vibes from an uploaded photo via the Claude Messages API
 * (vision + structured output) so bulk-uploaded items aren't left with empty
 * tags, which previously made them unmatchable by the tag-overlap scorer.
 *
 * Entirely optional: with no ANTHROPIC_API_KEY set, isEnabled() is false and
 * every caller skips this service without attempting a network call. A model
 * or network failure is caught and logged - it never blocks an upload.
 */
@Service
public class ImageTaggingService {

    private static final Logger log = LoggerFactory.getLogger(ImageTaggingService.class);

    private final String model;
    private final AnthropicClient client;

    public ImageTaggingService(@Value("${fitlab.ai.tagging-model:claude-opus-5}") String model) {
        this.model = model;
        this.client = isApiKeyConfigured() ? AnthropicOkHttpClient.fromEnv() : null;
    }

    public boolean isEnabled() {
        return client != null;
    }

    /** Empty when disabled, the content type isn't a supported image format, or the API call fails. */
    public Optional<TagSuggestion> suggestTags(byte[] imageBytes, String contentType, String itemName, Category category) {
        if (client == null) {
            return Optional.empty();
        }
        Base64ImageSource.MediaType mediaType = mediaTypeFor(contentType);
        if (mediaType == null) {
            return Optional.empty();
        }

        try {
            StructuredMessageCreateParams<TagSuggestion> params = MessageCreateParams.builder()
                    .model(model)
                    .maxTokens(512L)
                    .outputConfig(TagSuggestion.class)
                    .addUserMessageOfBlockParams(List.of(
                            ContentBlockParam.ofImage(ImageBlockParam.builder()
                                    .source(Base64ImageSource.builder()
                                            .mediaType(mediaType)
                                            .data(Base64.getEncoder().encodeToString(imageBytes))
                                            .build())
                                    .build()),
                            ContentBlockParam.ofText(TextBlockParam.builder()
                                    .text(promptFor(itemName, category))
                                    .build())
                    ))
                    .build();

            StructuredMessage<TagSuggestion> response = client.messages().create(params);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .findFirst()
                    .map(StructuredTextBlock::text);
        } catch (RuntimeException e) {
            log.warn("Image auto-tagging failed, leaving item untagged: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String promptFor(String itemName, Category category) {
        String subject = itemName == null || itemName.isBlank() ? "" : " named \"" + itemName + "\"";
        return "This photo is a " + category + " item" + subject + " for a wardrobe-matching app. "
                + "Identify its dominant colors and its style \"vibes\" so it can be matched against other pieces.";
    }

    private Base64ImageSource.MediaType mediaTypeFor(String contentType) {
        if (contentType == null) {
            return null;
        }
        return switch (contentType) {
            case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif" -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            default -> null;
        };
    }

    private boolean isApiKeyConfigured() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        return key != null && !key.isBlank();
    }
}
