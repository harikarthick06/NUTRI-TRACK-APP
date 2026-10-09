# API reference

Base URL: `http://localhost:8080` in development. Interactive documentation with request and response
schemas is at `/swagger-ui.html`, and the OpenAPI document is at `/v3/api-docs`.

## Authentication

- `POST /api/auth/register` or `/api/auth/login` returns `{ accessToken, tokenType, expiresInSeconds, user }` and sets the
  `nt_refresh` cookie (HttpOnly, path `/api/auth`).
- Send the access token on every other request: `Authorization: Bearer <accessToken>`.
- When it expires (401), call `POST /api/auth/refresh`. The browser sends the cookie; the response
  holds a new access token and a new cookie. Each refresh token works once.
- Everything under `/api/**` except register, login, refresh and logout needs a token. Every record
  is scoped to the signed-in user; another user's ids return 404.

## Conventions

- JSON everywhere except CSV exports. Dates are `YYYY-MM-DD`, months `YYYY-MM`, and timestamps ISO-8601 UTC.
- **Null fields are left out of responses.** In particular, a nutrient `amount` that is absent means
  *unknown*, not zero. Totals include `entriesWithData` and `entries`, so clients can tell partial data apart.
- Nutrient amounts use the canonical unit in `/api/nutrients` (`KCAL`, `G`, `MG`, `UG`, `ML`).
- Report ranges are limited to 366 days.

## Errors

```json
{
  "timestamp": "2026-10-09T10:00:00Z",
  "status": 400,
  "error": "Bad Request",
  "code": "VALIDATION_FAILED",
  "message": "Some fields are invalid",
  "path": "/api/water-logs",
  "fieldErrors": [{ "field": "amountMl", "message": "must be less than or equal to 5000" }]
}
```

Common codes: `VALIDATION_FAILED` (400), `UNAUTHORIZED` (401), `NOT_FOUND` (404), conflicts such as
`EMAIL_TAKEN` or `WEIGHT_EXISTS` (409), and rule violations such as `UPPER_BELOW_TARGET` (400).

## Endpoints

### Authentication

| Method | Path | Query | Description |
|---|---|---|---|
| POST | `/api/auth/login` |  | Log in with email and password |
| POST | `/api/auth/logout` |  | Log out on all devices and clear the refresh cookie |
| POST | `/api/auth/refresh` |  | Exchange the refresh cookie for a new access token (rotates the refresh token) |
| POST | `/api/auth/register` |  | Create an account and start a session |

### Account

| Method | Path | Query | Description |
|---|---|---|---|
| DELETE | `/api/account` |  | Permanently delete the account and all of its data |
| PUT | `/api/account/password` |  | Change password; signs out all other sessions and returns a new one |

### Profile

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/profile` |  | Get the current user's profile and goals |
| PUT | `/api/profile` |  | Replace the current user's profile and goals |
| GET | `/api/profile/target-suggestions` |  | Suggest editable calorie, macro, fibre and water targets from the profile |

### Nutrients

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/nutrients` |  | All tracked nutrients with their canonical units |

### Foods

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/foods/barcode/{barcode}` |  | Find a packaged food by barcode (local first, then Open Food Facts if enabled) |
| GET | `/api/foods/categories` |  | Food categories for filtering |
| POST | `/api/foods/custom` |  | Create a private custom food |
| GET | `/api/foods/external/search` | `provider`, `q`, `limit` | Search an external provider (USDA_FDC or OPEN_FOOD_FACTS) without storing results |
| GET | `/api/foods/favorites` | `page`, `size` | Your favourite foods |
| POST | `/api/foods/import` |  | Import an external food so it can be logged |
| GET | `/api/foods/providers` |  | External food data providers and whether they are configured |
| GET | `/api/foods/recent` | `size` | Foods you logged most recently |
| GET | `/api/foods/search` | `q`, `scope`, `category`, `page`, `size` | Search local foods (seeded, imported, your custom foods and recipes) |
| GET | `/api/foods/{id}` |  | Food details with every tracked nutrient (unknown values are flagged, not zero) |
| PUT | `/api/foods/{id}` |  | Edit one of your custom foods |
| DELETE | `/api/foods/{id}` |  | Archive one of your custom foods (past diary entries keep their values) |
| POST | `/api/foods/{id}/favorite` |  | Mark as favourite |
| DELETE | `/api/foods/{id}/favorite` |  | Remove from favourites |

### Recipes

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/recipes` |  | Your recipes and the built-in South Indian recipes |
| POST | `/api/recipes` |  | Create a recipe |
| POST | `/api/recipes/calculate` |  | Calculate nutrition for an unsaved recipe (recipe builder preview) |
| GET | `/api/recipes/{id}` |  | Recipe with ingredients and calculated nutrition |
| PUT | `/api/recipes/{id}` |  | Edit one of your recipes |
| DELETE | `/api/recipes/{id}` |  | Delete one of your recipes |
| POST | `/api/recipes/{id}/duplicate` |  | Copy a recipe (e.g. a built-in one) into an editable recipe of your own |
| POST | `/api/recipes/{id}/log` |  | Log servings of the recipe, or the whole recipe, to the diary |
| GET | `/api/recipes/{id}/nutrition` |  | Calculated nutrition of a saved recipe (per 100 g, per serving and total) |

