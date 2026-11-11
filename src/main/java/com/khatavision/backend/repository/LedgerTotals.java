package com.khatavision.backend.repository;

import java.math.BigDecimal;

public interface LedgerTotals {
    BigDecimal getTotalDebit();
    BigDecimal getTotalCredit();

    default BigDecimal balance() {
        return getTotalDebit().subtract(getTotalCredit());
    }
}
