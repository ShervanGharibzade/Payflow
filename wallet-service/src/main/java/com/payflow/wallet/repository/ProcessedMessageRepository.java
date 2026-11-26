package com.payflow.wallet.repository;

import com.payflow.wallet.entity.ProcessedMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedMessageRepository
        extends JpaRepository<ProcessedMessage, String> {
}