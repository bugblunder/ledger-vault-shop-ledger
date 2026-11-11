package com.khatavision.backend.controller;

import com.khatavision.backend.dto.*;
import com.khatavision.backend.entity.*;
import com.khatavision.backend.repository.*;
import com.khatavision.backend.security.services.UserDetailsImpl;
import com.khatavision.backend.security.StaffRolePolicy;
import com.khatavision.backend.service.ArchiveService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/owner")
@PreAuthorize("hasRole('OWNER')")
public class OwnerController {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder encoder;
    private final StaffAssignmentRepository staffAssignmentRepository;
    private final ShopRepository shopRepository;
    private final ArchiveService archiveService;

    public OwnerController(UserRepository userRepository, RoleRepository roleRepository, PasswordEncoder encoder, StaffAssignmentRepository staffAssignmentRepository, ShopRepository shopRepository, ArchiveService archiveService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.encoder = encoder;
        this.staffAssignmentRepository = staffAssignmentRepository;
        this.shopRepository = shopRepository;
        this.archiveService = archiveService;
    }

    @PostMapping("/staff")
    @Transactional
    public ResponseEntity<?> registerStaff(@Valid @RequestBody RegisterRequest registerRequest) {
        if (userRepository.existsByUsername(registerRequest.username())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Username is already taken!"));
        }
        if (userRepository.existsByEmail(registerRequest.email())) {
            return ResponseEntity.badRequest().body(new MessageResponse("Error: Email is already in use!"));
        }
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User owner = userRepository.findById(userDetails.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found."));
        User staff = new User(registerRequest.username(), registerRequest.email(), encoder.encode(registerRequest.password()));
        staff.setPhoneNumber(registerRequest.phoneNumber());
        ERole requestedRole = StaffRolePolicy.parse(registerRequest.role());
        Role staffRole = roleRepository.findByName(requestedRole).orElseThrow(() -> new RuntimeException("Error: Role '" + requestedRole + "' not found in database."));
        Set<Role> roles = new HashSet<>();
        roles.add(staffRole);
        staff.setRoles(roles);
        User savedStaff = userRepository.save(staff);
        StaffAssignment assignment = new StaffAssignment(savedStaff, owner);
        staffAssignmentRepository.save(assignment);
        return ResponseEntity.ok(UserResponse.fromEntity(savedStaff, assignment));
    }

    @GetMapping("/staff")
    public ResponseEntity<List<UserResponse>> getStaff() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<StaffAssignment> assignments = staffAssignmentRepository.findByOwnerId(userDetails.getId());
        List<UserResponse> staffList = assignments.stream()
                .map(assignment -> UserResponse.fromEntity(assignment.getStaff(), assignment))
                .collect(Collectors.toList());
        return ResponseEntity.ok(staffList);
    }

