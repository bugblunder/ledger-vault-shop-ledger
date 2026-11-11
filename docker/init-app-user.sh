#!/bin/bash
set -euo pipefail
# SQL literal escaping uses NO_BACKSLASH_ESCAPES, including for custom local passwords.
escaped_password=$(printf '%s' "$APP_DB_PASSWORD" | sed "s/'/''/g")
MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot <<SQL
SET SESSION sql_mode = 'NO_BACKSLASH_ESCAPES';
CREATE USER 'ledger_app'@'%' IDENTIFIED BY '$escaped_password';
GRANT SELECT, INSERT, UPDATE, DELETE ON ledgervault.* TO 'ledger_app'@'%';
SQL
