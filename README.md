# Low-Code Workflow

## Description

A low-code workflow platform built with **Spring Boot 4.0.3** and **JDK 21**.

- **Workflow Management**: APIs for a low-code UI to create, update, and delete workflows
- **Online Execution**: A single online API that accepts requests and runs the configured workflow

## Required Tools

- **JDK 21+**
- **Maven 3.9+**

## Run Locally

```bash
mvn clean install
mvn spring-boot:run
```

App URL: `http://localhost:8080`

Useful endpoints:
- Health: `GET /api/health`
- Swagger UI: `http://localhost:8080/swagger-ui.html`

## Architecture

### Workflow Management APIs (for UI)

| API                 | Description                                                |
| ------------------- | ---------------------------------------------------------- |
| **Create Workflow** | Define a new workflow in the low-code platform             |
| **Delete Workflow** | Remove a workflow by ID                                    |
| **Update Workflow** | Replace an existing workflow (internally: delete + create) |

### Online API (Request Execution)

A single **online API** serves as the entry point for all incoming requests:

1. **Request Ingestion** – Accepts any request regardless of path, headers, or body
2. **Runtime JSON** – Collects all request data (path, headers, query params, body) into a unified runtime JSON
3. **Workflow Selection** – Resolves and loads the workflow defined in the low-code platform
4. **Workflow Execution** – Runs the workflow to:
  - Call backend systems (API-based) and gather required data
  - Send the assembled payload to the fulfillment system for processing

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐
│   Request   │────▶│   Online API     │────▶│   Workflow      │
│ (any path/  │     │ (build runtime   │     │   Engine        │
│  header/    │     │  JSON)           │     │                 │
│  body)      │     └────────┬─────────┘     └────────┬────────┘
└─────────────┘              │                        │
                             │                        ▼
                             │               ┌─────────────────┐
                             │               │ Backend APIs    │
                             │               │ (data gathering)│
                             │               └────────┬────────┘
                             │                        │
                             │                        ▼
                             │               ┌─────────────────┐
                             │               │ Fulfillment     │
                             │               │ System          │
                             └──────────────▶│ (processing)    │
                                             └─────────────────┘
```

