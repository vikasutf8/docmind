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

## 2026-10-09 append — AI config placement
- `spring.ai.openai.*` / `spring.ai.vectorstore.pgvector.*` live in base
  `application.yaml` with env overrides; secrets never committed.
  Never put provider/AI keys under `logging.level` (broke context once).

## 2026-10-09 append — RAG tuning
- `config/RagProperties` (`app.rag.*`): chunk-size / chunk-overlap /
  top-k / similarity-threshold. Pure values, always bound, validated in
  the record's compact constructor. Retrieval pipeline (Phase 1) consumes it.

## 2026-10-11 append — docManagement + shared
- New packages (`com.ai.docmind`):
```text
docManagement/
  controller/      DocumentController.java  @RestController /api/v1/documents
  service/         DocumentService.java     interface (no Spring imports)
  serviceImpl/     DocumentServiceImpl.java @Service, maps DTO<->entity
  repository/      DocumentRepository.java  plain interface (no Spring Data)
                   InMemoryDocumentRepository.java @Repository, ConcurrentHashMap
  dtos/            CreateDocumentRequest / UpdateDocumentRequest / DocumentResponse
                   records + jakarta.validation, Response.from(Document) mapper
shared/
  entity/
    Document.java  immutable record: UUID id, title, content, contentType,
                   sizeBytes, createdAt, updatedAt; factory newDocument();
                   compact-constructor validation
```
- Request flow (happy path):
```text
POST /api/v1/documents {title, content} -> validation
  -> DocumentService.create -> repository.save -> 201 DocumentResponse
GET /api/v1/documents -> 200 List<DocumentResponse>
GET /api/v1/documents/{id} -> 200 or 404 ResourceNotFoundException
PUT /api/v1/documents/{id} -> 200 or 404
DELETE /api/v1/documents/{id} -> 204 or 404
```
- Decisions: repository is interface-first so JPA/Mongo can replace the
  in-memory impl later without touching service/controller; errors reuse
  `ResourceNotFoundException`/`BadRequestException` -> existing
  `GlobalExceptionHandler` (RFC 9457 ProblemDetail).

## 2026-10-11 append — chat (dummy)
- Packages (`com.ai.docmind`):
```text
chat/
  controller/      ChatController.java  dummy @RestController /api/v1/chat
  service/         (empty — reserved for ChatService later)
  serviceImpl/     (empty — reserved for RAG impl later)
  repository/      (empty — reserved for conversation history later)
  dtos/            (empty — reserved for ChatRequest/ChatResponse records)
```
- Dummy flow:
```text
POST /api/v1/chat {"message":"hi"} -> 200 {"reply":"echo: hi"} (no AI call)
```
- Decisions: controller takes a `Map<String,String>` body + manual blank
  check (no DTO yet on purpose); RAG (`RagProperties`, pgvector
  VectorStore) will be injected at the service layer later without
  changing the endpoint path.
