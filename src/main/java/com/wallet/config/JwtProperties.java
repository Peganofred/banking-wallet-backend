package com.wallet.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Maps the `app.jwt` block from application.yml into a typed object.
 * secret       -> HS256 signing key
 * expirationMs -> how long a token stays valid (ms)
 */
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        String secret,
        long expirationMs
) {
}
