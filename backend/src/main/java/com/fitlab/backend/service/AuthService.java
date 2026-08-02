package com.fitlab.backend.service;

import com.fitlab.backend.domain.User;
import com.fitlab.backend.dto.AuthResponse;
import com.fitlab.backend.dto.UserDto;
import com.fitlab.backend.exception.EmailAlreadyInUseException;
import com.fitlab.backend.exception.InvalidCredentialsException;
import com.fitlab.backend.repository.UserRepository;
import com.fitlab.backend.security.JwtService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(String email, String password) {
        String normalized = normalize(email);
        if (userRepository.existsByEmail(normalized)) {
            throw new EmailAlreadyInUseException(normalized);
        }
        User user = userRepository.save(User.builder()
                .id(UUID.randomUUID())
                .email(normalized)
                .passwordHash(passwordEncoder.encode(password))
                .createdAt(Instant.now())
                .build());
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(normalize(email)).orElseThrow(InvalidCredentialsException::new);
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }
        return toAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserDto getUser(UUID id) {
        return UserDto.from(userRepository.findById(id).orElseThrow(InvalidCredentialsException::new));
    }

    private AuthResponse toAuthResponse(User user) {
        String token = jwtService.generateToken(user.getId(), user.getEmail());
        return new AuthResponse(token, UserDto.from(user));
    }

    private String normalize(String email) {
        return email.trim().toLowerCase();
    }
}
