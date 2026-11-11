package com.khatavision.backend.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import java.math.BigDecimal;
import java.time.Instant;

@org.hibernate.annotations.Immutable
@Entity
@Table(name = "ledger_entries")
public class LedgerEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Enumerated(EnumType.STRING)
    private EntryType entryType;
    @Column(precision = 10, scale = 2, nullable = false)
    private BigDecimal amount;
    private String description;
    private String imageUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recorded_by_user_id", nullable = false)
    private User recordedBy;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private EntryKind entryKind = EntryKind.ORIGINAL;
    private Long reversesEntryId;
    private Long replacesEntryId;
    @Column(length = 500)
    private String reason;

    public enum EntryKind { ORIGINAL, REVERSAL, REPLACEMENT }
    public EntryKind getEntryKind() { return entryKind; }
    public void setEntryKind(EntryKind value) { entryKind = value; }
    public Long getReversesEntryId() { return reversesEntryId; }
    public void setReversesEntryId(Long value) { reversesEntryId = value; }
    public Long getReplacesEntryId() { return replacesEntryId; }
    public void setReplacesEntryId(Long value) { replacesEntryId = value; }
    public String getReason() { return reason; }
    public void setReason(String value) { reason = value; }

    public enum EntryType {
        CREDIT, DEBIT
    }

    // --- MANUALLY ADDED GETTERS AND SETTERS ---
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public Customer getCustomer() {
        return customer;
    }

    public void setCustomer(Customer customer) {
        this.customer = customer;
    }

    public User getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(User recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}