### Saved meals

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/saved-meals` |  | Your saved meals |
| POST | `/api/saved-meals` |  | Create a saved meal from foods |
| POST | `/api/saved-meals/from-diary` |  | Save a logged meal (date + meal) as a reusable meal |
| GET | `/api/saved-meals/{id}` |  | A saved meal with its items |
| PUT | `/api/saved-meals/{id}` |  | Rename or change a saved meal |
| DELETE | `/api/saved-meals/{id}` |  | Delete a saved meal |
| POST | `/api/saved-meals/{id}/log` |  | Add every item of the saved meal to the diary |

### Food diary

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/diary` | `date` | All entries for a day, grouped by meal, with meal and day macro totals |
| POST | `/api/diary/copy-day` |  | Copy a day's entries (optionally only some meals) to another day |
| POST | `/api/diary/copy-meal` |  | Copy one meal to another day and/or meal |
| PUT | `/api/diary/days/{date}` |  | Mark a day's log as complete or incomplete |
| POST | `/api/diary/entries` |  | Log a food. Use weightState=COOKED_WEIGHT_OF_RAW_FOOD to enter a cooked weight for a raw food |
| PUT | `/api/diary/entries/{id}` |  | Change quantity, unit, meal, date or notes of an entry |
| DELETE | `/api/diary/entries/{id}` |  | Delete an entry |

### Nutrition summaries

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/nutrition/daily` | `date` | Every nutrient for one day, with data coverage and comparison with targets |
| GET | `/api/nutrition/micronutrients` | `date`, `category`, `q`, `status` | Vitamins, minerals, fatty acids and other nutrients for one day, filterable |

### Nutrient targets

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/nutrient-targets` |  | Daily targets and upper limits for every nutrient, with where each value came from |
| PUT | `/api/nutrient-targets` |  | Set your own targets or upper limits; send both as null to go back to the reference value |
| DELETE | `/api/nutrient-targets` |  | Remove all custom targets |
| DELETE | `/api/nutrient-targets/{code}` |  | Remove the custom target for one nutrient |
| GET | `/api/reference-values` | `system`, `code` | The reference value table, with sources |

### Weight and water

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/water-logs` | `date` | Water entries and total for one day (default: today) |
| POST | `/api/water-logs` |  | Log water (1-5,000 mL); returns the day |
| GET | `/api/water-logs/history` | `from`, `to` | Daily water totals in a date range (default: last 30 days) |
| PUT | `/api/water-logs/{id}` |  | Edit a water entry |
| DELETE | `/api/water-logs/{id}` |  | Delete a water entry; returns the day |
| GET | `/api/weight-logs` | `from`, `to` | Weight entries in a date range (default: last 90 days) with summary statistics |
| POST | `/api/weight-logs` |  | Log weight, waist and body fat for a date (one per day) |
| PUT | `/api/weight-logs/{id}` |  | Edit a weight entry |
| DELETE | `/api/weight-logs/{id}` |  | Delete a weight entry |

### Reports, calendar and exports

| Method | Path | Query | Description |
|---|---|---|---|
| GET | `/api/analytics/trend` | `code`, `from`, `to` | Daily series of one nutrient (default: energy, last 30 days) |
| GET | `/api/calendar` | `month` | Per-day overview of a month for the calendar/history page |
| GET | `/api/exports/daily-report.csv` | `date` | CSV: every nutrient for one day |
| GET | `/api/exports/diary.csv` | `from`, `to` | CSV: every diary entry in a range |
| GET | `/api/exports/nutrient-summary.csv` | `from`, `to` | CSV: daily nutrient totals in a range |
| GET | `/api/exports/weight.csv` | `from`, `to` | CSV: weight entries in a range |
| GET | `/api/reports/custom` | `from`, `to` | Report for any range of up to 366 days |
| GET | `/api/reports/monthly` | `month` | Report for a calendar month, e.g. month=2026-10 (default: this month) |
| GET | `/api/reports/weekly` | `date` | Monday-Sunday report for the week containing the date (default: this week) |

## Spec-compatible aliases

These paths from the original specification serve exactly the same responses:

| Alias | Same as |
|---|---|
| `/api/nutrition/weekly` | `/api/reports/weekly` |
| `/api/nutrition/monthly` | `/api/reports/monthly` |
| `/api/analytics/nutrition` | `/api/reports/custom` |
| `/api/analytics/weight` | `/api/weight-logs` |
| `/api/export/diary` | `/api/exports/diary.csv` |
| `/api/export/nutrition` | `/api/exports/nutrient-summary.csv` |

## Example

```bash
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"you@example.com","password":"your-password"}' | jq -r .accessToken)

# Log 2 medium idlis (portion id from GET /api/foods/{id}) for breakfast
curl -s -X POST localhost:8080/api/diary/entries -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"foodId":153,"date":"2026-10-09","mealType":"BREAKFAST","quantity":2,"unit":"SERVING","portionId":115}'

curl -s "localhost:8080/api/nutrition/daily?date=2026-10-09" -H "Authorization: Bearer $TOKEN"
```
