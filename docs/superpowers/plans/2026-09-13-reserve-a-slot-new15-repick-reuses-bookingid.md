# Reserve a Slot — NEW-15 (v2): re-picking reuses the bookingId, so the pipeline wipes the old hold

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a clerk changes their mind and picks a different session, the hold on the session they abandoned is released in the same request that takes the new one — so a clerk can never hold two sessions for one next-hearing.

**Architecture:** The re-pick comes back under the **same bookingId**, instead of minting a new one. Nothing else is needed: a reservation's `hearing_id` *is* its bookingId, and `saveBookedSlots` already opens with a hearing-wide `releaseOldAllocatedListings(hearing_id)`. Reusing the id therefore makes the existing pipeline wipe every row of the previous pick — all of them, including a multi-day Crown hold — and take the new ones, in one transaction. `POST /provisionalBooking` gains one optional `bookingId`: supplied means "reuse this booking", absent means "mint me one".

**Tech Stack:** Java 17. **cpp-context-listing-courtscheduler is Gradle. cpp-context-hearing is Maven.** Never mix them up.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6, and NEW-15 in `cpp-context-hearing/docs/reserve-a-slot-jira.md`

## This plan supersedes `2026-09-13-reserve-a-slot-new15-repick-wipes-previous.md`

That earlier plan added a second field, `replacesBookingId`, and a second release mechanism, `ReservationService.releasePreviousHold`, bolted onto a pipeline that already releases. **Task 1 of it was built and must now be partly undone.** Task 1 here is that rework, not a fresh change. Its Task 2 was stopped before it wrote anything; confirm that before starting Task 2 (`git status` in `cpp-context-hearing` must show no `replacesBookingId` anywhere).

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. Everything stays as unstaged working-tree edits. Any "Commit" step is an explicit no-op — read it, skip it, say so in your report.
- **Nothing in this feature is committed**, so `git diff` in either repo shows tens of files of prior, already-reviewed tickets. Do not revert, tidy, or comment on any of it. **The one exception is the superseded `replacesBookingId` work named explicitly in Task 1** — removing that is the job.
- `bookingId` on the request is **optional**: nullable in Java, absent from every `required` list, **omitted rather than sent as null** on every JSON hop (`JsonObjectBuilder.add` throws on null).
- Hearing is a **pure pass-through**: it never invents, defaults, validates or resolves a bookingId. It forwards what it was given, or forwards nothing.
- **Do not add a second release.** If you find yourself writing a call to `releaseOldAllocatedListings` in `ReservationService` or `ProvisionalBookingService`, stop — that is the mistake this plan exists to undo.

## Deployment ordering

`ProvisionalSlotConverter` uses a bare `new ObjectMapper()`, so `FAIL_ON_UNKNOWN_PROPERTIES` is **on**. If hearing ships first and sends `bookingId` to a courtscheduler that does not know the field, every provisional booking fails. Task 1 (courtscheduler, the receiver) deploys before Task 2 (hearing, the sender). Build them in that order too.

---

## Why reuse is right, and what it changes about an existing comment

Today, re-picking mints a fresh bookingId. The old reservation stays keyed on the old id, holding real capacity, until the 01:00 purge — so trying three sessions holds all three for the sitting day. (For magistrates this never mattered before reservations existed: `provisional_booking` rows hold no capacity, so the stale rows were inert.)

Reusing the id makes the wipe fall out of the pipeline that is already there, at `CourtScheduleRepositoryImpl.bookSlotsWithCourtScheduleId`:

```java
final Optional<String> hearingId = getHearingId(slots);
if (releaseExistingHearingAllocations) {
    hearingId.ifPresent(this::releaseOldAllocatedListings);
}
```

`reserveAll` reaches this through `saveBookedSlots(slots, false, false)`, whose 3-arg form delegates with `releaseExistingHearingAllocations = true`. So the release is the **three-step** one (delete rows → `releaseCourtScheduleAllocatedSlotsForBookingId` → `releaseAllocatedSlotsOrDurationFromCourtSchedule`), which restores capacity properly. A bare `DELETE` would leak it permanently; you are not writing one, you are reusing the correct one.

**Two ids never collide.** Each NHCC/NHMC result line mints its own `bookingReference` and carries it in its own prompt, so the release only ever wipes that line's own previous pick.

**An existing comment now says the opposite of the truth.** `ReservationService`'s class Javadoc contains:

> The row's hearing_id is the minted bookingId, which is unique per pick. That matters: saveBookedSlots begins with a hearing-wide releaseOldAllocatedListings(hearing_id), so a key shared between two picks would silently release the other's hold.

