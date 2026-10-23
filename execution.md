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

## 2026-10-11 — docManagement + shared skeleton
- Created 9 files (verified by compile, not guessing):
  `shared/entity/Document.java` (record, `newDocument`/`withContent`
  factories, compact-constructor guards); `docManagement/dtos/`
  (`CreateDocumentRequest`, `UpdateDocumentRequest` with jakarta
  validation, `DocumentResponse.from` mapper);
  `docManagement/repository/` (`DocumentRepository` iface +
  `InMemoryDocumentRepository` @Repository/ConcurrentHashMap);
  `docManagement/service/DocumentService` iface;
  `docManagement/serviceImpl/DocumentServiceImpl` @Service reusing
  `ResourceNotFoundException` -> 404; `docManagement/controller/
  DocumentController` @ `/api/v1/documents` (POST 201 + Location,
  GET list, GET/PUT by id, DELETE 204) with springdoc @Tag/@Operation.
- No `pom.xml` change (plain record entity, no JPA/Mongo starter).
- Proof: `./mvnw -q compile` clean (Lombok Unsafe warnings only);
  `./mvnw test` — 7/7 green (1 app ctx + 2 RagProperties +
  4 GlobalExceptionHandler), BUILD SUCCESS. Context loads with new
  controller/service/repository beans and zero DBs.

## 2026-10-11 — chat package (dummy controller only)
- Created 5 sub-packages under `com.ai.docmind.chat/` (`controller/`,
  `service/`, `serviceImpl/`, `repository/`, `dtos/`); only 1 class:
  `controller/ChatController.java` — dummy `POST /api/v1/chat` echoing
  `{"message"}` -> `{"reply":"echo: ..."}`. Blank message ->
  `BadRequestException` -> 400 via existing handler.
- Other four packages left empty on purpose (reserved for Phase 1 RAG).
- Proof: `./mvnw test` — 7/7 green, BUILD SUCCESS (context loads with
  new ChatController bean, zero DBs).

## 2026-10-11 — pg document_metadata storage
- Added `spring-boot-starter-data-jpa` to `pom.xml`; base
  `application.yaml` excludes `HibernateJpaAutoConfiguration` + sets
  `spring.jpa.hibernate.ddl-auto: validate` (safe default, no-op with no EMF).
- Created `shared/enums/DocumentStatus.java`,
  `shared/entity/DocumentMetadata.java` (@Entity document_metadata,
  `newUpload()` factory, `@PrePersist/@PreUpdate` timestamps),
  `config/provider/postgres/PostgresJpaConfig.java` (conditional pg EMF +
  tx manager, ddl-auto update), plus `DocumentMetadataTest` (3 tests).
- Proof: `./mvnw test` — 10/10 green (1 app ctx + 2 RagProperties +
  4 GlobalExceptionHandler + 3 DocumentMetadata), BUILD SUCCESS. App
  context still loads with pg disabled (no EMF bean, zero DBs).

## 2026-10-21 — Spring AI starters + DocumentMetadata state machine (planned)
- [ ] `pom.xml`: declare `spring-ai-starter-model-google-genai`,
      `spring-ai-starter-vector-store-pgvector`,
      `spring-ai-pdf-document-reader` (versions from the 2.0.1 BOM).
- [ ] `DocumentMetadata`: drop raw setters, add `startProcessing()` /
      `complete(pages, chunks)` / `fail(reason)` with illegal-transition
      guards, `@DynamicUpdate`, `updatable=false` content columns,
      `isFailed()`.
- [ ] Rewrite `DocumentMetadataTest` for the domain API + illegal moves.
- [ ] Proof via `./mvnw test` only (no server, no Docker).

## 2026-10-21 — DocumentProcessingException (planned)
- [ ] Add `exception/DocumentProcessingException` + nested `Stage`
      (`STORAGE`, `EXTRACTION`, `CHUNKING`, `EMBEDDING`, `DELETION`).
- [ ] Map it in `GlobalExceptionHandler`: content stages -> 422,
      infrastructure stages -> 500, with `documentId` + `stage` properties.
- [ ] Keep the cause server-side (logged), never in the HTTP body.
- [ ] No tests/execution this round (per instruction); code-only change.

## 2026-10-21 — DocumentProcessingException (executed, code-only)
- Added `exception/DocumentProcessingException` (`UUID documentId` +
  nested `Stage`, cause retained, null-guarded) and the
  `@ExceptionHandler` in `GlobalExceptionHandler` with the 422/500 stage
  split, WARN logging (cause stays server-side) and `documentId`/`stage`
  ProblemDetail properties.
- No tests or Maven runs executed per instruction. No pipeline callers
  exist in the current tree, so this lands the contract + mapping only.
- Follow-up when the upload pipeline is restored: throw it at the
  storage/extraction/chunking/embedding/deletion boundaries.

