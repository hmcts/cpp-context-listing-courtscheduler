# Reserve a Slot — BUG-3: release the hold on the real share path

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Thread the `bookingId` through `courtscheduler.list.hearings-in-sessions` so the share path releases the reservation it is replacing, and stamps `booking_id` on the confirmed row.

**Architecture:** Two problems, one change. The reservation is released today in `persistHearingSlots`, but listing's share flow never reaches that method — it books through `updateListHearingSlots`, which releases only by the *real hearing id*. Carrying the `bookingId` on the list payload lets `updateListHearingSlots` release the bookingId-keyed hold, and lets the confirmed row record which booking produced it.

**Tech Stack:** Java 17, Gradle (courtscheduler) and Maven (listing), Spring, JPA/Hibernate, JUnit 5, Mockito.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6
**Ticket:** BUG-3 (new — raise it; it is a defect in work already reviewed under NEW-5)

**Repos:** `cpp-context-listing-courtscheduler` (`team/ras`) **and** `cpp-context-listing` (`team/ccsph2n`)

## The defect

NEW-5 put the release in `CourtScheduleRepositoryImpl.persistHearingSlots`. That method is only reached from the `saveBookedSlots` family, which serves `courtscheduler.update.hearing.slots`. **Listing's share flow does not use it.** It calls `courtscheduler.list.hearings-in-sessions` → `SlotsUpdateService.listHearingSlots` → `CourtScheduleRepositoryImpl.updateListHearingSlots`, which releases only by `hearing.getHearingId()`.

So at share the reservation survives and the session is decremented **twice** — once by the hold, once by the confirmed booking — until the 01:00 purge reclaims one. Silent, and it makes a session look fuller than it is for the rest of the day.

The same gap blocks the status check: `updateListHearingSlots` never sets `booking_id` on the row it creates (it sets `hearingId`, `courtScheduleId`, `courtRoomId`, `oucode`, `rotaBusinessType`, `id`, `hearingStartTime`, `duration`, `source`), and the payload carries no bookingId to set it from.

## Why this also resolves DEC-1

With `booking_id` on the confirmed row, "has this draft already been shared?" becomes answerable inside courtscheduler from data it owns:

| State | Meaning |
|---|---|
| a reservation row (`hearing_id` = bookingId, `expires_at` set) | held, not yet shared → allow the share |
| a live row with `booking_id` = bookingId (`expires_at` null) | already shared → allow the re-share |
| neither | expired and purged → block with the expiry message |

That is strictly better than resolving DEC-1 in the UI: the rule lives with the data instead of depending on the UI knowing its own history. **Extending `getBookingStatus` to use it is deliberately NOT in this plan** — land the plumbing first, then change the contract in a follow-up so the two are reviewable apart.

## Global Constraints

- courtscheduler builds with **Gradle**; listing builds with **Maven**. Do not cross them.
- `bookingId` is **optional** on the payload throughout. Crown fallback, search-and-book, rota flows and every pre-go-live caller send no bookingId and must keep working untouched.
- Releasing a bookingId with no reservation is a **no-op, not an error**.
- A session must end up decremented **exactly once** across reserve → share. The arithmetic that achieves this is spelled out in Task 1 Step 4 — read it before changing any ordering.
- Do not change `persistHearingSlots`. NEW-5's release there is correct for the `update.hearing.slots` path and must stay.
- Do not change `getBookingStatus` in this plan.

**Build and test commands**

courtscheduler:
```bash
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test
./gradlew :listingcourtscheduler-api:test
./gradlew build -x listingcourtscheduler-integration-test:test -x listingcourtscheduler-integration-test:performanceTest
```
listing:
```bash
mvn -q -pl listing-command/listing-command-api -am test
mvn -q clean install -DskipTests
```

---

### Task 1: courtscheduler — accept `bookingId`, release the hold, stamp the row

