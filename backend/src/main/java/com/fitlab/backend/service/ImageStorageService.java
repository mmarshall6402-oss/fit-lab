package com.fitlab.backend.service;

import com.fitlab.backend.config.StorageProperties;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Saves uploaded images to a directory outside the project (fitlab.upload-dir)
 * and serves them via the /uploads/** static handler (see WebConfig).
 * Swap-point: replace this class's save() with an S3 PutObject call (and
 * imageUrl with the resulting object URL/CDN path) to move to S3 - callers
 * are unaffected since they only depend on the returned URL.
 */
@Service
public class ImageStorageService {

    private final Path uploadDir;

    public ImageStorageService(StorageProperties storageProperties) {
        this.uploadDir = Path.of(storageProperties.getUploadDir());
        try {
            Files.createDirectories(uploadDir);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not create upload directory: " + uploadDir, e);
        }
    }

    /** Stores the file under a fresh UUID name and returns the public-facing URL path. */
    public String store(UUID itemId, MultipartFile file) {
        String extension = extensionOf(file.getOriginalFilename());
        String filename = itemId + extension;
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.getParent().equals(uploadDir)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store image for item " + itemId, e);
        }
        return "/uploads/" + filename;
    }

    private String extensionOf(String originalFilename) {
        if (originalFilename == null) {
            return ".jpg";
        }
        int dot = originalFilename.lastIndexOf('.');
        return dot >= 0 ? originalFilename.substring(dot) : ".jpg";
    }
}
