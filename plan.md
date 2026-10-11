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
