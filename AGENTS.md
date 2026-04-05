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

### CI / Render

- **GitHub Actions**: `.github/workflows/ci.yml` runs on push and pull requests to `main` / `master`. Optional: `render-deploy.yml` posts to `RENDER_DEPLOY_HOOK_URL` after CI succeeds.
- **Deploy details**: see `docs/deploy-render.md`. **Docker** image: `Dockerfile` at repo root (Render Java services typically use `runtime: docker`).

### Gotchas

- **JaCoCo coverage gate**: `mvn verify` / `mvn install` enforces a 98% instruction coverage threshold. The codebase currently sits at ~97%, so `mvn install` will fail unless you pass `-Djacoco.skip=true`. This is a pre-existing issue.
- **PostgreSQL**: Production `application.yml` points to a Neon cloud PostgreSQL instance. The app starts and connects to it without any local DB setup. If the Neon instance becomes unreachable, you will need a local PostgreSQL or must override `spring.datasource.*` properties.
- **Tests use H2**: All tests run against an in-memory H2 database (profile `application-test.yml`). No external database is needed for tests.
- **No lint tool beyond compilation**: There is no separate linter (e.g. Checkstyle, SpotBugs). Compilation with `mvn compile` is the primary static check.
- **Docker**: A `Dockerfile` is provided for Render and other hosts; local dev remains Maven-first. No Makefile/npm.
- **Swagger UI**: Available at `http://localhost:8080/swagger-ui.html` (redirects to the full Swagger UI path). OpenAPI spec at `/v3/api-docs`.
