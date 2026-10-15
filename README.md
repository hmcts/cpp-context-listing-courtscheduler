# Court Scheduler Service

`cpp-context-listing-courtscheduler` is the HMCTS Common Platform (Crime) service that manages court sitting capacity. It:

- creates, edits and deletes court sessions (AM, PM or all day) for Magistrates' and Crown courtrooms, including Crown draft sessions that get a courtroom later;
- books and releases hearing slots, and tracks available capacity by number of slots or by minutes;
- searches for free capacity and holds it with provisional bookings;
- assigns judges and magistrates to sessions, and manages judicial itineraries (availability and unavailability);
- ingests magistrates' rota XML files from Azure Blob Storage to create sessions and judiciary assignments;
- records hearings that took place on past dates;
- exports management information (MI) for reporting.

| | |
|---|---|
| Base path | `/listingcourtscheduler-api/rest/courtscheduler` |
| Default port | `8083` |
| Stack | Java 25, Spring Boot 4, PostgreSQL 16 + Liquibase, Azure Blob Storage, Redis |
| API contract | [`courtscheduler-api.openapi.yml`](listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml) (in this repo) |

## Documentation

The full application guide lives in [`doc/`](doc). It comes as [Markdown](doc/COURT_SCHEDULER_GUIDE.md), which renders on GitHub with diagrams, and as a standalone [HTML page](doc/COURT_SCHEDULER_GUIDE.html) to open in a browser.

