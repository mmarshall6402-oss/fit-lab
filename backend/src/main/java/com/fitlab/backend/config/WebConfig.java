package com.fitlab.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.util.Arrays;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final StorageProperties storageProperties;

    /** Comma-separated origin patterns; always includes localhost for dev regardless of what's configured. */
    @Value("${fitlab.frontend-origin:}")
    private String frontendOrigin;

    public WebConfig(StorageProperties storageProperties) {
        this.storageProperties = storageProperties;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins())
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                .allowedHeaders("*");
    }

    private String[] allowedOrigins() {
        if (frontendOrigin.isBlank()) {
            return new String[] { "http://localhost:*" };
        }
        String[] extra = frontendOrigin.split(",");
        String[] origins = Arrays.copyOf(extra, extra.length + 1);
        origins[extra.length] = "http://localhost:*";
        return origins;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = Path.of(storageProperties.getUploadDir()).toUri().toString();
        registry.addResourceHandler("/uploads/**").addResourceLocations(location);
    }
}
