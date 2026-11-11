package com.khatavision.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.khatavision.backend.dto.*;
import com.khatavision.backend.entity.*;
import com.khatavision.backend.repository.*;
import com.khatavision.backend.security.services.UserDetailsImpl;
import com.khatavision.backend.service.LedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
abstract class AbstractLedgerApiTests {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired RoleRepository roles;
    @Autowired ShopRepository shops;
    @Autowired CustomerRepository customers;
    @Autowired StaffAssignmentRepository assignments;
    @Autowired LedgerEntryRepository entries;
    @Autowired LedgerService ledger;
    @Autowired PasswordEncoder encoder;
    User owner;
    User outsider;
    User staff;
    User viewer;
    Customer customer;
    String suffix;

    @BeforeEach
    void fixture() {
        SecurityContextHolder.clearContext();
        suffix = java.util.UUID.randomUUID().toString().substring(0, 8);
        owner = createUser("owner", ERole.ROLE_OWNER);
        outsider = createUser("outsider", ERole.ROLE_OWNER);
        staff = createUser("staff", ERole.ROLE_DATA_ENTRY);
        viewer = createUser("viewer", ERole.ROLE_VIEWER);
        Shop shop = shops.save(new Shop("Main shop", "1234567890", owner));
        for (User employee : List.of(staff, viewer)) {
            StaffAssignment assignment = new StaffAssignment(employee, owner);
            assignment.setShop(shop);
            assignments.save(assignment);
        }
        customer = new Customer();
        customer.setName("Test customer");
        customer.setShop(shop);
        customer = customers.save(customer);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "OWNER", "ROLE_ADMIN", "unknown"})
    void ownerCannotEscalateStaffPrivileges(String role) throws Exception {
        mvc.perform(as(owner, put("/api/owner/staff/{id}", staff.getId()))
                .content(staffUpdate(role))).andExpect(status().isBadRequest());
        assertThat(users.findById(staff.getId()).orElseThrow().getRoles())
                .extracting(Role::getName).containsExactly(ERole.ROLE_DATA_ENTRY);
    }

    @Test
    void ownerCanAssignAllowedRoleButOtherOwnerCannot() throws Exception {
        mvc.perform(as(outsider, put("/api/owner/staff/{id}", staff.getId()))
                .content(staffUpdate("MANAGER"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, put("/api/owner/staff/{id}", staff.getId()))
                .content(staffUpdate("manager"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("ROLE_MANAGER"));
    }

    @Test
    void viewerCanReadButCannotWriteAndOtherOwnerCannotReadOrMutate() throws Exception {
        long entryId = addDebit("100.00");
        mvc.perform(as(viewer, get(balanceUrl()))).andExpect(status().isOk());
        mvc.perform(as(viewer, post(entriesUrl())).content(entry("DEBIT", "5")))
                .andExpect(status().isForbidden());
        mvc.perform(as(outsider, get(balanceUrl()))).andExpect(status().isForbidden());
        mvc.perform(as(outsider, get(entriesUrl()))).andExpect(status().isForbidden());
        mvc.perform(as(outsider, post(entriesUrl())).content(entry("DEBIT", "5")))
                .andExpect(status().isForbidden());
        mvc.perform(as(outsider, post("/api/customers/ledger-entries/{id}/corrections", entryId))
                .content(correction("DEBIT", "200"))).andExpect(status().isForbidden());
        mvc.perform(as(outsider, post("/api/customers/ledger-entries/{id}/reversals", entryId)).content(reason()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1.999", "100000000.00"})
    void invalidAmountsReturn400WithoutWriting(String amount) throws Exception {
        mvc.perform(as(owner, post(entriesUrl())).content(entry("DEBIT", amount)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.amount").exists());
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).isEmpty();
    }

    @Test
    void invalidEntryTypeAndMalformedJsonReturn400() throws Exception {
        mvc.perform(as(owner, post(entriesUrl())).content(entry("BOGUS", "1")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.entryType").exists());
        mvc.perform(as(owner, post(entriesUrl())).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).isEmpty();
    }

    @Test
    void emptyBalanceIsZeroAndDebitCreditMathIsExact() throws Exception {
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.netBalance").value(0));
        addDebit("100.10");
        mvc.perform(as(staff, post(entriesUrl())).content(entry("credit", "40.05")))
                .andExpect(status().isOk());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.netBalance").value(60.05))
                .andExpect(jsonPath("$.totalDebit").value(100.10))
                .andExpect(jsonPath("$.totalCredit").value(40.05));
    }

    @Test
    void creationUpdateTypeChangeAndDeletionAllEnforceBalanceRule() throws Exception {
        long debit = addDebit("100");
        var payment = mvc.perform(as(owner, post(entriesUrl())).content(entry("CREDIT", "80")))
                .andExpect(status().isOk()).andReturn();
        long credit = json.readTree(payment.getResponse().getContentAsString()).get("id").asLong();
        mvc.perform(as(owner, post(entriesUrl())).content(entry("CREDIT", "21")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/corrections", debit)).content(correction("DEBIT", "50")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/corrections", credit)).content(correction("CREDIT", "120")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/corrections", debit)).content(correction("CREDIT", "1")))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", debit)).content(reason()))
                .andExpect(status().isBadRequest());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(20));
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", credit)).content(reason()))
                .andExpect(status().isOk());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(100));
    }

    @Test
    void missingEntryReturns404() throws Exception {
        mvc.perform(as(owner, post("/api/customers/ledger-entries/999999/corrections"))
                .content(correction("DEBIT", "10"))).andExpect(status().isNotFound());
    }

    @Test
    void simultaneousPaymentsCannotSpendSameOutstandingBalanceTwice() throws Exception {
        addDebit("100");
        List<Integer> statuses = concurrently(
                () -> ledger.add(customer.getId(), new LedgerEntryRequest("CREDIT", new BigDecimal("80"), null)),
                () -> ledger.add(customer.getId(), new LedgerEntryRequest("CREDIT", new BigDecimal("80"), null)));
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(20));
    }

    @Test
    void simultaneousPaymentEditsShareTheCustomerLock() throws Exception {
        addDebit("100");
        long first = addCredit("20");
        long second = addCredit("20");
        List<Integer> statuses = concurrently(
                () -> ledger.correct(first, new LedgerCorrectionRequest(new LedgerEntryRequest("CREDIT", new BigDecimal("60"), null), "Correction")),
                () -> ledger.correct(second, new LedgerCorrectionRequest(new LedgerEntryRequest("CREDIT", new BigDecimal("60"), null), "Correction")));
        assertThat(statuses).containsExactlyInAnyOrder(200, 400);
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(20));
    }

    @Test
    void anonymousRequestsRequireAuthenticationAndConfiguredCorsIsEnforced() throws Exception {
        mvc.perform(get(balanceUrl())).andExpect(status().isUnauthorized());
        mvc.perform(options("/api/auth/signin").header("Origin", "https://ledger.example.test")
                .header("Access-Control-Request-Method", "POST")
                .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://ledger.example.test"));
        mvc.perform(options("/api/auth/signin").header("Origin", "https://untrusted.example.test")
                .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void loginProducesUsableBearerToken() throws Exception {
        var response = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", owner.getUsername(), "password", "test-password-123"))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(get(balanceUrl()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        mvc.perform(get(balanceUrl()).header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenKeepsOriginalIdentityWhenUsernameIsReused() throws Exception {
        var response = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", owner.getUsername(), "password", "test-password-123"))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(response.getResponse().getContentAsString()).get("token").asText();
        String previousUsername = owner.getUsername();
        owner.setUsername("renamed-" + suffix);
        owner.setEmail("renamed-" + suffix + "@example.test");
        users.save(owner);
        createUser("owner", ERole.ROLE_ADMIN);
        mvc.perform(get("/api/profile/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(owner.getId()))
                .andExpect(jsonPath("$.username").value("renamed-" + suffix));
        mvc.perform(get("/api/admin/owners").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    void correctionRetainsOriginalAndAddsLinkedReversalAndReplacement() throws Exception {
        long debit = addDebit("100");
        var result = mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/corrections", debit))
                .content(correction("DEBIT", "120"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.reversal.reversesEntryId").value(debit))
                .andExpect(jsonPath("$.replacement.replacesEntryId").value(debit))
                .andExpect(jsonPath("$.replacement.reason").value("Receipt checked"))
                .andExpect(jsonPath("$.replacement.recordedByUserId").value(owner.getId())).andReturn();
        assertThat(entries.findById(debit).orElseThrow().getAmount()).isEqualByComparingTo("100");
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).hasSize(3);
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(120));
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", debit)).content(reason()))
                .andExpect(status().isConflict());
        long reversalId = json.readTree(result.getResponse().getContentAsString()).get("reversal").get("id").asLong();
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", reversalId)).content(reason()))
                .andExpect(status().isConflict());
    }

    @Test
    void failedCorrectionWritesNeitherReversalNorReplacement() throws Exception {
        long debit = addDebit("100");
        addCredit("80");
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/corrections", debit))
                .content(correction("DEBIT", "50"))).andExpect(status().isBadRequest());
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).hasSize(2);
        assertThat(entries.existsByReversesEntryId(debit)).isFalse();
    }

    @Test
    void correctionsRequireReasonAndOwnerOrManager() throws Exception {
        long debit = addDebit("100");
        mvc.perform(as(staff, post("/api/customers/ledger-entries/{id}/corrections", debit))
                .content(correction("DEBIT", "120"))).andExpect(status().isForbidden());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", debit))
                .content("{\"reason\":\" \"}")).andExpect(status().isBadRequest());
    }

    @Test
    void simultaneousCorrectionsOfSameEntryAllowOnlyOne() throws Exception {
        long debit = addDebit("100");
        var correction = new LedgerCorrectionRequest(new LedgerEntryRequest("DEBIT", new BigDecimal("120"), null), "Correction");
        List<Integer> statuses = concurrently(() -> ledger.correct(debit, correction), () -> ledger.correct(debit, correction));
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId())).hasSize(3);
    }

    @Test
    void legacyDestructiveEndpointsCannotRemoveHistory() throws Exception {
        long debit = addDebit("100");
        mvc.perform(as(owner, put("/api/customers/ledger-entries/{id}", debit)).content(entry("DEBIT", "1")))
                .andExpect(status().isMethodNotAllowed());
        mvc.perform(as(owner, delete("/api/customers/ledger-entries/{id}", debit))).andExpect(status().isMethodNotAllowed());
        mvc.perform(as(owner, delete("/api/customers/{id}", customer.getId()))).andExpect(status().isMethodNotAllowed());
        mvc.perform(as(owner, delete("/api/owner/shops/{id}", customer.getShop().getId()))).andExpect(status().isMethodNotAllowed());
        mvc.perform(as(owner, delete("/api/owner/staff/{id}", staff.getId()))).andExpect(status().isMethodNotAllowed());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(100));
    }

    @Test
    void archivingStaffPreservesTheirEntriesAndRevokesLoginAndExistingToken() throws Exception {
        var login = mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", staff.getUsername(), "password", "test-password-123"))))
                .andExpect(status().isOk()).andReturn();
        String token = json.readTree(login.getResponse().getContentAsString()).get("token").asText();
        mvc.perform(post(entriesUrl()).header("Authorization", "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
                .content(entry("DEBIT", "100"))).andExpect(status().isOk());
        mvc.perform(as(outsider, post("/api/owner/staff/{id}/archive", staff.getId())).content(reason()))
                .andExpect(status().isForbidden());
        mvc.perform(as(owner, post("/api/owner/staff/{id}/archive", staff.getId())).content(reason()))
                .andExpect(status().isOk());
        User archived = users.findById(staff.getId()).orElseThrow();
        assertThat(archived.getArchivedAt()).isNotNull();
        assertThat(archived.getArchivedByUserId()).isEqualTo(owner.getId());
        mvc.perform(get(balanceUrl()).header("Authorization", "Bearer " + token)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/signin").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", staff.getUsername(), "password", "test-password-123"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(100));
        assertThat(entries.findByCustomerIdOrderByCreatedAtDescIdDesc(customer.getId()).get(0).getRecordedBy().getId())
                .isEqualTo(staff.getId());
    }

    @Test
    void archivedCustomerKeepsReadableHistoryAndRejectsNewTransactionsAndEdits() throws Exception {
        long debit = addDebit("100");
        mvc.perform(as(owner, post("/api/customers/{id}/archive", customer.getId())).content(reason()))
                .andExpect(status().isOk());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(100));
        mvc.perform(as(owner, post(entriesUrl())).content(entry("DEBIT", "10"))).andExpect(status().isConflict());
        mvc.perform(as(owner, post("/api/customers/ledger-entries/{id}/reversals", debit)).content(reason()))
                .andExpect(status().isConflict());
        mvc.perform(as(owner, put("/api/customers/{id}", customer.getId()))
                .content("{\"name\":\"New name\"}")).andExpect(status().isConflict());
        mvc.perform(as(owner, get("/api/customers/{id}", customer.getId())))
                .andExpect(jsonPath("$.archivedByUserId").value(owner.getId()));
    }

    @Test
    void archivedShopRetainsLedgerButRejectsNewCustomersAndEntries() throws Exception {
        addDebit("100");
        long shopId = customer.getShop().getId();
        mvc.perform(as(owner, post("/api/owner/shops/{id}/archive", shopId)).content(reason())).andExpect(status().isOk());
        mvc.perform(as(owner, get(entriesUrl()))).andExpect(status().isOk());
        mvc.perform(as(owner, post(entriesUrl())).content(entry("DEBIT", "10"))).andExpect(status().isConflict());
        mvc.perform(as(owner, post("/api/customers")).content(json.writeValueAsString(Map.of("name", "New", "shopId", shopId))))
                .andExpect(status().isConflict());
        mvc.perform(as(owner, get(balanceUrl()))).andExpect(jsonPath("$.netBalance").value(100));
    }

    private List<Integer> concurrently(Callable<?> first, Callable<?> second) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CyclicBarrier barrier = new CyclicBarrier(2);
        try {
            Callable<Integer> a = worker(first, barrier);
            Callable<Integer> b = worker(second, barrier);
            Future<Integer> x = pool.submit(a);
            Future<Integer> y = pool.submit(b);
            return List.of(x.get(15, TimeUnit.SECONDS), y.get(15, TimeUnit.SECONDS));
        } finally {
            pool.shutdownNow();
        }
    }

    private Callable<Integer> worker(Callable<?> action, CyclicBarrier barrier) {
        return () -> {
            UserDetailsImpl principal = UserDetailsImpl.build(owner);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
            try {
                barrier.await(5, TimeUnit.SECONDS);
                action.call();
                return 200;
            } catch (ResponseStatusException ex) {
                return ex.getStatusCode().value();
            } finally {
                SecurityContextHolder.clearContext();
            }
        };
    }

    private User createUser(String name, ERole role) {
        User entity = new User(name + "-" + suffix, name + "-" + suffix + "@example.test", encoder.encode("test-password-123"));
        entity.setRoles(Set.of(roles.findByName(role).orElseThrow()));
        return users.save(entity);
    }

    private MockHttpServletRequestBuilder as(User actor, MockHttpServletRequestBuilder request) {
        return request.with(user(UserDetailsImpl.build(actor))).contentType(MediaType.APPLICATION_JSON);
    }

    private String staffUpdate(String role) throws Exception {
        return json.writeValueAsString(Map.of("username", staff.getUsername(), "email", staff.getEmail(),
                "phoneNumber", "1234567890", "role", role));
    }

    private String correction(String type, String amount) throws Exception {
        return json.writeValueAsString(new LedgerCorrectionRequest(
                new LedgerEntryRequest(type, new BigDecimal(amount), "Corrected entry"), "Receipt checked"));
    }

    private String reason() throws Exception {
        return json.writeValueAsString(new ReasonRequest("Duplicate entry verified"));
    }

    private String entry(String type, String amount) throws Exception {
        return json.writeValueAsString(new LedgerEntryRequest(type, new BigDecimal(amount), null));
    }

    private String entriesUrl() { return "/api/customers/" + customer.getId() + "/ledger-entries"; }
    private String balanceUrl() { return "/api/customers/" + customer.getId() + "/balance"; }
    private long addDebit(String amount) throws Exception { return add("DEBIT", amount); }
    private long addCredit(String amount) throws Exception { return add("CREDIT", amount); }
    private long add(String type, String amount) throws Exception {
        var response = mvc.perform(as(owner, post(entriesUrl())).content(entry(type, amount)))
                .andExpect(status().isOk()).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("id").asLong();
    }
}
