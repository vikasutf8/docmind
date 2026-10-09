# Execution log — Phase 0 foundation

## What was built

- Docs: `readme.md`, `arch.md`, `plan.md`, this file
- `pom.xml`: validation, actuator, jdbc, docker-compose module,
  springdoc-openapi 3.1.1, spring-ai-bom 2.0.1 (managed, starter lands in
  Phase 1), mysql/postgres drivers, mongodb-driver-sync,
  `org.apache.cassandra:java-driver-core` (Boot-managed new group)
- Code: `constant/AppConstants`, `exception/` (3 custom + handler),
  `config/` (AppProperties, OpenApiConfig, 4 conditional providers),
  `GlobalExceptionHandlerTest`
- Config: `application.yaml` + `application-dev.yaml` +
  `application-prod.yaml`, `compose.yaml` (postgres/pgvector, mysql,
  mongo, cassandra)

## Fixes found by compiling/testing (not guessing)

1. `com.datastax.oss:java-driver-core` has no managed version in Boot 4 —
   driver moved to `org.apache.cassandra:java-driver-core` (managed).
2. Boot 4 is Jackson 3 (`tools.jackson`): old `Jackson2ObjectMapperBuilder`
   bean and `spring.jackson.serialization.*` keys are gone — removed the
   custom `JacksonConfig`, Boot defaults apply.
3. `ProblemDetail.getStatus()` returns `int` in Framework 7 — test updated.
4. Handler methods must be `public` for cross-package test access.
5. `starter-jdbc` needs `DataSourceAutoConfiguration` excluded (no default
   URL by design — providers own their pools).
6. `spring-boot-docker-compose` crashes boot when docker is absent —
   `spring.docker.compose.enabled: false` in base, `true` in dev.

## Proof

```text
./mvnw test
Tests run: 1 ... DocmindApplicationTests      (context loads, no DBs)
Tests run: 4 ... GlobalExceptionHandlerTest  (404/400/503/500)
Tests run: 5, Failures: 0, Errors: 0 — BUILD SUCCESS
```

Live boot (dev, compose overridden off — no docker on this box):
`GET /actuator/health → 200 UP`, `GET /v3/api-docs → 200`
(OpenAPI 3.1.0, title docmind), swagger-ui reachable.

## How to run

```bash
docker compose up -d postgres mysql   # needs docker; pick services
./mvnw spring-boot:run                # dev profile by default
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

## 2026-10-09 — init.sql
- Added `docker/postgres/init.sql`:
  `vector`, `"uuid-ossp"`, `hstore`, all `IF NOT EXISTS`.
- Mounted in `compose.yaml` postgres service:
  `./docker/postgres:/docker-entrypoint-initdb.d:ro`.
- Proof: `./mvnw test` — 5/5 green, BUILD SUCCESS (no boot, code-only check).
- Note: existing `pgdata` volume must be recreated for the script to run.

## 2026-10-09 — OpenAI + pgvector yaml
- Added `spring.ai.openai.*` (api-key/org/base-url, chat options
  model `gpt-4o-mini` + temperature, embedding `text-embedding-3-large` /
  3072 dims) and `spring.ai.vectorstore.pgvector.*`
  (ivfflat / cosine) to `application.yaml`. Secrets default empty so boot
  never fails on missing env.
- Normalizations vs raw snippet: nested under `spring.ai` (where Spring AI
  binds), `modal` typo fixed to `model`, chat/embedding moved under
  `options:` (Spring AI layout), env defaults added.
- Found + fixed: raw snippet had been pasted under `logging.level`, breaking
  context (`LogLevel.${OPENAI_API_KEY}`) — removed the duplicate.
- Open for Phase 1: verify `distance-type: cosine` binds Spring AI 2.x enum
  (`COSINE_DISTANCE`?) once the pgvector starter lands.
- Proof: `./mvnw test` — 5/5 green, BUILD SUCCESS.

## 2026-10-09 — RAG knobs
- `app.rag.*` in `application.yaml`: chunk-size 500, chunk-overlap 100,
  top-k 5, similarity-threshold 0.7 — all env-overridable
  (`RAG_CHUNK_SIZE`, `RAG_CHUNK_OVERLAP`, `RAG_TOP_K`,
  `RAG_SIMILARITY_THRESHOLD`).
- New `config/RagProperties` record with range validation
  (overlap < size, threshold in [0,1]) + `RagPropertiesTest`.
- Proof: `./mvnw test` — 7/7 green, BUILD SUCCESS.
