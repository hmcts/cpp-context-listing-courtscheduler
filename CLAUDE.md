# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

HMCTS Court Scheduler service: manages court schedules (sessions), hearing-slot allocation, provisional bookings, judiciary assignment/availability, and ingestion of ROTA XML files from Azure Blob Storage. It is a **Spring Boot 4 / Java 25 / Gradle port of a legacy WildFly + Justice Services + Maven WAR**. The guiding rule of the port (see `doc/breaking.md`): **it must be a drop-in replacement for clients**. Any client-visible divergence from the legacy contract (status codes, error body shape, media types, `Accept` handling, response shapes, `_metadata` envelope) is a bug to fix on the Spring side.

Docs live in `doc/`. Start with `doc/COURT_SCHEDULER_GUIDE.md` (an HTML copy sits beside it), which covers business rules, architecture, API, data model and QA. Background docs:
- `doc/MIGRATION.md`: API contract, auth/audit wiring, the full endpoint → controller → service table, open regression-audit items.
- `doc/REVIEW.md`: per-file rationale for the migration and the non-obvious fixes (§3 is worth reading before touching repositories or transactions).
- `doc/breaking.md`: contract decisions and the evidence behind them.
- `doc/TECHNICAL_DOCUMENTATION.md`: business rules and data model. It describes the **legacy** Maven/RAML/Java EE stack, so its build and framework details are out of date.

## Commands

The JDK 25 toolchain is required. Tests pin `user.timezone=Europe/London` in the root `build.gradle`; some code hard-codes that zone.

```bash
./gradlew clean build                 # everything: compile, unit tests, bootJar, AND the docker-compose integration suite (needs Docker)
./gradlew build -x :listingcourtscheduler-integration-test:test   # build + unit tests without the IT stack

./gradlew :listingcourtscheduler-api:test                          # unit tests for one module
./gradlew :listingcourtscheduler-api:test --tests 'uk.gov.moj.cpp.courtscheduler.SomeTest'          # single class
./gradlew :listingcourtscheduler-api:test --tests 'uk.gov.moj.cpp.courtscheduler.SomeTest.method'   # single method
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test         # repository tests (Testcontainers Postgres, needs Docker)

./gradlew :listingcourtscheduler-integration-test:test             # ITs: composeUp → run → composeDown
./gradlew :listingcourtscheduler-integration-test:test --tests '*CourtSchedulerIT'
./gradlew :listingcourtscheduler-integration-test:performanceTest  # opt-in **/performance/** ITs, not part of build

./gradlew :listingcourtscheduler-api:bootJar                       # fat jar: build/libs/cpp-context-listing-courtscheduler-<version>.jar
./gradlew :listingcourtscheduler-api:pmdMain                       # PMD only runs when named explicitly (non-blocking in CI until LPT-2318)
```

The version defaults to `0.0.999` and is overridden with `-DARTEFACT_VERSION=...` (CI does this). Dependencies resolve partly from the HMCTS Azure Artifacts feed (`AZURE_DEVOPS_ARTIFACT_USERNAME`/`_TOKEN` env vars), which is needed for `cp-auth-rules-filter` and `cp-audit-filter-springboot`.

Local run: `listingcourtscheduler-api/docker-compose.yml` brings up Postgres plus the app (port 8083). The integration stack is separate: `listingcourtscheduler-integration-test/docker/docker-compose.integration.yml` with app, db, WireMock (`:8189`) and Azurite. Base URL: `http://localhost:8083/listingcourtscheduler-api/rest/courtscheduler`.

## Module architecture

Dependency flow: `domain` ← `viewstore-persistence`; `common` depends on `domain`, `cache` and `viewstore-persistence`; `rota-file-processor` depends on `common`; `api` depends on all of them.

