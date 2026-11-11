package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/** Additive upgrade; preserves V1 rows and discovers FK names from database metadata. */
public class V2__Protect_financial_history extends BaseJavaMigration {
    @Override public Integer getChecksum() { return 2026111002; }

    @Override public void migrate(Context context) throws Exception {
        Connection c = context.getConnection();
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(
                "SELECT COUNT(*) FROM ledger_entries WHERE amount <= 0 OR entry_type IS NULL")) {
            r.next();
            if (r.getLong(1) != 0) throw new SQLException("Reconcile invalid legacy ledger rows before migrating.");
        }
        for (String table : List.of("users", "shops", "customers")) {
            sql(c, "ALTER TABLE " + table + " ADD archived_at TIMESTAMP(6) NULL, "
                    + "ADD archived_by_user_id BIGINT NULL, ADD archive_reason VARCHAR(500) NULL, "
                    + "ADD CONSTRAINT fk_" + table + "_archived_by FOREIGN KEY (archived_by_user_id) REFERENCES users(id) ON DELETE RESTRICT");
        }
        restrict(c, "shops", "owner_id", "users", "fk_shops_owner");
        restrict(c, "customers", "shop_id", "shops", "fk_customers_shop");
        restrict(c, "ledger_entries", "customer_id", "customers", "fk_ledger_customer");
        restrict(c, "ledger_entries", "recorded_by_user_id", "users", "fk_ledger_recorder");
        sql(c, "ALTER TABLE ledger_entries "
                + "ADD entry_kind VARCHAR(20) NOT NULL DEFAULT 'ORIGINAL', "
                + "ADD reverses_entry_id BIGINT NULL, ADD replaces_entry_id BIGINT NULL, "
                + "ADD reason VARCHAR(500) NULL, "
                + "ADD CONSTRAINT uk_ledger_reversal UNIQUE (reverses_entry_id), "
                + "ADD CONSTRAINT uk_ledger_replacement UNIQUE (replaces_entry_id), "
                + "ADD CONSTRAINT fk_ledger_reversal FOREIGN KEY (reverses_entry_id) REFERENCES ledger_entries(id) ON DELETE RESTRICT, "
                + "ADD CONSTRAINT fk_ledger_replacement FOREIGN KEY (replaces_entry_id) REFERENCES ledger_entries(id) ON DELETE RESTRICT, "
                + "ADD CONSTRAINT ck_ledger_amount CHECK (amount > 0), "
                + "ADD CONSTRAINT ck_ledger_kind CHECK (entry_kind IN ('ORIGINAL','REVERSAL','REPLACEMENT')), "
                + "ADD CONSTRAINT ck_ledger_links CHECK ("
                + "(entry_kind = 'ORIGINAL' AND reverses_entry_id IS NULL AND replaces_entry_id IS NULL AND reason IS NULL) OR "
                + "(entry_kind = 'REVERSAL' AND reverses_entry_id IS NOT NULL AND replaces_entry_id IS NULL AND CHAR_LENGTH(TRIM(reason)) > 0 AND reason IS NOT NULL) OR "
                + "(entry_kind = 'REPLACEMENT' AND reverses_entry_id IS NULL AND replaces_entry_id IS NOT NULL AND CHAR_LENGTH(TRIM(reason)) > 0 AND reason IS NOT NULL))");
        sql(c, "CREATE INDEX idx_ledger_customer_history ON ledger_entries(customer_id, created_at, id)");
        // Single-statement triggers need no DELIMITER handling and reject even direct SQL mutations.
        sql(c, "CREATE TRIGGER ledger_entries_no_update BEFORE UPDATE ON ledger_entries FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ledger entries are append-only; use a reversal or correction'");
        sql(c, "CREATE TRIGGER ledger_entries_no_delete BEFORE DELETE ON ledger_entries FOR EACH ROW "
                + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Ledger entries are append-only; use a reversal or correction'");
    }

    private static void restrict(Connection c, String table, String column, String parent, String name) throws SQLException {
        List<String> constraints = new ArrayList<>();
        try (ResultSet keys = c.getMetaData().getImportedKeys(c.getCatalog(), null, table)) {
            while (keys.next()) if (column.equalsIgnoreCase(keys.getString("FKCOLUMN_NAME"))) {
                constraints.add(keys.getString("FK_NAME"));
            }
        }
        if (constraints.size() != 1) throw new SQLException("Expected one foreign key for " + table + "." + column);
        String old = constraints.get(0).replace("`", "``");
        sql(c, "ALTER TABLE " + table + " DROP FOREIGN KEY `" + old + "`, ADD CONSTRAINT " + name
                + " FOREIGN KEY (" + column + ") REFERENCES " + parent + "(id) ON DELETE RESTRICT");
    }

    private static void sql(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) { s.execute(sql); }
    }
}
