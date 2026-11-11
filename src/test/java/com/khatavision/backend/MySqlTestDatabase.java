package com.khatavision.backend;

import org.testcontainers.containers.MySQLContainer;
import java.util.HashMap;
import java.util.Map;

/** An explicit disposable schema or a fresh container; never silently falls back to H2. */
final class MySqlTestDatabase {
    record ConnectionInfo(String url, String username, String password) {}
    private static final Map<String, ConnectionInfo> DATABASES = new HashMap<>();

    static synchronized ConnectionInfo get(String purpose) {
        return DATABASES.computeIfAbsent(purpose, key -> {
            String variable = key.equals("api") ? "TEST_MYSQL_URL" : "TEST_MYSQL_MIGRATION_URL";
            String url = System.getenv(variable);
            if (url != null && !url.isBlank()) {
                if (!url.matches("jdbc:mysql://[^/]+/ledger_test_[A-Za-z0-9_]+(?:\\?.*)?")) {
                    throw new IllegalStateException(variable + " must name a disposable ledger_test_* schema.");
                }
                String password = System.getenv("TEST_MYSQL_PASSWORD");
                if (password == null) throw new IllegalStateException("TEST_MYSQL_PASSWORD is required.");
                return new ConnectionInfo(url, System.getenv().getOrDefault("TEST_MYSQL_USERNAME", "root"), password);
            }
            if (System.getenv("TEST_MYSQL_URL") != null) {
                throw new IllegalStateException("Set TEST_MYSQL_MIGRATION_URL to a separate empty ledger_test_* schema.");
            }
            MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.4.13")
                    .withDatabaseName("ledger_test_" + key).withUsername("ledger_test")
                    .withPassword("isolated-test-password")
                    .withCommand("--log-bin-trust-function-creators=ON");
            mysql.start();
            Runtime.getRuntime().addShutdownHook(new Thread(mysql::stop));
            return new ConnectionInfo(mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        });
    }
}
