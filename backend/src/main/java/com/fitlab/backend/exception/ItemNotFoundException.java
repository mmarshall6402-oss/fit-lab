package com.fitlab.backend.exception;

import java.util.UUID;

public class ItemNotFoundException extends RuntimeException {
    public ItemNotFoundException(UUID id) {
        super("Item not found: " + id);
    }
}
