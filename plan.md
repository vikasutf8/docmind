# Build plan — docmind

## Phase 0 — Foundation (THIS TASK, in progress)

- [x] Docs: `readme.md`, `arch.md`, `plan.md`, `execution.md`
- [ ] `pom.xml`: validation, actuator, jdbc, 4 drivers, springdoc 3.1.1,
      spring-ai-bom 2.0.1 (managed), docker-compose module
- [ ] `config` package: AppProperties, Jackson, OpenAPI + 4 providers
- [ ] `constant` package: AppConstants
- [ ] `exception` package: 3 custom exceptions + handler + test
- [ ] YAMLs: base + dev + prod profiles
- [ ] `compose.yaml`: postgres/pgvector, mysql, mongo, cassandra
- [ ] Proof: `./mvnw test` green, `execution.md` updated

## Phase 1 — AI core (next)

- `spring-ai-starter-vector-store-pgvector` + embedding model (OpenAI or
  Ollama) wired to the pg provider; `initialize-schema: true` in dev
- `document` package: upload/chunk/embed pipeline, `DocumentService`
- `chat` package: `ChatController` (`POST /api/v1/chat`), RAG advisor

## Phase 2 — API surface

- CRUD controllers for documents, search endpoint, pagination
- Spring Data pagination + validation groups, rate limiting

## Phase 3 — Hardening

- Auth (OAuth2 resource server), Testcontainers integration tests,
  GraalVM native image, metrics/dashboards, CI pipeline

## Out of scope today

No controllers/services yet on purpose — foundation must boot clean
with zero DBs before anything is built on top of it.

## 2026-10-09 append — Phase 0 extra done
- `docker/postgres/init.sql` + compose mount added (pgvector prerequisites).
- Rule going forward: every change is APPENDED to these md files, never rewritten.

## 2026-10-09 append — OpenAI yaml in place
- `spring.ai.openai.*` + `spring.ai.vectorstore.pgvector.*` added to base yaml
  (dormant until Phase 1 starter). Phase 1 must verify enum binding for
  `distance-type` and add the pgvector starter + embedding model.

## 2026-10-09 append — RAG knobs in place
- `app.rag.*` + `RagProperties` done. Phase 1 chunking/retrieval will read
  these (no hardcoded sizes).

## 2026-10-11 append — docManagement + shared skeleton (Phase 2 start)
- New root packages (user-confirmed): `com.ai.docmind.docManagement.*`
  (`controller/`, `service/`, `serviceImpl/`, `repository/`, `dtos/`) and
  `com.ai.docmind.shared.entity/` holding `Document`.
- Naming kept exactly as requested: `docManagement` (capital M) + sibling
  `service` / `serviceImpl` (not `service.impl`).
- `Document` is a plain immutable carrier (record, no JPA/Mongo starter yet)
  so no new `pom.xml` dependency; fields: id/title/content/contentType/
  sizeBytes/createdAt/updatedAt.
- Scope: CRUD skeleton — `DocumentController` + `DocumentService` iface +
  `DocumentServiceImpl` + `DocumentRepository` iface +
  `InMemoryDocumentRepository` + `Create/Update/Response` DTOs with
  jakarta validation. In-memory store keeps `./mvnw test` green with zero DBs.
- Docs order for this change: plan.md -> arch.md -> code -> execution.md ->
  readme.md (append-only, never rewrite).

## 2026-10-11 append — chat package (dummy controller only)
- New root package `com.ai.docmind.chat.*` with same five sub-packages as
  docManagement (`controller/`, `service/`, `serviceImpl/`, `repository/`,
  `dtos/`), but only `controller/ChatController.java` gets a class for now.
- `ChatController` is a simple dummy: `POST /api/v1/chat` echoing the
  message back, no service/repository/DTO wiring yet. RAG + Spring AI
  wiring lands later (Phase 1).

