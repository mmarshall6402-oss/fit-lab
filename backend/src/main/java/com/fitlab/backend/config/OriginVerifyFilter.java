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
 * CloudFront routes API traffic to this backend, but that doesn't make the backend's own
 * eba-*.elasticbeanstalk.com URL private - it stays directly, publicly reachable, bypassing
 * CloudFront entirely. This is the backend's half of an origin-verify pattern: CloudFront sends a
 * shared secret as X-Origin-Verify on every request it forwards (see infra/cloudfront.tf); this
 * filter rejects anything missing it, so a request that skipped CloudFront gets a 403 instead of
 * a live response. Disabled entirely (see OriginVerifyProperties) when no secret is configured -
 * an explicit opt-in, not a silent fail-open.
 */
@Component
public class OriginVerifyFilter extends OncePerRequestFilter {

    private static final String HEADER = "X-Origin-Verify";

    private final OriginVerifyProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OriginVerifyFilter(OriginVerifyProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return properties.getSecret().isBlank() || "OPTIONS".equalsIgnoreCase(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided == null || !provided.equals(properties.getSecret())) {
            respondForbidden(response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void respondForbidden(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        Map<String, Object> body = Map.of(
                "timestamp", Instant.now().toString(),
                "status", HttpStatus.FORBIDDEN.value(),
                "error", "Direct access not allowed"
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
