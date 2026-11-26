package com.payflow.auth.config;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;

import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    @Bean
    public RSAKey rsaKey(JwtProperties props) throws Exception {
        RSAPublicKey pub;
        RSAPrivateKey priv;

        if (notBlank(props.privateKey()) && notBlank(props.publicKey())) {
            KeyFactory kf = KeyFactory.getInstance("RSA");
            priv = (RSAPrivateKey) kf.generatePrivate(new PKCS8EncodedKeySpec(pem(props.privateKey())));
            pub = (RSAPublicKey) kf.generatePublic(new X509EncodedKeySpec(pem(props.publicKey())));
        } else {
            log.warn("No payflow.jwt keys configured - generating an EPHEMERAL RSA key pair. "
                    + "Tokens will be invalid after restart. Do not use outside local development.");
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair kp = gen.generateKeyPair();
            pub = (RSAPublicKey) kp.getPublic();
            priv = (RSAPrivateKey) kp.getPrivate();
        }
        return new RSAKey.Builder(pub).privateKey(priv).keyID(props.keyId()).build();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(RSAKey rsaKey) {
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    @Bean
    public JwtEncoder jwtEncoder(JWKSource<SecurityContext> jwkSource) {
        return new NimbusJwtEncoder(jwkSource);
    }

    @Bean
    public JwtDecoder jwtDecoder(RSAKey rsaKey, JwtProperties props) throws Exception {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withPublicKey(rsaKey.toRSAPublicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .build();
        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(props.issuer()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    /** Public JWKS document (no private material) for the gateway / other services. */
    public static JWKSet publicJwks(RSAKey rsaKey) {
        return new JWKSet(rsaKey.toPublicJWK());
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    private static byte[] pem(String pem) {
        String body = pem.replaceAll("-----[A-Z ]+-----", "").replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }
}