That was written when per-pick uniqueness was the design. It now describes the feature as a hazard, and left alone it will get this behaviour "fixed" back out by the next reader. Task 1 rewrites it. **The neighbouring paragraph about one `saveBookedSlots` call per booking rather than one per slot is still correct and must survive** — reserving slot by slot would still make each slot release the previous ones of the same booking.

---

## File Structure

**Task 1 — cpp-context-listing-courtscheduler (Gradle), branch `team/ras`**

| File | Change |
|---|---|
| `.../domain/ProvisionalBookingSlots.java` | **Modify** — rename `replacesBookingId` → `bookingId` |
| `.../api/service/ReservationService.java` | **Modify** — delete the 3-arg overload and `releasePreviousHold`; rewrite the class Javadoc paragraph |
| `.../api/service/ProvisionalBookingService.java` | **Modify** — reuse the supplied id or mint |
| `.../api/src/raml/json/courtscheduler.create.provisional.booking.json` | **Modify** — example |
| `.../api/src/raml/json/schema/courtscheduler.create.provisional.booking.json` | **Modify** — rename the optional property |
| `.../api/src/test/.../ReservationServiceTest.java` | **Modify** — delete the 4 release tests |
| `.../api/src/test/.../ProvisionalBookingServiceTest.java` | **Modify** — 2-arg stubs again, 2 new tests |
| `.../viewstore-persistence/src/test/.../CourtScheduleRepositoryTest.java` | **Modify** — 1 new test, the real behaviour proof |

**Task 2 — cpp-context-hearing (Maven), branch `team/ccsph2`** — the `bookingId` pass-through: command schema + example, `BookProvisionalHearingSlotsCommandHandler`, `HearingAggregate:1261`, `BookProvisionalHearingSlots` event, `BookProvisionalHearingSlotsProcessor`, and the three touched classes' tests.

**Build commands**

```bash
# Task 1 — courtscheduler, GRADLE
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*' --tests '*ProvisionalBookingServiceTest*'
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*CourtScheduleRepositoryTest*'
./gradlew build -x test

# Task 2 — hearing, MAVEN
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor,hearing-command/hearing-command-handler -am test
mvn clean install -DskipTests
```

---

### Task 1: courtscheduler reuses a supplied bookingId

**Files:** as listed above for Task 1.

**Interfaces:**
- Produces: `POST /provisionalBooking` accepts an optional top-level `bookingId` (string), sibling of `provisionalSlots`. Supplied → that booking is reused and returned; absent → one is minted and returned. The response is unchanged in shape: `{"bookingId": "..."}`.
- Restores: `ReservationService.reserveAll(String bookingId, List<ProvisionalSlot> slots)` as the **only** signature. The 3-arg form added by the superseded plan is deleted.

**Read first.** Open `ReservationService.java` in full, and read `CourtScheduleRepositoryImpl.bookSlotsWithCourtScheduleId` (around line 1048) so you can see for yourself that the release you are deleting is already performed there. Do not take this plan's word for it.

- [ ] **Step 1: Undo the superseded work**

Three precise removals in `ReservationService.java`:
1. Delete the 3-arg `reserveAll(bookingId, requestedSlots, replacesBookingId)` entirely, moving its body back into the 2-arg `reserveAll(bookingId, requestedSlots)` — minus the `releasePreviousHold(...)` call.
2. Delete the `releasePreviousHold` method and its Javadoc.
3. Restore the 2-arg method's Javadoc to describe reserving every requested session under one bookingId atomically, and **keep** its existing final paragraph about `slotBased`, `expires_at` and `source`.

In `ProvisionalBookingSlots.java`, rename the field and both accessors from `replacesBookingId` to `bookingId` (`getBookingId` / `setBookingId`).

Do **not** run the tests yet — they will not compile until Step 3.

- [ ] **Step 2: Write the failing tests**

In `ReservationServiceTest.java`, **delete** the four tests the superseded plan added — `shouldReleaseThePreviousHoldBeforeReservingTheNewSessions`, `shouldNotReleaseAnythingWhenNoPreviousBookingIsNamed`, `shouldNotReleaseAnythingWhenThePreviousBookingIdIsBlank`, `shouldNeverReleaseTheNewBookingItself`. The behaviour they covered now lives in the repository pipeline and is proven by the new test below. Leave every other test in that class alone.

In `ProvisionalBookingServiceTest.java`, restore the `reserveAll` stubs and verifies to the **2-arg** form, then add:

