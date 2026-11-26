package com.payflow.auth.controller;

import com.nimbusds.jose.jwk.RSAKey;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Publishes the public signing key so the gateway and other services can verify tokens. */
@RestController
public class JwksController {

    private final Map<String, Object> jwks;

    public JwksController(RSAKey rsaKey) {
        this.jwks = new com.nimbusds.jose.jwk.JWKSet(rsaKey.toPublicJWK()).toJSONObject();
    }

    @GetMapping("/.well-known/jwks.json")
    public Map<String, Object> jwks() {
        return jwks;
    }
}
