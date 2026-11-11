package com.khatavision.backend.dto;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
public record LedgerCorrectionRequest(
        @NotNull @Valid LedgerEntryRequest replacement,
        @NotBlank @Size(max = 500) String reason) {}
