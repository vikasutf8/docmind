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

## 2026-10-11 append — pg document_metadata storage
- New types:
```text
shared/
  enums/
    DocumentStatus.java   PENDING | PROCESSING | READY | FAILED (VARCHAR, append-only)
  entity/
    Document.java         existing in-memory carrier (unchanged)
    DocumentMetadata.java @Entity @Table(document_metadata), UUID uuid PK,
                          filename(512) / contentType(120) / fileSize /
                          totalPages? / totalChunks? / status / createdAt /
                          updatedAt / errorMessage(TEXT), newUpload() factory
config/provider/postgres/
  PostgresJpaConfig.java  conditional (pg.enabled) pgEntityManagerFactory
                          + pgTransactionManager on postgresDataSource
```
- How a file is stored:
```text
upload (pdf now, doc/docx later)
  -> validate contentType in SUPPORTED_CONTENT_TYPES + size
  -> INSERT document_metadata (PENDING, pages/chunks null)
  -> save binary to disk/object storage (outside pg row)
  -> worker: PROCESSING -> extract pages -> chunk -> embed
  -> READY (pages/chunks set) | FAILED (errorMessage set, retryable)
```
- Decisions: metadata-only row in pg (no bytea — keeps the table small and
  pgvector-focused); enum as STRING for SQL readability; JPA starter added
  but auto-config excluded globally so zero-DB boot/tests unaffected;
  manual pg EMF preserves the no-`@Primary`-DataSource rule.

## 2026-10-21 append — document_metadata as a state machine
- Table and columns unchanged. Public setters are gone; transitions live
  in the entity:
```text
newUpload()        -> PENDING
startProcessing()  : PENDING|FAILED -> PROCESSING (clears error + counts)
complete(p, c)     : PROCESSING -> READY   (enforces p, c >= 0)
fail(reason)       : PENDING|PROCESSING|FAILED -> FAILED (reason truncated)
                     READY is terminal — every illegal move throws
                     IllegalStateException.
```
- Annotations: `@DynamicUpdate` (partial row updates); `filename`,
  `content_type`, `file_size` marked `updatable = false`; timestamps via
  `@PrePersist/@PreUpdate`; UUID stays client-assigned.
- `pom.xml` now carries the Spring AI stack its yaml references:
```text
spring-ai-starter-model-google-genai    (chat + embedding)
spring-ai-starter-vector-store-pgvector (vector store)
spring-ai-pdf-document-reader           (PagePdfDocumentReader)
```
  all Boot/BOM-managed at 2.0.1; auto-configs stay dormant while
  pg/api-key are off, so zero-DB boots and tests are unaffected.
- Known follow-ups (deliberately out of this step): a qualified
  `pgJdbcTemplate` so the pgvector auto-config has a pool to bind to once
  pg is enabled; Flyway migrations (then add `@Version`).

## 2026-10-21 append — document-processing error contract
```text
pipeline (future) throws DocumentProcessingException(documentId, stage, cause)
        |
        v
GlobalExceptionHandler.handleDocumentProcessing
  EXTRACTION/CHUNKING          -> 422 ProblemDetail
  STORAGE/EMBEDDING/DELETION   -> 500 ProblemDetail
  body: title + detail + documentId + stage
```
- `exception/DocumentProcessingException` extends `RuntimeException`,
  rejects null `documentId`/`stage`, keeps the cause for logs but never
  serializes it. Both fields are exposed via accessors.
- `GlobalExceptionHandler` logs at WARN with the cause, then maps as
  above using a `switch` over the stage; no stage-specific exception
  subclasses needed.
- The pipeline classes that would throw it are not in the current tree;
  the handler is ready for the upload/ingestion rebuild.

## 2026-10-21 append — upload entry point (indexing step 1)
```text
POST /api/v1/documents/uploads        (file)  ─┐
POST /api/v1/documents/uploads/batch  (files) ─┴─> DocumentUploadController
  -> DocumentIngestionService (pg enabled only)
     -> validate non-empty + SUPPORTED_CONTENT_TYPES
     -> INSERT document_metadata (PENDING)
     -> write <uuid>.pdf under app.storage.upload-dir
     -> DocumentIndexingService.index(id)   [optional hook, next step]
     -> 202 DocumentMetadataResponse
  storage failure -> metadata.fail(...) -> DocumentProcessingException(STORAGE)
  pg disabled     -> ProviderNotConfiguredException -> 503
```
- Separate controller from `DocumentController` so multipart/upload
  concerns do not leak into the text CRUD surface.
- Batch is sequential single uploads; every file is validated before the
  first one is persisted, so a bad entry rejects the request before any
  row/file is created.
- `DocumentIndexingService` has no implementation yet. It is resolved via
  `ObjectProvider`, so the upload API is complete and usable now; the
  loader/splitter/embedding/pgvector impl plugs into the same hook.
- `DocumentMetadataRepository` is interface-first + a JPA impl on the
  existing `pg` persistence unit (`@Transactional("pgTransactionManager")`),
  keeping the no-`@Primary` DataSource rule.
- `app.storage.upload-dir` is bound in base yaml (default `data/uploads`)
  because `StorageProperties` is always scanned; override with
  `APP_STORAGE_UPLOAD_DIR` per environment.

## 2026-10-21 append — logging convention
- Each service owns one `private static final Logger logs` obtained via
  `LoggerFactory.getLogger(<ServiceImpl>.class)`; no logging abstraction
  of our own.
