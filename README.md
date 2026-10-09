# NutriTrack

A full-stack daily nutrition tracker with a focus on Indian and Tamil Nadu foods. Log what you eat
in grams, cups or household portions ("2 medium idli", "1 katori sambar"), and see calories, macros,
fat types, fibre, sugars, cholesterol, 13 vitamins, minerals, omega-3/6 and water against reference
values, plus weight and water tracking, reports, a calendar and CSV exports.

NutriTrack is a logging tool, not medical advice. It never diagnoses deficiencies, never recommends
supplements, and treats a value it doesn't know as **unknown**, never as zero.

## Features

- **Accounts:** register, log in, change your password, or delete your account and all its data.
  Sessions use a short-lived JWT plus a rotating, HttpOnly refresh cookie.
- **Onboarding and profile:** age, sex, height, weight, activity and goal. Editable calorie, macro,
  fibre and water targets are suggested from the profile (Mifflin-St Jeor), with the method shown.
- **Food database:** 99 foods from the Indian Food Composition Tables 2017, 53 from USDA SR28 and
  19 built-in dishes (idli, dosa, sambar, rasam, curd rice, ven pongal, rava upma, poriyal, Tamil-style
  chicken curry, biryani, filter coffee and more),
  calculated from their ingredients. Optional online search of USDA FoodData Central and Open Food
  Facts, including barcodes. Each food shows its source, data quality and how complete its data is.
- **Custom foods and recipes:** create foods from a label. The recipe builder calculates per-100 g and
  per-serving nutrition live, including cooked-weight yield. Built-in recipes can be duplicated and edited.
- **Food diary:** six meals a day; edit, delete, copy a meal or a whole day, save a meal for reuse,
  log a cooked weight for a raw food, and mark a day complete.
- **Vitamins and minerals:** every tracked nutrient compared with its target and upper limit, with
  filters, search, compact and detailed views, collapsible categories and data-coverage badges.
- **Reference targets:** NASEM Dietary Reference Intakes picked by age and sex (RDA, then AI) and
  upper limits, each labelled with its source. You can override any target or limit.
- **Weight:** a line chart with a 7-day average, waist and body fat. The page reminds you that
  day-to-day changes are mostly water and food, not fat.
- **Water:** quick-add buttons, mL or L, a daily goal and history. The goal is editable and the app
  explains that needs vary.
- **Reports:** daily, weekly, monthly and custom ranges, with averages, macro split, meal split,
  top foods and nutrient trends.
- **Exports:** four CSV exports (daily report, diary, nutrient summary, weight).
- **Calendar:** month view showing complete and incomplete days, review any day, compare two days,
  copy a day to today.
- **Interface:** light and dark themes, a sidebar on desktop and a bottom bar on mobile, skeleton
  loaders, empty states, toasts and confirmation dialogs.

## Tech stack

| Layer | Technology |
|---|---|
| Frontend | React 19, TypeScript, Vite 7, Tailwind CSS 4, shadcn-style components (Radix UI), Recharts, React Router, TanStack Query, React Hook Form + Zod |
| Backend | Java 21, Spring Boot 3.5 (Web, Security, Data JPA, Validation, Actuator), JJWT, springdoc-openapi, Maven |
| Database | MySQL 8 with Flyway migrations |
| Tests | JUnit 5 + Mockito + MockMvc integration tests (backend), Vitest + React Testing Library (frontend) |

## Quick start (Docker, recommended)

You only need [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine with
the compose plugin). Make sure Docker is running, then from the repository folder:

```bash
./setup.sh          # macOS / Linux
.\setup.ps1         # Windows PowerShell
```

The script creates `.env` with random database passwords and a JWT secret (it never overwrites values
you've set), builds the app and starts three containers: MySQL, the API and the web app. When it
prints the address, open **http://localhost:3000**, create an account and follow the onboarding.

| Task | Command |
|---|---|
| Stop the app | `docker compose down` (your data stays in the `db-data` volume) |
| Start it again | `./setup.sh` or `docker compose up -d` |
| See logs | `docker compose logs -f backend` |
| Update after `git pull` | `./setup.sh` (rebuilds the images) |
| Use another port | set `APP_PORT=8088` in `.env`, then rerun the script |
| Delete everything, including data | `docker compose down -v` |

Online food search is off by default. To use it, add a USDA key or enable Open Food Facts in `.env`
(see [docs/configuration.md](docs/configuration.md)) and rerun the script.

## Manual setup (for development)

You need Java 21, Maven 3.9+, Node.js 20.19+ (or 22.12+) and MySQL 8.

```bash
# 1. Database (see docs/setup.md for details)
mysql -u root -p -e "CREATE DATABASE nutritrack CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
  CREATE USER 'nutritrack'@'localhost' IDENTIFIED BY 'choose-a-password';
  GRANT ALL PRIVILEGES ON nutritrack.* TO 'nutritrack'@'localhost';"

# 2. Configuration
cp .env.example .env
# edit .env: set DB_PASSWORD, and JWT_SECRET to the output of:
openssl rand -base64 48

# 3. Backend (http://localhost:8080; Flyway creates the schema and seed data on first start)
cd backend && mvn spring-boot:run

# 4. Frontend, in a second terminal (http://localhost:5173)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. The API documentation is at http://localhost:8080/swagger-ui.html.

## Tests

```bash
cd backend && mvn test            # 68 unit tests, no database needed
cd backend && IT_DB_PASSWORD=... mvn verify   # plus 16 integration tests against MySQL (nutritrack_test)
cd frontend && npm test           # 34 Vitest + Testing Library tests
cd frontend && npm run typecheck && npm run build
```

The integration tests need an empty `nutritrack_test` database the `nutritrack` user can use. See
[docs/setup.md](docs/setup.md#test-database).

## Project layout

```
docker-compose.yml, setup.sh, setup.ps1   One-command Docker setup
backend/    Spring Boot API (com.nutritrack.*) and Flyway migrations (src/main/resources/db/migration)
frontend/   React app (src/pages, src/components, src/hooks/queries.ts, src/lib/api.ts)
tools/seed/ Script that generates the seed-data migrations from IFCT 2017 and USDA SR28
docs/       Setup, configuration, API, data sources and troubleshooting
```

## Documentation

- [Setup](docs/setup.md): MySQL, running locally, the test database and production builds
- [Configuration](docs/configuration.md): every environment variable and the USDA API key
- [API](docs/api.md): endpoints, authentication and error format
- [Data sources and methods](docs/data-sources.md): where the numbers come from, licences and limits
- [Troubleshooting](docs/troubleshooting.md)

## Security notes

- Secrets come only from environment variables or a local `.env`, which is git-ignored. The backend
  refuses to start without a `JWT_SECRET` of at least 256 bits.
- Passwords are hashed with BCrypt. Passwords, tokens and food logs are never written to the logs.
- Every query is scoped to the signed-in user; another user's records return 404.
- CSV exports neutralise spreadsheet formulas in cells.

## Known limitations

- Reference values are NASEM (US/Canada) DRIs. ICMR-NIN 2020 values are not included yet; they can
  be added as rows with `reference_system = 'ICMR_NIN'`. See [docs/data-sources.md](docs/data-sources.md).
- Pregnancy and breastfeeding reference values are not included.
- IFCT 2017 has no carbohydrate or fibre values for eggs and meat, so those show as unknown.
