package com.fitlab.backend.dto;

import com.fitlab.backend.domain.Comment;

import java.time.Instant;
import java.util.UUID;

public record CommentDto(UUID id, String author, String body, Instant createdAt) {
    public static CommentDto from(Comment comment) {
        return new CommentDto(comment.getId(), comment.getAuthor(), comment.getBody(), comment.getCreatedAt());
    }
}
