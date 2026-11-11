#!/usr/bin/env python3
"""Create local demo configuration without overwriting existing secrets."""
import base64
import os
from pathlib import Path
import secrets

path = Path(__file__).resolve().parents[1] / '.env'
values = {
    'MYSQL_ROOT_PASSWORD': secrets.token_hex(24),
    'MIGRATION_DB_PASSWORD': secrets.token_hex(24),
    'DB_PASSWORD': secrets.token_hex(24),
    'JWT_SECRET': base64.b64encode(secrets.token_bytes(32)).decode(),
    'BOOTSTRAP_ADMIN_ENABLED': 'true',
    'BOOTSTRAP_ADMIN_USERNAME': 'demo-admin',
    'BOOTSTRAP_ADMIN_EMAIL': 'admin@example.test',
    'BOOTSTRAP_ADMIN_PASSWORD': secrets.token_urlsafe(24),
    'CORS_ALLOWED_ORIGINS': '',
}
try:
    with path.open('x') as output:
        output.write('# Local demo secrets; generated independently on each machine. Never commit.\n')
        output.writelines(f'{key}={value}\n' for key, value in values.items())
    os.chmod(path, 0o600)
    print('Created local .env. Next: docker compose up --build --wait')
except FileExistsError:
    print('Existing .env retained. Next: docker compose up --build --wait')
