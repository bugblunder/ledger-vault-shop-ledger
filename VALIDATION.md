# Supplied verification evidence — 10 November 2026

**The supplied validation report records: 72 test executions, zero failures, zero errors, zero skipped. This is archived evidence, not a fresh run during LedgerVault branding preparation.**

| Suite | Database / scope | Executions | Result |
| --- | --- | ---: | --- |
| BackendApplicationTests | H2 API contract | 28 | Passed |
| SecurityPolicyTests | Role policy, JWT, bootstrap | 13 | Passed |
| MySqlLedgerIT | Real MySQL API contract, rollback, locking | 30 | Passed |
| MySqlMigrationIT | Populated V1→V2 upgrade and database constraints/triggers | 1 | Passed |

The API contract is intentionally executed on both databases. These are 72 test executions, not 72 unrelated scenarios.

## Execution details

- Microsoft OpenJDK 17.0.21.0.2 and Maven 3.9.11.
- MySQL Community Server 8.4.13, using fresh disposable databases.
- Final Maven lifecycle: `-Pmysql-it clean verify`, with environment-only proxy/trust-store/temp-directory settings required by the review environment.
- MySQL tests used the explicit external-database mode with a dedicated schema-level account, rather than a root database connection. The provided Testcontainers mode uses the same MySQL version and trigger-creation setting.
- The original V1 SQL was checked byte-for-byte against the supplied archive and against the staged Git content.

## Financial-history checks

The suite verifies that corrections retain originals and append linked reversal/replacement records, reject duplicate reversals, preserve the balance invariant, and require an authorized role and reason. Staff archival preserves their transactions and rejects both password login and existing bearer tokens. Customer/shop archival retains readable history and blocks new financial writes.

MySQL verification additionally establishes:

- A populated V1 schema upgrades to V2 without losing the original amount, recorder, or entry.
- Flyway validation and repeated no-op migration succeed.
- Foreign keys block deletion of ledger recorders, customers, shops, and owners.
- SQL UPDATE/DELETE of ledger rows is rejected by triggers.
- Invalid amounts and duplicate reversal references are rejected by the database.
- A held customer lock blocks a competing payment until commit.
- Simultaneous payments/corrections obey the balance rule.
- Failure of the replacement insert rolls back the reversal insert as well.

## Executed HTTP demo

The application was launched from its packaged JAR against MySQL. Migrations ran first through the separate `MigrationApplication` launcher, using a migration-only account. The API then ran with an account granted only SELECT, INSERT, UPDATE, and DELETE. A direct attempt by that runtime account to drop a protection trigger was rejected.

`scripts/demo.py` completed successfully over HTTP. It demonstrated invoice/payment entry, overpayment rejection, linked correction, duplicate-reversal rejection, staff-token invalidation, retained balance, and customer/shop archival. The recorded transcript is [docs/demo-output.txt](docs/demo-output.txt).

## Packaging checks and limits

Compose configuration passed `docker compose config --quiet`; Python scripts compiled; the database-init shell script passed syntax validation; staged Git content passed whitespace checks and a scan for the original embedded credentials. Local secrets, build output, test databases, runtime logs, and imported Git history are excluded from the source archive.

A Docker daemon was unavailable, so the Docker image build/Compose runtime and the Testcontainers startup path were **not executed here**. The underlying migration process, restricted-account application startup, HTTP demo, and MySQL tests were executed directly. GitHub Actions is configured but has not run remotely; no GitHub push occurred.

The original Spring Boot dependency line remains outdated. Flyway emits a warning that upstream-tested support ends at MySQL 8.0; the project's 8.4.13 tests pass, but dependency modernization is still needed. No production database, load test, vulnerability scan, deployment, or backup/restore drill was involved.


## LedgerVault preparation note

The product branding, Maven artifact name, database schema default, Docker image name, and JAR path were updated after the recorded run. The complete Java 17/MySQL workflow must be rerun against this branded snapshot before treating the release as independently validated.
