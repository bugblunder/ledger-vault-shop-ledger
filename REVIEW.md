# LedgerVault — project assessment after the second improvement pass

The initial application was a useful Java/Spring Boot MVP: clear owner/shop/customer relationships, role-based endpoints, BCrypt, decimal money values, DTOs, Flyway, and OpenAPI. The two improvement passes strengthen the parts that make it a credible backend portfolio project: authorization, transaction correctness, auditability, and executable verification.

| Original finding | Current status |
| --- | --- |
| Owner could promote staff to ADMIN | Shared allowlist and regression tests |
| Embedded secrets/default admin | Environment credentials and explicit bootstrap; existing credentials still require rotation |
| JWT username reuse could change identity | Versioned tokens bind to immutable user IDs |
| Ledger mutations bypassed balance checks | Shared transactional rules; mutable endpoints retired |
| Concurrent payments could overpay | Shop/customer locking and READ_COMMITTED; verified on MySQL |
| Cascading deletes erased financial history | Restrictive FKs, append-only triggers, and archival APIs; verified on MySQL |
| Corrections destroyed earlier values | Linked reversal/replacement records with actor/time/reason |
| Only a context-load test | 41 fast and 31 MySQL test executions, plus a successful HTTP walkthrough |
| No reproducible setup/demo | Compose configuration, secret generator, demo script/output, API examples, diagrams, CI configuration |

Remaining work: request idempotency, pagination/query tuning for large ledgers, account lifecycle features, supported dependency versions, operational backup/restore testing, and full auditing of nonfinancial metadata changes. The original Spring Boot 3.2.5 dependency line remains technical debt. The installed Flyway version also emits an upstream MySQL 8.4 support warning; application-level verification succeeded, but version modernization is still needed.

Business scope remains deliberately narrow: one customer's outstanding balance within a shop, with no prepayment, currency conversion, double-entry accounting, or restoration of archived records. A privileged database administrator can remove triggers; the guarantees concern normal application operations and the restricted runtime account.

For a resume, emphasize implemented and verified behavior: tenant isolation, history-preserving corrections, SQL constraints/triggers, locking and rollback, migration compatibility, and integration testing. Do not claim production adoption, unmeasured scale, or compliance certifications.


## Release preparation status

Product branding and deployment identifiers have been updated for LedgerVault. The Java package namespace remains `com.khatavision.backend` intentionally. The recorded 72 test executions originate from the supplied validation report; this release preparation environment could not independently rerun Maven because Maven is not installed and the wrapper download requires network access.
