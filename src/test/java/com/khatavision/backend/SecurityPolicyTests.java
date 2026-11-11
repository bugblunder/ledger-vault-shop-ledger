package com.khatavision.backend;

import com.khatavision.backend.config.DataInitializer;
import com.khatavision.backend.entity.*;
import com.khatavision.backend.repository.RoleRepository;
import com.khatavision.backend.repository.UserRepository;
import com.khatavision.backend.security.StaffRolePolicy;
import com.khatavision.backend.security.jwt.JwtUtils;
import com.khatavision.backend.security.services.UserDetailsImpl;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class SecurityPolicyTests {
    private final byte[] key = "test-only-key-never-use-in-production-123456".getBytes(StandardCharsets.UTF_8);

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"ADMIN", "OWNER", "ROLE_ADMIN", "unknown", " admin "})
    void staffAllowlistRejectsPrivilegedAndInvalidRoles(String role) {
        assertThatThrownBy(() -> StaffRolePolicy.parse(role)).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void staffAllowlistAcceptsExistingCaseInsensitiveContract() {
        assertThat(StaffRolePolicy.parse("manager")).isEqualTo(ERole.ROLE_MANAGER);
        assertThat(StaffRolePolicy.parse("data_entry")).isEqualTo(ERole.ROLE_DATA_ENTRY);
        assertThat(StaffRolePolicy.parse("VIEWER")).isEqualTo(ERole.ROLE_VIEWER);
    }

    @Test
    void defaultStartupCreatesNoAdministrator() {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        new DataInitializer(roles, users, encoder, false, "", "", "").run();
        verifyNoInteractions(users, encoder);
        verify(roles, times(5)).save(any(Role.class));
    }

    @Test
    void bootstrapRejectsAnExistingNonAdmin() {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(users.findByUsername("bootstrap")).thenReturn(Optional.of(new User()));
        assertThatThrownBy(() -> new DataInitializer(roles, users, encoder, true,
                "bootstrap", "bootstrap@example.test", "long-test-password-123").run())
                .isInstanceOf(IllegalStateException.class);
        verify(users, never()).save(any());
        verifyNoInteractions(encoder);
    }

    @Test
    void explicitBootstrapHashesPasswordAndAssignsOnlyAdmin() {
        RoleRepository roles = mock(RoleRepository.class);
        UserRepository users = mock(UserRepository.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        Role adminRole = new Role();
        adminRole.setName(ERole.ROLE_ADMIN);
        when(roles.findByName(ERole.ROLE_ADMIN)).thenReturn(Optional.of(adminRole));
        when(encoder.encode("long-test-password-123")).thenReturn("hashed");
        new DataInitializer(roles, users, encoder, true, "bootstrap", "bootstrap@example.test",
                "long-test-password-123").run();
        verify(users).save(argThat(u -> u.getPassword().equals("hashed")
                && u.getRoles().equals(Set.of(adminRole))));
    }

    @Test
    void jwtRejectsWeakKeysAndInvalidLifetimeAtStartup() {
        assertThatThrownBy(() -> new JwtUtils("c2hvcnQ=", 1000)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtUtils(encodedKey(), 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new JwtUtils(encodedKey(), 86400001)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void jwtAcceptsGeneratedTokenAndRejectsInvalidExpiredUnsignedAndWronglySignedTokens() {
        JwtUtils jwt = new JwtUtils(encodedKey(), 3600000);
        UserDetailsImpl principal = new UserDetailsImpl(1L, "owner", "owner@example.test", "unused", List.of());
        String valid = jwt.generateJwtToken(new UsernamePasswordAuthenticationToken(principal, null, List.of()));
        assertThat(jwt.validateJwtToken(valid)).isTrue();
        assertThat(jwt.getUserIdFromJwtToken(valid)).isEqualTo(1L);
        String expired = Jwts.builder().setSubject("owner").setExpiration(new Date(0))
                .signWith(Keys.hmacShaKeyFor(key), SignatureAlgorithm.HS256).compact();
        String unsigned = Jwts.builder().setSubject("owner").compact();
        byte[] other = "another-test-key-never-use-in-production-000".getBytes(StandardCharsets.UTF_8);
        String wrong = Jwts.builder().setSubject("owner")
                .signWith(Keys.hmacShaKeyFor(other), SignatureAlgorithm.HS256).compact();
        String legacyNumericUsername = Jwts.builder().setSubject("1")
                .setExpiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor(key), SignatureAlgorithm.HS256).compact();
        for (String token : List.of(expired, unsigned, wrong, legacyNumericUsername, "not-a-jwt", "")) {
            assertThat(jwt.validateJwtToken(token)).isFalse();
        }
        assertThat(jwt.validateJwtToken(null)).isFalse();
    }

    private String encodedKey() {
        return Base64.getEncoder().encodeToString(key);
    }
}