- **listingcourtscheduler-domain**: pure POJOs plus Jackson annotations. Exposes `testFixtures` (shared `FileUtil#payloadToObject` etc.) that other modules' tests consume.
- **listingcourtscheduler-cache**: Redis (Lettuce) cache, configured from `redis.common-cache.*`.
- **listingcourtscheduler-viewstore-persistence**: JPA entities and Spring Data repositories. **listingcourtscheduler-viewstore-liquibase**: changelogs, a runtime-only dependency of the api (`classpath:liquibase/courtscheduler-view-store-db-changelog.xml`).
- **listingcourtscheduler-common**: the services (`SessionsService`, slot services, `ReferenceDataService`, Azure Blob clients). Outbound calls to other CPP services use `RestTemplate` via `CommonPlatformQueryClient`, which sends the system user (`courtscheduler.system-user-id`) as `CJSCPPUID`.
- **listingcourtscheduler-rota-file-processor**: rota XML capture, enrichment and processing. Triggered by `POST /rotasl/process-rota-files`, which returns 202 and processes `@Async`.
- **listingcourtscheduler-api**: the Spring Boot app (`uk.gov.moj.cpp.courtscheduler.Application`), controllers, config, envelope and validation.
- **listingcourtscheduler-integration-test**: tests only. The app runs inside Docker, and tests hit it over HTTP (`app.baseUrl`, `wiremock.baseUrl` system properties). There are two styles: ported legacy `*IT` classes (`AbstractIT`) and newer `*IntegrationTest` classes (`AbstractIntegrationTest`).

## API layer: how a request flows

- **Contract source of truth** is `listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml`. `openApiGenerate` runs before `compileJava` and emits `*OpenApi` interfaces into `build/generated/openapi`; the `*Api` classes in the `api` package (`CourtSchedulerApi`, `HearingSlotsApi`, `JudiciaryAvailabilityApi`, `RotaFileProcessorApi`) implement them. To change an endpoint, edit the spec first. Many request schemas there are still placeholder `PassthroughObject`s.
- **Request body validation** is done by `validation/RequestSchemaValidationFilter`, which uses everit (draft-04, same as legacy) against the legacy RAML JSON schemas in `src/raml/json/schema/`. Those are packaged onto the classpath as `request-schemas/`. Only vendor `+json` media types are schema-gated.
- **Endpoints are distinguished by vendor media type.** Many share a path, e.g. `POST /validate` and `POST /session`, and are dispatched on `Content-Type`.
- **Authorization** uses `cp-auth-rules-filter` with Drools rules in `src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl`. `Action.name` resolves from the `CPP-ACTION` header, then the vendor media-type token, then the `"<METHOD> <PATH>"` fallback (used by the plain-`application/json` `/judiciaries/*` endpoints). A new endpoint needs a matching DRL rule, or it fails closed. There is no Spring Security.
- **Audit** uses `cp-audit-filter-springboot` (`AuditFilterConfig`). Per-action metrics come from `ActionMetricsInterceptor`.
- **Responses** are wrapped in the legacy `_metadata` envelope by `envelope/EnvelopeResponseBodyAdvice`. Use `@SkipEnvelope` to opt out.
- **Errors** go through `GlobalExceptionHandler`, which must emit `{"error":"<msg>"}` (multiple messages joined with `"; "`). This is the proven legacy wire shape. Do not change it to `errorMessage`, even though validators use that key internally. Judiciary validate endpoints return 422 with `{"validationResult":{…}}`.
- Config is split into profile fragments: `application-{datasource,authz,audit,azure,server-management,integration}.yml`.

## Persistence gotchas

- `CourtScheduleRepository` is the only repo with a 3-file split (interface + `…Custom` + 2000-line `CourtScheduleRepositoryImpl`). The `Impl` is class-level `@Transactional(readOnly = true)`, so **every new mutating method needs its own method-level `@Transactional`**, or it fails at runtime with `TransactionRequiredException`. No test catches this generically.
- Other repos keep the legacy DeltaSpike-style declarative form (method names plus `@Query` on a `JpaRepository` interface) along with `default` compatibility aliases (`findBy`, `merge`, `remove`). Follow that style rather than hand-writing `EntityManager` code.
- Batch court-schedule upserts are try-insert, catch constraint violation, then update. Inserts must run in `CourtScheduleBatchInsertService.persistBatch` (`REQUIRES_NEW`). A flush failure inside the caller's transaction marks it rollback-only and causes an HTTP 500 (`doc/REVIEW.md` §3.12).
- The JPA static metamodel is generated by `hibernate-jpamodelgen`.

## Conventions

- PR template asks for a JIRA link and a breaking-change yes/no. Branches look like `dev/<JIRA-KEY>-<desc>` (e.g. `SPRDT-987`).
- Publishing (`./gradlew publish`) is idempotent by design: it skips a registry that already holds the version's pom.
