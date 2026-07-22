package com.fitlab.backend.exception;

/** Thrown when the catalog lacks at least one item in some category needed to build an outfit. */
public class InsufficientCatalogException extends RuntimeException {
    public InsufficientCatalogException(String message) {
        super(message);
    }
}
