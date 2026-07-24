# fitlab-backend

Spring Boot REST backend for FIT//LAB, the streetwear fit-builder app.

## Run

```
mvn spring-boot:run
```

Serves on `http://localhost:8080`. On first boot, if the catalog is empty it seeds
from `src/main/resources/seed/starter-catalog.json`.

## Config (env vars)

| Var | Default | Purpose |
|---|---|---|
| `DB_URL` | `jdbc:h2:file:./data/fitlab;AUTO_SERVER=TRUE` | Swap for a Postgres JDBC URL in prod |
| `DB_DRIVER` | `org.h2.Driver` | e.g. `org.postgresql.Driver` |
| `DB_USERNAME` / `DB_PASSWORD` | `sa` / (empty) | |
| `SERVER_PORT` | `8080` | |
| `FITLAB_UPLOAD_DIR` | `${user.home}/fitlab-uploads` | External dir for uploaded item images (not inside the repo) |

## Production database: use RDS, not the H2 file default

The default `DB_URL` above is a **file-based H2 database on local disk**. That default is
fine for `mvn spring-boot:run` on a laptop, but it must never be used as-is on Elastic
Beanstalk (or any other instance-based deploy target): EB replaces the underlying EC2
instance on platform updates, config changes, and health-check failures, and local disk
does not survive that replacement. There's no warning — the app just comes back up with
an empty database.

Before this app has real users, point it at an RDS Postgres instance instead (the
`postgresql` JDBC driver is already on the classpath):

1. Provision a small RDS Postgres instance **as a standalone resource**, not as an
   EB-managed add-on tied to the environment's lifecycle — that way the database
   survives if the EB environment is ever recreated or terminated.
2. Set these as environment properties on the EB environment (Configuration →
   Updates, monitoring, logging → Environment properties, or via `.ebextensions`):
   - `DB_URL=jdbc:postgresql://<rds-endpoint>:5432/<db-name>`
   - `DB_DRIVER=org.postgresql.Driver`
   - `DB_USERNAME` / `DB_PASSWORD` for the RDS instance
3. Redeploy. `ddl-auto: update` will create the schema on first boot against Postgres,
   same as it does against H2 today.

Nothing else in the codebase is H2-specific (no native H2 SQL, no H2-only dialect
features), so this is purely a configuration change.

## Endpoints

- `GET /items` / `GET /items?category=SHOES`
- `POST /items`, `POST /items/import` (bulk), `DELETE /items/{id}`
- `POST /items/{id}/image` (multipart `file`)
- `GET /recommend?anchorId=&category=`
- `GET /recommend/full?shirtId=`
- `GET /outfit/build?anchorId=`

## Architecture notes

- `matching.Matcher` is a strategy interface; `TagMatcher` is the only implementation.
  An embedding-based matcher could be added later behind the same interface.
- `OutfitScoringService` combines all three pairwise edges of an outfit (shirt-bottom,
  shirt-shoes, bottom-shoes) into one 0-100 cohesion score, and derives rule-based
  (non-ML) reason strings from the same tag data.
- `ImageStorageService` writes to a local directory today; swapping it for an S3
  `PutObject` call is a self-contained change (see comment in that class).

## Tests

`mvn test` covers `TagMatcher`, `MatchService`, `OutfitScoringService`, `OutfitService`,
and a full-stack integration test (seeding, CRUD, outfit build) against an in-memory H2 DB.
