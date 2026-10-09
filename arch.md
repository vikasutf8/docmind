# Architecture — docmind

## Layers

```text
client (swagger / REST)
  |
  v
controller (Phase 2+)          <- validation (jakarta.validation)
  |
  v
service (Phase 2+)             <- Spring AI chat / embedding / VectorStore
  |
  +---> provider: pg (JdbcTemplate, pgvector)   [primary AI store]
  +---> provider: mysql (JdbcTemplate)
  +---> provider: mongo (MongoClient)
  +---> provider: cassandra (CqlSession)
  |
  v
GlobalExceptionHandler (@RestControllerAdvice -> RFC 9457 ProblemDetail)
```

## Packages (`com.ai.docmind`)

```text
config/                  core + web/infra config
  AppProperties.java           @ConfigurationProperties("app")
  OpenApiConfig.java           springdoc OpenAPI bean (title/version/servers)
  provider/
    mysql/                   MySqlProperties + MySqlDataSourceConfig
    postgres/                PostgresProperties + PostgresDataSourceConfig
    mongo/                   MongoProperties + MongoProviderConfig
    cassandra/               CassandraProperties + CassandraProviderConfig
constant/
  AppConstants.java            API paths, provider names, headers, profiles
exception/
  ResourceNotFoundException.java   -> 404
  BadRequestException.java         -> 400
  ProviderNotConfiguredException.java -> 503
  GlobalExceptionHandler.java      ProblemDetail mapping + validation + fallback
```

## Key decisions (modern, Boot 4.1 / Java 25)

1. **Records over POJOs** for properties, DTOs, error payloads. Immutable,
   no Lombok needed for data carriers.
2. **`ProblemDetail` (RFC 9457)** from the exception handler instead of a
   hand-rolled error JSON — framework-native, content-negotiated.
3. **Conditional providers**: every provider config is gated on
   `app.providers.<name>.enabled=true` (default `false`). The app boots
   with no DB connections; tests stay green without Docker.
4. **Direct native drivers** for Mongo (`mongodb-driver-sync`) and
   Cassandra (`java-driver-core`) instead of Spring Data starters — no
   eager auto-configuration connecting at startup, full control over
   client lifecycle. JDBC providers use Boot-managed Hikari via
   `DataSourceBuilder`.
5. **No `@Primary` DataSource** — every bean is `@Qualifier`-named
   (`mysqlDataSource`, `postgresDataSource`, …). No ambiguity, ever.
6. **Profiles by file name** (`application-dev.yaml`, `application-prod.yaml`)
   — no `spring.config.activate` ceremony needed.
7. **spring-boot-docker-compose** for local dev services; disabled in prod.

## Request flow (error path)

```text
controller throws ResourceNotFoundException
  -> GlobalExceptionHandler -> 404 ProblemDetail { title, detail, instance }
  -> validation failure -> 400 ProblemDetail with "errors" { field: msg }
  -> provider used while disabled -> 503 via ProviderNotConfiguredException
  -> anything else -> 500, message hidden in prod
```

## 2026-10-09 append — postgres bootstrap
- `docker/postgres/init.sql` (vector, uuid-ossp, hstore) runs once on first
  postgres start via `/docker-entrypoint-initdb.d` mount in `compose.yaml`.
