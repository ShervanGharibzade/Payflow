package com.payflow.wallet.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.wallet.entity.ProcessedMessage;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.repository.ProcessedMessageRepository;
import com.payflow.wallet.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
public class UserRegisteredConsumer {

    private static final Logger log =
            LoggerFactory.getLogger(UserRegisteredConsumer.class);

    private final ObjectMapper objectMapper;
    private final WalletRepository walletRepository;
    private final ProcessedMessageRepository processedMessageRepository;

    public UserRegisteredConsumer(
            ObjectMapper objectMapper,
            WalletRepository walletRepository,
            ProcessedMessageRepository processedMessageRepository
    ) {
        this.objectMapper = objectMapper;
        this.walletRepository = walletRepository;
        this.processedMessageRepository = processedMessageRepository;
    }

    @KafkaListener(topics = "payflow.user.registered.v1")
    @Transactional
    public void consume(String message) throws Exception {
        UserRegisteredEnvelope event =
                objectMapper.readValue(message, UserRegisteredEnvelope.class);

        if (processedMessageRepository.existsById(event.eventId())) {
            log.info("Event already processed: {}", event.eventId());
            return;
        }

        walletRepository.findByUserId(event.payload().userId())
                .orElseGet(() -> {
                    Wallet wallet = Wallet.forUser(event.payload().userId());
                    log.info("Creating wallet {} for user {}",
                            wallet.getId(),
                            event.payload().userId());
                    return walletRepository.save(wallet);
                });

        processedMessageRepository.save(
                new ProcessedMessage(event.eventId(), event.eventType())
        );
    }

    private record UserRegisteredEnvelope(
            String eventId,
            String eventType,
            int version,
            Instant occurredAt,
            UserRegisteredPayload payload
    ) {
    }

    private record UserRegisteredPayload(
            Long userId,
            String email
    ) {
    }
}