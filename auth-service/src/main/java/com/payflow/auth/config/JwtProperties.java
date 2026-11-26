package com.payflow.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * privateKey / publicKey are PEM (PKCS#8 / X.509). If both are blank an ephemeral
 * key pair is generated at startup (dev only: tokens die on restart).
 */
@ConfigurationProperties(prefix = "payflow.jwt")
public record JwtProperties(
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl,
        String keyId,
        String privateKey,
        String publicKey
) {
    public JwtProperties {
        if (issuer == null || issuer.isBlank()) issuer = "payflow-auth";
        if (accessTokenTtl == null) accessTokenTtl = Duration.ofMinutes(15);
        if (refreshTokenTtl == null) refreshTokenTtl = Duration.ofDays(7);
        if (keyId == null || keyId.isBlank()) keyId = "payflow-key-1";
    }
}
