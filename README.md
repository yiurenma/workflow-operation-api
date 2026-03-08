# Low-Code Workflow

A low-code workflow platform built with Spring Boot 4 and JDK 21. It provides REST APIs for a UI to define and manage workflows (rules, types, entity settings), and a single **online API** that accepts any incoming request, builds a runtime JSON from the request data, selects the configured workflow, gathers data from backend APIs, and forwards the result to the fulfillment system for processing.

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
| **Health & docs** | | |
| GET | `/api/health` | Health check; returns `{"status":"UP","application":"lowcode-workflow"}` |
| GET | `/swagger-ui.html` | Interactive API documentation (Swagger UI) |
| GET | `/v3/api-docs` | OpenAPI 3.0 JSON spec |
| GET | `/actuator/health` | Actuator health endpoint |
| GET | `/actuator/info` | Actuator info |
| GET | `/actuator/metrics` | Actuator metrics |
| **Dev tools** | | |
| GET | `/h2-console` | H2 web console (JDBC: `jdbc:h2:mem:workflowdb`, user: `sa`, password: empty) |

## Architecture

### Workflow Management APIs

- **Create** – Define new workflows via REST
- **Delete** – Remove workflows
- **Update** – Delete then create internally (replace workflow)

### Online API

Single entry point that accepts any path, headers, and request body. It:

1. Gathers all request information into a **runtime JSON**
2. Loads the workflow defined in the low-code platform
3. Follows the workflow to gather data from backend APIs
4. Sends the result to the **fulfillment system** for processing

### Flow

```
Request → Online API (build runtime JSON) → Workflow Engine → Backend APIs → Fulfillment System
```

### Execution Steps

1. **Ingest** – Accept incoming request (any method, path, headers, body)
2. **Build Runtime JSON** – Merge request data into a single JSON payload
3. **Resolve Workflow** – Select the configured workflow from the platform
4. **Execute Workflow** – Call backend APIs as defined in the workflow
5. **Fulfill** – Forward the result to the fulfillment system
