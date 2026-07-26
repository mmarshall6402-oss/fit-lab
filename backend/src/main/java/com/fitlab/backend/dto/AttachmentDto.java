package com.fitlab.backend.dto;

import com.fitlab.backend.domain.Attachment;

import java.util.UUID;

public record AttachmentDto(
        UUID id,
        String url,
        String filename,
        String contentType
) {
    public static AttachmentDto from(Attachment attachment) {
        return new AttachmentDto(
                attachment.getId(),
                attachment.getUrl(),
                attachment.getFilename(),
                attachment.getContentType()
        );
    }
}