```java
    @Test
    void shouldReuseTheSuppliedBookingIdRatherThanMintingANewOne() {
        when(reservationService.reserveAll(anyString(), anyList())).thenReturn(List.of(new AllocatedSlot()));
        final ProvisionalBookingSlots request = slots("cs-1");
        request.setBookingId("existing-booking-1");

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(request);

        assertThat(response.getString("bookingId"), is("existing-booking-1"));
        verify(reservationService).reserveAll(eq("existing-booking-1"), anyList());
    }

    @Test
    void shouldMintABookingIdWhenNoneIsSupplied() {
        when(reservationService.reserveAll(anyString(), anyList())).thenReturn(List.of(new AllocatedSlot()));

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1"));

        assertThat(response.getString("bookingId"), is(notNullValue()));
        assertDoesNotThrow(() -> UUID.fromString(response.getString("bookingId")));
    }
```

Reuse the file's existing `slots(...)` helper. Add `java.util.UUID` and `assertDoesNotThrow` if absent.

Now the test that actually proves the feature. In `CourtScheduleRepositoryTest.java` — which runs against a real persistence context, unlike the mock-based service tests — add:

```java
    @Test
    public void shouldReleaseThePreviousPickWhenReservingAgainUnderTheSameBookingId() {
        final CourtSchedule first = slotBasedCourtSchedule(4);
        final CourtSchedule second = slotBasedCourtSchedule(4);
        final String bookingId = randomUUID().toString();

        courtScheduleRepository.saveBookedSlots(
                List.of(reservedSlotFor(bookingId, first)), false, false);
        assertThat(reload(first).getAvailableSlots(), is(3));

        courtScheduleRepository.saveBookedSlots(
                List.of(reservedSlotFor(bookingId, second)), false, false);

        assertThat("the abandoned session must get its slot back",
                reload(first).getAvailableSlots(), is(4));
        assertThat("the newly picked session must be held",
                reload(second).getAvailableSlots(), is(3));
        assertThat("only the new pick's row survives",
                allocatedListingRepository.findByHearingId(bookingId).size(), is(1));
    }
```

Adapt the fixture names to whatever this test class actually provides — it already has helpers for building slot-based court schedules and reservation rows from earlier tickets in this feature. **Read them and reuse them; do not invent new ones.** If no `reload(...)` helper exists, re-`find` the entity through the entity manager the way the file's neighbouring tests do. Report exactly which helpers you used.

This is the test that matters. The two service tests only prove the id is threaded; this one proves the capacity arithmetic of a re-pick: 4 → 3 → (release + take) → old back to 4, new at 3, one row left.

- [ ] **Step 3: Reuse or mint**

In `ProvisionalBookingService.bookProvisionalSlots`:

```java
    public JsonObject bookProvisionalSlots(final ProvisionalBookingSlots provisionalBookingSlots) {
        final String bookingId = isNotBlank(provisionalBookingSlots.getBookingId())
                ? provisionalBookingSlots.getBookingId()
                : randomUUID().toString();
        reservationService.reserveAll(bookingId, provisionalBookingSlots.getProvisionalSlots());

        return Json.createObjectBuilder()
                .add(BOOKING_ID, bookingId)
                .build();
    }
```

Use `org.apache.commons.lang3.StringUtils.isNotBlank` (static import), or an explicit null-and-blank check if that dependency is not already on this module's classpath — check before importing.

Extend that method's Javadoc with a paragraph explaining the reuse, in the style of the surrounding comments:

```
     * <p><b>Why a caller may name the bookingId.</b> When a clerk changes their mind and picks a
     * different session, the re-pick arrives under the bookingId the draft already holds. Because
     * a reservation's hearing_id is its bookingId, saveBookedSlots' hearing-wide release then
     * wipes every row of the previous pick before taking the new ones — so the abandoned session's
     * capacity comes back in the same transaction, with no second release mechanism and nothing
     * left for the nightly purge to find. An absent bookingId means a first pick, and one is
     * minted.
```

- [ ] **Step 4: Fix the misleading class Javadoc**

In `ReservationService`'s class-level Javadoc, replace:

```
 * <p>The row's hearing_id is the minted bookingId, which is unique per pick. That matters:
 * saveBookedSlots begins with a hearing-wide releaseOldAllocatedListings(hearing_id), so a
 * key shared between two picks would silently release the other's hold.
```

with:

```
 * <p>The row's hearing_id is the bookingId. That is load-bearing in two directions.
 * saveBookedSlots begins with a hearing-wide releaseOldAllocatedListings(hearing_id), so when a
 * clerk re-picks under the same bookingId, the previous pick's rows are released before the new
 * ones are taken — which is exactly how a re-pick gives back the session it abandons, with no
 * separate release call. Two different next-hearings can never collide on one id: each
 * NHCC/NHMC result line mints its own bookingReference and carries it in its own prompt.
```

