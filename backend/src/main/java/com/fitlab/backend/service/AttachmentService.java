package com.fitlab.backend.service;

import com.fitlab.backend.domain.Attachment;
import com.fitlab.backend.domain.Item;
import com.fitlab.backend.dto.AttachmentDto;
import com.fitlab.backend.exception.AttachmentNotFoundException;
import com.fitlab.backend.exception.ItemNotFoundException;
import com.fitlab.backend.repository.AttachmentRepository;
import com.fitlab.backend.repository.ItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

/** General file attachments (receipts, size charts, PDFs, etc.), distinct from an item's single main photo. */
@Service
public class AttachmentService {

    private final AttachmentRepository attachmentRepository;
    private final ItemRepository itemRepository;
    private final ImageStorageService storage;

    public AttachmentService(AttachmentRepository attachmentRepository, ItemRepository itemRepository, ImageStorageService storage) {
        this.attachmentRepository = attachmentRepository;
        this.itemRepository = itemRepository;
        this.storage = storage;
    }

    @Transactional(readOnly = true)
    public List<AttachmentDto> findByItem(UUID itemId) {
        requireItem(itemId);
        return attachmentRepository.findByItemId(itemId).stream().map(AttachmentDto::from).toList();
    }

    @Transactional
    public AttachmentDto create(UUID itemId, MultipartFile file) {
        Item item = requireItem(itemId);
        String url = storage.storeAttachment(file);
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "file";
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        Attachment attachment = Attachment.builder()
                .id(UUID.randomUUID())
                .item(item)
                .url(url)
                .filename(filename)
                .contentType(contentType)
                .build();

        return AttachmentDto.from(attachmentRepository.save(attachment));
    }

    @Transactional
    public void delete(UUID itemId, UUID attachmentId) {
        requireItem(itemId);
        Attachment attachment = attachmentRepository.findById(attachmentId)
                .filter(a -> a.getItem().getId().equals(itemId))
                .orElseThrow(() -> new AttachmentNotFoundException(attachmentId));
        attachmentRepository.delete(attachment);
    }

    private Item requireItem(UUID itemId) {
        return itemRepository.findById(itemId).orElseThrow(() -> new ItemNotFoundException(itemId));
    }
}
