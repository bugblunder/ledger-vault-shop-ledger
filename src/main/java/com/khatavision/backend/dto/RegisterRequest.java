package com.khatavision.backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 20)
        String username,

        @NotBlank @Size(max = 50) @Email
        String email,

        @NotBlank
        @Size(min = 6, message = "Password must be at least 6 characters long")
        @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "Password must contain at least one letter and one number")
        String password,

        @NotBlank
        @Pattern(regexp = "(?i)MANAGER|DATA_ENTRY|VIEWER", message = "Staff role must be MANAGER, DATA_ENTRY, or VIEWER")
        String role,

        @NotBlank(message = "Phone number is required")
        @Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
        String phoneNumber
) {}