package com.khatavision.backend.repository;

import com.khatavision.backend.entity.LedgerEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    List<LedgerEntry> findByCustomerIdOrderByCreatedAtDescIdDesc(Long customerId);

    boolean existsByReversesEntryId(Long entryId);

    @Query("select e.customer.id from LedgerEntry e where e.id = :entryId")
    Optional<Long> findCustomerIdByEntryId(@Param("entryId") Long entryId);

    @Query("""
            select coalesce(sum(case when e.entryType = :debit then e.amount else 0 end), 0) as totalDebit,
                   coalesce(sum(case when e.entryType = :credit then e.amount else 0 end), 0) as totalCredit
            from LedgerEntry e where e.customer.id = :customerId
            """)
    LedgerTotals totals(@Param("customerId") Long customerId,
                        @Param("debit") LedgerEntry.EntryType debit,
                        @Param("credit") LedgerEntry.EntryType credit);
}
