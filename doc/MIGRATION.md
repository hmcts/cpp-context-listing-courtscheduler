# listingcourtscheduler-api — Spring Boot migration

The `listingcourtscheduler-api` module is a from-scratch Spring Boot 4 / Java 25 /
Gradle port of the existing WildFly-deployed `listingcourtscheduler-api` WAR. It
deliberately mirrors the structure of
`~/devenv/project/msjs/cp-court-list-publishing-service` so the team has a single
shared shape for CPP-on-Spring-Boot services. The Spring Boot application
(`Application.java`, controllers, config) lives in `listingcourtscheduler-api`;
the other `listingcourtscheduler-*` modules are pulled into its source set (see
"Legacy module migration" below).

## REST API contract — preserved

| Aspect | Value |
|---|---|
| Context path | `/listingcourtscheduler-api/rest/courtscheduler` (set in `application.yaml` via `server.servlet.context-path`) |
| Paths/verbs/media types | Defined in [`src/main/resources/openapi/courtscheduler-api.openapi.yml`](src/main/resources/openapi/courtscheduler-api.openapi.yml). The OpenAPI generator emits Spring controller interfaces under `build/generated/openapi/src/main/java`; controllers in `controllers/` implement them. |
| Response `_metadata` envelope | Reproduced by [`EnvelopeResponseBodyAdvice`](src/main/java/uk/gov/moj/cpp/courtscheduler/envelope/EnvelopeResponseBodyAdvice.java). Toggle off via `courtscheduler.envelope.enabled=false` once the UI confirms it does not read `_metadata`. |
| Error response shape | [`GlobalExceptionHandler`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/GlobalExceptionHandler.java) maps most failures to `{"error":"<msg>"}` (single message, or messages joined with `"; "`) — matching the legacy WildFly body. The 422 `UnprocessableEntity` path preserves the validator's `{"errors":{...},"timestamp":...}` body. |
| Auth headers | `CJSCPPUID` is the only header the UI is required to send. The legacy framework derived `CPP-ACTION` from the request media type internally; in the Spring Boot port `cp-auth-rules-filter:1.0.9` resolves the Drools `Action.name` natively — from an explicit `CPP-ACTION` header, else the vendor-token of the request media type, else a `"<METHOD> <PATH>"` fallback for plain-`application/json` endpoints. The Drools rules in `courtscheduler-api.drl` therefore match on `Action(name == ...)` in both cases: the vendor-token action for vendor-MT endpoints, and the `"<METHOD> <PATH>"` action for the `/judiciaries/*` family that uses `application/json`. No bridge filter is needed; `authz.http.action-required: false` in `application-authz.yml` lets the filter run rule evaluation even when no `CPP-ACTION` header is supplied. (See "Auth filter — handling `/judiciaries/*`" below for detail.) |

## Auth and audit — no Spring Security

* `cp-auth-rules-filter:1.0.9` (declared in `gradle.properties` → `cpAuthFilterVersion`). Drools rules for every legacy action live in [`src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl`](src/main/resources/uk.gov.moj.cpp.courtscheduler.api.accesscontrol.drl/courtscheduler-api.drl). Imports follow the cp-court-list-publishing-service convention (`uk.gov.moj.cpp.authz.*`); confirm against the actual 2.0.0 artifact's published packages.
* `cp-audit-filter-springboot:1.0.5`. Configured by [`AuditFilterConfig`](src/main/java/uk/gov/moj/cpp/courtscheduler/config/AuditFilterConfig.java), driven by the OpenAPI spec.

## What is done in this scaffold

- Gradle build + Spring Boot 4 + Java 25 (`build.gradle`)
- `Application.java`, `application.yaml`, profile fragments for `datasource`, `authz`, `audit`, `azure`, `server-management`
- OpenAPI 3 spec covering every legacy endpoint from `listingcourtscheduler-api/src/raml/courtscheduler-api.raml`, including all vendor media types
- Skeleton `CourtScheduleController` implementing two of the generated interfaces, to lock the contract
- `EnvelopeResponseBodyAdvice` + `GlobalExceptionHandler` + `ValidationFailedException` to preserve UI-facing JSON shapes
- Drools rules ported from the existing `.drl` to the new `acl/` location, with rules for `/judiciaries/*` endpoints rewritten to match on `Action.attributes["method"]/["path"]` (since those endpoints negotiate plain `application/json` and have no vendor-MT-derived `Action.name`)
- `PermissionConstants` placeholder

## Legacy module migration — done in place

