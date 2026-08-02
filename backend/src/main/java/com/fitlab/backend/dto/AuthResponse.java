package com.fitlab.backend.dto;

public record AuthResponse(
        String token,
        UserDto user
) {
}
