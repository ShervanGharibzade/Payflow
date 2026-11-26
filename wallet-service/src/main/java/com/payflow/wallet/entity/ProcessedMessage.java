package com.payflow.wallet.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {

    @Id
    @Column(name = "event_id", length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    protected ProcessedMessage() {
    }

    public ProcessedMessage(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
    }

    @PrePersist
    void onCreate() {
        processedAt = LocalDateTime.now();
    }
}