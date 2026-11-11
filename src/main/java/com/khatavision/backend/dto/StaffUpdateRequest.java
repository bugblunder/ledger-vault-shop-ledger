package com.khatavision.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record StaffUpdateRequest(
        @NotBlank
        @Size(min = 3, max = 20)
        String username,

        @NotBlank
        @Email
        String email,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
        String phoneNumber,

        @NotBlank(message = "Role is required")
        @Pattern(regexp = "(?i)MANAGER|DATA_ENTRY|VIEWER", message = "Staff role must be MANAGER, DATA_ENTRY, or VIEWER")
        String role
) {}