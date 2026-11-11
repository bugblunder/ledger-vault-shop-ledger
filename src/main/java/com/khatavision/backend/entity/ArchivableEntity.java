package com.khatavision.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import java.time.Instant;

@MappedSuperclass
public abstract class ArchivableEntity {
    private Instant archivedAt;
    @Column(name = "archived_by_user_id")
    private Long archivedByUserId;
    @Column(length = 500)
    private String archiveReason;

    public boolean isArchived() { return archivedAt != null; }
    public Instant getArchivedAt() { return archivedAt; }
    public Long getArchivedByUserId() { return archivedByUserId; }
    public String getArchiveReason() { return archiveReason; }

    public void archive(Long actor, String reason) {
        if (!isArchived()) {
            archivedAt = Instant.now();
            archivedByUserId = actor;
            archiveReason = reason;
        }
    }
}
