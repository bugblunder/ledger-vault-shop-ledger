package com.khatavision.backend.controller;

import com.khatavision.backend.dto.*;
import com.khatavision.backend.entity.*;
import com.khatavision.backend.repository.*;
import com.khatavision.backend.security.services.UserDetailsImpl;
import com.khatavision.backend.service.LedgerService;
import com.khatavision.backend.service.ArchiveService;
import org.springframework.transaction.annotation.Transactional;
import com.khatavision.backend.service.ShopAccessService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/customers")
@PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY', 'VIEWER')")
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final ShopRepository shopRepository;
    private final LedgerService ledgerService;
    private final ArchiveService archiveService;
    private final ShopAccessService shopAccessService;
    private final UserRepository userRepository;
    private final StaffAssignmentRepository staffAssignmentRepository;

    public CustomerController(CustomerRepository customerRepository,
                              ShopRepository shopRepository,
                              LedgerService ledgerService,
                              ArchiveService archiveService,
                              ShopAccessService shopAccessService,
                              UserRepository userRepository,
                              StaffAssignmentRepository staffAssignmentRepository) {
        this.customerRepository = customerRepository;
        this.shopRepository = shopRepository;
        this.ledgerService = ledgerService;
        this.archiveService = archiveService;
        this.shopAccessService = shopAccessService;
        this.userRepository = userRepository;
        this.staffAssignmentRepository = staffAssignmentRepository;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY')")
    @Transactional
    public ResponseEntity<CustomerResponse> createCustomer(@Valid @RequestBody CustomerRequest customerRequest) {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User currentUser = userRepository.findById(userDetails.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Authenticated user not found in database."));

        Shop assignedShop;
        boolean isOwner = userDetails.getAuthorities().stream().anyMatch(auth -> auth.getAuthority().equals("ROLE_OWNER"));

        if (isOwner) {
            if (customerRequest.shopId() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Owner must provide a shopId to create a customer.");
            }
            assignedShop = shopRepository.findById(customerRequest.shopId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found with id: " + customerRequest.shopId()));

            if (!assignedShop.getOwner().getId().equals(currentUser.getId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access Denied: You do not own this shop.");
            }
        } else {
            StaffAssignment assignment = staffAssignmentRepository.findByStaff(currentUser)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff is not associated with any owner."));

            if (assignment.getShop() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Staff is not assigned to a shop. Please contact your owner.");
            }
            assignedShop = assignment.getShop();
        }

        shopAccessService.requireWritableShop(assignedShop.getId());
        Customer customer = new Customer();
        customer.setName(customerRequest.name());
        customer.setContactInfo(customerRequest.contactInfo());
        customer.setRelationshipType(customerRequest.relationshipType());
        customer.setNotes(customerRequest.notes());
        customer.setShop(assignedShop);

        Customer savedCustomer = customerRepository.save(customer);
        return ResponseEntity.ok(CustomerResponse.fromEntity(savedCustomer));
    }

    @GetMapping("/shop/{shopId}")
    public ResponseEntity<List<CustomerResponse>> getCustomersByShop(@PathVariable Long shopId) {
        validateShopAccess(shopId);
        List<Customer> customers = customerRepository.findByShopId(shopId);
        List<CustomerResponse> response = customers.stream()
                .map(CustomerResponse::fromEntity)
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomerById(@PathVariable Long customerId) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        validateShopAccess(customer.getShop().getId());
        return ResponseEntity.ok(CustomerResponse.fromEntity(customer));
    }

    @PutMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY')")
    @Transactional
    public ResponseEntity<CustomerResponse> updateCustomer(@PathVariable Long customerId, @Valid @RequestBody CustomerRequest customerRequest) {
        Long shopId = customerRepository.findShopIdById(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        shopAccessService.requireWritableShop(shopId);
        Customer customerToUpdate = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        ArchiveService.requireActive(customerToUpdate);
        customerToUpdate.setName(customerRequest.name());
        customerToUpdate.setContactInfo(customerRequest.contactInfo());
        customerToUpdate.setRelationshipType(customerRequest.relationshipType());
        customerToUpdate.setNotes(customerRequest.notes());

        Customer updatedCustomer = customerRepository.save(customerToUpdate);
        return ResponseEntity.ok(CustomerResponse.fromEntity(updatedCustomer));
    }

    @DeleteMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<?> deleteCustomer(@PathVariable Long customerId) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Use POST /api/customers/{id}/archive with a reason. History cannot be deleted.");
    }

    @PostMapping("/{customerId}/archive")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<MessageResponse> archiveCustomer(@PathVariable Long customerId,
            @Valid @RequestBody ReasonRequest request) {
        archiveService.customer(customerId, request.reason());
        return ResponseEntity.ok(new MessageResponse("Customer archived; financial history retained."));
    }

    @PostMapping("/{customerId}/ledger-entries")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY')")
    public ResponseEntity<LedgerEntryResponse> addLedgerEntry(@PathVariable Long customerId,
            @Valid @RequestBody LedgerEntryRequest entryRequest) {
        return ResponseEntity.ok(ledgerService.add(customerId, entryRequest));
    }

    @GetMapping("/{customerId}/ledger-entries")
    public ResponseEntity<List<LedgerEntryResponse>> getLedgerEntriesForCustomer(@PathVariable Long customerId) {
        return ResponseEntity.ok(ledgerService.list(customerId));
    }

    @PutMapping("/ledger-entries/{entryId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY')")
    public ResponseEntity<?> updateLedgerEntry(@PathVariable Long entryId) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Ledger entries are immutable. Use the corrections endpoint with a reason.");
    }

    @DeleteMapping("/ledger-entries/{entryId}")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER', 'DATA_ENTRY')")
    public ResponseEntity<?> deleteLedgerEntry(@PathVariable Long entryId) {
        throw new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Ledger entries are immutable. Use the reversals endpoint with a reason.");
    }

    @PostMapping("/ledger-entries/{entryId}/corrections")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<LedgerCorrectionResponse> correctLedgerEntry(@PathVariable Long entryId,
            @Valid @RequestBody LedgerCorrectionRequest request) {
        return ResponseEntity.ok(ledgerService.correct(entryId, request));
    }

    @PostMapping("/ledger-entries/{entryId}/reversals")
    @PreAuthorize("hasAnyRole('OWNER', 'MANAGER')")
    public ResponseEntity<LedgerEntryResponse> reverseLedgerEntry(@PathVariable Long entryId,
            @Valid @RequestBody ReasonRequest request) {
        return ResponseEntity.ok(ledgerService.reverse(entryId, request.reason()));
    }

    @GetMapping("/{customerId}/balance")
    public ResponseEntity<LedgerBalanceResponse> getCustomerBalance(@PathVariable Long customerId) {
        return ResponseEntity.ok(ledgerService.balance(customerId));
    }

    private void validateShopAccess(Long shopId) {
        shopAccessService.requireAccess(shopId);
    }
}
