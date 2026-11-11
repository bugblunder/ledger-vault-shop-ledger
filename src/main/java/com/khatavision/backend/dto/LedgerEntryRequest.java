package com.khatavision.backend.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record LedgerEntryRequest(
        @NotNull(message = "Entry type must not be null")
        @NotBlank(message = "Entry type must be CREDIT or DEBIT")
        @Pattern(regexp = "(?i)CREDIT|DEBIT", message = "Entry type must be CREDIT or DEBIT")
        String entryType,
        @NotNull(message = "Amount cannot be null")
        @DecimalMin(value = "0.01", message = "Amount must be greater than zero")
        @Digits(integer = 8, fraction = 2, message = "Amount supports eight integer digits and two decimal places")
        BigDecimal amount,
        @Size(max = 255)
        String description
) {}