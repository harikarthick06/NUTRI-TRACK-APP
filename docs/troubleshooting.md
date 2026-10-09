# Troubleshooting

## Docker setup

**"Docker is installed but not running"**
Start Docker Desktop and wait until it says it's running, then rerun the script.

**`port is already allocated` for 3000**
Another program uses port 3000. Set `APP_PORT=8088` (or any free port) in `.env` and rerun the script.

**`Access denied for user 'nutritrack'` in `docker compose logs backend` after editing `.env`**
MySQL keeps the password it was first created with. Put the original `DB_PASSWORD` back, or start
over with `docker compose down -v` (this deletes all data).

**On Windows: "running scripts is disabled on this system"**
Run `powershell -ExecutionPolicy Bypass -File .\setup.ps1` instead.

**The script waits and then says the app didn't start**
Look at `docker compose logs backend`. The first start creates the database and loads the food
data, which can take a minute on a slow machine; rerunning the script is safe.

## Backend won't start

**`JWT_SECRET is not set` / `must be base64 encoded` / `must decode to at least 32 bytes`**
Generate a key with `openssl rand -base64 48` and put it in `.env` as `JWT_SECRET=...`, with no quotes.
`.env` must be in the repository root or in `backend/`, and the backend must be started from
`backend/` (or the root) to find it.

**`Access denied for user 'nutritrack'@'localhost'`**
`DB_PASSWORD` doesn't match the MySQL user. Check it with `mysql -u nutritrack -p nutritrack`.

**`Public Key Retrieval is not allowed`**
Keep `allowPublicKeyRetrieval=true` in `DB_URL` for local MySQL 8, or enable SSL (`useSSL=true`) for a remote server.

**`Unknown database 'nutritrack'`**
Create it first; see [setup.md](setup.md#mysql).

**`Validate failed: Migrations have failed validation` / checksum mismatch**
An applied migration file was changed. Restore the original file from git. To start over on a
development database, drop and recreate it; Flyway will rebuild everything (this deletes all data).

**`Port 8080 was already in use`**
Stop the other process, or set `SERVER_PORT` and point the frontend at it with `VITE_DEV_API_PROXY`.

**`Schema-validation: wrong column type`**
The database was changed by hand or by a different build. Recreate the development database.

## Logging in and sessions

**Logged out after refreshing the page**
The refresh cookie isn't reaching the API. In development, use `npm run dev` and leave
`VITE_API_BASE_URL` empty so requests go through the Vite proxy. If the site uses HTTPS,
set `COOKIE_SECURE=true`. If the API is on a different site, see the cross-site notes in [setup.md](setup.md#production-build).

**CORS error in the browser console**
Add the exact frontend origin, including the port, to `CORS_ALLOWED_ORIGINS`. It isn't needed when using the dev proxy.

**Everyone was signed out**
`JWT_SECRET` changed, or a user changed their password (which ends their other sessions).

## Frontend

**`npm run dev` fails with a Node version error**
Vite 7 needs Node 20.19+ or 22.12+. Check with `node -v`.

**Blank page with `Failed to fetch` / "Can't reach the server"**
The backend isn't running on the proxy target. Check `curl http://localhost:8080/actuator/health`.

**A page shows old data after an edit**
Data is cached briefly and refreshed after every change you make. If another device made the
change, reload the page.

## Food data

**A nutrient shows "Unknown" or "No data"**
None of the foods you logged has a value for it. This is expected for nutrients that food tables
rarely measure (iodine, chromium, biotin, EPA/DHA in most foods). It doesn't mean your intake is zero.

**"Incomplete data" on a nutrient**
At least one logged food, or an ingredient of a logged dish, has no value, so the real total is
probably higher than shown.

**Eggs or meat show unknown carbohydrate or fibre**
IFCT 2017 doesn't report these for animal foods. They are close to zero in practice, but NutriTrack
doesn't fill in values its sources don't give.

**Online search tab is missing or USDA isn't listed**
Set `USDA_FDC_ENABLED=true` and `USDA_FDC_API_KEY`, or `OPENFOODFACTS_ENABLED=true`, then restart the
backend. The Providers list in the Online tab shows what's configured.

**USDA search says the provider is unavailable**
The key may be wrong or rate-limited (1,000 requests an hour per key). The backend log shows the HTTP
status. The key itself is never logged.

## Tests

**Integration tests fail with `Access denied` or `Unknown database 'nutritrack_test'`**
Create the test database and pass `IT_DB_PASSWORD`; see [setup.md](setup.md#test-database).
`mvn test` runs only the unit tests and needs no database.

**Running `mvn verify` while the app runs from `target/*.jar` breaks the running app**
Maven rebuilds the jar the app is running from. Restart the app afterwards, or run it with
`mvn spring-boot:run` during development.