Leave the following paragraph — the one explaining why `reserveAll` makes a single `saveBookedSlots` call for the whole booking rather than one per slot — **exactly as it is**. It is still true and still important.

- [ ] **Step 5: Run the tests**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*' --tests '*ProvisionalBookingServiceTest*'
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*CourtScheduleRepositoryTest*'
```

Expected: PASS throughout, including every pre-existing test. Before you accept the repository test as green, **prove it is not vacuous**: temporarily change the second `saveBookedSlots` call to use a *different* bookingId, confirm the test FAILS on the "abandoned session must get its slot back" assertion, then restore it. Put both outputs in your report — that is the evidence the feature works, and it is the only place in this task where the real mechanism is exercised.

- [ ] **Step 6: Update the API spec and build**

In `courtscheduler.create.provisional.booking.json` (example) and its schema, the field added by the superseded plan is **renamed**, not added: `replacesBookingId` → `bookingId`. It stays optional — do not put it in any `required` list. The schema description becomes:

```json
    "bookingId": {
      "description": "Reuse this booking rather than minting a new one. Sent when a clerk re-picks a session for a draft that already holds a bookingReference: because a reservation's hearing_id is its bookingId, the booking pipeline releases the previous pick's rows before taking the new ones, so the abandoned session's capacity is restored in the same transaction. Absent on a first pick, in which case an id is minted and returned.",
      "type": "string"
    }
```

Then:

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`. Finally, `grep -rn "replacesBookingId" --include=*.java --include=*.json .` (excluding `build/`) must return **nothing** — the superseded field is gone entirely.

- [ ] **Step 7: Commit** — **skipped, no commits.** Say so in your report.

---

### Task 2: hearing forwards the bookingId

**Files:** command schema + example, `BookProvisionalHearingSlotsCommandHandler`, `HearingAggregate:1261`, `BookProvisionalHearingSlots` event, `BookProvisionalHearingSlotsProcessor`, and those classes' tests. **This repo is Maven.**

**Interfaces:**
- Produces: the `POST /provisionalBooking` body gains a top-level `bookingId` string when, and only when, the command carried one. Task 1 is the consumer and is already built — the field name must match exactly.

**Read first.** Open `BookProvisionalHearingSlots.java` and read `addSlotInfoFromMap` and its comment about event replay rebuilding slots field by field. **That hazard does not apply here** — `bookingId` is a top-level field handled by the `@JsonCreator` constructor, not a map-reconstructed slot field. Add it as a constructor parameter with its own `@JsonProperty`, exactly as `bookingType` and `priority` already are.

Also read how the `duration` field (added by an earlier ticket) is handled at each hop and mirror it. The only difference: `duration` is per-slot, `bookingId` is top-level.

- [ ] **Step 1: Confirm the stopped task left nothing behind**

```bash
grep -rn "replacesBookingId" --include=*.java --include=*.json . | grep -v target
```

Expected: **no output**. An earlier agent was stopped mid-task before writing; if this finds anything, remove it before starting and say so in your report.

- [ ] **Step 2: Write the failing tests**

`BookProvisionalHearingSlotsTest.java` (in `hearing-domain-event`) — match the file's existing style:

```java
    @Test
    public void shouldCarryBookingIdThroughTheEvent() {
        final BookProvisionalHearingSlots event = BookProvisionalHearingSlots.bookProvisionalHearingSlots()
                .withHearingId(randomUUID())
                .withSlots(List.of())
                .withBookingId("existing-booking-1")
                .build();

        assertThat(event.getBookingId(), is("existing-booking-1"));
    }

    @Test
    public void shouldLeaveBookingIdNullWhenNotSupplied() {
        final BookProvisionalHearingSlots event = BookProvisionalHearingSlots.bookProvisionalHearingSlots()
                .withHearingId(randomUUID())
                .withSlots(List.of())
                .build();

        assertThat(event.getBookingId(), is(nullValue()));
    }
```

`BookProvisionalHearingSlotsProcessorTest.java` — read the file first and follow whatever capture idiom its existing tests use for the payload passed to `provisionalBookingService.bookSlots`. Two tests:

```java
    @Test
    public void shouldForwardBookingIdToCourtscheduler() {
        // build the event envelope with bookingId set, exactly as the file's existing tests
        // build theirs (they already cover the duration field the same way)
        ...
        verify(provisionalBookingService).bookSlots(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getString("bookingId"), is("existing-booking-1"));
    }

    @Test
    public void shouldOmitBookingIdWhenAbsent() {
        // same, with no bookingId on the event
        ...
        verify(provisionalBookingService).bookSlots(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().containsKey("bookingId"), is(false));
    }
```

