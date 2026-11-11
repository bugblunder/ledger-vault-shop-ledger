package com.khatavision.backend;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.assertj.core.api.Assertions.*;

class MySqlMigrationIT {
    @Test
    void upgradesPopulatedV1AndEnforcesRetentionAndImmutabilityInMysql() throws Exception {
        var db = MySqlTestDatabase.get("migration");
        try (Connection c = DriverManager.getConnection(db.url(), db.username(), db.password()); Statement s = c.createStatement()) {
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()")) {
                r.next();
                assertThat(r.getInt(1)).as("Migration test requires a separate empty disposable schema").isZero();
            }
            Flyway.configure().dataSource(db.url(), db.username(), db.password()).target("1").load().migrate();
            s.executeUpdate("INSERT INTO users(id,username,email,password) VALUES (1,'legacy-owner','owner@test.invalid','test'),(2,'legacy-staff','staff@test.invalid','test')");
            s.executeUpdate("INSERT INTO shops(id,name,owner_id) VALUES (1,'Legacy shop',1)");
            s.executeUpdate("INSERT INTO customers(id,name,shop_id) VALUES (1,'Legacy customer',1)");
            s.executeUpdate("INSERT INTO ledger_entries(id,entry_type,amount,customer_id,recorded_by_user_id) VALUES (1,'DEBIT',100,1,2)");
            Flyway flyway = Flyway.configure().dataSource(db.url(), db.username(), db.password()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(1);
            flyway.validate();
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try (ResultSet r = s.executeQuery("SELECT amount,entry_kind,recorded_by_user_id FROM ledger_entries WHERE id=1")) {
                assertThat(r.next()).isTrue();
                assertThat(r.getBigDecimal(1)).isEqualByComparingTo("100");
                assertThat(r.getString(2)).isEqualTo("ORIGINAL");
                assertThat(r.getLong(3)).isEqualTo(2);
            }
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM users WHERE id=2")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM customers WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM shops WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM users WHERE id=1")).isInstanceOf(SQLException.class);
            assertThatThrownBy(() -> s.executeUpdate("UPDATE ledger_entries SET amount=1 WHERE id=1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.executeUpdate("DELETE FROM ledger_entries WHERE id=1"))
                    .isInstanceOf(SQLException.class).hasMessageContaining("append-only");
            assertThatThrownBy(() -> s.executeUpdate("INSERT INTO ledger_entries(entry_type,amount,customer_id,recorded_by_user_id) VALUES ('DEBIT',-1,1,2)"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("INSERT INTO ledger_entries(entry_type,amount,customer_id,recorded_by_user_id,entry_kind,reverses_entry_id,reason) VALUES ('CREDIT',100,1,1,'REVERSAL',1,'Duplicate invoice')");
            assertThatThrownBy(() -> s.executeUpdate("INSERT INTO ledger_entries(entry_type,amount,customer_id,recorded_by_user_id,entry_kind,reverses_entry_id,reason) VALUES ('CREDIT',100,1,1,'REVERSAL',1,'Duplicate invoice')"))
                    .isInstanceOf(SQLException.class);
            s.executeUpdate("UPDATE users SET archived_at=CURRENT_TIMESTAMP(6),archived_by_user_id=1,archive_reason='Left business' WHERE id=2");
            try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM ledger_entries")) {
                r.next(); assertThat(r.getInt(1)).isEqualTo(2);
            }
        }
    }
}
