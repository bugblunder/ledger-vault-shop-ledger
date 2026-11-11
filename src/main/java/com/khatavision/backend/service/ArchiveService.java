package com.khatavision.backend.service;

import com.khatavision.backend.entity.*;
import com.khatavision.backend.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ArchiveService {
    private final UserRepository users;
    private final ShopRepository shops;
    private final CustomerRepository customers;
    private final StaffAssignmentRepository assignments;
    private final ShopAccessService access;

    public ArchiveService(UserRepository users, ShopRepository shops, CustomerRepository customers,
                          StaffAssignmentRepository assignments, ShopAccessService access) {
        this.users = users; this.shops = shops; this.customers = customers;
        this.assignments = assignments; this.access = access;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void staff(Long id, String reason) {
        User staff = users.findByIdForUpdate(id).orElseThrow(() -> missing("Staff"));
        assignments.findByStaff(staff).filter(a -> a.getOwner().getId().equals(access.currentUserId()))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this staff member."));
        staff.archive(access.currentUserId(), checkedReason(reason));
        // Keep assignment and recorder identity for historical reads. Authentication rejects archived users.
        users.save(staff);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void shop(Long id, String reason) {
        Shop shop = shops.findByIdForUpdate(id).orElseThrow(() -> missing("Shop"));
        if (!shop.getOwner().getId().equals(access.currentUserId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not own this shop.");
        }
        shop.archive(access.currentUserId(), checkedReason(reason));
        shops.save(shop);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void customer(Long id, String reason) {
        Long shopId = customers.findShopIdById(id).orElseThrow(() -> missing("Customer"));
        // Match the ledger's shop-before-customer lock order to avoid a lock inversion.
        shops.findByIdForRead(shopId).orElseThrow(() -> missing("Shop"));
        Customer customer = customers.findByIdForUpdate(id).orElseThrow(() -> missing("Customer"));
        access.requireAccess(shopId);
        customer.archive(access.currentUserId(), checkedReason(reason));
        customers.save(customer);
    }

    public static String checkedReason(String reason) {
        if (reason == null || reason.isBlank() || reason.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A reason of 1 to 500 characters is required.");
        }
        return reason.strip();
    }

    public static void requireActive(ArchivableEntity entity) {
        if (entity.isArchived()) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "This record is archived. Its history is available, but changes are disabled.");
    }

    private static ResponseStatusException missing(String entity) {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, entity + " not found.");
    }
}
