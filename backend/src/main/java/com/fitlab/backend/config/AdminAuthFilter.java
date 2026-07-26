package com.fitlab.backend.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

/**
 * Gates every /admin/** request behind a shared secret sent as the
 * X-Admin-Token header. There are no user accounts yet, so this is
 * intentionally a single shared password rather than per-user auth -
 * good enough for one operator calibrating the app live, not meant to
 * scale past that.
 */
@Component
public class AdminAuthFilter extends OncePerRequestFilter {

    private static final String TOKEN_HEADER = "X-Admin-Token";

    private final AdminProperties adminProperties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminAuthFilter(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // CORS preflight requests never carry the custom header - let them through untouched.
        return "OPTIONS".equalsIgnoreCase(request.getMethod()) || !request.getRequestURI().startsWith("/admin/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(TOKEN_HEADER);
        if (provided == null || !provided.equals(adminProperties.getToken())) {
            respondUnauthorized(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void respondUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.UNAUTHORIZED.value(),
                "error", "Missing or invalid admin token"
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
