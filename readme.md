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

### Documented endpoints (Swagger)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/v1/documents` | Create document |
| GET | `/api/v1/documents` | List all documents |
| GET | `/api/v1/documents/{id}` | Get document by ID |
| PUT | `/api/v1/documents/{id}` | Update document |
| DELETE | `/api/v1/documents/{id}` | Delete document |
| POST | `/api/v1/documents/uploads` | Upload single PDF (multipart `file`) |
| POST | `/api/v1/documents/uploads/batch` | Upload multiple PDFs (multipart `files`) |
| POST | `/api/v1/chat` | Dummy echo chat (`{"message": "..."}`) |

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

## 2026-10-09 append — OpenAI env vars
- `OPENAI_API_KEY` (required for Phase 1), optional `OPENAI_ORGANIZATION`,
  `OPENAI_BASE_URL`, `OPENAI_CHAT_MODEL`, `OPENAI_CHAT_TEMPERATURE`,
  `OPENAI_EMBEDDING_MODEL`, `OPENAI_EMBEDDING_DIMENSIONS`,
  `VECTORSTORE_PGVECTOR_INDEX_TYPE`, `VECTORSTORE_PGVECTOR_DISTANCE_TYPE`.
  App boots with all unset (AI dormant until Phase 1).

## 2026-10-09 append — RAG env vars
- `RAG_CHUNK_SIZE` (500), `RAG_CHUNK_OVERLAP` (100), `RAG_TOP_K` (5),
  `RAG_SIMILARITY_THRESHOLD` (0.7).

## 2026-10-11 append — document management API (in-memory)
- CRUD at `/api/v1/documents`: `POST` (201 + Location), `GET /`,
  `GET /{id}`, `PUT /{id}`, `DELETE /{id}` (204). Validation errors ->
  400, unknown id -> 404 (ProblemDetail), all visible in Swagger UI.
- Packages: `com.ai.docmind.docManagement.{controller,service,
  serviceImpl,repository,dtos}` + shared entity
  `com.ai.docmind.shared.entity.Document`. Store is in-memory for now —
  data resets on restart; JPA/Mongo backing lands later without
  changing the API.
- Try it:
```bash
./mvnw spring-boot:run
curl -X POST localhost:8080/api/v1/documents \
  -H 'Content-Type: application/json' \
  -d '{"title":"hello","content":"world"}'
curl localhost:8080/api/v1/documents
```

## 2026-10-11 append — dummy chat API
- `POST /api/v1/chat` with `{"message":"hi"}` -> `{"reply":"echo: hi"}`.
  Blank message -> 400. No AI call yet; RAG wiring later keeps same path.
```bash
curl -X POST localhost:8080/api/v1/chat \
  -H 'Content-Type: application/json' \
  -d '{"message":"hi"}'
```

## 2026-10-21 append — document-processing exception
- New `DocumentProcessingException` (`documentId` + `stage`) handled by
  `GlobalExceptionHandler` -> RFC 9457 `ProblemDetail`:
  `EXTRACTION`/`CHUNKING` -> **422**, `STORAGE`/`EMBEDDING`/`DELETION` ->
  **500**, with `documentId` + `stage` properties.
- The cause is logged server-side and never serialized. The upload
  pipeline will throw it at each stage boundary when rebuilt.

## 2026-10-21 append — document upload APIs
- `POST /api/v1/documents/uploads` — one file, multipart field `file`:
```bash
curl -X POST localhost:8080/api/v1/documents/uploads \
  -F 'file=@paper.pdf'
```
- `POST /api/v1/documents/uploads/batch` — many files, multipart field
  `files`:
```bash
curl -X POST localhost:8080/api/v1/documents/uploads/batch \
  -F 'files=@a.pdf' -F 'files=@b.pdf'
```
- Both return **202** with `DocumentMetadataResponse` (status `PENDING`);
  files are stored under `app.storage.upload-dir`
  (`APP_STORAGE_UPLOAD_DIR`, default `data/uploads`).
- The RAG indexing pipeline (loader -> splitter -> embedding -> pgvector)
  plugs into the `DocumentIndexingService` hook next; until then uploads
  stay `PENDING`. pg disabled -> 503.
- `DocumentProcessingException(STORAGE)` is raised if the file cannot be
  written and the metadata row is marked `FAILED`.

## 2026-10-21 append — upload logging
- `DocumentIngestionServiceImpl` uses SLF4J directly:
  `private static final Logger logs = LoggerFactory.getLogger(
  DocumentIngestionServiceImpl.class)`, with `info` lifecycle logs,
  `debug` metadata/indexing detail, `warn` rejects/cleanup and `error`
  storage/persist failures.

