package com.payflow.auth.service;

import com.payflow.auth.config.KafkaTopics;
import com.payflow.auth.dto.*;
import com.payflow.auth.entity.User;
import com.payflow.auth.exception.EmailAlreadyRegisteredException;
import com.payflow.auth.exception.InvalidCredentialsException;
import com.payflow.auth.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final OutboxService outboxService;
    private final String dummyHash;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       OutboxService outboxService,
                       TokenService tokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.outboxService = outboxService;
        this.dummyHash = passwordEncoder.encode("not-a-real-password");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalize(request.email());

        if (userRepository.existsByEmail(email)) {
            throw new EmailAlreadyRegisteredException();
        }

        User user = new User();
        user.setFirstName(request.firstName().trim());
        user.setLastName(request.lastName().trim());
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));

        User saved = userRepository.save(user);

        outboxService.add(
                KafkaTopics.USER_REGISTERED,
                String.valueOf(saved.getId()),
                "UserRegistered",
                new EventEnvelope<>(
                        UUID.randomUUID().toString(),
                        "UserRegistered",
                        1,
                        Instant.now(),
                        new UserRegisteredEvent(saved.getId(), saved.getEmail())
                ));

        return toResponse(saved);
    }

    public TokenResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(normalize(request.email())).orElse(null);

        boolean passwordOk = passwordEncoder.matches(
                request.password(), user != null ? user.getPasswordHash() : dummyHash);

        if (user == null || !passwordOk || !user.isEnabled()) {
            throw new InvalidCredentialsException();
        }
        return tokenService.issue(user);
    }

    public TokenResponse refresh(String refreshToken) {
        return tokenService.refresh(refreshToken);
    }

    public void logout(String refreshToken) {
        tokenService.revoke(refreshToken);
    }

    @Transactional(readOnly = true)
    public UserResponse getCurrentUser(Long userId) {
        return userRepository.findById(userId)
                .filter(User::isEnabled)
                .map(AuthService::toResponse)
                .orElseThrow(InvalidCredentialsException::new);
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getFirstName(), u.getLastName(),
                u.getEmail(), u.getCreatedAt());
    }
}