**Files:**
- Modify: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.list.hearings-in-sessions.json`
- Modify: `listingcourtscheduler-api/src/raml/json/courtscheduler.list.hearings-in-sessions.json` (example)
- Modify: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/HearingSlot.java`
- Modify: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/Hearing.java`
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryImpl.java` (`flattenHearingSlots` ~2766, `updateListHearingSlots` ~1275)
- Test: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/test/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryTest.java`

**Interfaces:**
- Consumes: nothing new. `releaseOldAllocatedListings(String)` already performs the full three-step release and already no-ops on absence.
- Produces, for Task 2 to send: an **optional** `bookingId` string on each entry of `hearingSlots[]`, beside `hearingId`.

- [ ] **Step 1: Write the failing tests**

Add to `CourtScheduleRepositoryTest`, reusing the class's existing `persistRandomCourtSchedule()` / allocated-listing helpers:

```java
    @Test
    void shouldReleaseTheReservationAndDecrementTheSessionExactlyOnceWhenListing() {
        final CourtSchedule session = persistRandomCourtSchedule();
        session.setSlotBased(true);
        session.setMaxSlots(4);
        session.setAvailableSlots(3);           // the reservation already took one
        courtScheduleRepository.saveAndFlush(session);

        final AllocatedListing reservation = createAllocateListing(
                "AL-RES", "BK-1", session, "BK-1");   // hearing_id = bookingId
        reservation.setExpiresAt(LocalDate.now(java.time.ZoneOffset.UTC));
        allocatedListingRepository.saveAndFlush(reservation);

        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-1", "BK-1", session.getCourtScheduleId()));

        // reserve -1, list -1, release +1  =>  net -1 from the original 4
        assertThat(courtScheduleRepository.findBy(session.getCourtScheduleId()).getAvailableSlots(), is(3));
        assertThat(allocatedListingRepository.findByHearingId("BK-1").isEmpty(), is(true));
        final List<AllocatedListing> confirmed = allocatedListingRepository.findByHearingId("HEARING-1");
        assertThat(confirmed.size(), is(1));
        assertThat(confirmed.get(0).getExpiresAt(), is(nullValue()));
        assertThat(confirmed.get(0).getBookingId(), is("BK-1"));
    }

    @Test
    void shouldListNormallyWhenNoBookingIdIsSupplied() {
        final CourtSchedule session = persistRandomCourtSchedule();
        session.setSlotBased(true);
        session.setMaxSlots(4);
        session.setAvailableSlots(4);
        courtScheduleRepository.saveAndFlush(session);

        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-2", null, session.getCourtScheduleId()));

        assertThat(courtScheduleRepository.findBy(session.getCourtScheduleId()).getAvailableSlots(), is(3));
        assertThat(allocatedListingRepository.findByHearingId("HEARING-2").get(0).getBookingId(), is(nullValue()));
    }

    @Test
    void shouldTolerateABookingIdWithNoReservation() {
        final CourtSchedule session = persistRandomCourtSchedule();
        session.setSlotBased(true);
        session.setMaxSlots(4);
        session.setAvailableSlots(4);
        courtScheduleRepository.saveAndFlush(session);

        // a pre-go-live magistrates draft: a bookingId, but no reservation behind it
        courtScheduleRepository.updateListHearingSlots(
                requestedSlotsFor("HEARING-3", "BK-LEGACY", session.getCourtScheduleId()));

        assertThat(courtScheduleRepository.findBy(session.getCourtScheduleId()).getAvailableSlots(), is(3));
        assertThat(allocatedListingRepository.findByHearingId("HEARING-3").size(), is(1));
    }
```

Write a small `requestedSlotsFor(hearingId, bookingId, courtScheduleId)` helper building a `RequestedSlots` with one `HearingSlot`. Match the real field names on `HearingSlot`/`Hearing` — read them first.

The second and third tests are the regression net: **every existing caller sends no bookingId**, and a legacy draft sends one with nothing behind it. Neither may change behaviour.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*CourtScheduleRepositoryTest'`
Expected: the first test FAILS — `availableSlots` is 2 (decremented twice, never released) and `getBookingId()` is null. The other two should already pass; if either fails, stop, because the change has broken an existing path.

- [ ] **Step 3: Add `bookingId` to the domain types and the schema**

On `HearingSlot` (the payload-level type Jackson maps from `hearingSlots[]`) and on `Hearing` (what `flattenHearingSlots` produces), add a nullable `String bookingId` with getter and setter, following each class's existing style.

In `flattenHearingSlots` (~line 2766), copy it onto each flattened `Hearing`: `hearing.setBookingId(hearingSlot.getBookingId());` — one booking spans every session in that hearing slot.

In the request schema, add to the `hearingSlots` item `properties`, beside `hearingId`:

```json
          "bookingId": {
            "description": "The booking whose reservation this listing confirms. Optional: callers that never reserved (Crown fallback, search-and-book, rota) omit it.",
            "type": "string"
          }
```

Leave `required` as `["hearingId", "courtScheduleIds"]`. Add `"bookingId"` to the example payload.

- [ ] **Step 4: Release the hold, and stamp the row**

Two edits inside `updateListHearingSlots` (~line 1275). **Read this reasoning before changing the ordering.**

First, release each distinct booking **once, before** the per-session loop, so a multi-session booking is released as a unit rather than re-attempted per session:

```java
        // Release the hold taken at slot-pick time before the loop below charges each session for
        // the real booking. The hold is keyed on the bookingId, so the hearing-wide release inside
        // the loop (keyed on the real hearing id) does not cover it. Without this the session is
        // decremented twice — once by the hold, once by the booking — until the 01:00 purge.
        // No-op when there is nothing to release: a pre-go-live draft has only a provisional_booking row.
        slots.getHearingSlots().stream()
                .map(HearingSlot::getBookingId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .forEach(this::releaseOldAllocatedListings);
```