## 2026-10-22 append — uploads now parse, chunk and embed
- Both upload endpoints now index synchronously and return **201**
  (supersedes the earlier 202): validate -> `PENDING` row -> store file
  -> `PROCESSING` -> PDF parse -> sliding-window chunk -> embed into
  pgvector -> `READY` with `totalPages` + `totalChunks`.
- Requires `app.providers.pg.enabled=true` plus a configured embedding
  model/`VectorStore`; without a `VectorStore` the request fails with
  503 and the row is marked `FAILED`.
- Chunks carry deterministic ids (`<documentId>:<chunkIndex>`) and
  metadata (`documentId`, `filename`, `pageNumber`, `chunkIndex`), so a
  retried document does not duplicate vectors.
- Failure mapping: parse -> 422 `EXTRACTION`, split -> 422 `CHUNKING`,
  embed -> 500 `EMBEDDING`, file write -> 500 `STORAGE`. The metadata
  row stores the reason (`errorMessage`) and can be retried.
- Batch uploads are sequential; earlier files stay indexed if a later
  file fails.
- Tuning: `RAG_CHUNK_SIZE` (500) and `RAG_CHUNK_OVERLAP` (100).

## 2026-10-22 append — OpenAI + pgvector alignment
- **AI provider**: OpenAI (`text-embedding-3-small` @ 1536 dims for RAG, `gpt-4o-mini` for chat). Google GenAI starter removed.
- **Config**: Spring AI 2.0.1 flat keys in `application.yaml`:
  ```yaml
  spring.ai.openai.api-key: ${OPENAI_API_KEY:}
  spring.ai.openai.chat.model: ${OPENAI_CHAT_MODEL:gpt-4o-mini}
  spring.ai.openai.embedding.model: ${OPENAI_EMBEDDING_MODEL:text-embedding-3-small}
  spring.ai.openai.embedding.dimensions: ${OPENAI_EMBEDDING_DIMENSIONS:1536}
  spring.ai.vectorstore.type: ${VECTOR_STORE_TYPE:none}
  spring.ai.vectorstore.pgvector.initialize-schema: true
  spring.ai.vectorstore.pgvector.dimensions: 1536
  spring.ai.vectorstore.pgvector.index-type: hnsw
  spring.ai.vectorstore.pgvector.distance-type: cosine-distance
  ```
- **Providers**: only PostgreSQL+pgvector active (via Docker Compose). `application-dev.yaml` enables pg by default (`APP_PG_ENABLED:true`); `application-prod.yaml` env-overridable. Base yaml keeps everything dormant so tests/CI boot without DB or keys.
- **DB reset**: dropped old `vector_store` (Google 768-dim) and truncated `document_metadata` — fresh baseline for OpenAI 1536-dim vectors.
- **Tests**: all 12 pass (`./mvnw test`), context test overrides pg/AI to run offline.

### Run (dev)
```bash
docker compose up -d postgres
export OPENAI_API_KEY=sk-...   # from platform.openai.com
./mvnw spring-boot:run
```
- Dev profile (default) — pg enabled, vectorstore pgvector, Swagger on.
- Swagger: http://localhost:8080/swagger-ui.html
- Health: http://localhost:8080/actuator/health

### Verify upload
```bash
curl -X POST localhost:8080/api/v1/documents/uploads \
  -F 'file=@/path/paper.pdf'
```
Returns **201** with `READY` + `totalPages`/`totalChunks`.

### Verify DB
```bash
docker exec docmind-postgres psql -U docmind -d docmind \
  -c "select status,total_pages,total_chunks from document_metadata;"
docker exec docmind-postgres psql -U docmind -d docmind \
  -c "select count(*), vector_dims(embedding) from vector_store group by 2;"
```
Expect: `vector_dims = 1536`, rows > 0 after upload.

### Notes
- Never commit `OPENAI_API_KEY`; use shell/IDE env (add `.env` to `.gitignore` if needed).
- Only pg enabled — MySQL/Mongo/Cassandra stay disabled. Enabling MySQL later would need a qualified `VectorStore` config to avoid `JdbcTemplate` ambiguity.

