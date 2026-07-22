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
