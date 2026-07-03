
# Migration code-review companion

Context for reviewers: this is the WildFly + Justice Services + Maven →
Spring Boot 4 + Java 25 + Gradle migration of `cpp-context-listing-courtscheduler`.
HEAD before migration was commit `d2b4a1c79` ("Updating develop poms back to
pre merge state"). All changes are uncommitted in the working tree.

The migration mirrors the structure of `cp-court-list-publishing-service`
(the team's reference Spring Boot service). See [MIGRATION.md](MIGRATION.md)
for the high-level narrative; this file concentrates on per-file rationale
plus the non-obvious fixes that changed behaviour.

---

## 1. Files added — what and why

### 1.1 Build infrastructure

| File | Why |
|---|---|
| `settings.gradle` | Maven multi-module → Gradle multi-project; declares every retained module. |
| `build.gradle` (root, modified `AM`) | Centralises Java 25 toolchain, Spring Boot 4 BOM, JUnit 6 BOM, testcontainers BOM, Lombok, PMD, reproducible-archive defaults — all subprojects inherit. |
| `<module>/build.gradle` (one per module, all `AM` or `A`) | Per-module dependencies; replaces each module's deleted `pom.xml`. |
| `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`, `gradle/wrapper/gradle-wrapper.properties` | Gradle wrapper. The jar is force-staged via a `!gradle/wrapper/*.jar` exception in `.gitignore` so fresh checkouts can bootstrap. |
| `gradle.properties` | Gradle JVM args + library version pins (`cpAuthFilterVersion`, `cpAuditFilterVersion`, `lombokVersion`, etc.) and ported `sonar.coverage.exclusions` / `sonar.cpd.exclusions` from the old root `pom.xml`. |

### 1.2 Application bootstrap

| File | Why |
|---|---|
| `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/Application.java` | `@SpringBootApplication` entry point, replaces the WildFly WAR descriptor. |
| `application.yaml` | Top-level config: server context path, profile fragments wiring, OpenAPI generator inputs. |
| `application-audit.yml`, `application-azure.yml`, `application-server-management.yml` | New profile fragments — split from the legacy monolithic config so each concern (HTTP audit, Azurite, actuator/metrics endpoints) toggles independently. |

`application-authz.yml`, `application-datasource.yml`, `application-integration.yaml` are modifications of pre-existing files (not new), but worth knowing because authz config is non-trivial — see §3.

### 1.3 Web/Servlet plumbing (replaces legacy framework filters)

| File | Why |
|---|---|
| `config/JacksonObjectMapperConfig.java` | Configures the global `ObjectMapper` (jackson-datatype-jsr310, JakartaJsonModule, parameter-names). Replaces Justice Services `ObjectMapperProducer`. |
| `config/JakartaJsonModule.java` + `JsonValueConverter.java` | Lets Jackson serialise `jakarta.json.JsonObject` / `JsonString` / `JsonArray` natively. Without these, JSON-API types serialise as Java beans (`{"chars":"...","string":"...","valueType":"STRING"}`) — the verbose form that broke the legacy body shape. |
| `config/CorsConfig.java` | Equivalent of legacy CORS interceptor; `allowedOriginPatterns("*")` is the same default the legacy framework used. |
| `config/PrimaryHandlerMappingConfig.java` | Forces Spring's `RequestMappingHandlerMapping` to be the primary mapping over OpenAPI generator's auto-registered one — needed because both register handlers and Spring picks the first by default. |
| `config/AuditFilterConfig.java` | Wires `cp-audit-filter-springboot:1.0.5` (replaces Justice Services `LocalAuditInterceptor`). |
| `config/ActionMetricsInterceptor.java` | Restores per-action Micrometer metrics that legacy `IndividualActionMetricsInterceptor` + `TotalActionMetricsInterceptor` provided. Emits two meters: `cpp.action.total` (counter) and `cpp.action.individual` (Timer tagged with `action` and `status`). Action name resolved via the same priority chain the auth filter uses. See §3.6 for why this was non-trivial. |

### 1.4 Response envelope + error shape (preserves UI contract)

| File | Why |
|---|---|
| `envelope/EnvelopeResponseBodyAdvice.java` | Wraps response bodies with the legacy `_metadata` envelope the UI reads. Toggleable via `courtscheduler.envelope.enabled=false` once the UI confirms it can drop the wrapper. |
| `envelope/JsonEnvelopeWrapper.java` + `envelope/SkipEnvelope.java` | Marker types so controllers can opt out per endpoint (e.g. download endpoints that should not be enveloped). |
| `controllers/GlobalExceptionHandler.java` (modified `M`) | Reproduces legacy framework's error response shape: every 4xx/5xx returns `{"error":"<msg>"}` (flat, single key — the migration deliberately normalised the legacy `{"errorMessage":...}` shape; ITs assert this new shape). See §3.5. |
| `controllers/ValidationFailedException.java` | Validator-driven 400s carrying a list of error messages. |

### 1.5 Common module (replaces Justice Services framework primitives)

| File | Why |
|---|---|
| `common/JsonObjects.java` | Local replacement for the deleted `JsonObjects` helper from Justice Services. Same API surface so call sites compile unchanged. |
| `common/converter/{JsonObjectToObjectConverter,ObjectToJsonObjectConverter,StringToJsonObjectConverter}.java` | Re-implementations of the same-named Justice Services classes using `jakarta.json` + Jackson. Same API, no caller updates needed. |
| `common/service/CommonPlatformQueryClient.java` | Replaces Justice Services `Requester#requestAsAdmin(envelope)`. Issues outbound GETs against `${referencedata.base-url}` / `${usersgroups.base-url}` via `RestTemplate`, injecting `CJSCPPUID` from `COURTSCHEDULER_SYSTEM_USER_ID`. Mirrors the cp-court-list-publishing-service pattern. |
| `common/config/CourtSchedulerSystemUserConfig.java` | Reads `COURTSCHEDULER_SYSTEM_USER_ID` env var into a Spring `@Value` bean used by `CommonPlatformQueryClient`. |

### 1.6 Persistence

| File | Why |
|---|---|
| `viewstore-persistence/.../repository/CourtScheduleRepositoryCustom.java` | The five view-store repositories were all converted from DeltaSpike `AbstractEntityRepository` to Spring Data interfaces. Four of them ended up as **single-file** `interface … extends JpaRepository, …RepositoryCustom { }` because their custom logic was small enough to inline. The CourtSchedule one stays as the **classic 3-file split** because its custom `…Impl` is 2000+ lines (entity-manager-driven business logic) — colocating that with the public interface would be unreadable. The Custom interface is the contract between them. |
| `viewstore-persistence/.../service/CourtScheduleBatchInsertService.java` | Migration-specific. Isolates the batch `persist`+`flush` in `@Transactional(REQUIRES_NEW)` so a unique-index collision can't poison the caller's transaction with Hibernate's rollback-only flag. See §3.12 for the full diagnosis. |
| `viewstore-persistence/src/test/resources/application.yml` | Minimal Spring config for the persistence module's slice tests (testcontainers-driven). |
| `viewstore-liquibase/build.gradle` | Resource-only module; `java.srcDirs=[]` so no compile, only the Liquibase changelogs are packaged. Spring Boot's `spring.liquibase.change-log` config picks them up at runtime. |

### 1.7 Domain test fixtures (consolidation)

| File | Why |
|---|---|
| `listingcourtscheduler-domain/src/testFixtures/java/uk/gov/moj/cpp/platform/test/data/utils/FileUtil.java` | Single shared shim consumed by every test sourceset. Replaces FOUR drifted per-module `FileUtil.java` copies (one in api, common, rota-file-processor, integration-test) plus the legacy Justice Services library shim. Wired via the `java-test-fixtures` Gradle plugin; consumers depend on `testFixtures(project(':listingcourtscheduler-domain'))`. |

### 1.8 Integration-test infrastructure

The new `listingcourtscheduler-integration-test` module is a fresh test
sourceset that drives the bootJar via Docker Compose (Postgres 16 +
Wiremock 3.9 + Azurite + the api container). All 17 IT classes live there.

| File | Why |
|---|---|
| `AbstractIntegrationTest.java` | New Spring-style base class for *modern* IT classes (those built fresh during the migration). Uses `RestTemplate` against `app.baseUrl`. Co-exists with the legacy `AbstractIT.java` (`AM`) which is the `*IT.java` re-platformed shim — kept so the 11 legacy IT classes need minimal edits. |
| `AdditionalEndpointsIntegrationTest.java`, `EnvelopeAndErrorShapeIntegrationTest.java`, `SmokeIntegrationTest.java` | New ITs added during the migration to lock the OpenAPI contract, the response-envelope shape, the error-body shape, and a smoke baseline. Not in the legacy suite. |
| `utils/RequestParams.java` | Builder for parameterised GETs — replaces the legacy `RestClient`'s fluent API. |
| `utils/RestPoller.java` | Awaitility-based polling helper for async endpoints (e.g. `POST /rotasl/process-rota-files` is `@Async`). |

### 1.9 WireMock stubs (test infrastructure)

| File | Why |
|---|---|
| `wiremock/mappings/identity-denied-user.json` | Stub for the negative-auth user used by `AuthorizationIntegrationTest`. Returns empty `permissions[]` so the auth filter denies. |
| `wiremock/mappings/referencedata-*.json` (6 files) | Stubs the upstream `referencedata-query-api` responses — court rooms, business types, judiciaries, public holidays, courtroom-session-allocations, courtroom-mappings. The legacy ITs spoke to a separately-spun-up Justice Services framework instance; the new ITs run as a self-contained docker-compose stack. |
| `wiremock/__files/referencedata.judiciaries.json` | Body file referenced from one of the stub mappings. |

The two **modified** identity stubs (`identity-court-schedule-user.json`,
`identity-system-user.json`) are PascalCase per §3.2.

### 1.10 Diagnostic tests (untracked)

| File | Why |
|---|---|
| `listingcourtscheduler-api/src/test/java/.../UserPermissionDeserialisationTest.java` | Single-method test verifying the cp-auth-rules-filter `UserPermission` record can be deserialised from the partial JSON `{"object":"…","action":"…"}` that `PermissionConstants` emits. Authored during a debugging session to rule out Jackson record-handling as the cause of repeated 403s. Worth keeping as a regression test. |
| `listingcourtscheduler-api/src/test/java/.../AuthzMatchReproducerTest.java` | End-to-end (in-JVM) reproducer of the cp-auth-rules-filter `RequestUserAndGroupProvider.hasPermission` chain. Constructs an `AuthzPrincipal` with the same permissions the WireMock stub serves and asserts `hasPermission` returns true. Functions as a guardrail against future casing drift between `PermissionConstants` and the stub. |

Both files are **untracked (`??`)** and not yet `git add`ed; reviewers can
choose to keep or drop them. Recommendation: keep — they're cheap and
catch a class of bug we hit twice.

---

## 2. Files deleted — why each can go

### 2.1 Maven build files (replaced by Gradle)

- `pom.xml` (root) and one per module (`api`, `cache`, `common`, `domain`,
  `healthchecks`, `integration-test`, `rota-file-processor`,
  `viewstore`, `viewstore-jdbc`, `viewstore-liquibase`,
  `viewstore-persistence`).
- `runIntegrationTests.sh` — superseded by `:integration-test:test` which
  internally runs `composeUp` → tests → `composeDown`.

### 2.2 CDI / EJB / WildFly framework descriptors

- `META-INF/beans.xml` (one per module). CDI bootstrap descriptor — Spring
  uses component scanning + autoconfiguration instead.
- `META-INF/persistence.xml` (viewstore-persistence). Spring Boot
  autoconfigures the `EntityManagerFactory` from `application-datasource.yml`.
- `META-INF/kmodule.xml` (api). Drools KIE module descriptor — not used by
  `cp-auth-rules-filter`, which loads DRLs via classpath glob.
- `META-INF/services/org.junit.platform.launcher.TestExecutionListener`
  (integration-test) — Justice Services test listener; not needed.

### 2.3 WildFly-specific Docker / shell

- `docker/Dockerfile_courtscheduler-service` — WildFly base image; replaced
  by `listingcourtscheduler-api/Dockerfile` (`AM`) layered on
  `eclipse-temurin:25-jdk` + the bootJar.
- `docker/scripts/liquibase.sh` — manual Liquibase invocation; replaced by
  `spring-boot-liquibase` autoconfig + `SPRING_LIQUIBASE_*` env vars in the
  IT compose stack.

### 2.4 Whole modules dropped

- **`listingcourtscheduler-healthchecks`** — Justice Services
  `CourtSchedulerDatabaseHealthcheck` and
  `CourtSchedulerIgnoredHealthcheckNamesProvider` are functionally replaced
  by Spring Boot Actuator's built-in `db` healthcheck and the
  `management.endpoint.health.*` config. Unit tests for those classes were
  also deleted.
- **`listingcourtscheduler-jdbc`** — `CourtSchedulerDataSourceProvider` was a
  Justice Services-specific provider; Spring Boot's
  `spring-boot-starter-data-jpa` autoconfigures the same DataSource from
  the standard `spring.datasource.*` properties.

### 2.5 Per-module `FileUtil.java` duplicates

- `listingcourtscheduler-api/src/test/java/.../api/utils/FileUtil.java`
- `listingcourtscheduler-common/src/test/java/.../common/utils/FileUtil.java`
- `listingcourtscheduler-rota-file-processor/src/test/java/.../rotafileprocessor/utils/FileUtil.java`
- `listingcourtscheduler-integration-test/src/test/java/.../integration/utils/FileUtil.java`

Four near-duplicates with subtly different error-handling, all consolidated
into the single test-fixtures shim under
`listingcourtscheduler-domain/src/testFixtures/java/uk/gov/moj/cpp/platform/test/data/utils/FileUtil.java`.
12 caller files rewritten to import the new package.

### 2.6 Replaced framework chains

- `listingcourtscheduler-api/src/main/java/.../api/CourtSchedulerApiInterceptorChainProvider.java`
  — Justice Services `InterceptorChainEntryProvider` that wired the
  legacy `IndividualActionMetricsInterceptor` + `TotalActionMetricsInterceptor`
  + `LocalAuditInterceptor` + `LocalAccessControlInterceptor`. The four
  interceptors are replaced by, respectively:
  - `ActionMetricsInterceptor` (new — see §1.3)
  - `cp-audit-filter-springboot` (third-party JAR + `AuditFilterConfig`)
  - `cp-auth-rules-filter` (third-party JAR + `application-authz.yml` + the DRL)
  - The chain itself is replaced by Spring's filter chain ordering
    (`Ordered.HIGHEST_PRECEDENCE + 30` for authz).

---

## 3. Complex / interesting fixes

These are the changes where the diff alone won't tell the whole story.

### 3.1 View-store repository transactional boundaries

**Why interesting:** the very first IT run after the repository conversion
ballooned from 6 min to 22 min, then later showed 12 hard 403/500 failures.
The cause was a transactional-mode mistake in `CourtScheduleRepositoryImpl`.

**Background.** DeltaSpike's `AbstractEntityRepository` had implicit
per-method `@Transactional` via a CDI interceptor. Spring Data fragment
`…Impl` classes don't get that automatically. After the conversion, every
`entityManager.merge|persist|remove|executeUpdate` call ran without an
active transaction — `TransactionRequiredException` on writes.

**Naïve fix attempted:** annotate the `@Component class CourtScheduleRepositoryImpl`
with class-level `@Transactional` (read-write). This made it work but
**every read** (e.g. `getCourtSchedulesBy`, `searchListHearingSlotFilterCriteria`,
the 30+ native queries that build hearing slot rows) opened a write
transaction. Hibernate then ran the dirty-check + auto-flush cycle on
every query. Connections were held longer; the IT runtime exploded.

**Final shape:**
- Class-level `@Transactional(readOnly = true)` on `CourtScheduleRepositoryImpl`.
  Reads stay read-only; Hibernate skips dirty-checks and auto-flush.
- Method-level `@Transactional` (read-write) on every public method that
  actually mutates: `update(*)`, `saveCourtSchedules`, `saveBookedSlots`,
  `searchBookHearingSlots`, `updateListHearingSlots`, `deleteCourtSchedule`,
  `deleteUnAllocatedCourtScheduleEntriesForRotaPeriod`,
  `deleteUnAllocatedProvisionalEntries`, `deleteSlots`,
  `releaseAllocatedSlotsOrDurationFromCourtSchedule`,
  `releaseOldAllocatedListings`, `getInconsistentCourtSchedulersByOucode`,
  `searchListHearingSlotFilterCriteria` *(this one mutates via a
  `saveInternal` call deep in the method, not obvious from the name)*,
  `deleteRedundantRotaData`.
- The two `protected` helpers (`saveAllocatedListing`, `deleteProvisionalBooking`)
  also carry method-level `@Transactional` because they're invoked outside
  the class proxy boundary.

If a reviewer adds a new mutating method and forgets the annotation, it
will fail at runtime with `TransactionRequiredException`. There's no
test that catches "missing @Transactional" generically — keep an eye out.

### 3.2 Permission-key casing alignment (production parity, not invention)

**Why interesting:** the migration originally flipped permission keys
from PascalCase to UPPERCASE (`COURT_SCHEDULE` / `CREATE` / `READ`) on
both the production code (`PermissionConstants`) and the WireMock stubs.
The audit reverted `PermissionConstants` back to the legacy PascalCase
(`CourtSchedule` / `Create` / `View`) because the production
`usersgroups-query-api` returns PascalCase — the legacy WildFly app
authenticates against it successfully, so flipping PermissionConstants
would have silently broken auth in prod.

**The catch:** the test stubs serve PascalCase from disk (good), but the
test bootstrap (`StubUtil.setupLoggedInUsersPermissionQueryStub` /
`setupUserAsSystemUser`) overrides them at runtime via
`/__admin/mappings` POST + a body referenced from
`usersgroups.user-permissions.json` — and **those payloads still served
UPPERCASE**. The disk-based stubs were dead.

**Fixed in three places:**
- `PermissionConstants.java` — PascalCase JSON literals (`CourtSchedule`/`Create`/`View`)
- `wiremock/mappings/identity-court-schedule-user.json`,
  `identity-system-user.json` — disk fallback stubs in PascalCase
- `usersgroups.user-permissions.json` — the dynamic-stub payload (15 entries)
  + the inline JSON in `StubUtil.setupUserAsSystemUser` — both flipped to
  PascalCase
- The two unit-test fixtures `create-court-schedule-permission.json` /
  `get-court-schedule-permission.json` consumed by `PermissionConstantsTest`
  — also flipped

`READ` → `View` (not `Read`) is the legacy spelling preserved from HEAD's
`PermissionConstants`. Note this when reviewing.

### 3.3 DRL action-name vs vendor-MT alignment

**Why interesting:** the migration's `cp-auth-rules-filter` resolves the
Drools `Action.name` via this priority chain (lowercased to ROOT locale
afterwards):

1. Explicit `CPP-ACTION` request header
2. Vendor segment of `Content-Type` (`application/vnd.<token>+json` → `<token>`)
3. Vendor segment of `Accept`
4. `"<METHOD> <PATH>"` fallback

With `application-authz.yml: deny-when-no-rules: true`, **any request
whose action doesn't match a DRL rule returns 403**. The migration
deliberately removed the legacy `CppActionResolverFilter` /
`JudiciaryActionResolverFilter` because the priority chain above
subsumes them.

Two consequences worth knowing:

- The DRL rule for the GET endpoint matches `name == "courtscheduler.get"`
  *(not `courtscheduler.get.court_schedule`)* — because the OpenAPI spec
  declares the request media type as `application/vnd.courtscheduler.get+json`,
  the resolver extracts `courtscheduler.get`. An audit-pass attempt to
  "correct" this rule to `…get.court_schedule` was the cause of 5
  separate IT 403s before being re-reverted. Lesson: the rule must
  match the *vendor token*, not the legacy CDI action name.
- `/judiciaries/availability*` endpoints negotiate as plain
  `application/json` (no vendor MT). The DRL rules for these match on
  the synthesised `<METHOD> <PATH>` fallback — e.g.
  `Action(name == "GET /judiciaries/availability-rules")`. Looks
  unusual, intentional. UI clients are not required to send `CPP-ACTION`
  for these.

The full rule-vs-MT cross-walk passed audit: every vendor-token rule
matches a real MT in the OpenAPI or an IT, and every MT used by
the OpenAPI/ITs has a corresponding rule. No latent 403s.

### 3.4 Action-resolver filters described in MIGRATION.md don't exist

The earliest migration scaffold included two `*ActionResolverFilter`
classes that were later deleted because `cp-auth-rules-filter` does the
same job natively. The filters are still mentioned in `MIGRATION.md` only
in places where the file describes the deletion ("instead of the
filter, the auth-rules-filter resolves Action.name from…"). No code
references them.

### 3.5 Error-response body shape normalisation

**Legacy framework:** validator-driven 400s returned the
`BadRequestException`'s `JsonObject` directly, which serialised as
`{"errorMessage":"<msg>"}` (flat) via the legacy framework's JSON-API
writer.

**Migration choice:** every 4xx/5xx body is now a flat
`{"error":"<msg>"}` map. The migrator wrote ITs that explicitly assert
this shape (see `CourtSchedulerIT.shouldGet400IfAllDaySplitFlagMissingToCreateDurationBasedSchedule`
and several siblings). The audit briefly returned the JsonObject
unchanged through Spring's MessageConverter — that produced the verbose
Jackson-bean form `{"errorMessage":{"chars":"…","string":"…","valueType":"STRING"}}`,
which broke the new ITs.

**Final shape** (in `GlobalExceptionHandler.handleValidationException`):
extract `errorMessage` from the validator's JsonObject (a `JsonString`)
and wrap in `errorBody(List.of(extracted))` so it shares the
`{"error":"<msg>"}` shape with every other 400 in the handler.

`PersistenceStoreException` and `IllegalArgumentException` go through
the same `errorBody`. `IllegalStateException` is **deliberately not
mapped** (falls through to `handleAny` → 500) because the only
`IllegalStateException` throw sites in the codebase are config / IO
errors — `CourtSchedulerSystemUserConfig`, `CommonPlatformQueryClient`,
the JsonObject converters — not user input. The single validator-side
throw in `JudiciaryUnassignmentService` was changed to throw
`IllegalArgumentException` (already 400) so this assignment holds.

### 3.6 Per-action metrics replacement

Justice Services' `IndividualActionMetricsInterceptor` +
`TotalActionMetricsInterceptor` emitted per-action metrics with the
action name as a dimension. The migration replaces them with a
`HandlerInterceptor` that:

- Records `cpp.action.total` (counter) — equivalent of
  `TotalActionMetricsInterceptor`
- Records `cpp.action.individual` (Timer tagged with `action` and `status`)
  — equivalent of `IndividualActionMetricsInterceptor`
- Resolves the action name via the same priority chain `cp-auth-rules-filter`
  uses (CPP-ACTION → vendor MT in Content-Type → vendor MT in Accept →
  `<METHOD> <PATH>` fallback) so dashboards built against the legacy
  metrics dimension still match.

The interceptor is registered as a `WebMvcConfigurer`. It silently
catches recording failures so a misconfigured `MeterRegistry` can't
break the request path.

### 3.7 Repository style — preserved DeltaSpike convention

Each Spring Data repository keeps the legacy DeltaSpike "method-name
+ `@Query` annotation" style on the public interface (see e.g.
`AllocatedListingRepository`, `CourtScheduleJudiciaryRepository`). An
earlier conversion attempt rewrote these as `entityManager.createQuery`
boilerplate inside an Impl class — the team's convention is the
declarative Spring Data form, so the conversion was redone to match.

The CourtSchedule repo is the **only one with a 3-file split** (`Repository`
interface + `RepositoryCustom` interface + `RepositoryImpl` class) because
its custom Impl is 2000+ lines. The other four use the single-file
`interface … extends JpaRepository` form.

For backwards compatibility with legacy call sites, every repo provides
`default` aliases: `findBy(K)` (returns `null` if absent),
`remove(entity)`, sometimes `removeAndFlush(entity)`, `merge(entity)`.

### 3.8 Mismatched method signature changes — `findByCourtRoomIdAnd…CourtSession`

The legacy DeltaSpike repo had `findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession`
returning a single optional row (DB-side `LIMIT 1`). Spring Data's
`@Query` cannot mix a constructor projection with `Optional`/single
return. Solution: split into

- A public method `findMatcherInfoBy…(…, Pageable pageable)` returning
  `List<CourtScheduleMatcherInfo>`,
- A `default` wrapper preserving the legacy 4-arg signature, passing
  `PageRequest.of(0, 1)` so the LIMIT is still pushed to the DB.

Behavioural equivalence preserved.

### 3.9 SessionsService.update validation enhancement

The legacy `SessionsService.update` only validated min/max-hearing-time
when both `sessionStartTime` and `sessionEndTime` were present in the
update payload — leaving the door open for an "update other fields, but
the persisted session times still violate the hearing time" scenario.

The migration added a fall-back-to-persisted branch that applies the
validation unconditionally:

```java
final Date sessionStartTimeWithDate = StringUtils.isNotEmpty(updateCourtSchedule.getSessionStartTime())
        ? combineDateAndTime(persisted.getSessionDate(), update.getSessionStartTime())
        : persisted.getSessionStartTime();
// …same for end…
if (sessionStartTimeWithDate != null && earliestHearingStartTime.isPresent()
        && sessionStartTimeWithDate.after(earliestHearingStartTime.get())) {
    return new Result(SESSION_START_TIME_CANNOT_BE_CHANGED_TO_AFTER_HEARING_TIME, false);
}
```

A new IT explicitly tests this scenario
(`shouldGet400WhenUpdatingCourtScheduleWithNullSessionTimesAndMinHearingTimeAfterSessionStart`).
The audit briefly reverted this to legacy gating; the test caught it.

### 3.10 Phantom-state cleanup

Earlier-session work left the index in a confusing shape: ~135 files
in "added but missing on disk" (`AD`) state — mostly under an
abandoned `listingcourtscheduler-springboot/` directory from a first
migration attempt, plus a handful of stale early-Spring controller
files, an early CDI filter, and three duplicate `FileUtil.java`s
already consolidated. All cleared via `git rm --cached`. None of them
held logic missing from the new layout (verified per-area in audit).

### 3.11 Two diagnostic tests worth keeping

`UserPermissionDeserialisationTest` and `AuthzMatchReproducerTest` were
written to debug a particularly tedious 92-failure IT cascade and pin
down which layer was misbehaving. They're cheap (~30 lines each), exec
in milliseconds, and would catch any future regression of either:

- The `UserPermission` record being un-deserialisable from a partial
  JSON (e.g. if cp-auth-rules-filter ever drops the `-parameters`
  compile flag), or
- The `PermissionConstants` JSON literals drifting away from the WireMock
  stubs.

The two failure modes they check are exactly the two we hit in this
migration. Recommend `git add` and merge.

### 3.12 Rollback-only contamination in `saveCourtSchedules`

| File | Change |
|---|---|
| `service/CourtScheduleBatchInsertService.java` | **New.** Single method `persistBatch(List<CourtSchedule>)` annotated `@Transactional(propagation = REQUIRES_NEW)`. Wraps Hibernate's `entityManager.persist(...)` + `flush()` in an isolated transaction. |
| `repository/CourtScheduleRepositoryImpl.java` | `processOptimizedBatches` no longer calls `entityManager.persist`/`flush` directly. It now delegates each batch to `courtScheduleBatchInsertService.persistBatch(...)` via the proxy; on failure (constraint violation), it falls through to the existing per-record `processIndividualRecordsFast` retry path. The old `flushBatchOptimized` helper is gone. |
| `repository/SaveCourtSchedulesDuplicateTest.java` | **New.** `@DataJpaTest` against Testcontainers Postgres reproducing priming's HTTP 500. Asserts that a second `saveCourtSchedules` whose natural key already exists must commit cleanly, not throw. |
| `repository/AbstractRepositoryTest.java` | `@ComponentScan` now also includes `uk.gov.moj.cpp.courtscheduler.service` so the retry / batch-insert services are resolvable inside `@DataJpaTest` contexts. |

**Bug** (manifested in priming logs, root cause traced from `kubectl logs`):
the legacy WildFly code's `upsertOne` / `processOptimizedBatches`
implements an upsert as **try-insert, catch unique-constraint-violation,
then update**. The batch persist+flush ran inside the caller's
`@Transactional` boundary. Under EJB CMT this was tolerated (catching
the application exception didn't propagate the rollback-only flag the
same way). Under Spring's `@Transactional` proxy + Hibernate 7's JPA
contract, the `flush()` failure marks the surrounding JPA transaction
**rollback-only at flush-time** — before the catch block runs.
`processIndividualRecordsFast` then recovers each record cleanly via
`@Transactional(REQUIRES_NEW)` on `upsertOne`, but the outer transaction
is already poisoned. Spring's interceptor refuses to commit a
rollback-only tx → `UnexpectedRollbackException` → HTTP 500.

**Important:** the entire upsert-by-exception pattern (`upsertOne`,
`retryAndSave`, `processIndividualRecordsFast`, the recovery flow) is
inherited verbatim from legacy `origin/main`. The migration did not
introduce it. The bug was always latent — Spring's stricter
rollback-only enforcement surfaced it.

**Fix shape (taken):** isolate just the batch `persist`+`flush` in a
`REQUIRES_NEW` transaction. If it fails, only that inner tx is rolled
back — the caller's outer transaction stays committable, and the
existing per-record retry path (already on `REQUIRES_NEW` via a separate
bean) resolves the collision by updating the existing row.

**Fix not taken (deliberately, for now):** replace the
exception-as-control-flow upsert with Postgres-native
`INSERT … ON CONFLICT (oucode, court_room_id, rota_business_type,
session_start, court_session) WHERE active DO UPDATE …`. That removes
~200 lines (`CourtScheduleRetryService`, `CourtScheduleBatchInsertService`,
half of `CourtScheduleRepositoryImpl`'s batch machinery) and is
race-safe under the concurrent priming load. Tracked as a follow-up in
§4. The current REQUIRES_NEW fix unblocks priming today without
rewriting the persistence path.

`Session.upsert(entity)` (Hibernate 6.4+ / 7.x) is **not** the right
primitive for this schema: it resolves conflicts on the PK, but the
collision here is on a partial unique index over a non-PK natural key.
HQL or native `INSERT … ON CONFLICT` is the only conflict-target-aware
upsert option.

---

## 4. Out-of-scope but worth flagging

These were noted by the second-pass audit but **deliberately left
alone** — neither regressions nor migration-introduced. Reviewers may
want to track them as separate cleanup items.

- `application.yaml: spring.main.allow-circular-references: true` —
  band-aid for the `CourtScheduleRepository ↔ CourtScheduleRetryService`
  cycle (pre-existing under DeltaSpike, tolerated by Spring 4 only via
  this flag). Should be untangled in a follow-up.
- `CorsConfig` uses `allowedOriginPatterns("*")` + `allowCredentials(true)`
  — same as legacy framework default; the team should pin origins
  before prod hardening.
- `ProvisionalBookingRepository.saveProvisionalBooking` switched from
  DeltaSpike's `save()` (auto-merge) to bare `entityManager.persist`.
  Single caller passes a fresh UUID, so safe today; would EntityExistsException
  if a caller ever reuses a key.
- `SecurityGroupConstants.SYSTEM_USERS = "SYSTEM_USERS"` — invented
  during the migration to model the legacy `isSystemUser($action)` check
  as group membership. The WireMock stub matches; whether prod's
  `usersgroups-query-api` returns a group of exactly that name for
  legacy "system users" is a separate ops question.
- DRL eval-condition perf warnings + DRL package-vs-folder warning —
  informational, no functional impact.
- Two DRL rule labels (`drl:28`, `drl:65`) don't match their `name == `
  constraint string. Display-only. Cosmetic.
- §3.12 follow-up: replace the exception-as-control-flow upsert in
  `CourtScheduleRepositoryImpl` with native Postgres
  `INSERT … ON CONFLICT (…) WHERE active DO UPDATE`. Removes
  ~200 lines (`CourtScheduleRetryService`, `CourtScheduleBatchInsertService`,
  the batch/individual retry chain), eliminates the rollback-only
  failure class entirely, and is race-safe under concurrent priming.
  Estimated 2–4 days. Hibernate 7's `Session.upsert` is not applicable
  here because the conflict target is a non-PK partial unique index.

---

## 5. Reviewer checklist

- [ ] Every deleted file matches an entry in §2.
- [ ] Every new file matches an entry in §1.
- [ ] §3.1 — when adding a new public method to
      `CourtScheduleRepositoryImpl`, does it need `@Transactional`?
      (yes if it persist/merge/remove/executeUpdate / calls `saveInternal`).
- [ ] §3.2 — any new permission JSON / WireMock stub uses PascalCase
      values consistently.
- [ ] §3.3 — any new endpoint uses a vendor MT that has a corresponding
      DRL rule (or a `<METHOD> <PATH>` rule for plain `application/json`
      endpoints).
- [ ] §3.5 — any new `@ExceptionHandler` returns the `errorBody(...)` shape.
- [ ] §3.7 — any new repository method follows the method-name + `@Query`
      convention, not the entityManager.createQuery boilerplate.
- [ ] §3.12 — any new write path that may collide on a unique index
      runs inside its own `@Transactional(REQUIRES_NEW)` (not the
      caller's tx), or uses a real upsert (`INSERT … ON CONFLICT`).
      Do **not** add new `try persist+flush / catch ConstraintViolation`
      blocks in shared transactions.
- [ ] No new file under `META-INF/` (no CDI / persistence.xml descriptors).
- [ ] No new dependency on Justice Services artefacts.
