# Changelog

## LedgerVault portfolio preparation — 12 November 2026

- Renamed product-facing documentation and runtime application identity to LedgerVault.
- Updated Maven coordinates to `com.khatavision:ledger-vault:1.0.0` while retaining the existing Java package namespace to avoid unnecessary compatibility risk.
- Renamed the default database schema and Docker image/JAR references to LedgerVault.
- Reworked publishing instructions and documented owner-authorized source provenance.
- Preserved the existing security, migration, demo, and CI implementation without claiming a fresh full test run.


## History protection and reviewer demo — 10 November 2026

- Added a V2 migration that preserves V1 data, discovers actual legacy FK names, restricts financial parent deletion, adds correction/archival metadata and checks, and installs ledger UPDATE/DELETE rejection triggers.
- Replaced mutable ledger edits with reasoned append-only reversal/correction APIs. Corrections commit reversal and replacement together; duplicate reversals are rejected.
- Added staff/customer/shop archival with actor/time/reason. Staff archival revokes authentication; customer/shop archival keeps history readable and blocks new writes.
- Added a shared shop lock before customer locks; this coordinates ledger writes with archival while allowing separate customers to proceed independently.
- Added fast regression tests and a real MySQL profile with Testcontainers or explicitly supplied disposable schemas. Tests cover populated V1 upgrades, concurrency, direct SQL protection, and rollback after a failed second insert.
- Added a separate migration launcher, restricted runtime database account, non-root application Docker image, Compose startup ordering, locally generated secrets, and an HTTP demo.
- Added API request examples, an architecture diagram, reviewer walkthrough, verification notes, and a pinned-action GitHub CI workflow.

### Compatibility changes

Ledger PUT/DELETE and legacy staff/customer/shop DELETE now return 405. Use `/corrections`, `/reversals`, or `/archive` with a reason. Corrections/reversals require OWNER or MANAGER. Financial history includes reversal/replacement rows and additional lineage fields. User/shop/customer responses include archival metadata. A real database must apply V2 before starting the new application; rehearse upgrades and back up data first.

## Initial review fixes — 10 November 2026

Restricted staff roles on both creation/update, removed embedded credentials/default admin, bound JWTs to immutable IDs, configured explicit CORS, centralized ledger transactions and balance aggregates, validated monetary inputs, and added the first 33 regression tests. The second pass extends that baseline.
