package com.payflow.wallet.service;

import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.entity.Wallet;
import com.payflow.wallet.repository.WalletRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;

@Service
public class WalletService {

    private final WalletRepository walletRepository;

    public WalletService(WalletRepository walletRepository) {
        this.walletRepository = walletRepository;
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(Long userId) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found for this user"
                ));

        return new WalletResponse(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getAvailableBalance(),
                wallet.getReservedBalance()
        );
    }


    @Transactional
    public WalletResponse deposit(Long userId, BigDecimal amount) {
        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Wallet not found for this user"
                ));

        wallet.deposit(amount);

        return new WalletResponse(
                wallet.getId(),
                wallet.getCurrency(),
                wallet.getAvailableBalance(),
                wallet.getReservedBalance()
        );
    }
}