    @GetMapping("/staff/unassigned")
    public ResponseEntity<List<UserResponse>> getUnassignedStaff() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<StaffAssignment> assignments = staffAssignmentRepository.findByOwnerId(userDetails.getId());
        List<UserResponse> unassignedStaffList = assignments.stream()
                .filter(assignment -> assignment.getShop() == null)
                .map(assignment -> UserResponse.fromEntity(assignment.getStaff(), assignment))
                .collect(Collectors.toList());
        return ResponseEntity.ok(unassignedStaffList);
    }

    @PutMapping("/staff/{staffId}")
    @Transactional
    public ResponseEntity<UserResponse> updateStaff(@PathVariable Long staffId, @Valid @RequestBody StaffUpdateRequest updateRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User owner = userRepository.findById(userDetails.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found."));
        User staffToUpdate = userRepository.findByIdForUpdate(staffId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff not found."));
        StaffAssignment assignment = staffAssignmentRepository.findByStaff(staffToUpdate).filter(a -> a.getOwner().getId().equals(owner.getId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this staff member."));
        ArchiveService.requireActive(staffToUpdate);
        ERole newRoleEnum = StaffRolePolicy.parse(updateRequest.role());
        staffToUpdate.setUsername(updateRequest.username());
        staffToUpdate.setEmail(updateRequest.email());
        staffToUpdate.setPhoneNumber(updateRequest.phoneNumber());
        Role newRole = roleRepository.findByName(newRoleEnum).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role specified."));
        Set<Role> roles = new HashSet<>();
        roles.add(newRole);
        staffToUpdate.setRoles(roles);
        User updatedStaff = userRepository.save(staffToUpdate);
        return ResponseEntity.ok(UserResponse.fromEntity(updatedStaff, assignment));
    }

    @DeleteMapping("/staff/{staffId}")
    public ResponseEntity<?> deleteStaff(@PathVariable Long staffId) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Use POST /api/owner/staff/{id}/archive with a reason.");
    }

    @PostMapping("/staff/{staffId}/archive")
    public ResponseEntity<MessageResponse> archiveStaff(@PathVariable Long staffId,
            @Valid @RequestBody ReasonRequest request) {
        archiveService.staff(staffId, request.reason());
        return ResponseEntity.ok(new MessageResponse("Staff archived; recorded transactions retained."));
    }

    @DeleteMapping("/staff/{staffId}/assignment")
    @Transactional
    public ResponseEntity<?> unassignStaffFromShop(@PathVariable Long staffId) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User owner = userRepository.findById(userDetails.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found."));
        User staffUser = userRepository.findByIdForUpdate(staffId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff user not found."));
        StaffAssignment assignment = staffAssignmentRepository.findByStaff(staffUser).filter(a -> a.getOwner().getId().equals(owner.getId())).orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this staff member."));
        assignment.setShop(null);
        staffAssignmentRepository.save(assignment);
        return ResponseEntity.ok(new MessageResponse("Staff member has been unassigned from their shop."));
    }

    @PostMapping("/shops")
    public ResponseEntity<ShopResponse> createShop(@Valid @RequestBody ShopRequest shopRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User owner = userRepository.findById(userDetails.getId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Owner not found."));
        Shop newShop = new Shop(shopRequest.name(), shopRequest.contactNumber(), owner);
        Shop savedShop = shopRepository.save(newShop);
        return ResponseEntity.ok(ShopResponse.fromEntity(savedShop));
    }

    @GetMapping("/shops")
    public ResponseEntity<List<ShopResponse>> getShopsByOwner() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<Shop> shops = shopRepository.findByOwnerId(userDetails.getId());
        List<ShopResponse> shopResponses = shops.stream().map(ShopResponse::fromEntity).collect(Collectors.toList());
        return ResponseEntity.ok(shopResponses);
    }

    @PutMapping("/shops/{shopId}")
    @Transactional
    public ResponseEntity<ShopResponse> updateShop(@PathVariable Long shopId, @Valid @RequestBody ShopRequest shopRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Shop shopToUpdate = shopRepository.findByIdForUpdate(shopId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found."));
        if (!shopToUpdate.getOwner().getId().equals(userDetails.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have permission to edit this shop.");
        }
        ArchiveService.requireActive(shopToUpdate);
        shopToUpdate.setName(shopRequest.name());
        shopToUpdate.setContactNumber(shopRequest.contactNumber());
        Shop updatedShop = shopRepository.save(shopToUpdate);
        return ResponseEntity.ok(ShopResponse.fromEntity(updatedShop));
    }

    @DeleteMapping("/shops/{shopId}")
    public ResponseEntity<?> deleteShop(@PathVariable Long shopId) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Use POST /api/owner/shops/{id}/archive with a reason.");
    }

    @PostMapping("/shops/{shopId}/archive")
    public ResponseEntity<MessageResponse> archiveShop(@PathVariable Long shopId,
            @Valid @RequestBody ReasonRequest request) {
        archiveService.shop(shopId, request.reason());
        return ResponseEntity.ok(new MessageResponse("Shop archived; customers and ledger history retained."));
    }

    @PutMapping("/shops/{shopId}/staff/{staffId}")
    @Transactional
    public ResponseEntity<?> assignStaffToShop(@PathVariable Long shopId, @PathVariable Long staffId) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Shop shop = shopRepository.findByIdForRead(shopId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found."));
        if (!shop.getOwner().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Error: You do not own this shop."));
        }
        User staffUser = userRepository.findByIdForUpdate(staffId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff user not found."));
        StaffAssignment assignment = staffAssignmentRepository.findByStaff(staffUser).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Staff assignment record does not exist for this user."));
        if (!assignment.getOwner().getId().equals(userDetails.getId())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new MessageResponse("Error: You do not own this staff member."));
        }
        ArchiveService.requireActive(shop);
        ArchiveService.requireActive(staffUser);
        assignment.setShop(shop);
        staffAssignmentRepository.save(assignment);
        return ResponseEntity.ok(new MessageResponse("Staff member successfully assigned to shop '" + shop.getName() + "'."));
    }
}