package com.khatavision.backend.dto;
public record LedgerCorrectionResponse(LedgerEntryResponse reversal, LedgerEntryResponse replacement) {}
