# Workflow Operation API

A Workflow Operation API platform built with **Spring Boot 4.0.3** and **JDK 21**. It provides REST APIs for a UI to define and manage workflows (rules, types, entity settings), and a single **online API** that accepts any incoming request, builds a runtime JSON from the request data, selects the configured workflow, gathers data from backend APIs, and forwards the result to the fulfillment system for processing.

## Quick Start

**Requirements:** JDK 21, Maven 3.9+

```bash
mvn clean install
mvn spring-boot:run
```

Runs at `http://localhost:8080`.

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
| GET | `/swagger-ui.html` | Interactive API documentation (Swagger UI) |
| GET | `/v3/api-docs` | OpenAPI 3.0 JSON spec |
| GET | `/actuator/health` | Actuator health endpoint |
| GET | `/actuator/info` | Actuator info |
| GET | `/actuator/metrics` | Actuator metrics |

## Architecture

### Workflow Management APIs (for UI)

| API | Description |
|-----|-------------|
| **Create Workflow** | Define a new workflow in the Workflow Operation API platform |
| **Delete Workflow** | Remove a workflow by application name |
| **Update Workflow** | Replace an existing workflow (internally: delete + create) |

### Entity Setting Query APIs (for admin/audit)

- `GET /api/workflow/entity-setting`
  - Query through `@QuerydslPredicate(root = WorkflowEntitySetting.class)`.
  - Includes fuzzy search for `applicationName` (`containsIgnoreCase`), e.g.
    - `/api/workflow/entity-setting?applicationName=itest&page=0&size=20`
- `GET /api/workflow/entity-setting/history`
  - Returns revision history from Envers by exact `applicationName`, e.g.
    - `/api/workflow/entity-setting/history?applicationName=ITEST_APP&page=0&size=20`

### Online API (Request Execution)

A single **online API** serves as the entry point for all incoming requests:

1. **Request Ingestion** – Accepts any request regardless of path, headers, or body
2. **Runtime JSON** – Collects all request data (path, headers, query params, body) into a unified runtime JSON
3. **Workflow Selection** – Resolves and loads the workflow defined in the Workflow Operation API platform
4. **Workflow Execution** – Runs the workflow to call backend systems and send the payload to the fulfillment system

### Flow

```
Request → Online API (build runtime JSON) → Workflow Engine → Backend APIs → Fulfillment System
```
