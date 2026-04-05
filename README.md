# Workflow Operation API

A **control-plane** service built with **Spring Boot 4.0.3** and **JDK 21**. It exposes REST APIs for a UI to **define and manage** workflows (rules, types, entity settings, entity-setting queries). It shares a PostgreSQL database with the online ingress service for workflow definitions and related tables.

**Related repositories**

| Role | Repository | Notes |
|------|------------|--------|
| **Online ingress** (execution entry) | [workflow-online-api](https://github.com/yiurenma/workflow-online-api) | `POST /api/workflow` only; see its README for runtime pipeline and shared DB |
| **Management UI** | [workflow-ui](https://github.com/yiurenma/workflow-ui) | Consumes **this** service’s OpenAPI (`/v3/api-docs`) for CRUD; optional proxy to Online for other flows |

If you check out all three next to each other locally, typical folder names are `workflow-operation-api`, `workflow-online-api`, and `workflow-ui`.

## Quick Start

**Requirements:** JDK 21, Maven 3.9+

```bash
mvn clean install
mvn spring-boot:run
```

Runs at `http://localhost:8080` (override with env **`PORT`**; see `application.yml`).

### Running Operation and Online on the same machine

Both services default to **port 8080**. To run them together, start one of them on another port, for example:

```bash
# Terminal 1 — Operation (default)
mvn spring-boot:run

# Terminal 2 — Online on 8081
mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=8081
```

Point your UI or clients at the matching base URLs (`VITE_OPERATION_API_BASE`, `VITE_ONLINE_API_BASE`, or Vite proxy targets — see **workflow-ui** README).

## Key Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| **Workflow APIs** | | |
| GET | `/api/workflow` | Get workflow by application name |
| POST | `/api/workflow` | Create or update workflow (delete then create) |
| POST | `/api/workflow/autoCopy` | Copy workflow from one application to another |
| DELETE | `/api/workflow` | Delete workflow by application name |
| **Entity Setting Query APIs** | | |
| GET | `/api/workflow/entity-setting` | Query entity settings with QueryDSL predicate (supports fuzzy `applicationName`) |
| GET | `/api/workflow/entity-setting/history` | Get entity setting revision history by `applicationName` |
| **Docs & tools** | | |
| GET | `/redoc.html` | Interactive API documentation (ReDoc) |
| GET | `/swagger-ui.html` | Swagger UI endpoint (based on same OpenAPI spec) |
| GET | `/v3/api-docs` | OpenAPI 3.0 JSON spec |
| GET | `/actuator/health` | Actuator health endpoint |
| GET | `/actuator/info` | Actuator info |
| GET | `/actuator/metrics` | Actuator metrics |

## Architecture

### Workflow Management APIs (for UI)

| API | Description |
|-----|-------------|
| **Create Workflow** | Define a new workflow in this platform |
| **Delete Workflow** | Remove a workflow by application name |
| **Update Workflow** | Replace an existing workflow (internally: delete + create) |

Frontend: **[workflow-ui](https://github.com/yiurenma/workflow-ui)** — use this service’s **`/v3/api-docs`** as the contract for management features.

### Entity Setting Query APIs (for admin/audit)

- `GET /api/workflow/entity-setting`
  - Query through `@QuerydslPredicate(root = WorkflowEntitySetting.class)`.
  - Includes fuzzy search for `applicationName` (`containsIgnoreCase`), e.g.
    - `/api/workflow/entity-setting?applicationName=itest&page=0&size=20`
- `GET /api/workflow/entity-setting/history`
  - Returns revision history from Envers by exact `applicationName`, e.g.
    - `/api/workflow/entity-setting/history?applicationName=ITEST_APP&page=0&size=20`

### Online execution (not in this repository)

Request ingestion, runtime JSON assembly, workflow selection, and fulfillment forwarding are implemented in **[workflow-online-api](https://github.com/yiurenma/workflow-online-api)** (`POST /api/workflow`). This Operation service owns **definitions and schema**; Online owns the **ingress path** against the **shared** database. For sequence diagrams, encrypted payload rules, and configuration, read that repository’s README.

High-level flow:

```text
Client → Online API (workflow-online-api) → workflow engine / backends → fulfillment
                ↑
         definitions & config from DB (maintained via Operation API + UI)
```
