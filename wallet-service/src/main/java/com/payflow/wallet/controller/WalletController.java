package com.payflow.wallet.controller;

import com.payflow.wallet.dto.DepositRequest;
import com.payflow.wallet.dto.WalletResponse;
import com.payflow.wallet.service.WalletService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/wallets")
public class WalletController {

    private final WalletService walletService;

    public WalletController(WalletService walletService) {
        this.walletService = walletService;
    }

    @GetMapping("/ping")
    public String ping() {
        return "wallet controller is active";
    }

    @GetMapping("/me")
    public WalletResponse me(@AuthenticationPrincipal Jwt jwt) {
        Long userId = Long.valueOf(jwt.getSubject());
        return walletService.getMyWallet(userId);
    }


    @PostMapping("/me/deposits")
    public WalletResponse deposit(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody DepositRequest request
    ) {
        Long userId = Long.valueOf(jwt.getSubject());

        return walletService.deposit(userId, request.amount());
    }
}