| If you want to… | Read |
|---|---|
| Understand what the service does and the terms it uses | [Executive summary](doc/COURT_SCHEDULER_GUIDE.md#2-executive-summary) · [Glossary](doc/COURT_SCHEDULER_GUIDE.md#3-glossary) · [Core concepts](doc/COURT_SCHEDULER_GUIDE.md#5-core-concepts) |
| Understand the business rules | [Business rules](doc/COURT_SCHEDULER_GUIDE.md#6-business-rules) · [Availability calculation](doc/COURT_SCHEDULER_GUIDE.md#9-availability-calculation) |
| Follow the booking, judiciary and past-date flows | [Key user journeys](doc/COURT_SCHEDULER_GUIDE.md#7-key-user-journeys) |
| Understand rota file ingestion | [Rota file processing](doc/COURT_SCHEDULER_GUIDE.md#8-rota-file-processing) |
| Understand the architecture and modules | [System architecture](doc/COURT_SCHEDULER_GUIDE.md#10-system-architecture) |
| Call the API | [API reference](doc/COURT_SCHEDULER_GUIDE.md#11-api-reference) |
| Check who can do what | [Security](doc/COURT_SCHEDULER_GUIDE.md#12-security-authentication-authorisation-and-audit) |
| Work with the database | [Data model](doc/COURT_SCHEDULER_GUIDE.md#13-data-model) |
| Configure an environment | [Configuration](doc/COURT_SCHEDULER_GUIDE.md#14-configuration) |
| Support or troubleshoot it | [Observability and operations](doc/COURT_SCHEDULER_GUIDE.md#15-observability-and-operations) |
| Develop and change the code | [Developer guide](doc/COURT_SCHEDULER_GUIDE.md#16-developer-guide) |
| Test the service | [QA and testing guide](doc/COURT_SCHEDULER_GUIDE.md#17-qa-and-testing-guide) |
| Understand the build and deployment | [CI/CD and release](doc/COURT_SCHEDULER_GUIDE.md#18-cicd-and-release) |

Other references:

- [`doc/MIGRATION.md`](doc/MIGRATION.md), [`doc/REVIEW.md`](doc/REVIEW.md) and [`doc/breaking.md`](doc/breaking.md) record the WildFly → Spring Boot migration: the preserved client contract, the reasons for each change, and the regression audit.
- [`doc/TECHNICAL_DOCUMENTATION.md`](doc/TECHNICAL_DOCUMENTATION.md) is the original technical write-up. Its business rules still apply, but its build and framework details describe the legacy stack.
- [`CLAUDE.md`](CLAUDE.md) is a condensed command and architecture reference for Claude Code.
- [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md) explains how to contribute.

The HTML guide is generated from the Markdown. After editing the Markdown, run `python3 doc/build_html.py`.

## API contract (OpenAPI)

The REST API is defined in [`courtscheduler-api.openapi.yml`](listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml). On each build, the OpenAPI Generator turns it into Spring interfaces (`*OpenApi`) under `listingcourtscheduler-api/build/generated/openapi`. The `*Api` classes in `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api` implement them: `CourtSchedulerApi`, `HearingSlotsApi`, `JudiciaryAvailabilityApi` and `RotaFileProcessorApi`.

Request bodies are validated against the JSON schemas in [`listingcourtscheduler-api/src/raml/json/schema`](listingcourtscheduler-api/src/raml/json/schema). Most operations are identified by a vendor media type such as `application/vnd.courtscheduler.create+json`.

The service is a drop-in replacement for the legacy WildFly service, so paths, media types, status codes and response shapes (including the `_metadata` envelope and `{"error": "..."}` error bodies) must not change. See [`doc/breaking.md`](doc/breaking.md).

To add or change an endpoint:

1. Change the OpenAPI spec.
2. Add or update the JSON schema in `src/raml/json/schema` for any vendor `+json` request body.
3. Implement the change in the matching `*Api` class.
4. Add a Drools rule for any new endpoint in [`courtscheduler-api.drl`](listingcourtscheduler-api/src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl). Requests with no matching rule are denied.

## Prerequisites

- **Java 25.** The Gradle toolchain is set to 25. Check with `java -version`.
- **Docker** (Docker Desktop or the Docker engine), needed for integration tests, repository tests (Testcontainers) and the local database.
- **Azure Artifacts credentials.** `cp-auth-rules-filter` and `cp-audit-filter-springboot` come from the HMCTS Azure Artifacts feed. Export `AZURE_DEVOPS_ARTIFACT_USERNAME` and `AZURE_DEVOPS_ARTIFACT_TOKEN` before the first build.

Always use the Gradle wrapper (`./gradlew`). It pins the Gradle version in [`gradle/wrapper/gradle-wrapper.properties`](gradle/wrapper/gradle-wrapper.properties).

## Build and test

```bash
./gradlew :listingcourtscheduler-api:bootJar                      # build the application jar
./gradlew build -x :listingcourtscheduler-integration-test:test   # compile + unit tests, no Docker stack
./gradlew :listingcourtscheduler-integration-test:test            # integration tests against a docker-compose stack
./gradlew clean build                                             # everything, including integration tests (needs Docker)
```

Run a single test class or method:

```bash
./gradlew :listingcourtscheduler-api:test --tests 'uk.gov.moj.cpp.courtscheduler.SomeTest'
./gradlew :listingcourtscheduler-api:test --tests 'uk.gov.moj.cpp.courtscheduler.SomeTest.someMethod'
./gradlew :listingcourtscheduler-integration-test:test --tests '*HearingSlotIT'
```

Notes:

- The jar is written to `listingcourtscheduler-api/build/libs/cpp-context-listing-courtscheduler-<version>.jar`. The version defaults to `0.0.999`. Override it with `-DARTEFACT_VERSION=x.y.z`.
- Tests run with the JVM time zone fixed to `Europe/London`.
- Repository tests in `listingcourtscheduler-viewstore-persistence` start PostgreSQL with Testcontainers, so they also need Docker.
- The integration `test` task builds the jar and the app image, starts [`docker-compose.integration.yml`](listingcourtscheduler-integration-test/docker/docker-compose.integration.yml), runs the tests and then stops the stack.
- Performance tests are opt-in: `./gradlew :listingcourtscheduler-integration-test:performanceTest`.

### Integration test stack

| Container | Host port | Purpose |
|---|---|---|
| `app` | `8083` | The service, with profile `integration`, authorisation and HTTP audit switched on |
| `db` | `55433` | PostgreSQL 16 |
| `wiremock` | `8189` | Stubs for Reference Data and Users & Groups. Mappings are in [`src/test/resources/wiremock`](listingcourtscheduler-integration-test/src/test/resources/wiremock) |
| `azurite` | `10011` | Azure Blob Storage emulator for rota file tests. The connection string is in [`docker/.env`](listingcourtscheduler-integration-test/docker/.env) |

Tests reach the app through the `app.baseUrl` system property and the stubs through `wiremock.baseUrl`. Rota processing is asynchronous, so rota tests poll for results.

For the test layers, the scenario checklist and how to triage defects, see the [QA and testing guide](doc/COURT_SCHEDULER_GUIDE.md#17-qa-and-testing-guide).

## Running locally

1. Start PostgreSQL. The local compose file exposes it on `localhost:55434`, with database, user and password all set to `courtscheduler`:

   ```bash
   docker compose -f listingcourtscheduler-api/docker-compose.yml up -d db
   ```

2. Optionally, start the WireMock stubs so reference-data and permission calls have something to answer them:

   ```bash
   docker compose -f listingcourtscheduler-integration-test/docker/docker-compose.integration.yml up -d wiremock
   ```

3. Run the service against them:

   ```bash
   export CSCHED_DATASOURCE_URL=jdbc:postgresql://localhost:55434/courtscheduler
   export CSCHED_DATASOURCE_USERNAME=courtscheduler
   export CSCHED_DATASOURCE_PASSWORD=courtscheduler
   export CSCHED_BASE_URL=http://localhost:8189
   ./gradlew :listingcourtscheduler-api:bootRun
   ```

4. Check that it's up: <http://localhost:8083/listingcourtscheduler-api/rest/courtscheduler/actuator/health>

Liquibase applies the database migrations on startup. Authorisation is off by default locally (`CSCHED_AUTHZ_ENABLED=false`), but requests should still send a `CJSCPPUID` header. The rota endpoints also need Azure Blob settings (see [Configuration](#configuration)).

The integration stack uses host port `55433` and the local database uses `55434`, so both can run at the same time. The app in either stack binds `8083`.

## Configuration

All settings come from [`application.yaml`](listingcourtscheduler-api/src/main/resources/application.yaml) and the per-concern files it imports: `application-datasource.yml`, `application-azure.yml`, `application-authz.yml`, `application-audit.yml` and `application-server-management.yml`. Every value can be overridden with an environment variable.

Commonly used variables:

| Variable | Default | Purpose |
|---|---|---|
| `SERVER_PORT` | `8083` | HTTP port |
| `CSCHED_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` | `jdbc:postgresql://localhost:5432/scsl` / `scsl` / `scsl` | Database |
| `CSCHED_BASE_URL` | `http://localhost:8080` | Gateway for the Reference Data and Users & Groups query APIs |
| `COURTSCHEDULER_SYSTEM_USER_ID` | — | User ID sent as `CJSCPPUID` on outbound calls |
| `CSCHED_AUTHZ_ENABLED` | `false` | Drools authorisation filter |
| `CSCHED_HTTP_AUDIT_ENABLED`, `CSCHED_AUDIT_ENABLED` | — | Audit filter |
| `AZURE_STORAGE_ACCOUNT_NAME`, `AZURE_CLIENT_ID`, `AZURE_TENANT_ID` | — | Rota blob storage through managed identity |
| `CSCHED_ROTASLSTORAGECONNECTIONSTRING` | — | Rota blob storage connection string (local and test) |
| `CSCHED_ROTASLINPUTCONTAINERNAME` / `CSCHED_ROTASLARCHIVECONTAINERNAME` | `schedulelistinginput` / `schedulelistingarchive` | Rota containers |
| `REDIS_COMMON_CACHE_ENABLED`, `REDIS_COMMON_CACHE_HOST`, `REDIS_COMMON_CACHE_PORT` | `false`, `localhost`, `6380` | Reference-data cache |
| `ROTA_MONTHS_OF_PROVISIONAL_DATA`, `ROTA_CYCLE_LENGTH_DAYS` | `6`, `28` | Provisional rota data generation |
| `LOG_LEVEL_ROOT`, `LOG_LEVEL_COURTSCHEDULER` | `WARN`, `INFO` | Log levels |

The full list is in the [Configuration](doc/COURT_SCHEDULER_GUIDE.md#14-configuration) section of the guide. In deployed environments, secrets are mounted at `/mnt/secrets/rpe/`.

## Static code analysis

CI runs PMD with the ruleset in [`.github/pmd-ruleset.xml`](.github/pmd-ruleset.xml). It is reported as a warning and does not fail the build until LPT-2318 is resolved. To run it locally, install PMD with `brew install pmd`, then:

```bash
pmd check --rulesets .github/pmd-ruleset.xml --format html -r build/reports/pmd/pmd-report.html \
  --dir listingcourtscheduler-api/src/main/java,listingcourtscheduler-common/src/main/java,listingcourtscheduler-domain/src/main/java,listingcourtscheduler-cache/src/main/java,listingcourtscheduler-rota-file-processor/src/main/java,listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java
```

CI also runs CodeQL and a secrets scanner. See [CI/CD and release](doc/COURT_SCHEDULER_GUIDE.md#18-cicd-and-release).

## Contributing

See [`.github/CONTRIBUTING.md`](.github/CONTRIBUTING.md).

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE) for details.
