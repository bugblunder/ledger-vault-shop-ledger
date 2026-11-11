package com.khatavision.backend.config;

import com.khatavision.backend.entity.ERole;
import com.khatavision.backend.entity.Role;
import com.khatavision.backend.entity.User;
import com.khatavision.backend.repository.RoleRepository;
import com.khatavision.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
public class DataInitializer implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private final RoleRepository roles;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final boolean adminEnabled;
    private final String username;
    private final String email;
    private final String password;

    public DataInitializer(RoleRepository roles, UserRepository users, PasswordEncoder encoder,
            @Value("${app.bootstrap-admin.enabled:false}") boolean adminEnabled,
            @Value("${app.bootstrap-admin.username:}") String username,
            @Value("${app.bootstrap-admin.email:}") String email,
            @Value("${app.bootstrap-admin.password:}") String password) {
        this.roles = roles;
        this.users = users;
        this.encoder = encoder;
        this.adminEnabled = adminEnabled;
        this.username = username;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(String... args) {
        for (ERole name : ERole.values()) {
            if (roles.findByName(name).isEmpty()) {
                Role role = new Role();
                role.setName(name);
                roles.save(role);
            }
        }
        if (!adminEnabled) return;
        if (!StringUtils.hasText(username) || username.length() > 100
                || !StringUtils.hasText(email) || email.length() > 100 || !email.contains("@")
                || !StringUtils.hasText(password) || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalStateException("Admin bootstrap requires a username, email, and a password "
                    + "of at least 12 characters and at most 72 UTF-8 bytes.");
        }
        var existing = users.findByUsername(username);
        if (existing.isPresent()) {
            boolean admin = existing.get().getRoles().stream()
                    .anyMatch(role -> role.getName() == ERole.ROLE_ADMIN);
            if (!admin) {
                throw new IllegalStateException("Bootstrap username already belongs to a non-admin account.");
            }
            return;
        }
        if (users.existsByEmail(email)) {
            throw new IllegalStateException("Bootstrap email already belongs to another account.");
        }
        Role adminRole = roles.findByName(ERole.ROLE_ADMIN).orElseThrow();
        User admin = new User(username, email, encoder.encode(password));
        admin.setRoles(Set.of(adminRole));
        users.save(admin);
        log.info("Bootstrap administrator created. Disable bootstrap after initial setup.");
    }
}
