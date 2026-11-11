package com.khatavision.backend.security;

import com.khatavision.backend.entity.ERole;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;

/** The same allowlist applies to both staff creation and staff updates. */
public final class StaffRolePolicy {
    private StaffRolePolicy() {}

    public static ERole parse(String role) {
        if (role != null) {
            switch (role.toUpperCase(Locale.ROOT)) {
                case "MANAGER": return ERole.ROLE_MANAGER;
                case "DATA_ENTRY": return ERole.ROLE_DATA_ENTRY;
                case "VIEWER": return ERole.ROLE_VIEWER;
                default: break;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Staff role must be MANAGER, DATA_ENTRY, or VIEWER.");
    }
}
