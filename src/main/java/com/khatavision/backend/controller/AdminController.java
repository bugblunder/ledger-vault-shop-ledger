package com.khatavision.backend.controller;

import com.khatavision.backend.dto.MessageResponse;
import com.khatavision.backend.dto.OwnerCreateRequest;
import com.khatavision.backend.dto.OwnerUpdateRequest;
import com.khatavision.backend.dto.UserResponse;
import com.khatavision.backend.entity.ERole;
import com.khatavision.backend.entity.Role;
import com.khatavision.backend.entity.User;
import com.khatavision.backend.repository.RoleRepository;
import com.khatavision.backend.repository.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;

    public AdminController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder encoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
    }

    @PostMapping("/owners")
    public ResponseEntity<?> createOwner(@Valid @RequestBody OwnerCreateRequest createRequest) {
        if (userRepository.existsByUsername(createRequest.username())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }
        if (userRepository.existsByEmail(createRequest.email())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }

        User owner = new User(createRequest.username(), createRequest.email(), encoder.encode(createRequest.password()));
        owner.setPhoneNumber(createRequest.phoneNumber());

        Role ownerRole = roleRepository.findByName(ERole.ROLE_OWNER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CRITICAL: Role 'OWNER' not found."));

        Set<Role> roles = new HashSet<>();
        roles.add(ownerRole);
        owner.setRoles(roles);

        userRepository.save(owner);
        return ResponseEntity.ok(new MessageResponse("Owner account created successfully!"));
    }

    @GetMapping("/owners")
    public ResponseEntity<List<UserResponse>> getAllOwners() {
        Role ownerRole = roleRepository.findByName(ERole.ROLE_OWNER)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "CRITICAL: Role 'OWNER' not found."));
        List<User> owners = userRepository.findByRoles(ownerRole);
        List<UserResponse> ownerResponses = owners.stream()
                .map(UserResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(ownerResponses);
    }

    @PutMapping("/owners/{id}")
    public ResponseEntity<UserResponse> updateOwner(@PathVariable Long id, @Valid @RequestBody OwnerUpdateRequest updateRequest) {
        User ownerToUpdate = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found with id: " + id));

        ownerToUpdate.setUsername(updateRequest.username());
        ownerToUpdate.setEmail(updateRequest.email());
        ownerToUpdate.setPhoneNumber(updateRequest.phoneNumber());

        User updatedOwner = userRepository.save(ownerToUpdate);
        return ResponseEntity.ok(UserResponse.fromEntity(updatedOwner));
    }
}