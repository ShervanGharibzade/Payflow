package com.payflow.auth.service;

import com.payflow.auth.entity.OutboxEvent;
import com.payflow.auth.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.TimeUnit;

@Component
@ConditionalOnProperty(name = "payflow.outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxPublisher(OutboxEventRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void publish() {
        for (OutboxEvent e : repository.findTop50ByPublishedAtIsNullOrderByCreatedAtAsc()) {
            try {
                kafka.send(e.getTopic(), e.getMessageKey(), e.getPayload()).get(5, TimeUnit.SECONDS);
                e.markPublished();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                log.warn("Outbox publish failed for {}, will retry: {}", e.getId(), ex.getMessage());
                return; // ترتیب حفظ شود؛ تیک بعدی دوباره تلاش می‌کند
            }
        }
    }
}