## 2026-10-21 — document upload APIs (planned)
- [ ] Add `DocumentUploadController` with single (`file`) and batch
      (`files`) multipart endpoints under `/api/v1/documents/uploads`.
- [ ] Add `DocumentIngestionService` + pg-conditional impl: validate,
      persist PENDING metadata, store the binary, call the optional
      indexing hook.
- [ ] Add `DocumentMetadataRepository` + JPA impl, response DTO,
      `StorageProperties`, and `app.storage.upload-dir` yaml.
- [ ] Keep `DocumentProcessingException(STORAGE)` mapping for storage
      failures; pg disabled -> 503 via the controller.
- [ ] No tests/execution this round (per instruction).

## 2026-10-21 — document upload APIs (executed, code-only)
- Added `DocumentUploadController`:
  `POST /api/v1/documents/uploads` (multipart `file`) and
  `POST /api/v1/documents/uploads/batch` (multipart `files`), both 202.
- Added `DocumentIngestionService` + pg-conditional impl: validates
  emptiness/content type, inserts `document_metadata` as `PENDING`,
  stores `<uuid>.pdf` under `app.storage.upload-dir`, then calls the
  optional `DocumentIndexingService.index(id)` hook. Batch validates
  every file before persisting the first one.
- Added `DocumentMetadataRepository` + `JpaDocumentMetadataRepository`
  on the `pg` persistence unit, `DocumentMetadataResponse`,
  `StorageProperties`, and `app.storage.upload-dir`
  (`APP_STORAGE_UPLOAD_DIR`, default `data/uploads`).
- Storage failures mark the row `FAILED` and raise
  `DocumentProcessingException(STORAGE)`; pg disabled -> 503 via the
  controller's `ObjectProvider` guard.
- Indexing impl (loader/splitter/embedding/pgvector) is the NEXT step;
  compile verified with `./mvnw -q -DskipTests compile` (Lombok warnings
  only). No tests/server were run, per instruction.

## 2026-10-21 — DocumentIngestionService logging (executed)
- Added `private static final Logger logs =
  LoggerFactory.getLogger(DocumentIngestionServiceImpl.class)` and log
  statements across upload/batch/validate/store: info lifecycle, debug
  metadata/status/indexer decisions, warn rejects + cleanup, error
  storage/persist failures with cause.
- No custom logger wrapper; SLF4J `Logger` is used directly.
- Proof: `./mvnw -q -DskipTests compile` — clean (Lombok warnings only).

## 2026-10-21 — parse + ingest inside upload (planned)
- [ ] Add `DocumentParsingService` (PDF -> `List<Document>`).
- [ ] Add `DocumentIngestService` + `SlidingWindowChunker`
      (pages -> chunks -> `VectorStore.add`).
- [ ] Wire both into `DocumentIngestionServiceImpl.upload` with
      `startProcessing` -> `complete` and stage-specific failures.
- [ ] Remove the `DocumentIndexingService` placeholder hook.
- [ ] Return 201 from the upload endpoints (sync indexing).
- [ ] Compile-only verification; no tests/server.

## 2026-10-22 — parse + ingest inside upload (executed, code-only)
- Added `DocumentParsingService` + `DocumentParsingServiceImpl`:
  `PagePdfDocumentReader(new FileSystemResource(path)).get()` returns one
  Spring AI `Document` per PDF page; failures become
  `DocumentProcessingException(EXTRACTION)`.
- Added `SlidingWindowChunker` (`@Component`): character window from
  `RagProperties.chunkSize()` with step `chunkSize - chunkOverlap`,
  skipping blank input; no tokenizer dependency.
- Added `DocumentIngestService` + pg-conditional
  `DocumentIngestServiceImpl`: splits every page, builds chunks with
  deterministic id `<documentId>:<chunkIndex>` and metadata
  `documentId`/`filename`/`pageNumber`/`chunkIndex`, then
  `VectorStore.add(...)`. Chunking -> `DocumentProcessingException(
  CHUNKING)`, embedding -> `(EMBEDDING)`; missing `VectorStore` ->
  `ProviderNotConfiguredException` -> 503.
- Rewired `DocumentIngestionServiceImpl.upload`:
  `PENDING -> store -> startProcessing -> parse -> ingest -> complete
  (pages, chunks) -> READY`; every runtime failure marks the row
  `FAILED` before rethrowing (including provider-not-configured).
  `DocumentIndexingService` placeholder deleted.
- `DocumentUploadController` single + batch now return **201 Created**;
  redundant empty-file check removed (service validation already maps it
  to 400 `BadRequestException`).
- Proof: `./mvnw -q -DskipTests compile` — clean (Lombok Unsafe warnings
  only). No tests/server per instruction.