## 2026-10-11 append — pg document_metadata storage
- New `shared/enums/DocumentStatus` (PENDING -> PROCESSING -> READY, plus
  FAILED with errorMessage) parallel to `shared/entity/`, and new JPA
  `@Entity shared/entity/DocumentMetadata` mapped to table
  `document_metadata`: uuid (PK) / filename / contentType / fileSize /
  totalPages / totalChunks / status (STRING) / createdAt / updatedAt /
  errorMessage (TEXT). Metadata row only — binary stays on disk/object
  storage; pdf accepted now, doc/docx join SUPPORTED_CONTENT_TYPES later.
- `pom.xml`: added `spring-boot-starter-data-jpa` (Boot-managed). Base
  yaml excludes `HibernateJpaAutoConfiguration` so boot stays DB-free;
  new conditional `config/provider/postgres/PostgresJpaConfig`
  (pg.enabled=true only) builds `pgEntityManagerFactory` +
  `pgTransactionManager` on the qualified `postgresDataSource`
  (ddl-auto update, no @Primary — keeps the no-ambiguity rule).

## 2026-10-21 append — Spring AI starters declared + document_metadata hardening
- Root cause fixed: the BOM manages versions but puts nothing on the
  classpath; every `./mvnw` compile of AI-touching code failed. `pom.xml`
  now declares what `application.yaml` already configures, versions from
  `spring-ai-bom` 2.0.1:
  - `spring-ai-starter-model-google-genai` (chat + embedding,
    `spring.ai.google.genai.*` — dormant without an api-key)
  - `spring-ai-starter-vector-store-pgvector`
  - `spring-ai-pdf-document-reader` (`PagePdfDocumentReader`)
- `DocumentMetadata` modernised for Boot 4.1 / Hibernate 7 / Java 25:
  setters removed (`setFilename/setStatus/setTotalPages/setTotalChunks/
  setErrorMessage`); lifecycle goes through the entity itself —
  `startProcessing()` (PENDING|FAILED -> PROCESSING, FAILED retryable),
  `complete(pages, chunks)` (PROCESSING -> READY), `fail(reason)`
  (any pre-READY -> FAILED, truncated to 2000). READY is terminal;
  illegal moves throw `IllegalStateException` in the entity, so callers
  cannot corrupt the state machine.
  - `@DynamicUpdate` (Hibernate) for partial UPDATE statements; content
    columns become `updatable = false` (identity never changes).
  - `isFailed()` added beside `isReady()`; timestamps stay JPA-portable
    (`@PrePersist/@PreUpdate`); client-assigned UUID kept (the id must
    exist before the binary file is named on disk).
- Deliberately NOT added: `@Version` optimistic locking — it needs a real
  migration (`ALTER TABLE document_metadata ADD COLUMN version BIGINT NOT
  NULL DEFAULT 0`); to be added together with Flyway, not via
  ddl-auto=update (prod runs `validate`).
- Tests: `DocumentMetadataTest` moves to the domain API and covers
  illegal transitions.
- Note: earlier untracked pipeline files (upload controller, ingestion
  service, processor, chat DTOs, etc.) are no longer in the working tree;
  the consumers of this entity return with the pipeline rebuild.

## 2026-10-21 append — DocumentProcessingException in the global advice
- Add `exception/DocumentProcessingException` with the affected
  `documentId` and a nested `Stage`:
  `STORAGE | EXTRACTION | CHUNKING | EMBEDDING | DELETION`.
- Extend `GlobalExceptionHandler` with an `@ExceptionHandler` for it:
  - `EXTRACTION` / `CHUNKING` -> 422 `Unprocessable Content`
    (valid request, unusable source document)
  - `STORAGE` / `EMBEDDING` / `DELETION` -> 500
    (server/infrastructure fault)
  - RFC 9457 body: title `Document processing failed`, properties
    `documentId` + `stage`; the cause is logged server-side only.
- No pipeline classes exist in the current tree (the upload/processor
  files were removed), so this lands the contract + mapping only; the
  ingestion pipeline will throw it when rebuilt. No `@Data`/`@Setter` on
  the exception — immutable carrier.
- Verification: code-only, no tests/execution per instruction.
