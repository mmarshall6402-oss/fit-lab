package com.fitlab.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * fitlab.origin-verify.secret gates every request behind a shared secret CloudFront sends as
 * X-Origin-Verify (see infra/cloudfront.tf). Left blank (the default), the check is skipped
 * entirely - local dev, tests, and any deploy not yet sitting behind that CloudFront origin.
 */
@ConfigurationProperties(prefix = "fitlab.origin-verify")
public class OriginVerifyProperties {

    private String secret = "";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }
}
