package com.khatavision.backend.dto;

import com.khatavision.backend.entity.StaffAssignment;
import com.khatavision.backend.entity.User;

import java.util.List;
import java.util.stream.Collectors;

public record UserResponse(
        Long id,
        String username,
        String email,
        List<String> roles,
        String phoneNumber,
        Long assignedShopId,
        String assignedShopName,
        java.time.Instant archivedAt,
        Long archivedByUserId,
        String archiveReason
) {
    public static UserResponse fromEntity(User user) {
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roleNames,
                user.getPhoneNumber(),
                null,
                null,
                user.getArchivedAt(), user.getArchivedByUserId(), user.getArchiveReason()
        );
    }

    public static UserResponse fromEntity(User user, StaffAssignment assignment) {
        List<String> roleNames = user.getRoles().stream()
                .map(role -> role.getName().name())
                .collect(Collectors.toList());

        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                roleNames,
                user.getPhoneNumber(),
                assignment.getShop() != null ? assignment.getShop().getId() : null,
                assignment.getShop() != null ? assignment.getShop().getName() : null,
                user.getArchivedAt(), user.getArchivedByUserId(), user.getArchiveReason()
        );
    }
}