package com.payflow.wallet.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        String currency,
        BigDecimal availableBalance,
        BigDecimal reservedBalance
) {
}