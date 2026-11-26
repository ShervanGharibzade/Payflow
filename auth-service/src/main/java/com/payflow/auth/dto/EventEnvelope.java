package com.payflow.auth.dto;

import java.time.Instant;

public record EventEnvelope<T>(
        String eventId,
        String eventType,
        int version,
        Instant occurredAt,
        T payload
) {}