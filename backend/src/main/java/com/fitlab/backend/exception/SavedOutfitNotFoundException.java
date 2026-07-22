package com.fitlab.backend.exception;

import java.util.UUID;

public class SavedOutfitNotFoundException extends RuntimeException {
    public SavedOutfitNotFoundException(UUID id) {
        super("Saved outfit not found: " + id);
    }
}
