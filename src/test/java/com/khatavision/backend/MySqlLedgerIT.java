package com.khatavision.backend;

import com.khatavision.backend.dto.LedgerCorrectionRequest;
import com.khatavision.backend.dto.LedgerEntryRequest;
import com.khatavision.backend.security.services.UserDetailsImpl;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("mysql-test")
class MySqlLedgerIT extends AbstractLedgerApiTests {
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        var db = MySqlTestDatabase.get("api");
        properties.add("spring.datasource.url", db::url);
        properties.add("spring.datasource.username", db::username);
        properties.add("spring.datasource.password", db::password);
    }

    @Test
    void databaseFailureOnReplacementRollsBackTheReversalToo() {
        authenticate();
        try {
            long original = ledger.add(customer.getId(), new LedgerEntryRequest("DEBIT", new BigDecimal("100"), null)).id();
            var invalid = new LedgerCorrectionRequest(new LedgerEntryRequest("DEBIT", new BigDecimal("120"), "x".repeat(300)), "Correction");
            assertThatThrownBy(() -> ledger.correct(original, invalid)).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(entries.existsByReversesEntryId(original)).isFalse();
            assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).hasSize(1);
            assertThat(ledger.balance(customer.getId()).netBalance()).isEqualByComparingTo("100");
        } finally { SecurityContextHolder.clearContext(); }
    }

    @Test
    void mysqlCustomerRowLockBlocksAnotherPaymentUntilCommit() throws Exception {
        authenticate();
        ledger.add(customer.getId(), new LedgerEntryRequest("DEBIT", new BigDecimal("100"), null));
        SecurityContextHolder.clearContext();
        var pool = Executors.newFixedThreadPool(2);
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try {
            Future<?> holder = pool.submit(() -> new TransactionTemplate(transactions).execute(status -> {
                customers.findByIdForUpdate(customer.getId()).orElseThrow();
                locked.countDown();
                try {
                    if (!release.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Timed out waiting for release");
                } catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                return null;
            }));
            assertThat(locked.await(5, TimeUnit.SECONDS)).isTrue();
            Future<?> payment = pool.submit(() -> {
                authenticate();
                try {
                    started.countDown();
                    return ledger.add(customer.getId(), new LedgerEntryRequest("CREDIT", new BigDecimal("80"), null));
                } finally { SecurityContextHolder.clearContext(); }
            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> payment.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
            release.countDown();
            holder.get(10, TimeUnit.SECONDS);
            payment.get(10, TimeUnit.SECONDS);
            authenticate();
            assertThat(ledger.balance(customer.getId()).netBalance()).isEqualByComparingTo("20");
        } finally {
            release.countDown(); pool.shutdownNow(); SecurityContextHolder.clearContext();
        }
    }

    private void authenticate() {
        var principal = UserDetailsImpl.build(owner);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
