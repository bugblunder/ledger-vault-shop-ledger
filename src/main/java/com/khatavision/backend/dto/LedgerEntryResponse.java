package com.khatavision.backend.dto;

import com.khatavision.backend.entity.LedgerEntry;
import java.math.BigDecimal;
import java.time.Instant;

public record LedgerEntryResponse(
        Long id,
        String entryType,
        BigDecimal amount,
        String description,
        String imageUrl,
        Long customerId,
        Long recordedByUserId,
        Instant createdAt,
        String entryKind,
        Long reversesEntryId,
        Long replacesEntryId,
        String reason
) {
    public static LedgerEntryResponse fromEntity(LedgerEntry entry) {
        return new LedgerEntryResponse(
                entry.getId(),
                entry.getEntryType().name(),
                entry.getAmount(),
                entry.getDescription(),
                entry.getImageUrl(),
                entry.getCustomer().getId(),
                entry.getRecordedBy().getId(),
                entry.getCreatedAt(),
                entry.getEntryKind().name(),
                entry.getReversesEntryId(),
                entry.getReplacesEntryId(),
                entry.getReason()
        );
    }
}