package com.khatavision.backend.dto;

import java.math.BigDecimal;

public record LedgerBalanceResponse(
        BigDecimal totalCredit,
        BigDecimal totalDebit,
        BigDecimal netBalance,
        String balanceStatus
) {}