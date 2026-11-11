package com.khatavision.backend.dto;

import com.khatavision.backend.entity.Customer;

public record CustomerResponse(
        Long id,
        String name,
        String contactInfo,
        String relationshipType,
        String notes,
        Long shopId,
        java.time.Instant archivedAt,
        Long archivedByUserId,
        String archiveReason
) {
    public static CustomerResponse fromEntity(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getName(),
                customer.getContactInfo(),
                customer.getRelationshipType(),
                customer.getNotes(),
                customer.getShop().getId(),
                customer.getArchivedAt(), customer.getArchivedByUserId(), customer.getArchiveReason()
        );
    }
}