package com.khatavision.backend.dto;
import jakarta.validation.constraints.NotBlank;
public record CustomerRequest(
        @NotBlank(message = "Customer name cannot be blank")
        String name,
        String contactInfo,
        String relationshipType,
        String notes,
        Long shopId
) {}