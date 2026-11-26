package com.payflow.auth.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.auth.entity.OutboxEvent;
import com.payflow.auth.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;
    private final ObjectMapper mapper;

    public OutboxService(OutboxEventRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    // MANDATORY: اگر تراکنشی از قبل باز نباشد خطا می‌دهد؛ یعنی نمی‌شود اشتباهاً بیرون از تراکنش نوشت
    @Transactional(propagation = Propagation.MANDATORY)
    public void add(String topic, String key, String type, Object event) {
        try {
            repository.save(new OutboxEvent(topic, key, type, mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialize event " + type, e);
        }
    }
}