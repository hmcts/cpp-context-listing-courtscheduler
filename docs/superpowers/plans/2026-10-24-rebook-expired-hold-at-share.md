# Re-book an expired hold at share (LPT-2611) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** At share, an expired (purged) hold whose session still has room is re-reserved under the same booking id; every booking action is recorded in a periodically cleared `booking_log`.

**Architecture:** courtscheduler gains an append-only `booking_log` written wherever a hold is reserved, released, expired, confirmed or re-booked. The existing booking-status call takes `reserveIfExpired`; for a `NOT_FOUND` booking whose latest log event is `EXPIRED`, courtscheduler replays that event's rows through the ordinary `ReservationService.reserveAll` (capacity, `isOverbookingAllowed`, active-session and duplicate-hold rules unchanged) and answers `REBOOKED`. listing passes the flag through; the UI gate sets it; hearing and progression tag their releases with a reason.

**Tech Stack:** courtscheduler Spring Boot 3 / Gradle / JPA / Liquibase / openapi-generator; listing WildFly CQRS / Maven / RAML; cpp-ui-hearing Angular / Jest; hearing and progression WildFly / Maven.

**Spec:** `docs/superpowers/specs/2026-10-24-rebook-expired-hold-at-share-design.md` (same repo).

> **Later changes (2026-10-25), made after this plan was executed - the spec is current:**
> `BookingAction` is renamed `BookingState` and the column `action` is `state`; `booking_log` gains
> `expires_at`; the courtscheduler `GET /unconfirmedBooking/status` is removed (listing always
> POSTs); and nothing decides from the `court_schedule` counters any more (rota delete, move-to-past
> `overbooked` and Crown fallback `lacksCapacity` count `allocated_listings`); the team/ccsph2 paths
> still write them for the MI export until SPRDT-1406, and holds never do. Task text below is left as
> executed.

## Global Constraints

