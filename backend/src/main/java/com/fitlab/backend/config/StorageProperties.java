package com.fitlab.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** fitlab.upload-dir points outside the project (e.g. a user-home or ops-managed volume). */
@ConfigurationProperties(prefix = "fitlab")
public class StorageProperties {

    private String uploadDir = System.getProperty("user.home") + "/fitlab-uploads";

    public String getUploadDir() {
        return uploadDir;
    }

    public void setUploadDir(String uploadDir) {
        this.uploadDir = uploadDir;
    }
}
