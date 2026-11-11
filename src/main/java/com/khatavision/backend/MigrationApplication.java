package com.khatavision.backend;

import org.flywaydb.core.Flyway;

/** One-shot schema migration process, separate from the restricted runtime database account. */
public final class MigrationApplication {
    public static void main(String[] args) {
        Flyway.configure().dataSource(required("DB_URL"), required("DB_USERNAME"), required("DB_PASSWORD"))
                .baselineOnMigrate(false).load().migrate();
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException(name + " is required.");
        return value;
    }
}
