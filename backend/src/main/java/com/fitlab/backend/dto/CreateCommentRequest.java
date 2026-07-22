package com.fitlab.backend.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCommentRequest(@NotBlank String body, String author) {
}