## 2026-10-22 — OpenAI + pgvector alignment (executed)
- Reset DB: `DROP TABLE IF EXISTS vector_store; TRUNCATE TABLE document_metadata;`
- `pom.xml`: swapped `spring-ai-starter-model-google-genai` -> `spring-ai-starter-model-openai` (2.0.1).
- `application.yaml`: AI block rewritten to 2.0.1 flat keys (`openai.api-key`, `chat.model/temperature`, `embedding.model/dimensions` = `text-embedding-3-small` @ 1536). Vector store: `type: none` base, `pgvector.initialize-schema: true`, `dimensions: 1536`, `index-type: hnsw`, `distance-type: cosine-distance`, `table-name: vector_store`. Removed `organization`, empty `base-url`, dead `app.providers.pg.vector` block.
- `application-dev.yaml`: pg enabled `${APP_PG_ENABLED:true}`, vectorstore `type: pgvector`.
- `application-prod.yaml`: same toggles, env-overridable.
- `DocmindApplicationTests`: `@SpringBootTest(properties = {"app.providers.pg.enabled=false", "spring.ai.vectorstore.type=none", "spring.ai.model.chat=none", "spring.ai.model.embedding=none"})`.
- Proof: `./mvnw test` — 12/12 green; `./mvnw -q -DskipTests compile` clean.

## 2026-10-22 — Document upload pipeline implemented (Phase 2 core)
- **pom.xml**: added `spring-ai-tika-document-reader` (BOM 2.0.1).
- **New files**:
  - `src/main/java/.../service/DocumentMetadataService.java`
  - `src/main/java/.../serviceImpl/DocumentMetadataServiceImpl.java`
  - `src/main/java/.../service/DocumentParsingService.java` (updated interface: `parse(UUID, MultipartFile)`)
  - `src/main/java/.../serviceImpl/DocumentParsingServiceImpl.java` (rewritten: PDF + Tika parsing)
  - `src/main/java/.../service/DocumentIngestService.java` (renamed from DocumentIngestService, new signature `ingest(DocumentMetadata, List<Document>)`)
  - `src/main/java/.../serviceImpl/DocumentIngestServiceImpl.java` (TokenTextSplitter, enrichment, vectorStore.add)
- **Updated files**:
  - `DocumentMetadata.java`: added `INDEXED` status, `markIndexed()`, expanded `SUPPORTED_CONTENT_TYPES`, `isReady()` covers INDEXED, `isIndexed()`.
  - `DocumentStatus.java`: added `INDEXED` constant.
  - `DocumentUploadController.java`: injects `DocumentMetadataService`, enhanced `@Operation`, batch per-file.
  - `DocumentMetadataTest.java`: updated `rejectsBadUploads` for expanded content types.
- **Deleted files**:
  - `DocumentIngestionService.java` (old upload service)
  - `DocumentIngestionServiceImpl.java`
  - `SlidingWindowChunker.java` (unused after TokenTextSplitter adoption)
- **Compile & test**: `./mvnw -q -DskipTests compile` clean; `./mvnw test` — 12/12 green.
- **Endpoints**:
  - `POST /api/v1/documents/uploads` (multipart `file`) → 201 CREATED, returns `DocumentMetadataResponse`
  - `POST /api/v1/documents/uploads/batch` (multipart `files`) → 201 CREATED, list of responses
  - Swagger UI: `http://localhost:8080/swagger-ui.html`

## 2026-10-22 append — CORS configuration implemented
- **New files**:
  - `src/main/java/.../config/CorsProperties.java` — record binding `app.cors.*`
  - `src/main/java/.../config/CorsConfig.java` — `WebMvcConfigurer` bean, conditional on `app.cors.enabled=true`
- **application-dev.yaml**: CORS enabled with permissive localhost origins (3000, 5173), credentials allowed, max-age 3600s.
- **application-prod.yaml**: CORS disabled by default (`app.cors.enabled=false`); all properties env-overridable (`APP_CORS_*`).
- **Compile & test**: `./mvnw -q -DskipTests compile` clean; `./mvnw test` — 12/12 green.

## 2026-10-23 append — Phase 2: RAG retrieval & generation (chat package)
- **New DTOs** (`chat/dtos/`):
  - `SearchRequestDto.java`, `SearchResultDto.java`, `CitationDto.java`
  - `ChatRequestDto.java`, `ChatResponseDto.java`
- **New service** (`chat/service/`, `chat/serviceImpl/`):
  - `RagService.java` (interface)
  - `RagServiceImpl.java` — VectorStore retrieval, ChatClient generation, MessageWindowChatMemory
- **Updated controller** (`chat/controller/ChatController.java`):
  - `POST /api/v1/chat/search` → `SearchResultDto`
  - `POST /api/v1/chat/ask` → `ChatResponseDto`
  - `GET /api/v1/chat/stream` (SSE) → `Flux<String>`
  - Guarded by `ObjectProvider<RagService>` (503 if pg disabled)
- **Dependencies**: `spring-ai-model`, `spring-ai-client-chat`, `spring-ai-vector-store` (via BOM 2.0.1)
- **Compile & test**: `./mvnw -q -DskipTests compile` clean; `./mvnw test` — 12/12 green.
