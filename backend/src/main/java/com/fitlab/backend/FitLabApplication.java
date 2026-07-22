package com.fitlab.backend;

import com.fitlab.backend.config.StorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(StorageProperties.class)
public class FitLabApplication {
    public static void main(String[] args) {
        SpringApplication.run(FitLabApplication.class, args);
    }
}