The Spring Boot module pulls these legacy modules' sources directly via Gradle
source sets — no copying — and the source files have been edited where required
to retire javax.* / EJB / CDI / DeltaSpike usage:

| Module | Status | Notes |
|---|---|---|
| `listingcourtscheduler-domain` | ✅ Plugged in (zero edits) | Pure POJOs + Jackson annotations; no `javax.*`, no framework deps. |
| `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-liquibase` | ✅ Resources only | `spring.liquibase.change-log` points at the existing changelog xml. |
| `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence` | ✅ In-place migrated | `javax.persistence` → `jakarta.persistence`; `javax.transaction.Transactional` → `org.springframework.transaction.annotation.Transactional`; `@Stateless` → `@Service`; DeltaSpike `AbstractEntityRepository`/`@Query`/`@QueryParam` → plain Spring `@Repository` classes/interfaces using `EntityManager` directly. JPA static metamodel is regenerated from `@Entity`s by `hibernate-jpamodelgen` annotation processor. |
| `listingcourtscheduler-cache` | ✅ In-place migrated | `@Stateless` → `@Service`; Justice `@Value(key=, defaultValue=)` → Spring `@Value("${...:default}")` bound to `redis.common-cache.*` in `application.yaml`. |
| `listingcourtscheduler-common` | ✅ In-place migrated | `Requester#requestAsAdmin(envelope)` call sites in `ReferenceDataService` rewritten as explicit GETs against `${referencedata.base-url}` via [`CommonPlatformQueryClient`](src/main/java/uk/gov/moj/cpp/courtscheduler/config/CommonPlatformQueryClient.java) using `RestTemplate` with the system user UUID injected as `CJSCPPUID` (mirrors cp-court-list-publishing-service pattern). The `Requester` parameter dropped from every public method signature; pass-through callers updated. `StringToJsonObjectConverter` / `ObjectToJsonObjectConverter` / `JsonObjectToObjectConverter` re-implemented in place against `jakarta.json` + Jackson; `ObjectMapperProducer().objectMapper()` → Spring `ObjectMapper` bean. `JsonObjects` helper provided locally in the common module. `@Transactional(TxType.X)` → `@Transactional(propagation = Propagation.X)`. |
| `listingcourtscheduler-rota-file-processor` | ✅ In-place migrated | `Requester` parameter removed from `RotaFileProcessorService`, `RotaFilePartialProcessor`, `CourtScheduleEnricher`, `JudiciaryScheduleEnricher`, `RotaDataEnricher`. `@Stateless` → `@Service`; `@TransactionAttribute` → `@Transactional`; javax → jakarta. |

Build wires every legacy module into the source set:
```groovy
sourceSets.main.java.srcDirs += [
        file("$legacyRoot/listingcourtscheduler-domain/src/main/java"),
        file("$legacyRoot/listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java"),
        file("$legacyRoot/listingcourtscheduler-common/src/main/java"),
        file("$legacyRoot/listingcourtscheduler-rota-file-processor/src/main/java"),
        file("$legacyRoot/listingcourtscheduler-cache/src/main/java"),
]
```

System user UUID is read from `courtscheduler.system-user-id` (env: `COURTSCHEDULER_SYSTEM_USER_ID`) and used by [`CommonPlatformQueryClient`](src/main/java/uk/gov/moj/cpp/courtscheduler/config/CommonPlatformQueryClient.java) as the `CJSCPPUID` header on every outbound HTTP call to other CPP context-services.

## What is still TODO

1. **OpenAPI schemas** are placeholder `PassthroughObject`s (still ~40 `$ref`s to `PassthroughObject` in `courtscheduler-api.openapi.yml`). Replace each with the JSON Schema files under `listingcourtscheduler-api/src/raml/json/schema/` (41 schema files are present and ready to wire in). Note this is a strengthening step only — the passthrough contract is already client-compatible, so it does not block deployment.

## Done since the original scaffold

- **Legacy IT classes re-platformed to `RestTemplate`** — `AbstractIT` was migrated in place from `extends RestClient` (Justice Services test framework, `localhost:8080`) to Spring `RestTemplate` against the `app.baseUrl` system property set by the Gradle `integration` task. All 16 non-abstract test classes now extend either the re-platformed `AbstractIT` (the ported `*IT` classes) or the new-style `AbstractIntegrationTest` (the `*IntegrationTest` classes). The only remaining Justice-services references in the test tree are Javadoc comments describing what each helper replaced.
- **`ReferenceDataService` outbound URLs/media types cross-checked** — the paths and Accept headers in `ReferenceDataService` have been reconciled against the upstream `referencedata-query-api` RAML, with inline comments recording the non-obvious cases (`application/vnd.reference-data.judiciaries+json` — hyphenated `reference-data`, no `query` segment, verified against the referencedata-query-api RAML; `application/vnd.referencedata.ou-courtrooms+json` for `/courtrooms`). Re-verify only if the upstream referencedata-query-api contract changes.