- NEVER commit in any repo - the user commits. Leave every change in the working tree.
- Branches: courtscheduler, hearing, progression `team/ras`; cpp-ui-hearing `dev/rasproto`; listing `team/ras` via a worktree at `/home/arcad/devenv/project/msjs/cpp-context-listing-ras` (the main listing checkout is on `LPT-2526`; do not switch it).
- Nothing new may read `court_schedule.available_slot` / `available_duration_mins`; keep writing them where a pick does.
- `isOverbookingAllowed = true` on a session passes capacity for that row, read at re-book time.
- Re-book keeps the SAME booking id; all-or-nothing per booking; each booking its own transaction.
- A full / past / missing / inactive session, or a latest log action other than `EXPIRED` -> `NOT_FOUND` (UI shows today's message).
- Status values: `RESERVED | SHARED | NOT_FOUND | REBOOKED` (+ listing's `UNKNOWN`); `safeToShare = status != NOT_FOUND`.
- Log actions: `RESERVED, REPICK_RELEASED, DRAFT_RELEASED, SURPLUS_RELEASED, RELEASED, EXPIRED, CONFIRMED, REBOOKED`.
- Release reasons on the wire: `DROPPED_FROM_DRAFT` -> `DRAFT_RELEASED`, `SURPLUS` -> `SURPLUS_RELEASED`, absent -> `RELEASED`.
- Log retention: `courtscheduler.booking-log.retention-days`, default `90`, cleared by the nightly purge.
- No Docker locally: `@DataJpaTest` repository tests are written but run in CI only; verify new SQL against STE13 with temp tables (Task 10).

## Review Focus

1. Double share click: two concurrent re-books of one booking -> the second hits `allocated_listings_reservation_idx` (unique `court_schedule_id, booking_id WHERE expires_at IS NOT NULL`); it must answer the booking's real status (`RESERVED`), not 500. Test in Task 5.
2. Multi-day Crown booking with one full day -> nothing re-booked, `NOT_FOUND`, and no partial rows left. Test in Task 5.
3. Booking released after it expired (expire, then line deleted -> `DRAFT_RELEASED` logged with zero rows) -> must NOT re-book. Task 3 logs a release even when it removed no rows? No: a release of an already-purged booking deletes nothing; the latest action stays `EXPIRED`. Accepted (spec: release removes holds; an expired booking has none). Test in Task 5 that `DRAFT_RELEASED` latest blocks re-book.
4. Hearing start time round trip: re-booked row's `hearing_start_time` equals the expired row's. Test in Task 5.
5. Status call without the flag is read-only: no log rows, no reservations. Test in Task 5.

---

### Task 1: courtscheduler `booking_log` table, entity, repository

**Files:**
- Create: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-liquibase/src/main/resources/liquibase/courtscheduler-view-store-db-changesets/054-create-booking-log.xml`
- Create: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/persist/entity/BookingLog.java`, `.../persist/entity/BookingAction.java`
- Create: `.../persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/BookingLogRepository.java`
- Test: `.../persistence/src/test/java/uk/gov/moj/cpp/courtscheduler/repository/BookingLogRepositoryTest.java`

**Interfaces:**
- Produces: `enum BookingAction { RESERVED, REPICK_RELEASED, DRAFT_RELEASED, SURPLUS_RELEASED, RELEASED, EXPIRED, CONFIRMED, REBOOKED }`
- Produces: `BookingLogRepository extends JpaRepository<BookingLog, Long>` with
  - `default void record(BookingAction action, Collection<AllocatedListing> rows)` - groups rows by `bookingId` (skips null), one `eventId` (random UUID) per booking, one `BookingLog` per row copying `courtScheduleId, hearingId, oucode, courtRoomId, rotaBusinessType, hearingStartTime, duration`, `createdOn = Instant.now()`; no-op on empty.
  - `List<BookingLog> findLatestEvent(String bookingId)` - native: rows `WHERE event_id = (SELECT event_id FROM booking_log WHERE booking_id = :bookingId ORDER BY id DESC LIMIT 1)`.
  - `@Modifying int deleteCreatedBefore(Instant cutoff)` - JPQL delete.

- [ ] Step 1: changeset `054` (author `arcadius.ahouansou`, `logicalFilePath` = file name): table `booking_log` - `id BIGINT GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY`, `event_id VARCHAR(36) NOT NULL`, `booking_id VARCHAR(36) NOT NULL`, `action VARCHAR(30) NOT NULL`, `court_schedule_id VARCHAR(36)`, `hearing_id VARCHAR(36)`, `oucode VARCHAR(10)`, `court_room_id INTEGER`, `rota_business_type VARCHAR(10)`, `hearing_start_time TIMESTAMP`, `duration INTEGER`, `created_on TIMESTAMP NOT NULL`; indexes `(booking_id, id)` and `(created_on)`; rollback drops the table. Mirror column types of `allocated_listings`.
- [ ] Step 2: entity `BookingLog` (`@Id @GeneratedValue(strategy = IDENTITY) Long id`, `@Enumerated(STRING) action`), enum, repository as above.
- [ ] Step 3: `BookingLogRepositoryTest extends AbstractRepositoryTest`: `recordsOneEventPerBooking` (2 rows booking A + 1 row booking B -> 3 log rows, A's two share one event id, B's differs); `latestEventReturnsOnlyTheNewestEventsRows` (record EXPIRED for A with 2 rows, then REBOOKED with 2 rows -> findLatestEvent(A) = the 2 REBOOKED rows); `deleteCreatedBeforeRemovesOnlyOlderRows`.
- [ ] Step 4: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:compileTestJava` -> BUILD SUCCESSFUL (tests need Docker; CI).

### Task 2: courtscheduler - log reserve, re-pick, purge, confirm; retention

**Files:**
- Modify: `listingcourtscheduler-viewstore/.../repository/CourtScheduleRepositoryCustom.java:178` + `CourtScheduleRepositoryImpl.java` (`releaseReservationsForBooking` ~2513, `persistHearingSlots` ~1155, `updateListHearingSlots` ~1360, `releaseExpiredReservations` ~2650)
- Modify: `listingcourtscheduler-api/.../api/service/ReservationService.java` (`reserveAll`)
- Modify: `listingcourtscheduler-common/.../common/service/AllocatedListingService.java` (`purgeExpiredReservedSessions`)
- Test: `ReservationServiceTest`, `AllocatedListingServiceTest` (create if absent), `CourtScheduleRepositoryTest`

**Interfaces:**
- Consumes: Task 1 `BookingLogRepository.record`, `deleteCreatedBefore`.
- Produces: `List<AllocatedListing> releaseReservationsForBooking(String bookingId)` (was `void`) - returns the rows it removed.

- [ ] Step 1: failing tests - `ReservationServiceTest.reserveAllLogsRepickReleaseThenReserved`: repository `releaseReservationsForBooking` returns 1 old row -> `record(REPICK_RELEASED, [old])` then, after `saveBookedSlots` succeeds, `record(RESERVED, rows)` where rows = `allocatedListingRepository.findUnconfirmedByBookingIds(List.of(bookingId))`; `reserveAllDoesNotLogReservedWhenSaveFails`. `AllocatedListingServiceTest.purgeDeletesLogRowsOlderThanRetention`: retention 90 -> `deleteCreatedBefore(now - 90 days)` (assert within a second).
- [ ] Step 2: run `./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest' :listingcourtscheduler-common:test --tests '*AllocatedListingServiceTest'` -> FAIL.
- [ ] Step 3: implement. `releaseReservationsForBooking` returns `reservations` (empty list when none). In the repository impl, inject `BookingLogRepository` and: `releaseExpiredReservations` -> `record(EXPIRED, expired)` before removal; `persistHearingSlots` (provisional branch) -> after `releaseReservationsForBooking`, `record(CONFIRMED, allocatedListingRepository.findByBookingId(bookingId) filtered expiresAt == null)`; `updateListHearingSlots` -> after `saveAllocatedListingWithRequestedList`, `record(CONFIRMED, allocatedListings filtered bookingId != null)`. `AllocatedListingService` gets `@Value("${courtscheduler.booking-log.retention-days:90}") int bookingLogRetentionDays` and calls `deleteCreatedBefore(Instant.now().minus(days, DAYS))` inside `purgeExpiredReservedSessions` (return value unchanged: purged hold count).
- [ ] Step 4: tests from Step 2 PASS; `CourtScheduleRepositoryTest.purgeLogsExpiredRows` + `confirmLogsConfirmedRows` written (CI). Update any existing test that stubbed `releaseReservationsForBooking` as void.

### Task 3: courtscheduler - release reason

**Files:**
- Modify: `listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml` (`CourtschedulerReleaseUnconfirmedBookings`: add `reason: { type: string, enum: [DROPPED_FROM_DRAFT, SURPLUS], nullable: true }`)
- Modify: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.release.unconfirmed.bookings.json` (optional `reason` enum)
- Modify: `CourtSchedulerApi.postReleaseUnconfirmedBookings`, `ProvisionalBookingService.releaseUnconfirmedBookings`, `ReservationService.releaseUnconfirmedBookings`, `CourtScheduleRepositoryCustom/Impl.releaseUnconfirmedReservations`
- Test: `CourtSchedulerApiTest` (or the existing controller test), `ReservationServiceTest`, `RequestSchemaValidationFilterTest`, `CourtScheduleRepositoryTest`

**Interfaces:**
- Produces: `ReservationService.releaseUnconfirmedBookings(List<String> bookingIds, BookingAction action)`; `ProvisionalBookingService.releaseUnconfirmedBookings(List<String> bookingIds, String reason)`; `int releaseUnconfirmedReservations(Collection<String> bookingIds, BookingAction action)`.
- Mapping (in `ProvisionalBookingService`): `DROPPED_FROM_DRAFT -> DRAFT_RELEASED`, `SURPLUS -> SURPLUS_RELEASED`, null -> `RELEASED`.

- [ ] Step 1: failing tests: controller passes `reason` through; `ProvisionalBookingServiceTest.releaseMapsReasonToAction` (3 cases); filter test: body with `reason: "BOGUS"` -> 400, `reason: "SURPLUS"` -> passes, no reason -> passes.
- [ ] Step 2: run the api tests -> FAIL.
- [ ] Step 3: implement. `DELETE_UNCONFIRMED_RESERVATIONS_QUERY` returns `RETURNING id, booking_id, court_schedule_id, hearing_id, oucode, court_room_id, rota_business_type, hearing_start_time, duration`; map each row to a transient `AllocatedListing` and `bookingLogRepository.record(action, rows)`; capacity restore unchanged (court_schedule_id, duration from the same rows). `hearing_start_time` comes back as `java.sql.Timestamp` -> `.toInstant()`.
- [ ] Step 4: api tests PASS.

### Task 4: courtscheduler - capacity rule counts, like the search API (spec 1b)

**Files:**
- Modify: `listingcourtscheduler-common/src/main/java/uk/gov/moj/cpp/courtscheduler/common/service/SessionsService.java` (`evaluateAvailability` ~1188-1205)
- Test: `listingcourtscheduler-common/src/test/.../SessionsServiceTest.java`

- [ ] Step 1: failing tests: `durationBasedSessionUsesAllocatedListingsNotTheColumn` - non-AD-split, `maxDuration 120`, `availableDuration 120`, bookings summing 100 -> request 30 refused; `durationBasedSessionIgnoresStaleLowColumn` - `availableDuration 0`, bookings summing 0, max 120 -> request 30 accepted; `overbookingAllowedSessionAcceptsWhenFull` (exists? add if not).
- [ ] Step 2: `./gradlew :listingcourtscheduler-common:test --tests '*SessionsServiceTest'` -> FAIL.
- [ ] Step 3: plain branch -> `available = max(0, nullToZero(cs.getMaxDuration()) - sum(duration of allocatedListingService.getAllocatedListingEachBookedByCourtScheduleId(id)))` (the same row source the AD-split branch uses). Check `AllocatedListingEachBooked` carries duration; if not, add `AllocatedListingService.getTotalBookedDuration(String courtScheduleId)` backed by `SELECT COALESCE(SUM(duration),0) FROM allocated_listings WHERE court_schedule_id = :id`.
- [ ] Step 4: PASS, and the whole `:listingcourtscheduler-common:test` and `:listingcourtscheduler-api:test` suites stay green (fix any test that relied on `availableDuration` by giving it allocated rows instead).

### Task 5: courtscheduler - re-book on the status call

**Files:**
- Modify: `ReservationService.java` (add `rebookExpired`), `ProvisionalBookingService.java` (`getBookingStatus`), `CourtSchedulerApi.java`, openapi yml (`/unconfirmedBooking/status` gets a `post` alongside the `get`)
- Create: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.query.booking.status.json` (request: `bookingIds` array minItems 1 of non-blank strings, `reserveIfExpired` boolean, additionalProperties false)
- Test: `ReservationServiceTest`, `ProvisionalBookingServiceTest`, controller test, filter test

**Interfaces:**
- Consumes: Task 1 `findLatestEvent`, `record`; Task 4 counting rule (via `reserveAll`).
- Produces: `@Transactional(propagation = REQUIRES_NEW) boolean ReservationService.rebookExpired(String bookingId)`; `JsonObject ProvisionalBookingService.getBookingStatus(List<String> bookingIds, boolean reserveIfExpired)` (old `getBookingStatus(String)` delegates with `false`); `POST /unconfirmedBooking/status`, request media type `application/vnd.courtscheduler.query.booking.status+json`, operationId `postBookingStatus`, response identical to the GET's.

- [ ] Step 1: failing tests (ReservationServiceTest):
  - `rebookReplaysExpiredRowsUnderSameBookingId`: latest event EXPIRED with 2 rows -> `reserveAll(bookingId, [slot(cs1, start1, 30), slot(cs2, start2, 30)])` called, `record(REBOOKED, rows reserved)`, returns true.
  - `rebookRefusedWhenLatestIsNotExpired` (DRAFT_RELEASED, SURPLUS_RELEASED, RELEASED, REBOOKED, RESERVED, empty) -> false, `reserveAll` never called.
  - `rebookRefusedForPastSession`: a row whose `hearingStartTime` date < today (UTC) -> false.
  - `rebookFalseWhenReserveRefuses`: `reserveAll` throws `NoCapacityException` / `NoSessionAvailableException` / `ConfirmedBookingExistsException` -> false (exception swallowed, logged).
  - `rebookKeepsHearingStartTime`: expired row start `2026-10-26T10:00:00Z` -> ProvisionalSlot `hearingStartTime` round-trips to the same instant through `DateUtils.toExactTimestamp` (build it as `LocalDateTime.ofInstant(i, ZoneId.systemDefault()).atOffset(ZoneOffset.UTC).toString()`, matching how `saveAllocatedListing` parses).
  ProvisionalBookingServiceTest:
  - `withoutFlagNotFoundStaysNotFoundAndNothingIsWritten` -> `rebookExpired` never called.
  - `withFlagNotFoundRebookedAnswersRebooked` -> `{status: REBOOKED, safeToShare: true}`.
  - `withFlagRebookRefusedAnswersNotFound`.
  - `withFlagRebookRaceAnswersRealStatus`: `rebookExpired` throws `DataIntegrityViolationException` -> status re-read -> `RESERVED`.
  - `reservedAndSharedNeverRebooked`.
- [ ] Step 2: run -> FAIL.
- [ ] Step 3: implement. `rebookExpired`: `findLatestEvent`; not EXPIRED -> false; any row's session date (from `hearingStartTime`, UTC) before today -> false; build `ProvisionalSlot`s (courtScheduleId, hearingStartTime as above, duration); `reserveAll(bookingId, slots)` (does active check, capacity with `isOverbookingAllowed`, confirmed guard); then `record(REBOOKED, allocatedListingRepository.findUnconfirmedByBookingIds(List.of(bookingId)))`; return true; catch the three refusal exceptions -> false. A Crown run's rows are per-day with duration <= one court day, so `expandCrownMultiDay` does not re-expand them and each day is capacity-checked on its own session. `ProvisionalBookingService.getBookingStatus(ids, flag)`: per id `statusOf`; if `NOT_FOUND && flag` -> try `reservationService.rebookExpired(id)` -> `REBOOKED` on true; on `DataIntegrityViolationException` (or any `RuntimeException`) -> `statusOf(id)` again. If `ProvisionalBookingService` is class-level `@Transactional`, make the new method `@Transactional(propagation = NOT_SUPPORTED)` so each `REQUIRES_NEW` re-book commits independently. Controller `postBookingStatus(body)`: validate non-empty, non-blank ids (as release does); log `courtscheduler.post.booking-status bookingIds=.. reserveIfExpired=..`.
- [ ] Step 4: PASS; full `:listingcourtscheduler-api:test` green.

### Task 6: listing (team/ras worktree) - pass the flag

**Files (in `/home/arcad/devenv/project/msjs/cpp-context-listing-ras`):**
- Modify: `listing-query/listing-query-api/src/raml/json/schema/listing.query.booking.status.json` (optional boolean `reserveIfExpired`)
- Modify: `listing-query/listing-query-api/src/raml/json/schema/listing.query.booking.status.response.json` (status enum gains `REBOOKED` if it has an enum) and the RAML `/bookingStatus` description (`RESERVED | SHARED | NOT_FOUND | REBOOKED | UNKNOWN`)
- Modify: `listing-common/.../CourtSchedulerServiceAdapter.getBookingStatus`, `HearingSlotsService` (new POST client method to `/unconfirmedBooking/status`, media type `application/vnd.courtscheduler.query.booking.status+json`)
- Test: `CourtSchedulerServiceAdapterTest`

- [ ] Step 1: `git -C /home/arcad/devenv/project/msjs/cpp-context-listing worktree add ../cpp-context-listing-ras team/ras` (if absent).
- [ ] Step 2: failing tests: flag true -> POST client called with `{bookingIds, reserveIfExpired: true}`; flag absent/false -> existing GET path unchanged; POST error -> `UNKNOWN` fail-open as today.
- [ ] Step 3: implement following the existing `getBookingStatus` client pattern in `HearingSlotsService`.
- [ ] Step 4: `mvn -q -pl listing-common,listing-query/listing-query-api -am test -DfailIfNoTests=false` (scope as the module layout allows) -> PASS.

### Task 7: cpp-ui-hearing - set the flag

**Files:** Modify `src/app/core/services/listing/listing.service.ts` (`getBookingStatus` body `{ bookingIds, reserveIfExpired: true }`; status type adds `'REBOOKED'`); Test `listing.service.spec.ts`, `share-result.container.spec.ts`.

- [ ] Step 1: failing tests: request body carries `reserveIfExpired: true`; gate lets a `{status: 'REBOOKED', safeToShare: true}` booking share.
- [ ] Step 2/4: run per [[cpp-ui-hearing-test-runner-quirks]]: `npx jest src/app/core/services/listing src/app/results/share-results` (not `ng test`, which watches) -> FAIL then PASS.

### Task 8: hearing - send `DROPPED_FROM_DRAFT`

**Files:** hearing-event-processor `ProvisionalBookingService.releaseUnconfirmedBookings` (posts `/unconfirmedBooking/release`): body gains `reason` = `"DROPPED_FROM_DRAFT"` for both event reasons (`DROPPED_FROM_DRAFT`, `RELEASED_BY_USER`). Test: its unit test asserts the body.
- [ ] Steps: failing test -> implement -> `mvn -q -pl <processor module> test -Dtest=<ProvisionalBookingService test>` PASS.

### Task 9: progression - send `SURPLUS`

**Files:** `progression-event-processor/.../service/SurplusReservationReleaseService.java`: body gains `reason: "SURPLUS"`. Test: `SurplusReservationReleaseServiceTest` asserts the body.
- [ ] Steps: failing test -> implement -> module test PASS.

### Task 10: verification

- [ ] courtscheduler: `./gradlew build -x test` then `./gradlew :listingcourtscheduler-api:test :listingcourtscheduler-common:test` green; persistence `compileTestJava` green.
- [ ] SQL against STE13 (read-only user, one transaction, rolled back): `CREATE TEMP TABLE allocated_listings (LIKE public.allocated_listings INCLUDING ALL)`, same for `court_schedule`, create the `booking_log` DDL as a temp table, insert fixtures, run the new DELETE ... RETURNING and the `findLatestEvent` native query; check results. Temp tables shadow `public` via `pg_temp` search path.
- [ ] listing, UI, hearing, progression: the module tests from Tasks 6-9 green.
- [ ] Report: what ran, what only compiles (repository tests), deploy order courtscheduler -> listing/hearing/progression -> UI.