- `info` = accepted work (upload/batch start+finish), `debug` =
  ids/status/indexing decisions, `warn` = client rejects + cleanup
  problems, `error` = storage/persistence failures with the cause.
- Exception messages returned to clients stay unchanged; the logger is
  the only place stack traces/details are emitted.

## 2026-10-21 append — upload -> parse -> ingest (sync)
```text
DocumentUploadController
  -> DocumentIngestionService.upload(file)
     validate contentType/size
     save DocumentMetadata (PENDING)
     store <uuid>.pdf
     metadata.startProcessing() + save (PROCESSING)
     DocumentParsingService.parse(id, path)          PDF -> List<Document> pages
     DocumentIngestService.ingest(pages, metadata)   pages -> chunks -> VectorStore
     metadata.complete(pages, chunks) + save (READY)
     -> 201 DocumentMetadataResponse
  failure -> metadata.fail(reason) + DocumentProcessingException
             (EXTRACTION | CHUNKING | EMBEDDING | STORAGE)
```
- `DocumentParsingServiceImpl` owns PDF extraction only; adding doc/docx
  later means another loader behind the same interface.
- `DocumentIngestServiceImpl` chunks each page with the RAG
  (`app.rag.chunk-size` / `chunk-overlap`) sliding window, stamps
  `documentId`/`filename`/`pageNumber`/`chunkIndex`, and writes to the
  pgvector `VectorStore` (`ObjectProvider`, 503 if not configured).
- Deterministic chunk ids (`<documentId>:<chunkIndex>`) keep retries from
  duplicating vectors.
- The upload request performs indexing synchronously; move to 202 +
  background worker when CDC/async ingestion lands.

## 2026-10-22 append — OpenAI + pgvector alignment
- Google GenAI starter removed; OpenAI starter (`spring-ai-starter-model-openai` 2.0.1) is now the sole AI provider.
- AI config migrated to Spring AI 2.0.1 flat property style (no `options.*` nesting):
  - `spring.ai.openai.api-key`, `chat.model`, `chat.temperature`, `embedding.model`, `embedding.dimensions`.
  - `spring.ai.vectorstore.type` toggles pgvector autoconfig (`none` base, `pgvector` dev/prod).
  - `pgvector.initialize-schema: true`, `dimensions: 1536`, `index-type: hnsw`, `distance-type: cosine-distance`.
- Dead config removed: `app.providers.pg.vector.*` yaml block (was never read by Spring AI pgvector autoconfig).
- Provider toggles: base `type: none` + pg disabled; dev/prod `type: pgvector` + pg enabled (`${APP_PG_ENABLED:true}`). Keeps the "zero-DB boot" invariant for tests and CI.
- Only pg is enabled; MySQL/Mongo/Cassandra remain disabled scaffolding.

## 2026-10-22 append — Document upload pipeline (Phase 2 core)
- **Three-layer service architecture**:
  1. `DocumentMetadataService` — upload orchestration: save metadata (PENDING), call parsing, call ingestion, return response (201).
  2. `DocumentParsingService` — `parse(UUID, MultipartFile)`:
     - PDF: `PagePdfDocumentReader(resource, PdfDocumentReaderConfig.defaultConfig())` → one document per page.
     - Generic (Word, text, etc.): `TikaDocumentReader(resource).read()`.
     - Uses `ByteArrayResource` with overridden `getFilename()` for metadata.
  3. `DocumentIngestService` — chunking + embedding + status:
     - `TokenTextSplitter` (1000 tokens, 400 min chars, 10 min embed, 5000 max chunks, keepSeparator=true).
     - Enrichment per chunk: `documentId`, `fileName`, `contentType`, `chunkIndex`, `page_number` (from `PagePdfDocumentReader.METADATA_START_PAGE_NUMBER` or default 1).
     - `VectorStore.add(enrichedChunks)`.
     - Status: `startProcessing()` → `PROCESSING`; empty chunks → `FAILED`; success → `markIndexed(pages, chunks)` → `INDEXED` (terminal).
- **Entity updates**: `DocumentStatus` adds `INDEXED`; `SUPPORTED_CONTENT_TYPES` expanded; `markIndexed()` method; `isReady()` covers READY+INDEXED.
- **Controller**: `POST /api/v1/documents/uploads` (single), `POST /api/v1/documents/uploads/batch` (per-file loop), both 201 CREATED; Swagger `@Operation` enriched.
- **Removed**: `DocumentIngestionService` (old upload orchestrator), `SlidingWindowChunker` (replaced by TokenTextSplitter).
- **Dependencies**: added `spring-ai-tika-document-reader` (BOM 2.0.1).

## 2026-10-22 append — CORS configuration
- **Package**: `config/CorsProperties.java` (record, `@ConfigurationProperties("app.cors")`) + `config/CorsConfig.java` (conditional `WebMvcConfigurer`).
- **Dev defaults**: `allowedOrigins` = localhost:3000/5173, `allowedMethods` = GET/POST/PUT/PATCH/DELETE/OPTIONS, `allowedHeaders` = *, `exposedHeaders` = Location, `allowCredentials` = true, `maxAge` = 3600s.
- **Prod**: disabled by default (`app.cors.enabled=false`); all properties env-overridable via `APP_CORS_*`.
- **Scope**: applied globally to `/**` via Spring MVC `CorsRegistry`. Web-layer only — no service or repository changes.
