package com.fitlab.backend;

import com.fitlab.backend.config.AdminProperties;
import com.fitlab.backend.config.JwtProperties;
import com.fitlab.backend.config.OriginVerifyProperties;
import com.fitlab.backend.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ StorageProperties.class, AdminProperties.class, JwtProperties.class, OriginVerifyProperties.class })
public class FitLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(FitLabApplication.class, args);
    }
}
