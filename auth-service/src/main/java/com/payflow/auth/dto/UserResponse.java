package com.payflow.auth.dto;

import java.time.LocalDateTime;

public record UserResponse(
        Long id,
        String firstName,
        String lastName,
        String email,
        LocalDateTime createdAt
) {}
