# AGENTS.md

## Cursor Cloud specific instructions

### Project overview

Workflow Operation API — a Spring Boot 4.0.3 / JDK 21 application providing REST APIs for workflow management and a single online API for request execution. See `README.md` for endpoints and architecture.

### Prerequisites

- **JDK 21** (system-installed via `openjdk-21`)
- **Maven 3.9+** (installed at `/opt/apache-maven-3.9.9/bin/mvn`, symlinked to `/usr/bin/mvn`)

### Build, test, lint, and run

Standard Maven commands per `README.md`:

| Task | Command |
|------|---------|
| Build + test | `mvn clean install -Djacoco.skip=true` |
| Tests only | `mvn test` |
| Run (dev) | `mvn spring-boot:run -Djacoco.skip=true` |
| Package | `mvn package -Djacoco.skip=true` |
| CI (same as GitHub Actions) | `mvn -B verify -Djacoco.skip=true` |

### CI / DigitalOcean

- **GitHub Actions**: `.github/workflows/ci.yml` runs tests on push and pull requests to `main` / `master`.
- **Deploy**: DigitalOcean App Platform deploys automatically when `main` is pushed — no manual deploy hook required.
- **Docker** image: `Dockerfile` at repo root. `docs/deploy-render.md` is archived (Render no longer used).

### Gotchas

- **JaCoCo coverage gate**: `mvn verify` / `mvn install` enforces a 98% instruction coverage threshold. The codebase currently sits at ~97%, so `mvn install` will fail unless you pass `-Djacoco.skip=true`. This is a pre-existing issue.
- **PostgreSQL**: Production `application.yml` points to a Neon cloud PostgreSQL instance. The app starts and connects to it without any local DB setup. If the Neon instance becomes unreachable, you will need a local PostgreSQL or must override `spring.datasource.*` properties.
- **Tests use H2**: All tests run against an in-memory H2 database (profile `application-test.yml`). No external database is needed for tests.
- **No lint tool beyond compilation**: There is no separate linter (e.g. Checkstyle, SpotBugs). Compilation with `mvn compile` is the primary static check.
- **Docker**: A `Dockerfile` is provided for Render and other hosts; local dev remains Maven-first. No Makefile/npm.
- **Swagger UI**: Available at `http://localhost:8080/swagger-ui.html` (redirects to the full Swagger UI path). OpenAPI spec at `/v3/api-docs`.

---

## Test Plan

> **Scope:** operation-api (port 8080). For online-api execution pipeline tests see `workflow-online-api/AGENTS.md`.
> **Last verified:** 2026-04-06 — all 117 unit/integration tests green, all E2E cases below passed.

### Unit / integration tests (automated)

```bash
mvn test          # 117 tests, all green
```

Key test classes:
- `WorkflowDeleteControllerTest` — unit tests for delete cascade (rules, mappings, types, reports)
- `WorkflowEdgeCaseIntegrationTest` — edge cases: empty ruleList, null action, delete-with-reports, validation errors
- `WorkflowAutoCopyIntegrationTest` — autoCopy error codes WF-400-301 / 302 / 303
- `WorkflowEntitySettingPatchIntegrationTest` — PATCH partial-update semantics, workflow field preservation

### Manual E2E checklist (against live Neon DB)

Prerequisites: both APIs running (`mvn spring-boot:run` on 8080 and 8081), at least one app row in `WORKFLOW_ENTITY_SETTING`.

#### TC-01 List & Search
| Step | Command | Expected |
|------|---------|----------|
| List all apps | `GET /api/workflow/entity-setting` | 200, page of results |
| Filter by name | `GET /api/workflow/entity-setting?applicationName=<name>` | 200, 1 result |

#### TC-02 Create / Update Workflow
| Step | Command | Expected |
|------|---------|----------|
| Save workflow | `POST /api/workflow?applicationName=<name>` + JSON body | 200, persisted pluginList |
| Re-save (update) | Same call with changed plugin | 200, old rules/types replaced |

#### TC-03 Delete Workflow (OP-03)
| Step | Command | Expected |
|------|---------|----------|
| Delete app | `DELETE /api/workflow?applicationName=<name>` | 200 |
| Verify records retained | `GET /api/workflow/records?applicationName=<name>` | 200, orphaned records still visible |
| Delete non-existent | `DELETE /api/workflow?applicationName=DOES_NOT_EXIST` | 400 `WF-400-101` |

> **OP-03 note:** `WorkflowRecord` rows are intentionally kept as orphans after delete. `WorkflowReport` rows ARE deleted (FK constraint). Records can still be queried by `applicationName` filter.

#### TC-10 AutoCopy (OP-06)
| Step | Command | Expected |
|------|---------|----------|
| Copy A → B | `POST /api/workflow/autoCopy?fromApplicationName=A&toApplicationName=B` | 200, B workflow matches A |
| Same src/target | same but `from=A&to=A` | 400 `WF-400-301` |
| Non-existent src | `from=DOES_NOT_EXIST&to=B` | 400 `WF-400-302` |

#### TC-11 Revision History (OP-07)
| Step | Command | Expected |
|------|---------|----------|
| Get history | `GET /api/workflow/entity-setting/history?applicationName=<name>` | 200, list of Envers revisions with `entity`, `metadata.revisionType`, `metadata.revisionDate` |
| Each PATCH creates revision | PATCH then re-check history | count incremented, `revisionType=UPDATE` |

#### TC-18 PATCH Entity Setting (OP-02)
| Step | Command | Expected |
|------|---------|----------|
| Disable | `PATCH /api/workflow/entity-setting?applicationName=<name>` body `{"enabled":false}` | 200, `enabled=false` |
| Set asyncMode | body `{"asyncMode":true}` | 200, `asyncMode=true` |
| Set retryProperties | body `{"retryProperties":"{\"maxAttempts\":3}"}` | 200, field updated |
| Workflow field untouched | PATCH any field, then `GET /api/workflow` | workflow base64 unchanged |

#### TC-19 Execution Records (OP-04 / OP-05)
| Step | Command | Expected |
|------|---------|----------|
| List (no filter) | `GET /api/workflow/records?page=0&size=20` | 200, paginated |
| Filter by app + status | `?applicationName=X&overallStatus=GI_SUCCESS` | filtered results |
| Sort descending | `?sort=createdDateTime,desc` | newest first |
| Record detail | `GET /api/workflow/records/{id}` | `{record: {...}, children: [...]}` |
| Non-existent ID | `GET /api/workflow/records/99999` | 404 `WF-404-000` |

### Known gotchas for agents

- **`WorkflowDeleteController` needs `WorkflowReportRepository`** — without it, deleting an app that has `WorkflowReport` rows causes a FK constraint 500. Always include the report repository when modifying the delete flow.
- **JPA Specification for records queries** — `WorkflowRecordRepository` extends `JpaSpecificationExecutor`. Do NOT revert to JPQL with optional null params — PostgreSQL cannot infer the type of null `Date` parameters (error: `could not determine data type of parameter $N`).
- **autoCopy uses `produces`, not `consumes`** — `@PostMapping` on `/autoCopy` has no request body; adding `consumes = APPLICATION_JSON_VALUE` would require a Content-Type header and break callers.
- **Empty rule key causes `GI_FAIL` in online-api** — saving a workflow with `ruleList: []` creates a rule with `key=""`. The online-api's JSONPath evaluator fails on an empty expression. Validate rule keys are non-empty before saving if end-to-end execution is required.
