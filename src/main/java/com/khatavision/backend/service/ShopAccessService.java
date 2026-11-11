package com.khatavision.backend.service;

import com.khatavision.backend.entity.User;
import com.khatavision.backend.repository.ShopRepository;
import com.khatavision.backend.repository.StaffAssignmentRepository;
import com.khatavision.backend.security.services.UserDetailsImpl;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShopAccessService {
    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;
    private final ShopRepository shops;
    private final StaffAssignmentRepository assignments;

    public ShopAccessService(ShopRepository shops, StaffAssignmentRepository assignments) {
        this.shops = shops;
        this.assignments = assignments;
    }

    public Long currentUserId() {
        return currentUser().getId();
    }

    public void requireAccess(Long shopId) {
        UserDetailsImpl principal = currentUser();
        boolean owner = principal.getAuthorities().stream()
                .anyMatch(authority -> authority.getAuthority().equals("ROLE_OWNER"));
        if (owner) {
            var shop = shops.findById(shopId).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found."));
            if (!shop.getOwner().getId().equals(principal.getId())) {
                throw forbidden();
            }
            return;
        }
        User staff = new User();
        staff.setId(principal.getId());
        assignments.findByStaff(staff)
                .filter(a -> a.getShop() != null && a.getShop().getId().equals(shopId))
                .orElseThrow(ShopAccessService::forbidden);
    }

    public void requireWritableShop(Long shopId) {
        var shop = shops.findByIdForRead(shopId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found."));
        // Controllers may already have loaded the shop; refresh after acquiring the lock.
        entityManager.refresh(shop, jakarta.persistence.LockModeType.PESSIMISTIC_READ);
        requireAccess(shopId);
        ArchiveService.requireActive(shop);
    }

    private UserDetailsImpl currentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required.");
        }
        return principal;
    }

    private static ResponseStatusException forbidden() {
        return new ResponseStatusException(HttpStatus.FORBIDDEN, "You do not have access to this shop.");
    }
}
