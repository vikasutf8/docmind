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
