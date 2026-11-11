# Two-minute reviewer demo

Requires Docker with Compose v2 and Python 3.9+. No Java or Maven installation is needed for this path.

```bash
python scripts/init_env.py
docker compose up --build --wait
python scripts/demo.py
```

The first build downloads Java, Maven dependencies, and MySQL. Later runs reuse cached images and the database volume. The API binds only to `127.0.0.1:8080`; MySQL is not exposed on a host port. Bootstrap credentials and database passwords are generated locally in ignored `.env`, not embedded in the repository.

The script creates isolated synthetic demo records and verifies actual API responses. It does not print passwords or tokens. Re-running creates a new demo owner/shop/customer, so earlier runs remain intact. It finishes by archiving its staff, customer, and shop; synthetic owner accounts remain available to the administrator.

| Time | Show | Interview talking point |
| --- | --- | --- |
| 0:00–0:20 | Owner, shop, assigned staff, customer | Role and shop-level authorization |
| 0:20–0:40 | Invoice 100, payment 40; balance 60 | Decimal arithmetic and the nonnegative balance rule |
| 0:40–1:10 | Correct invoice to 120 | Original remains; linked reversal and replacement yield balance 80 |
| 1:10–1:30 | Duplicate reversal rejected | Unique reversal reference plus customer locking |
| 1:30–2:00 | Archive staff and customer | Token disabled; payment/history retained; new writes blocked |

Open [Swagger UI](http://localhost:8080/swagger-ui/index.html) to inspect the API. [requests.http](requests.http) contains manual requests. [demo-output.txt](demo-output.txt) records the executed walkthrough.

Stop without deleting the demo database:

```bash
docker compose down
```

For an intentional complete reset of **local synthetic demo data**, `docker compose down -v` removes the database volume. Do not use that command against data you want to keep. Changing generated database passwords while retaining an existing volume does not automatically change MySQL account passwords.

After initial setup, set `BOOTSTRAP_ADMIN_ENABLED=false` in `.env` and restart the app. Keep the admin credentials locally if you want to run the demo again. The setup script never overwrites an existing `.env`.
