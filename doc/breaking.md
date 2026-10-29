# Breaking changes — WildFly → Spring Boot migration

**Guiding principle: the migration must be a drop-in replacement for clients — no UI/client change.**
Any client-visible divergence from the legacy contract (error-body shape, media types, `Accept`
handling, HTTP status, response shapes) is a defect to fix on the Spring side, not something clients
should adapt to. Items below are tracked against that principle.

Scope audited: per-endpoint HTTP status codes, error-response body shapes, response content
negotiation (`Accept`/`produces`), and the rota pipeline's sync/async semantics.

---

## Pending

**NONE.**

---

## Resolved (kept faithful to the legacy contract — no client change)

### Error-response body shape — `{"error":"<msg>"}` (faithful to WildFly)  ✅ confirmed
The legacy WildFly HTTP error body was `{"error":"<msg>"}`, and the migration preserves it exactly.
This was initially mis-diagnosed: the `*ApiValidator`s build an *internal* `JsonObject` keyed
`errorMessage` (`ApiConstants.ERROR_MESSAGE = "errorMessage"`), so it looked as though the wire shape
should be `errorMessage` too. It is not — the WildFly framework's exception mapper (`rest-adapter-core`)
extracted that message and rendered the HTTP body as `{"error":…}`. **Proof:** the genuine
WildFly-era integration test (`origin/main` `CourtSchedulerIT:398`, run against a real containerised
WildFly via the RESTEasy `RestClient`) asserts the `/validate` 400 body is
`{"error":"All day split flag should be sent for All Day(AD) session"}`, and the string `errorMessage`
appears **zero** times in that whole IT. So `{"error":…}` is the contract — no client change.
`GlobalExceptionHandler#errorBody` (all 4xx/5xx) and `RequestSchemaValidationFilter#writeBadRequest`
both emit it. Response bodies that carry an `error` field (delete / assign-courtroom partial-failure)
use the same key and are likewise unchanged. *Verified: full IT suite green.*

> A brief intermediate edit flipped these to `errorMessage`/`errorMessages` on the mistaken
> assumption that the validators' internal key was the wire key; that edit was reverted once the
> WildFly IT proved `{"error":…}` is the real contract.

### Strict `Accept` → 406, not 500  ✅ fixed
A request whose `Accept` matches no producible media type was being mapped to **500** by the
catch-all handler. Added a `HttpMediaTypeNotAcceptableException` handler so it returns **406**, as
WildFly/JAX-RS did. Conforming clients (sending the vendor `Accept`) never hit this. Separately,
`POST /provisionalBooking` now also `produces application/json` (what WildFly returned for it, since
its RAML response declared no media type), so an `application/json` client gets 200 not an error.

### Request JSON-schema validation restored  ✅ fixed
The migration dropped it (`PassthroughObject`). Restored via `RequestSchemaValidationFilter` using
**everit** (the same library WildFly used), for exact parity (draft-04 semantics — e.g. `format: date`
ignored; structure/required/type/enum/pattern/additionalProperties enforced).

### Operational / deployment fixes
- Azure Blob RBAC (`springboot` MI missing `StorageBlobDataContributor` on `sasteccmscsl`) — fixed in `cpp-aks-deploy`.
- Azure/Redis config keys not bridged to env vars — fixed.
- Datasource `auto-commit=false` idle-in-transaction connection death — fixed (`auto-commit=true`).
- `startup.sh` ignored `$JAVA_OPTS` — fixed.
- Root log level ERROR → INFO — defaulted back toward quiet (WARN, env-overridable).

---

## Audited and confirmed NOT breaking

- **HTTP status codes** — 33 endpoints; declared statuses identical between RAML and OpenAPI
  (0 diffs), and every controller's actual returned status matches (create/edit/session/oucode/rota
  → 202; assign-courtroom/delete/PUT-hearingslots/list/MI/provisional → 200; `searchupdate` → 204;
  `deleteHearingSlots` → 202; validate → 200 pass / 400 fail; judiciary validate → 200 / 422 with the
  legacy `{"validationResult":{…}}` shape).
- **`DELETE /hearingslots/{hearingId}`** — dropped its legacy request-body media type but takes the id
  from the path and declares no `consumes`; legacy callers still work.
- **`application/json` `/judiciaries/availability*` endpoints** — not schema-gated by the filter,
  matching WildFly (its framework filters keyed off `+json`); covered by their bean validators.
- **Rota processing is asynchronous** — and it was async in WildFly too. Legacy
  `RotaFileCaptureAndProcessTriggerService.captureRotaFilesAndProcessEach` was `@Asynchronous`
  (`Future<String>`) and `RotaFilePartialProcessor.process*` were `@Asynchronous`; the Spring port maps
  these 1:1 to `@Async`. `POST /rotasl/process-rota-files` returns 202 immediately in both. (The
  `b1edcb510` "process synchronously" commit touched an older, since-restructured file and was
  superseded — the final `origin/main` is `@Asynchronous`.) No behavioural change.
