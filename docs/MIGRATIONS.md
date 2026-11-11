# Schema upgrade and MySQL verification

V1 is preserved byte-for-byte. V2 is a Java Flyway migration because the original foreign keys were unnamed; it resolves their actual database names from metadata before replacing them. It adds archival/correction fields, restrictive financial foreign keys, positive-amount/link checks, a history index, unique reversal/replacement references, and append-only triggers. Existing ledger rows become ORIGINAL entries without changing amount, actor, or creation time.

Before using a real existing database: back it up, rehearse on a copy, check for invalid historical amounts and inconsistent balances, and run the upgrade with a schema-owner account. MySQL DDL is not fully transactional. A failed migration may require diagnosis and repair of a partial schema; do not automatically run Flyway repair or baseline. V2 preflights nonpositive amounts/null entry types before changing the schema, but it does not reconcile historical negative customer balances or custom schema modifications.

The Docker setup runs `MigrationApplication` once with `ledger_migrator`, then starts the API with `ledger_app` and `SPRING_FLYWAY_ENABLED=false`. The runtime account cannot ALTER, DROP, or create/remove triggers. For an external database, provision equivalent privileges and run migration separately before startup.

MySQL with binary logging enabled requires either elevated administrative privileges or `log_bin_trust_function_creators=ON` to create these triggers. The dedicated Compose and Testcontainers instances enable that setting; only the migration account has TRIGGER privileges. For an external server, have its administrator choose the appropriate migration policy before running V2. Do not grant schema/trigger privileges to the runtime account.

## Tests with Docker

With JDK 17+ and Docker running:

```bash
./mvnw -Pmysql-it clean verify
```

On Windows use `.\mvnw.cmd -Pmysql-it clean verify`. Testcontainers starts fresh MySQL 8.4.13 instances for the API and legacy-schema upgrade tests. Missing Docker fails the integration run; it is not silently skipped or replaced with H2.

## Tests against an explicitly supplied disposable instance

Provide these environment variables:

- `TEST_MYSQL_URL`: JDBC URL of an isolated `ledger_test_*` database for API tests.
- `TEST_MYSQL_MIGRATION_URL`: JDBC URL of a **separate empty** `ledger_test_*` database for the V1 upgrade test.
- `TEST_MYSQL_USERNAME` and `TEST_MYSQL_PASSWORD`: a test-only schema-owner account with migration/trigger privileges.

Then run the same Maven command. The schema-name prefix is checked. No cleanup is performed against a supplied database; the migration test refuses a nonempty schema. API fixtures use unique synthetic accounts. Supply fresh disposable schemas for a clean rerun.

The MySQL suite reruns the API contract used by the H2 suite and additionally checks a populated V1→V2 upgrade, database-enforced retention, SQL mutation rejection, actual row-lock waiting, and rollback after the replacement insert fails.
