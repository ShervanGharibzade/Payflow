package com.payflow.auth.service;

import com.payflow.auth.config.JwtProperties;
import com.payflow.auth.dto.TokenResponse;
import com.payflow.auth.entity.RefreshToken;
import com.payflow.auth.entity.User;
import com.payflow.auth.exception.InvalidRefreshTokenException;
import com.payflow.auth.repository.RefreshTokenRepository;
import com.payflow.auth.repository.UserRepository;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties props;
    private final RefreshTokenRepository refreshTokens;
    private final UserRepository users;
    private final SecureRandom random = new SecureRandom();

    public TokenService(JwtEncoder jwtEncoder, JwtProperties props,
                        RefreshTokenRepository refreshTokens, UserRepository users) {
        this.jwtEncoder = jwtEncoder;
        this.props = props;
        this.refreshTokens = refreshTokens;
        this.users = users;
    }

    @Transactional
    public TokenResponse issue(User user) {
        return new TokenResponse(
                accessToken(user),
                newRefreshToken(user.getId()),
                "Bearer",
                props.accessTokenTtl().toSeconds());
    }

    /**
     * Rotates the refresh token. Presenting an already-revoked token is treated as theft:
     * every session of that user is revoked. noRollbackFor keeps that revocation committed.
     */
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public TokenResponse refresh(String rawToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(rawToken))
                .orElseThrow(InvalidRefreshTokenException::new);

        if (stored.isRevoked()) {
            refreshTokens.revokeAllForUser(stored.getUserId());
            throw new InvalidRefreshTokenException();
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            throw new InvalidRefreshTokenException();
        }

        User user = users.findById(stored.getUserId())
                .filter(User::isEnabled)
                .orElseThrow(InvalidRefreshTokenException::new);

        stored.revoke();
        return issue(user);
    }

    @Transactional
    public void revoke(String rawToken) {
        refreshTokens.findByTokenHash(hash(rawToken)).ifPresent(RefreshToken::revoke);
    }

    private String accessToken(User user) {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(props.issuer())
                .subject(String.valueOf(user.getId()))
                .issuedAt(now)
                .expiresAt(now.plus(props.accessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("email", user.getEmail())
                .claim("role", user.getRole())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    private String newRefreshToken(Long userId) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String raw = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokens.save(new RefreshToken(userId, hash(raw),
                LocalDateTime.now().plus(props.refreshTokenTtl())));
        return raw;
    }

    static String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