## Current controller wiring

| Path | Controller | Backing service(s) |
|---|---|---|
| `POST /courtschedule` | [`CourtScheduleController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/CourtScheduleController.java) | `SessionsService.create` |
| `GET /courtschedule` | `CourtScheduleController` | `SessionsService.getCourtSchedules` |
| `POST /courtschedule/edit` | `CourtScheduleController` | `SessionsService.update` |
| `POST /courtschedule/delete` | `CourtScheduleController` | `SessionsService.deleteCourtScheduleSessions` |
| `POST /courtschedule/assign.courtroom` | `CourtScheduleController` | `SessionsService.assignCourtroom` |
| `GET /courtschedule/search.court-schedules-by-id` | `CourtScheduleController` | `SessionsService.getCourtSchedulesById` |
| `PUT /hearingslots` | [`HearingSlotsController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/HearingSlotsController.java) | `SlotsUpdateService.update` |
| `GET /hearingslots` | `HearingSlotsController` | `SlotsSearchService.search` |
| `DELETE /hearingslots/{hearingId}` | `HearingSlotsController` | `SlotsRemoveService.remove` |
| `PUT /list/hearingslots` | `HearingSlotsController` | `SlotsUpdateService.listHearingSlots` |
| `PUT /searchupdate/hearingslots` | `HearingSlotsController` | `SlotsUpdateService.searchUpdate` |
| `GET /searchlist/hearingslots` | `HearingSlotsController` | `SlotsUpdateService.searchAndBook` |
| `POST /provisionalBooking` | [`ProvisionalBookingController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/ProvisionalBookingController.java) | `ProvisionalBookingService.bookProvisionalSlots` |
| `GET /provisionalBooking` | `ProvisionalBookingController` | `ProvisionalBookingService.fetchProvisionalSlots` |
| `GET /mi/court_schedules` | [`MiController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/MiController.java) | `MiService.getCourtSchedules` |
| `GET /mi/court_schedule_judiciaries` | `MiController` | `MiService.getCourtSchedulesJudiciary` |
| `GET /mi/allocated_listings` | `MiController` | `MiService.getAllocatedListings` |
| `POST /oucode/migrate` | [`OuCodeController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/OuCodeController.java) | `SessionsService.migrateOuCodes` |
| `POST /oucode/recalculate-availability` | `OuCodeController` | `SessionsService.ouCodesRecalculateAvailability` |
| `POST /rotasl/process-rota-files` | [`RotaslController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/RotaslController.java) | `RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach` (`@Async`) |
| `POST /rotasl/clean-redundant-rota-data` | `RotaslController` | `RotaRedundantDataCleanerService.cleanDataForPreviousMonths` |
| `POST /validate` | [`ValidateController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/ValidateController.java) | dispatches on Content-Type to `SessionsApiValidator.getSessionsCreateValidation` / `getSessionsUpdateValidation` / `SessionsConverter.convert` |
| `POST /validate-session-availability` | `ValidateController` | `SessionsApiValidator.getSessionsAvailabilityValidation` |
| `POST /session` (assign-judiciary) | [`SessionController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/SessionController.java) | `JudiciaryAssignmentService.assignJudiciaries` |
| `POST /session` (unassign.judiciary) | `SessionController` | `JudiciaryUnassignmentService.unassignJudiciary` |
| `GET /judiciaries/availability` | [`JudiciaryAvailabilityController`](src/main/java/uk/gov/moj/cpp/courtscheduler/controllers/JudiciaryAvailabilityController.java) | `JudiciaryAvailabilityService.findJudiciaryAvailability` |
| `GET /judiciaries/availability-rules` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityService.findJudiciaryAvailabilityRules` |
| `GET /judiciaries/availability-rules/{ruleId}` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityService.getJudiciaryAvailabilityRule` |
| `POST /judiciaries/availability-rules/add` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityService.addJudiciaryAvailabilityRule` |
| `POST /judiciaries/availability-rules/update` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityService.updateJudiciaryAvailabilityRule` |
| `POST /judiciaries/availability-rules/delete` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityService.deleteJudiciaryAvailabilityRule` |
| `POST /judiciaries/availability-rules/validate-(add\|update\|delete)` | `JudiciaryAvailabilityController` | `JudiciaryAvailabilityRuleApiValidator.validateXxxJudiciaryAvailabilityRuleForValidationEndpoint` |

## Auth filter — handling `/judiciaries/*`

`cp-auth-rules-filter:1.0.9` resolves the Drools `Action.name` from, in order:

1. an explicit `CPP-ACTION` request header,
2. the vendor segment of the request media type (`application/vnd.<token>+json` →
   the `<token>` part), or
3. `"<METHOD> <PATH>"` as a fallback for endpoints that don't negotiate a vendor
   media type — including `/judiciaries/availability*`, which use plain
   `application/json`.

The Drools rules for the judiciary endpoints in `courtscheduler-api.drl` therefore
match on that fallback `Action.name`:

```drl
rule "API - Action - courtscheduler.judiciary.find.availability"
  when
    $outcome: Outcome();
    $action: Action(name == "GET /judiciaries/availability");
    eval(userAndGroupProvider.hasPermission($action, PermissionConstants.getCourtSchedulePermission()));
  then
    $outcome.setSuccess(true);
end
```

`application-authz.yml` sets `authz.http.action-required: false` so the filter
proceeds with rule evaluation even when no `CPP-ACTION` header is supplied; any
unmatched action still fails closed via `deny-when-no-rules: true`.

The synthesised `<METHOD> <PATH>` strings in `courtscheduler-api.drl` mirror the
legacy `(mapping)` directives one-for-one:

| Method + path | Legacy action mapping |
|---|---|
| `GET /judiciaries/availability-rules` | `courtscheduler.judiciary.find.availability.rule` |
| `GET /judiciaries/availability` | `courtscheduler.judiciary.find.availability` |
| `GET /judiciaries/availability-rules/{ruleId}` | `courtscheduler.judiciary.get.availability.rule` |
| `POST /judiciaries/availability-rules/add` | `courtscheduler.judiciary.add.availability.rule` |
| `POST /judiciaries/availability-rules/update` | `courtscheduler.judiciary.update.availability.rule` |
| `POST /judiciaries/availability-rules/delete` | `courtscheduler.judiciary.delete.availability.rule` |
| `POST /judiciaries/availability-rules/validate-add` | `courtscheduler.judiciary.add.availability.rule.validate` |
| `POST /judiciaries/availability-rules/validate-update` | `courtscheduler.judiciary.update.availability.rule.validate` |
| `POST /judiciaries/availability-rules/validate-delete` | `courtscheduler.judiciary.delete.availability.rule.validate` |

UI clients are not required to send the `CPP-ACTION` header for any endpoint;
sending one (e.g. for legacy compatibility) is still accepted as the highest-priority
source of `Action.name`.

## Regression audit

WildFly → Spring Boot contract audit (migration `5f5be8e1f`, baseline `95d1263af`), audited 2026-07-06.

### Open

| # | Sev | Endpoint(s) | Legacy | Spring Boot | Fix |
|---|-----|-------------|--------|-------------|-----|
| 1 | 🟠 | provisional booking (get/create), list.hearings-in-court-sessions, get.hearing.ids, get.hearing.slots, search.book.hearing.slots | 200 + `{"error":{"errorMessage":…}}` | 400 + `{"error":"…"}` | Verify legacy really returned 200 (may be intentional normalization); if regression, return enveloped 200 |
| 2 | 🟠 | search.court-schedules-by-id, 3 MI exports | nulls emitted (`Include.ALWAYS`) | null keys dropped (`NON_NULL`) | Serialize these bodies with `Include.ALWAYS` |
| 3 | 🟡 | validate.* vs others | uniform `_metadata` envelope | validate.* return bare `{}`; `name` from `CPP-ACTION` header | Envelope validate.* too; derive `name` from action |

### Fixed

| Endpoint | Issue |
|----------|-------|
| GET /courtschedule | group emitted `courtRoomNumber` not `courtRoomName` + lost legacy sort |
| rotasl.clean_redundant_rota_data | read `months` instead of `numberOfPreviousMonthsAndOlder` (retention window ignored) |

### Not a regression

| Item | Reason |
|------|--------|
| organisation-unit(s)-hmi-status | removed by pre-migration commit `86d917693`, not the migration |
| JudiciaryAvailabilityApi | net-new — no legacy baseline |

### Faithful

create · validate.create · validate.session.availability · delete · update · assign.courtroom · assign/unassign judiciary · oucode.migrate · oucode.recalculate · update/remove hearing slots · process_rota_files · `{"error":…}` key on throw-path endpoints · date formatting
