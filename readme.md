# docmind

AI document-mind backend: Spring Boot 4 + Spring AI + pgvector RAG project.

## What this is

`docmind` ingests documents, embeds them, stores vectors in PostgreSQL +
pgvector, and answers questions over them. Day-1 foundation in this repo:
multi-provider datasource configs (MySQL, PostgreSQL/pgvector, MongoDB,
Cassandra), global exception handling, env profiles, Docker Compose, Swagger.

## Doc map

| File            | Purpose                               |
|-----------------|-------------------------------------------|
| `readme.md`     | This file — what, stack, how to run       |
| `arch.md`       | Architecture, packages, request flow      |
| `plan.md`       | Phased build plan (what is done / next)   |
| `execution.md`  | Log of what was actually executed + proof |

## Stack

- Java 25, Spring Boot 4.1.1, Spring MVC
- Spring AI 2.0.1 (BOM) — vector store wiring lands in Phase 2
- PostgreSQL + pgvector, MySQL 8.4, MongoDB 8, Cassandra 5 (via Compose)
- springdoc-openapi 3.1.1 (Swagger UI + Scalar), Actuator, Validation
- HikariCP connection pools, direct native drivers for Mongo/Cassandra

## Run

```bash
# infra (pick what you need)
docker compose up -d postgres mysql

# dev (default profile, port 8080, swagger on)
./mvnw spring-boot:run

# prod profile
SPRING_PROFILES_ACTIVE=prod ./mvnw spring-boot:run
```

- Swagger UI: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- Health: http://localhost:8080/actuator/health

## Env profiles

| File                    | When                       |
|-------------------------|----------------------------|
| `application.yaml`      | Base defaults, all envs    |
| `application-dev.yaml`  | Local dev, relaxed, docs on |
| `application-prod.yaml` | Prod, strict, docs off     |

Activate with `SPRING_PROFILES_ACTIVE=dev|prod` (defaults to `dev`).

## Providers

All four providers are **disabled by default** — the app boots with zero
database connections. Enable per env, e.g. dev:

```yaml
app:
  providers:
    pg:
      enabled: true
```

See `arch.md` for the package layout and `plan.md` for the phases.

## 2026-10-09 append — postgres init script
- `docker/postgres/init.sql` creates `vector`, `uuid-ossp`, `hstore`
  extensions (`IF NOT EXISTS`); auto-mounted into postgres via compose.
- If the `pgdata` volume already exists, recreate it or the script is skipped.