The arithmetic, for a slot-based session starting at 4: reserving took it to 3 and persisted that; this release pays the slot back to 4; the loop then charges the real booking, ending at 3. Decremented exactly once. `releaseOldAllocatedListings` restores capacity through `releaseAllocatedSlotsOrDurationFromCourtSchedule`, which re-`find`s the `CourtSchedule` — the same managed instance the loop then mutates — so the two compose correctly within the persistence context.

Second, stamp the booking onto the confirmed row, beside the existing setters:

```java
                allocatedlisting.setBookingId(hearing.getBookingId());
```

Leave `isCourtScheduleReleased` alone. It asks whether *this hearing* was previously listed and feeds `resolveAllocatedListingSource`; the booking release is a different question and must not be folded into it.

- [ ] **Step 5: Run the tests**

```bash
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test
./gradlew :listingcourtscheduler-api:test
```
Expected: PASS, all three new tests and every pre-existing test. A pre-existing failure here means an existing caller's behaviour moved — investigate rather than adjusting the test.

- [ ] **Step 6: Full build**

```bash
./gradlew build -x listingcourtscheduler-integration-test:test -x listingcourtscheduler-integration-test:performanceTest
```

- [ ] **Step 7: Commit**

*Skipped when the controller has instructed no commits.*

---

### Task 2: listing — send the `bookingId` on the list payload

**Files:**
- Modify: `listing-command/listing-command-api/src/main/java/uk/gov/moj/cpp/listing/command/api/util/SlotsToJsonStringConverter.java`
- Modify: `listing-command/listing-command-api/src/main/java/uk/gov/moj/cpp/listing/command/api/service/CourtScheduleEnrichmentService.java` (`getUpdateSlotsPayload` ~1045, `listHearingSessionsAndExtractData` ~2062, and the call site at ~132)
- Test: `listing-command/listing-command-api/src/test/java/uk/gov/moj/cpp/listing/command/api/service/CourtScheduleEnrichmentServiceTest.java`

**Interfaces:**
- Consumes: the optional `bookingId` on `hearingSlots[]` from Task 1.
- Produces: no new public API.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    public void shouldSendTheBookingReferenceOnTheListPayloadSoTheHoldIsReleased() {
        final UUID bookingId = randomUUID();
        // build a HearingListingNeeds with bookingReference = bookingId and a hearing day
        // carrying a courtScheduleId, then drive the enrichment that lists it

        final ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
        verify(hearingSlotsService).listHearingInCourtSessions(payload.capture());

        final JsonObject hearingSlot = payload.getValue().getJsonArray("hearingSlots").getJsonObject(0);
        assertThat(hearingSlot.getString("bookingId"), is(bookingId.toString()));
    }

    @Test
    public void shouldOmitBookingIdWhenTheHearingHasNoBookingReference() {
        // same, with no bookingReference set
        final JsonObject hearingSlot = payload.getValue().getJsonArray("hearingSlots").getJsonObject(0);
        assertThat(hearingSlot.containsKey("bookingId"), is(false));
    }
```

Adapt to the class's existing harness. The second test matters as much as the first: Crown fallback, search-and-book and rota flows have no booking reference, and `JsonObjectBuilder.add` throws on null.

- [ ] **Step 2: Run to verify it fails**

Run: `mvn -q -pl listing-command/listing-command-api -am test -Dtest=CourtScheduleEnrichmentServiceTest`
Expected: FAIL — no `bookingId` key on the payload.

- [ ] **Step 3: Thread it through**

`getUpdateSlotsPayload` gains a nullable booking reference and adds the key only when present:

```java
    private static JsonObject getUpdateSlotsPayload(final UUID hearingId, final JsonArray courtScheduleIds, final UUID bookingReference) {
        final JsonObjectBuilder hearingSlot = createObjectBuilder()
                .add(HEARING_ID, hearingId.toString())
                .add(COURT_SCHEDULE_IDS, courtScheduleIds);
        // Omit rather than send null — JsonObjectBuilder.add rejects nulls, and courtscheduler
        // treats an absent bookingId as "this caller never reserved".
        if (nonNull(bookingReference)) {
            hearingSlot.add("bookingId", bookingReference.toString());
        }
        ...
```

Pass the hearing's `getBookingReference()` from both call sites. `listHearingSessionsAndExtractData(UUID hearingId, List<HearingDay> hearingDays)` needs the booking reference too — add a parameter rather than reaching for state, and update its callers.

- [ ] **Step 4: Run the tests and build**

```bash
mvn -q -pl listing-command/listing-command-api -am test
mvn -q clean install -DskipTests
```

- [ ] **Step 5: Commit**

*Skipped when the controller has instructed no commits.*

---

## Deliberately not in scope

- **Extending `getBookingStatus`** to report an already-shared booking as live. That is the DEC-1 contract change and belongs in its own ticket, reviewable apart from this plumbing.
- **`persistHearingSlots`.** NEW-5's release there is correct for the `update.hearing.slots` path.
- **Crown multi-day holding only its anchor day.** Still open, still needs a product view.
