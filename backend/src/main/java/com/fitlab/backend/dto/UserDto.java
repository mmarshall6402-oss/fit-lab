package com.fitlab.backend.dto;

import com.fitlab.backend.domain.User;

import java.util.UUID;

public record UserDto(
        UUID id,
        String email
) {
    public static UserDto from(User user) {
        return new UserDto(user.getId(), user.getEmail());
    }
}
