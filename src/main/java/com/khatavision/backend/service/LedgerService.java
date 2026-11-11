package com.khatavision.backend.service;

import com.khatavision.backend.dto.*;
import com.khatavision.backend.dto.LedgerEntryRequest;
import com.khatavision.backend.dto.LedgerEntryResponse;
import com.khatavision.backend.entity.Customer;
import com.khatavision.backend.entity.LedgerEntry;
import com.khatavision.backend.repository.CustomerRepository;
import com.khatavision.backend.repository.ShopRepository;
import com.khatavision.backend.repository.LedgerEntryRepository;
import com.khatavision.backend.repository.LedgerTotals;
import com.khatavision.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

@Service
public class LedgerService {
    private final CustomerRepository customers;
    private final LedgerEntryRepository entries;
    private final UserRepository users;
    private final ShopAccessService access;
    private final ShopRepository shops;

    public LedgerService(CustomerRepository customers, LedgerEntryRepository entries,
                         UserRepository users, ShopAccessService access, ShopRepository shops) {
        this.customers = customers;
        this.entries = entries;
        this.users = users;
        this.access = access;
        this.shops = shops;
    }

    // Every ledger writer locks the same customer before checking the balance.
    // READ_COMMITTED prevents a pre-lock lookup from creating a stale MySQL snapshot.
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerEntryResponse add(Long customerId, LedgerEntryRequest request) {
        Customer customer = lockCustomer(customerId);
        LedgerEntry.EntryType type = parseType(request.entryType());
        requireNonnegative(totals(customerId).balance().add(signed(type, request.amount())));
        var recorder = users.findById(access.currentUserId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists."));
        LedgerEntry entry = new LedgerEntry();
        entry.setCustomer(customer);
        entry.setRecordedBy(recorder);
        apply(entry, type, request);
        return LedgerEntryResponse.fromEntity(entries.saveAndFlush(entry));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerCorrectionResponse correct(Long entryId, LedgerCorrectionRequest request) {
        LedgerEntry original = lockEntryCustomer(entryId);
        requireCorrectable(original);
        String reason = ArchiveService.checkedReason(request.reason());
        LedgerEntryRequest replacement = request.replacement();
        if (replacement == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Replacement is required.");
        LedgerEntry.EntryType type = parseType(replacement.entryType());
        BigDecimal projected = totals(original.getCustomer().getId()).balance()
                .subtract(signed(original.getEntryType(), original.getAmount()))
                .add(signed(type, replacement.amount()));
        requireNonnegative(projected);
        LedgerEntry reversal = reversalOf(original, reason);
        entries.saveAndFlush(reversal);
        LedgerEntry corrected = new LedgerEntry();
        corrected.setCustomer(original.getCustomer());
        corrected.setRecordedBy(reversal.getRecordedBy());
        corrected.setEntryKind(LedgerEntry.EntryKind.REPLACEMENT);
        corrected.setReplacesEntryId(original.getId());
        corrected.setReason(reason);
        apply(corrected, type, replacement);
        entries.saveAndFlush(corrected);
        return new LedgerCorrectionResponse(LedgerEntryResponse.fromEntity(reversal),
                LedgerEntryResponse.fromEntity(corrected));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LedgerEntryResponse reverse(Long entryId, String reason) {
        LedgerEntry original = lockEntryCustomer(entryId);
        requireCorrectable(original);
        reason = ArchiveService.checkedReason(reason);
        requireNonnegative(totals(original.getCustomer().getId()).balance()
                .subtract(signed(original.getEntryType(), original.getAmount())));
        LedgerEntry reversal = entries.saveAndFlush(reversalOf(original, reason));
        return LedgerEntryResponse.fromEntity(reversal);
    }

    private void requireCorrectable(LedgerEntry entry) {
        if (entry.getEntryKind() == LedgerEntry.EntryKind.REVERSAL
                || entries.existsByReversesEntryId(entry.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This entry is a reversal or has already been reversed. Correct the active replacement instead.");
        }
    }

    private LedgerEntry reversalOf(LedgerEntry original, String reason) {
        LedgerEntry reversal = new LedgerEntry();
        reversal.setCustomer(original.getCustomer());
        reversal.setRecordedBy(users.findById(access.currentUserId()).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User no longer exists.")));
        reversal.setEntryType(original.getEntryType() == LedgerEntry.EntryType.DEBIT
                ? LedgerEntry.EntryType.CREDIT : LedgerEntry.EntryType.DEBIT);
        reversal.setAmount(original.getAmount());
        reversal.setEntryKind(LedgerEntry.EntryKind.REVERSAL);
        reversal.setReversesEntryId(original.getId());
        reversal.setReason(reason);
        reversal.setDescription("Reversal of entry " + original.getId());
        return reversal;
    }

    @Transactional(readOnly = true)
    public List<LedgerEntryResponse> list(Long customerId) {
        requireCustomerAccess(customerId);
        return entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customerId).stream()
                .map(LedgerEntryResponse::fromEntity).toList();
    }

    @Transactional(readOnly = true)
    public LedgerBalanceResponse balance(Long customerId) {
        requireCustomerAccess(customerId);
        LedgerTotals totals = totals(customerId);
        BigDecimal net = totals.balance();
        String status = net.signum() > 0 ? "Customer has to give"
                : net.signum() < 0 ? "Customer has credit" : "Settled";
        return new LedgerBalanceResponse(totals.getTotalCredit(), totals.getTotalDebit(), net, status);
    }

    private Customer lockCustomer(Long id) {
        Long shopId = customers.findShopIdById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        var shop = shops.findByIdForRead(shopId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found."));
        ArchiveService.requireActive(shop);
        Customer customer = customers.findByIdForUpdate(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        access.requireAccess(customer.getShop().getId());
        ArchiveService.requireActive(customer);
        return customer;
    }

    private LedgerEntry lockEntryCustomer(Long entryId) {
        // Fetch only the ID before locking; an entity loaded here could be stale after waiting.
        Long customerId = entries.findCustomerIdByEntryId(entryId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger entry not found."));
        lockCustomer(customerId);
        return entries.findById(entryId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Ledger entry not found."));
    }

    private void requireCustomerAccess(Long id) {
        Customer customer = customers.findById(id).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found."));
        access.requireAccess(customer.getShop().getId());
    }

    private LedgerTotals totals(Long customerId) {
        return entries.totals(customerId, LedgerEntry.EntryType.DEBIT, LedgerEntry.EntryType.CREDIT);
    }

    private static LedgerEntry.EntryType parseType(String type) {
        try {
            return LedgerEntry.EntryType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Entry type must be CREDIT or DEBIT.");
        }
    }

    private static BigDecimal signed(LedgerEntry.EntryType type, BigDecimal amount) {
        if (amount == null || amount.signum() <= 0 || amount.stripTrailingZeros().scale() > 2
                || amount.compareTo(new BigDecimal("99999999.99")) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Amount must be between 0.01 and 99999999.99 with at most two decimal places.");
        }
        return type == LedgerEntry.EntryType.DEBIT ? amount : amount.negate();
    }

    private static void requireNonnegative(BigDecimal projected) {
        if (projected.signum() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This change would make payments exceed the customer's total debit.");
        }
    }

    private static void apply(LedgerEntry entry, LedgerEntry.EntryType type, LedgerEntryRequest request) {
        entry.setEntryType(type);
        entry.setAmount(request.amount());
        entry.setDescription(request.description());
    }
}
