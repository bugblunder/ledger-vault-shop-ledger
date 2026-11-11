package com.khatavision.backend.controller;

import com.khatavision.backend.dto.UserResponse;
import com.khatavision.backend.entity.StaffAssignment;
import com.khatavision.backend.entity.User;
import com.khatavision.backend.repository.StaffAssignmentRepository;
import com.khatavision.backend.repository.UserRepository;
import com.khatavision.backend.security.services.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final UserRepository userRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;

    public ProfileController(UserRepository userRepository, StaffAssignmentRepository staffAssignmentRepository) {
        this.userRepository = userRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
    }

    @GetMapping("/me")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<UserResponse> getCurrentUser() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Logged in user not found in database."));

        Optional<StaffAssignment> assignmentOpt = staffAssignmentRepository.findByStaff(user);

        if (assignmentOpt.isPresent()) {
            return ResponseEntity.ok(UserResponse.fromEntity(user, assignmentOpt.get()));
        } else {
            return ResponseEntity.ok(UserResponse.fromEntity(user));
        }
    }
}