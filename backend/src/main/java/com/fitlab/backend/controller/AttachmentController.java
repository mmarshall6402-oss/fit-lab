package com.fitlab.backend.controller;

import com.fitlab.backend.dto.AttachmentDto;
import com.fitlab.backend.security.CurrentUser;
import com.fitlab.backend.service.AttachmentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/items/{itemId}/attachments")
public class AttachmentController {

    private final AttachmentService attachmentService;

    public AttachmentController(AttachmentService attachmentService) {
        this.attachmentService = attachmentService;
    }

    @GetMapping
    public List<AttachmentDto> findAll(@PathVariable UUID itemId) {
        return attachmentService.findByItem(CurrentUser.id(), itemId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentDto create(@PathVariable UUID itemId, @RequestParam MultipartFile file) {
        return attachmentService.create(CurrentUser.id(), itemId, file);
    }

    @DeleteMapping("/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID itemId, @PathVariable UUID attachmentId) {
        attachmentService.delete(CurrentUser.id(), itemId, attachmentId);
    }
}
