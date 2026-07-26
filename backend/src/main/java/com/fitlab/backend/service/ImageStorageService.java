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

    /** Stores the file under the item's id (one photo per item, overwriting any previous one) and returns the public-facing URL path. */
    public String store(UUID itemId, MultipartFile file) {
        return storeAs(itemId.toString() + extensionOf(file.getOriginalFilename()), file);
    }

    /** Stores the file under a fresh random name (many per item allowed) and returns the public-facing URL path. */
    public String storeAttachment(MultipartFile file) {
        return storeAs(UUID.randomUUID() + extensionOf(file.getOriginalFilename()), file);
    }

    private String storeAs(String filename, MultipartFile file) {
        Path target = uploadDir.resolve(filename).normalize();
        if (!target.getParent().equals(uploadDir)) {
            throw new IllegalArgumentException("Invalid file name");
        }
        try {
            file.transferTo(target);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store file " + filename, e);
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
