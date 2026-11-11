package com.khatavision.backend.dto;

import com.khatavision.backend.entity.Shop;

public record ShopResponse(
        Long id,
        String name,
        String contactNumber,
        Long ownerId,
        java.time.Instant archivedAt,
        Long archivedByUserId,
        String archiveReason
) {
    public static ShopResponse fromEntity(Shop shop) {
        return new ShopResponse(
                shop.getId(),
                shop.getName(),
                shop.getContactNumber(),
                shop.getOwner().getId(),
                shop.getArchivedAt(), shop.getArchivedByUserId(), shop.getArchiveReason()
        );
    }
}