The `containsKey(...) is false` assertion is the important one: `JsonObjectBuilder.add` throws on a null value, so "omitted" and "null" are not interchangeable, and courtscheduler's schema has no null type.

- [ ] **Step 3: Run them to verify they fail**

```bash
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor -am test
```

Expected: compilation failure first (no `withBookingId` / `getBookingId`), then — once Step 4 adds the event field — genuine assertion failures from the processor tests. Record the assertion failures as the red, not the compile error.

- [ ] **Step 4: Thread the field through**

**a.** `BookProvisionalHearingSlots.java`: add `private final String bookingId;`, a `@JsonProperty("bookingId") final String bookingId` parameter on the `@JsonCreator` constructor (assigned alongside `bookingType`), a `getBookingId()` getter, and a `withBookingId(...)` builder method with its matching builder field, passed through `build()`.

**b.** `BookProvisionalHearingSlotsCommandHandler.bookProvisionalHearingSlots`, beside the existing `bookingType` / `priority` reads:

```java
        final String bookingId = envelope.payloadAsJsonObject().getString("bookingId", null);
```

and pass it as the final argument to `hearingAggregate.bookProvisionalHearingSlots(...)`.

**c.** `HearingAggregate.java:1261` — add a trailing `final String bookingId` parameter and `.withBookingId(bookingId)` to the builder chain. Change nothing else about the method.

**d.** `BookProvisionalHearingSlotsProcessor.handleBookProvisionalHearingSlots` — the payload construction becomes:

```java
        final JsonObjectBuilder payloadBuilder = createObjectBuilder().add("provisionalSlots", arrayBuilder.build());
        // Omit rather than send null: JsonObjectBuilder.add rejects nulls, and courtscheduler
        // treats an absent bookingId as "first pick, mint one".
        if (StringUtils.isNotBlank(bookProvisionalHearingSlots.getBookingId())) {
            payloadBuilder.add("bookingId", bookProvisionalHearingSlots.getBookingId());
        }
        final JsonObject payload = payloadBuilder.build();
```

`StringUtils` is already imported in that file.

**e.** `schema/hearing.book-provisional-hearing-slots.json` has `"additionalProperties": false`, so an undeclared field makes the whole command fail validation. Add as a sibling of `bookingType`, and **not** to `required`:

```json
    "bookingId": {
      "description": "Reuse this booking rather than minting a new one. Sent when a clerk re-picks a session for a draft that already holds a bookingReference; courtscheduler then releases the previous pick's hold before taking the new sessions. Absent on a first pick. Passed straight through — hearing never invents or validates it.",
      "type": "string"
    }
```

Add it to the example `hearing.book-provisional-hearing-slots.json` too, matching that file's existing shape.

- [ ] **Step 5: Run the tests**

```bash
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor,hearing-command/hearing-command-handler -am test
```

Expected: PASS, including pre-existing tests. `HearingAggregate`'s signature changed, so every caller must be updated — run `grep -rn "bookProvisionalHearingSlots(" --include=*.java . | grep -v target` and fix each test call site by passing `null`. List every one you touched in your report.

- [ ] **Step 6: Full build**

```bash
mvn clean install -DskipTests
```

Expected: `BUILD SUCCESS` for the whole reactor.

- [ ] **Step 7: Commit** — **skipped, no commits.** Say so in your report.

---

## Deliberately not in scope

- **The front end.** Both pickers have `resultLine` in scope, so the previous value is `resultLine.resultPrompts.find(p => p.promptRef === 'bookingReference')?.value`, sent as `bookingId` on `bookProvisionalHearingSlots`. The returned id is then the same value, so writing it back into the prompt is a harmless no-op. **UI tickets are deferred by standing instruction** — this is NEW-15's FE half and gets its own ticket. Until it ships the backend accepts the field and nobody sends it, which is exactly the mint-a-new-one path.
- **NEW-12's other cases** — result-line delete and reset-results still strand a hold until 01:00, because no new pick follows them to carry a bookingId. NEW-15 shrinks NEW-12 to those two cases; it does not retire it.
- **Caller-supplied ids as a trust question.** A request could name another draft's booking and release its hold. It is an internal service behind platform auth and the ids are UUIDs, so this is accepted rather than defended against — but it is a real difference from minting server-side and belongs in the ticket, not in code.
- **`releaseOldAllocatedListings` and `saveBookedSlots`** — unchanged. The whole point is that they already do this.
