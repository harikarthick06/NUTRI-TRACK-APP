# Setup

The quickest way to run NutriTrack is the Docker setup in the [README](../README.md#quick-start-docker-recommended):
one script starts MySQL, the API and the web app. This page covers running the parts yourself.

## Requirements

| Tool | Version |
|---|---|
| Java | 21 |
| Maven | 3.9 or newer |
| Node.js | 20.19+ or 22.12+ (needed by Vite 7) |
| MySQL | 8.0 or newer |

## MySQL

Create the database and a user for the app. The schema, the nutrient table, reference values and
the seed foods are created by Flyway the first time the backend starts.

```sql
CREATE DATABASE nutritrack CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
CREATE USER 'nutritrack'@'localhost' IDENTIFIED BY 'choose-a-password';
GRANT ALL PRIVILEGES ON nutritrack.* TO 'nutritrack'@'localhost';
```

The backend stores all timestamps in UTC (`serverTimezone=UTC` in the JDBC URL). Each user's own time
zone, set in Profile & settings, decides which calendar day an entry belongs to.

### Test database

The integration tests (`mvn verify`) use a separate, empty database so they never touch your data:

```sql
CREATE DATABASE nutritrack_test CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
GRANT ALL PRIVILEGES ON nutritrack_test.* TO 'nutritrack'@'localhost';
```

Run them with the password in an environment variable:

```bash
cd backend
IT_DB_PASSWORD=choose-a-password mvn verify
```

`IT_DB_URL` and `IT_DB_USERNAME` override the defaults (`jdbc:mysql://localhost:3306/nutritrack_test`,
`nutritrack`). Each test registers new users with random emails, so the database needs no cleanup.

## Configuration

```bash
cp .env.example .env
openssl rand -base64 48     # paste the output into JWT_SECRET in .env
```

Set `DB_PASSWORD` too. The backend reads `.env` from the repository root (or from `backend/`)
automatically; real environment variables take priority. See [configuration.md](configuration.md).

## Running locally

```bash
# Terminal 1: API on http://localhost:8080
cd backend
mvn spring-boot:run

# Terminal 2: web app on http://localhost:5173
cd frontend
npm install
npm run dev
```

The Vite dev server forwards `/api` to the backend, so the browser sees one origin and the refresh
cookie works without any CORS setup. Swagger UI is at http://localhost:8080/swagger-ui.html.

To run the packaged backend instead: `mvn package` then `java -jar target/nutritrack-backend-1.0.0.jar`
(from `backend/`, so it finds `../.env`).

## Production build

```bash
cd backend && mvn -B package                 # target/nutritrack-backend-1.0.0.jar
cd frontend && npm ci && npm run build      # static files in frontend/dist
```

Recommended layout: serve `frontend/dist` and proxy `/api` to the backend from the **same origin**
(for example nginx or Caddy), so the refresh cookie stays first-party:

```nginx
location /api/ { proxy_pass http://127.0.0.1:8080; proxy_set_header X-Forwarded-Proto $scheme; }
location /     { root /srv/nutritrack/dist; try_files $uri /index.html; }
```

For production also:

- Serve over HTTPS and set `COOKIE_SECURE=true`.
- Use a strong, unique `JWT_SECRET` and database password, supplied by your platform's secret store.
- Set `CORS_ALLOWED_ORIGINS` to your site's origin (only needed if the API is on a different origin).
- If the API must live on a different site, build the frontend with `VITE_API_BASE_URL=https://api.example.com`
  and set `COOKIE_SAME_SITE=None` with `COOKIE_SECURE=true`. Same-origin hosting is simpler and safer.
- Back up the MySQL database; it holds every user's logs.

## Regenerating seed data

The seed-food migrations are generated from published datasets by `tools/seed/generate_seed.py`.
See [tools/seed/README.md](../tools/seed/README.md). Never edit an applied migration; add a new one.
