package com.fitlab.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** fitlab.admin.token gates every /admin/** endpoint; override via ADMIN_TOKEN in production. */
@ConfigurationProperties(prefix = "fitlab.admin")
public class AdminProperties {

    private String token = "changeme-admin-token";

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }
}
