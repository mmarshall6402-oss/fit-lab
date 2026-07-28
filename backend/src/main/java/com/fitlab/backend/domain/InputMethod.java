package com.fitlab.backend.domain;

/**
 * How the user supplied their feedback text. VOICE means the browser's speech
 * recognition transcribed it client-side - no audio is ever sent to or stored
 * by the backend, only the resulting transcript.
 */
public enum InputMethod {
    TEXT,
    VOICE
}
