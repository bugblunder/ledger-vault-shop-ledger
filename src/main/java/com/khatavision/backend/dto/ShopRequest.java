package com.khatavision.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ShopRequest(
        @NotBlank @Size(min = 2, max = 100) String name,
        @Size(max = 20) String contactNumber
) {}