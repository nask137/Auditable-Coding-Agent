# Repository Guidelines

## Project Structure & Module Organization

This is a Java 25 Spring Boot backend for an auditable local coding agent runtime. Main code lives under `src/main/java/com/nask/agent`, organized by domain package: `workspace`, `task`, `run`, `plan`, `step`, `action`, `file`, `command`, `approval`, `audit`, `report`, `validation`, `llm`, `tool`, `api`, `cli`, and `common`.

Configuration and migrations live in `src/main/resources`: `application.properties` contains runtime defaults, and `db/migration` contains Flyway SQL such as `V1__phase1_core_schema.sql`. Tests mirror the main package structure under `src/test/java/com/nask/agent`. Product and phase design notes are in `docs/`.

## Build, Test, and Development Commands

- `mvn test` runs the JUnit 5 test suite.
- `mvn spring-boot:run` starts the backend on `http://localhost:8080`.
- `mvn -q -DskipTests package` builds the application without running tests.
- `mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt` prepares the classpath used by the picocli client.

Local runs expect PostgreSQL unless overridden with environment variables. Defaults are `jdbc:postgresql://localhost:5432/agent`, user `codex`, password `codex`.

## Coding Style & Naming Conventions

Use standard Java conventions: 4-space indentation, package-private helpers where possible, `PascalCase` classes, `camelCase` fields and methods, and uppercase constants. Keep classes focused on their package responsibility; for example, path validation belongs in `workspace`, approval state changes in `approval`, and diff logic in `file`.

Prefer constructor injection and existing Spring/JDBC patterns already used in the codebase. Keep JSON/API DTOs explicit and small.

## Testing Guidelines

Tests use JUnit 5 with AssertJ, Mockito, Spring Boot test support, and Testcontainers where needed. Name tests after the unit or workflow under test, using the existing `*Tests.java` pattern, such as `WorkspacePathGuardTests.java` or `Phase1ApiIntegrationTests.java`.

Run `mvn test` before submitting changes. Add or update tests for path-boundary logic, approval decisions, command policy behavior, file changes, API workflows, and database-backed behavior.

## Commit & Pull Request Guidelines

Git history uses short, conventional-style prefixes such as `feat:`, `docs:`, and `merge:`. Keep commit subjects imperative and scoped, for example `feat: add command policy audit event`.

Pull requests should include a concise summary, test results, linked issues or design docs when applicable, and screenshots only for user-visible UI changes. Call out schema migrations, configuration changes, and any change that affects workspace safety, command execution, approvals, or audit records.

## Security & Configuration Tips

Do not bypass `WorkspacePathGuard`, command policies, or approval checks. Keep file and command operations constrained to trusted workspaces, and document any new environment variables in `README.md` and `application.properties`.
