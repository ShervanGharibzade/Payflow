package com.payflow.auth.dto;

public record UserRegisteredEvent(
        Long userId,
        String email
) {}