## 2026-10-22 append — Upload pipeline: TokenTextSplitter + Tika + INDEXED status
- **Pipeline refactor**: three services (`DocumentMetadataService`, `DocumentParsingService`, `DocumentIngestService`) replacing the old monolithic `DocumentIngestionService`.
- **Parsing**: PDF → `PagePdfDocumentReader` (one document per page, preserves `page_number` metadata); non-PDF → `TikaDocumentReader` (Word, text, etc.). Added `spring-ai-tika-document-reader` dependency.
- **Chunking**: `TokenTextSplitter` (1000 tokens, min 400 chars, min embed length 10, max 5000 chunks, keep separator) — replaces `SlidingWindowChunker`. Deterministic chunk IDs (`<documentId>:<chunkIndex>`).
- **Metadata enrichment** per chunk: `documentId`, `fileName`, `contentType`, `chunkIndex`, `page_number`.
- **Vector store**: `VectorStore.add(enrichedChunks)` writes embeddings to pgvector.
- **Status flow**: `PENDING` → `startProcessing()` → `PROCESSING` → success → `markIndexed(pages, chunks)` → `INDEXED` (terminal); failure → `FAILED` with `errorMessage` (retryable). `READY` retained for legacy but `INDEXED` is the new success state. `isReady()` returns true for both.
- **Supported formats**: `application/pdf`, `application/msword`, `application/vnd.openxmlformats-officedocument.wordprocessingml.document`, `text/plain`, `application/octet-stream` (Tika fallback).
- **Upload endpoints** return **201 CREATED** with full `DocumentMetadataResponse`:
  - `POST /api/v1/documents/uploads` — single file (`file`)
  - `POST /api/v1/documents/uploads/batch` — multiple files (`files`)
- **Batch**: sequential per-file; earlier files stay indexed if a later file fails.
- **Test**: all 12 pass; `DocumentMetadataTest` updated for expanded content types.

## 2026-10-22 append — CORS configuration
- **New**: `CorsProperties` (`app.cors.*`) + `CorsConfig` (`WebMvcConfigurer`) in `config/`.
- **Dev profile** (`application-dev.yaml`): CORS enabled by default with permissive origins for local frontends (localhost:3000, localhost:5173, 127.0.0.1:3000, 127.0.0.1:5173), credentials allowed, max-age 1h.
- **Prod profile** (`application-prod.yaml`): CORS disabled by default (`app.cors.enabled=false`); all properties env-overridable (`APP_CORS_*`).
- **Properties**: `allowed-origins`, `allowed-methods`, `allowed-headers`, `exposed-headers`, `allow-credentials`, `max-age`.
- **Usage**: Applied globally to `/**` via Spring MVC `CorsRegistry`. No code changes needed in services (CORS is a web-layer concern).

## 2026-10-23 append — Phase 2: RAG retrieval & generation (chat package)
- **New DTOs** (`chat/dtos/`): `SearchRequestDto`, `SearchResultDto`, `CitationDto`, `ChatRequestDto`, `ChatResponseDto`
- **RagService** (`chat/service/`): `searchSimilarChunks`, `askQuestion`, `streamQuestionAnswer`
- **RagServiceImpl** (`chat/serviceImpl/`):
  - VectorStore similarity search with `FilterExpressionBuilder` for documentId filter
  - ChatClient with `MessageWindowChatMemory` + `InMemoryChatMemoryRepository` for conversation context
  - Citation mapping: `documentId`, `fileName`, `chunkIndex`, `page_number`, similarity = 1 - distance
  - Context builder: `[Source: filename (page N)]\ntext` joined with `---`
  - Prompt template: conditional (no-context / with-context) with grounding instructions
- **ChatController** (`chat/controller/`):
  - `POST /api/v1/chat/search` — similarity search, returns `SearchResultDto` (query, totalMatches, citations[])
  - `POST /api/v1/chat/ask` — RAG Q&A, returns `ChatResponseDto` (answer, conversationId, citations[], responseTimeMs)
  - `GET /api/v1/chat/stream` — SSE streaming, query params: `question`, `documentId`, `topK`, `minSimilarity`, `conversationId`
  - All endpoints return 503 if PostgreSQL provider disabled
- **Usage examples**:
```bash
# Search similar chunks
curl -X POST localhost:8080/api/v1/chat/search \
  -H 'Content-Type: application/json' \
  -d '{"query":"machine learning", "topK":5, "minSimilarity":0.7}'

# Ask a question (RAG)
curl -X POST localhost:8080/api/v1/chat/ask \
  -H 'Content-Type: application/json' \
  -d '{"question":"What is RAG?", "topK":5, "minSimilarity":0.7}'

# Stream answer (SSE)
curl -N localhost:8080/api/v1/chat/stream?question=What%20is%20RAG%3F
```
- **Tuning**: `app.rag.topK` (default 5), `app.rag.similarity-threshold` (default 0.7)
- **Dependencies**: `spring-ai-model` (ChatMemory), `spring-ai-client-chat` (ChatClient), `spring-ai-vector-store` (SearchRequest)
- **Proof**: `./mvnw test` — 12/12 green; compile clean.
