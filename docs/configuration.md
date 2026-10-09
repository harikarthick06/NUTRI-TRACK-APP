# Configuration

All settings come from environment variables. For local development, put them in `.env` at the
repository root (copied from `.env.example`). `.env` is git-ignored. Never commit real values.

## Backend

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_URL` | no | `jdbc:mysql://localhost:3306/nutritrack?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC` | JDBC URL. Keep `serverTimezone=UTC`. Use `useSSL=true` for a remote database. |
| `DB_USERNAME` | no | `nutritrack` | Database user |
| `DB_PASSWORD` | yes | (empty) | Database password |
| `JWT_SECRET` | yes | (none) | Base64 key of at least 32 bytes for signing access tokens. Generate with `openssl rand -base64 48`. The app won't start without it. Changing it signs everyone out. |
| `JWT_ACCESS_TTL_MINUTES` | no | `30` | Access token lifetime |
| `JWT_REFRESH_TTL_DAYS` | no | `14` | Refresh cookie lifetime. Refresh tokens rotate on every use and are stored hashed. |
| `CORS_ALLOWED_ORIGINS` | no | `http://localhost:5173` | Comma-separated origins allowed to call the API from a browser |
| `COOKIE_SECURE` | no | `false` | Set `true` whenever the site uses HTTPS |
| `COOKIE_SAME_SITE` | no | `Strict` | `Strict`, `Lax`, or `None` (only for a cross-site API, and only with `COOKIE_SECURE=true`) |
| `SERVER_PORT` | no | `8080` | HTTP port |
| `USDA_FDC_ENABLED` | no | `false` | Turn on USDA FoodData Central search and import |
| `USDA_FDC_API_KEY` | if USDA enabled | (empty) | Your FoodData Central API key |
| `USDA_FDC_BASE_URL` | no | `https://api.nal.usda.gov/fdc/v1` | Override for testing |
| `OPENFOODFACTS_ENABLED` | no | `false` | Turn on Open Food Facts search, import and barcode lookup |
| `OPENFOODFACTS_USER_AGENT` | recommended | `NutriTrack/1.0 (contact not configured)` | Open Food Facts asks every app to identify itself with a contact address |
| `OPENFOODFACTS_BASE_URL` | no | `https://world.openfoodfacts.org` | Override for testing |

Integration tests use `IT_DB_URL`, `IT_DB_USERNAME` and `IT_DB_PASSWORD` (see [setup.md](setup.md#test-database)).

## Frontend

Vite reads `VITE_*` variables from `frontend/.env`, `frontend/.env.local` or the shell at build time.
They end up in the public JavaScript bundle, so they must never contain secrets.

| Variable | Default | Purpose |
|---|---|---|
| `VITE_API_BASE_URL` | (empty: same origin) | Base URL of the API. Leave empty in development and for same-origin deployments. |
| `VITE_DEV_API_PROXY` | `http://localhost:8080` | Shell variable read by `npm run dev`: where the dev server forwards `/api` |

## Getting a USDA FoodData Central API key

1. Sign up (free) at https://fdc.nal.usda.gov/api-key-signup. The key arrives by email.
2. In `.env`: `USDA_FDC_ENABLED=true` and `USDA_FDC_API_KEY=<your key>`.
3. Restart the backend. Food search shows an **Online** tab with USDA as a source.

The key stays on the server; the browser never sees it. Searches are not stored. A food is saved to
your database only when you import it, with its source, its FDC id and a link back.

## Open Food Facts

No key is needed. Set `OPENFOODFACTS_ENABLED=true` and a User-Agent with a real contact, for example
`OPENFOODFACTS_USER_AGENT=NutriTrack/1.0 (you@example.com)`. Open Food Facts data is crowd-sourced
and labelled as such in the app (see [data-sources.md](data-sources.md)).

## Logging

The backend logs at INFO. It never logs passwords, tokens, cookies, request bodies or food logs.
Provider errors are logged with the HTTP status only, so API keys never appear in logs.
