# Reserve a Slot — NEW-15: re-picking a session wipes the previous hold

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** When a clerk changes their mind and picks a different session, the hold on the session they abandoned is released in the same transaction that takes the new one — so a clerk can never hold two sessions for one next-hearing.

**Architecture:** The reserve request carries one new optional field, `replacesBookingId` — the `bookingReference` the draft already holds. `ReservationService.reserveAll` releases that booking's hold first, then reserves the new sessions, inside the existing `@Transactional` boundary. One field travelling the same chain `duration` travelled in NEW-8: browser → hearing command → domain event → processor → courtscheduler. No new table, no new column, no migration.

**Tech Stack:** Java 17. **cpp-context-listing-courtscheduler is Gradle. cpp-context-hearing is Maven.** Never mix them up.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6, and NEW-15 in `cpp-context-hearing/docs/reserve-a-slot-jira.md`

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. Everything stays as unstaged working-tree edits. Any "Commit" step in a task is an explicit no-op — read it, skip it, say so in your report.
- **Nothing in this feature is committed**, so `git diff` in either repo shows tens of files of prior, already-reviewed tickets. Do not revert, tidy, or comment on any of it.
- `replacesBookingId` is **optional everywhere** — nullable in Java, absent from every `required` list, and **omitted rather than sent as null** on every JSON hop. `JsonObjectBuilder.add` throws on a null value.
- Hearing is a **pure pass-through**. It never invents, defaults, validates or resolves a `replacesBookingId`. It forwards what it was given, or forwards nothing.
- An unknown, already-purged, already-confirmed or legacy `replacesBookingId` is a **silent no-op**, never an error. "Nothing to release" is the normal case for a first pick.

## Deployment ordering (must be in the ticket)

`ProvisionalSlotConverter` uses a bare `new ObjectMapper()`, so `FAIL_ON_UNKNOWN_PROPERTIES` is **on**. If hearing ships first and starts sending `replacesBookingId` to a courtscheduler that does not know the field, **every provisional booking fails**. Task 1 (courtscheduler, the receiver) must deploy before Task 2 (hearing, the sender). Build them in that order too.

---

## Why this is needed at all, and why it is not free

Today, re-picking for magistrates writes fresh `provisional_booking` rows under a new bookingId and leaves the old rows in place — `deleteProvisionalBooking` only runs at confirm. Nobody noticed, because a provisional booking **holds no capacity**: the stale rows are inert. Reservations take that free property away. Every stale hold is now a real slot off a real session, held until the 01:00 purge. So "one hearing, one held session" must become deliberate.

**Why not simply release by hearing id**, the way `saveBookedSlots` already does with `releaseOldAllocatedListings(hearing_id)`? Because the `hearingId` in the booking request is the **current** hearing — the one being resulted — and that hearing owns the `allocated_listings` rows for the session it is sitting in *today*. Releasing by it would free the current hearing's own listing. The hearing we would want to key on is the **next** hearing, which has no id yet; minting one was the design dropped with LPT-2456/2474/2457. Hence carrying the previous bookingId instead.

**What makes it structurally safe:** `releaseOldAllocatedListings` is keyed on `hearing_id`. A reservation's `hearing_id` *is* its bookingId, but a **confirmed** row's `hearing_id` is the real next-hearing id, with the bookingId in `booking_id`. So passing an already-shared bookingId as `replacesBookingId` cannot match a confirmed row, and a shared booking can never be released by this path. Task 1 pins that with a test rather than trusting the argument.

---

## File Structure

**Task 1 — cpp-context-listing-courtscheduler (Gradle), branch `team/ras`**

| File | Change |
|---|---|
| `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/ProvisionalBookingSlots.java` | **Modify** — add `replacesBookingId` field + accessors |
| `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationService.java` | **Modify** — 3-arg `reserveAll` overload that releases first |
| `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java` | **Modify** — one line, pass the field through |
| `listingcourtscheduler-api/src/raml/json/courtscheduler.create.provisional.booking.json` | **Modify** — example |
| `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.create.provisional.booking.json` | **Modify** — optional property |
| `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationServiceTest.java` | **Modify** — 4 new tests |

**Task 2 — cpp-context-hearing (Maven), branch `team/ccsph2`**

| File | Change |
|---|---|
| `hearing-command/hearing-command-api/src/raml/json/schema/hearing.book-provisional-hearing-slots.json` | **Modify** — `additionalProperties: false`, so the field MUST be declared or the command is rejected |
| `hearing-command/hearing-command-api/src/raml/json/hearing.book-provisional-hearing-slots.json` | **Modify** — example |
| `hearing-command/hearing-command-handler/src/main/java/uk/gov/moj/cpp/hearing/command/handler/BookProvisionalHearingSlotsCommandHandler.java` | **Modify** — read the field |
| `hearing-domain/hearing-domain-aggregate/src/main/java/uk/gov/moj/cpp/hearing/domain/aggregate/HearingAggregate.java:1261` | **Modify** — one extra parameter |
| `hearing-domain/hearing-domain-event/src/main/java/uk/gov/moj/cpp/hearing/domain/event/BookProvisionalHearingSlots.java` | **Modify** — field, `@JsonProperty`, getter, builder |
| `hearing-event/hearing-event-processor/src/main/java/uk/gov/moj/cpp/hearing/event/BookProvisionalHearingSlotsProcessor.java` | **Modify** — forward, omitting when null |
| the three touched classes' test files | **Modify** — see task |

**Build commands**

```bash
# Task 1 — courtscheduler, GRADLE
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*'
./gradlew build -x test

# Task 2 — hearing, MAVEN
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor,hearing-command/hearing-command-handler -am test
mvn clean install -DskipTests
```

---

### Task 1: courtscheduler releases the named booking before reserving

**Files:** as listed above for Task 1.

**Interfaces:**
- Consumes: `CourtScheduleRepository.releaseOldAllocatedListings(String hearingId)` — the existing three-step release (delete rows → `releaseCourtScheduleAllocatedSlotsForBookingId` → `releaseAllocatedSlotsOrDurationFromCourtSchedule`). A bare delete would leak capacity permanently; do not write one.
- Produces: `ReservationService.reserveAll(String bookingId, List<ProvisionalSlot> slots, String replacesBookingId)` → `List<AllocatedSlot>`. **The existing 2-arg `reserveAll(bookingId, slots)` must survive as a delegating overload passing `null`** — `ReservationService.reserve(...)` and several existing tests call it, and they must keep compiling untouched. Request field name is `replacesBookingId`, a top-level sibling of `provisionalSlots`, never inside a slot.

**Read first.** Open `ReservationService.java` and read the class Javadoc and `reserveAll` in full. The "why one call per booking, not one per slot" paragraph explains that `saveBookedSlots` opens with a hearing-wide `releaseOldAllocatedListings(hearing_id)` keyed on the minted bookingId. Your new release is a *second, different* release keyed on the **previous** bookingId, and it must happen **before** `saveBookedSlots`, not inside the slot loop.

- [ ] **Step 1: Write the failing tests**

Add to `ReservationServiceTest.java`. Read the file first and reuse its existing fixtures and mock names (`courtScheduleRepository`, `allocatedListingRepository`, `slotRequest(...)`, `SESSION_ID`, `BOOKING_ID`) rather than inventing new ones. Mockito strict stubs are in force: stub only what each path reaches.

```java
    @Test
    void shouldReleaseThePreviousHoldBeforeReservingTheNewSessions() {
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)), "old-booking-1");

        final InOrder inOrder = inOrder(courtScheduleRepository);
        inOrder.verify(courtScheduleRepository).releaseOldAllocatedListings("old-booking-1");
        inOrder.verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldNotReleaseAnythingWhenNoPreviousBookingIsNamed() {
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)), null);

        verify(courtScheduleRepository, never()).releaseOldAllocatedListings(anyString());
    }

    @Test
    void shouldNotReleaseAnythingWhenThePreviousBookingIdIsBlank() {
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)), "   ");

        verify(courtScheduleRepository, never()).releaseOldAllocatedListings(anyString());
    }

    @Test
    void shouldNeverReleaseTheNewBookingItself() {
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)), BOOKING_ID);

        verify(courtScheduleRepository, never()).releaseOldAllocatedListings(BOOKING_ID);
    }
```

The ordering assertion in the first test is the one that matters: releasing *after* `saveBookedSlots` would hand the slot back after taking it and leave the session over-credited.

The last test guards a real foot-gun. If the caller ever passes the new bookingId as `replacesBookingId`, releasing it would undo the reservation the same call is about to make — and because `saveBookedSlots` itself opens with a release keyed on that id, the damage would be invisible. Reject the self-reference rather than trusting callers.

Add whatever of `inOrder`, `never`, `anyString`, `anyBoolean`, `anyList` the file does not already import statically from `org.mockito.Mockito` / `org.mockito.ArgumentMatchers`, plus `org.mockito.InOrder`.

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*'
```

Expected: **compilation failure** — no 3-arg `reserveAll`. That is a legitimate first red: the tests cannot express the behaviour without the signature. Add only the signature (Step 3a), re-run, and record *that* run — genuine assertion failures — as the red.

- [ ] **Step 3a: Add the overload and the release**

In `ReservationService.java`, keep the existing 2-arg method as a delegate and put the work in a 3-arg one:

```java
    /**
     * Reserves under a freshly minted bookingId with no previous hold to give back — the first
     * pick on a draft. Kept so {@link #reserve} and existing callers need no change.
     */
    @Transactional
    public List<AllocatedSlot> reserveAll(final String bookingId, final List<ProvisionalSlot> requestedSlots) {
        return reserveAll(bookingId, requestedSlots, null);
    }

    /**
     * Reserves every requested session under one bookingId, first giving back the hold the clerk
     * is abandoning.
     *
     * <p><b>Why the caller names the booking to release rather than us deriving it.</b> A
     * reservation is keyed on its own minted bookingId, so there is nothing in
     * {@code allocated_listings} tying two successive picks of the same draft together. The
     * request's {@code hearingId} is the <em>current</em> hearing — the one being resulted, which
     * owns the rows for the session it is sitting in today — so releasing by it would free the
     * current hearing's own listing. The draft already holds the previous bookingId in its
     * {@code bookingReference} prompt, so the caller passes it here and the release and the new
     * reservation share one transaction.
     *
     * <p>Releasing an unknown, already-purged or legacy bookingId is a no-op, which is the normal
     * case for a first pick. An <em>already-shared</em> booking cannot be released here either,
     * and structurally so: {@code releaseOldAllocatedListings} is keyed on {@code hearing_id},
     * and a confirmed row carries the real next-hearing id there, with the bookingId in
     * {@code booking_id}.
     */
    @Transactional
    public List<AllocatedSlot> reserveAll(final String bookingId,
                                          final List<ProvisionalSlot> requestedSlots,
                                          final String replacesBookingId) {

        releasePreviousHold(bookingId, replacesBookingId);

        guardAgainstConfirmedAllocation(bookingId);

        final List<AllocatedSlot> slots = new ArrayList<>();
        for (final ProvisionalSlot requested : requestedSlots) {
            slots.add(toReservedSlot(bookingId, requested));
        }

        final Result result = courtScheduleRepository.saveBookedSlots(slots, false, false);
        if (!result.isSuccess()) {
            throw new NoCapacityException(
                    "Could not reserve sessions for booking " + bookingId + ": " + result.getMsg());
        }
        return slots;
    }

    /**
     * Gives back the hold the clerk is abandoning, through the same three-step release used
     * everywhere else — delete the rows, release the court schedule's allocated slots for the
     * booking, then restore the slot or duration on the session. A bare delete would leave the
     * capacity consumed forever.
     *
     * <p>Self-reference is rejected rather than trusted: releasing the bookingId this call is
     * about to reserve under would undo its own work, and invisibly, since
     * {@code saveBookedSlots} opens with a release keyed on that same id.
     */
    private void releasePreviousHold(final String bookingId, final String replacesBookingId) {
        if (replacesBookingId == null || replacesBookingId.isBlank() || replacesBookingId.equals(bookingId)) {
            return;
        }
        courtScheduleRepository.releaseOldAllocatedListings(replacesBookingId);
    }
```

- [ ] **Step 3b: Carry the field in**

`ProvisionalBookingSlots.java` — add beside `provisionalSlots`:

```java
    private String replacesBookingId;

    public String getReplacesBookingId() {
        return replacesBookingId;
    }

    public void setReplacesBookingId(final String replacesBookingId) {
        this.replacesBookingId = replacesBookingId;
    }
```

`ProvisionalBookingService.bookProvisionalSlots` — the single call becomes:

```java
        reservationService.reserveAll(bookingId,
                provisionalBookingSlots.getProvisionalSlots(),
                provisionalBookingSlots.getReplacesBookingId());
```

Change nothing else in that method.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*'
./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest*'
```

Expected: PASS, including every pre-existing test in both classes. `ProvisionalBookingServiceTest` stubs `reserveAll(anyString(), anyList())` — the 2-arg form — so watch whether those stubs still match after the production call site moved to the 3-arg form. **If they no longer match, update the stubs to the 3-arg form; do not re-point the production code at the 2-arg method to keep a test happy.** Report exactly what you changed there.

- [ ] **Step 5: Update the API spec and build**

`courtscheduler.create.provisional.booking.json` (example) — add the new field above `provisionalSlots`:

```json
{
  "replacesBookingId": "9f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f",
  "provisionalSlots" : [
    {
      "courtScheduleId": "f2ea88af-5cd9-339c-8e2c-405df1f55ea6",
      "hearingStartTime": "2020-01-01T11:00:00.000Z",
      "duration": 60
    },
    {
      "courtScheduleId": "074d6f74-6e16-3351-8592-b083d02a3d80",
      "hearingStartTime": "2020-01-01T11:00:00.000Z",
      "duration": 60
    }
  ]
}
```

`schema/courtscheduler.create.provisional.booking.json` — add the property as a sibling of `provisionalSlots`, inside the top-level `properties` object. Do **not** add it to any `required` list:

```json
    "replacesBookingId": {
      "description": "The bookingId whose hold this pick replaces, when the clerk is changing a session they already picked. Its hold is released in the same transaction that takes the new sessions. Absent on a first pick. An unknown, already-purged or already-shared id is a no-op.",
      "type": "string"
    }
```

Then:

```bash
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 6: Commit** — **skipped, no commits.** Say so in your report.

---

### Task 2: hearing forwards the previous bookingId

**Files:** as listed above for Task 2. **This repo is Maven.**

**Interfaces:**
- Produces: the `POST /provisionalBooking` body gains a top-level `replacesBookingId` string when, and only when, the command carried one. Task 1 is the consumer and is already built — the field name must match it exactly.
- Consumes: nothing new.

**Read first.** Open `BookProvisionalHearingSlots.java` and read `addSlotInfoFromMap` and its comment about event replay rebuilding slots field by field. **That hazard does not apply here** — `replacesBookingId` is a top-level field handled by the `@JsonCreator` constructor, not one of the map-reconstructed slot fields. Add it as a constructor parameter with its own `@JsonProperty`, exactly as `bookingType` and `priority` already are, and it replays correctly.

- [ ] **Step 1: Write the failing tests**

`BookProvisionalHearingSlotsTest.java` (in `hearing-domain-event`) — read the file and match its existing style:

```java
    @Test
    public void shouldCarryReplacesBookingIdThroughTheEvent() {
        final BookProvisionalHearingSlots event = BookProvisionalHearingSlots.bookProvisionalHearingSlots()
                .withHearingId(randomUUID())
                .withSlots(List.of())
                .withReplacesBookingId("old-booking-1")
                .build();

        assertThat(event.getReplacesBookingId(), is("old-booking-1"));
    }

    @Test
    public void shouldLeaveReplacesBookingIdNullWhenNotSupplied() {
        final BookProvisionalHearingSlots event = BookProvisionalHearingSlots.bookProvisionalHearingSlots()
                .withHearingId(randomUUID())
                .withSlots(List.of())
                .build();

        assertThat(event.getReplacesBookingId(), is(nullValue()));
    }
```

`BookProvisionalHearingSlotsProcessorTest.java` — read the file first; it already captures the payload sent to `provisionalBookingService.bookSlots`. Follow whatever capture idiom it uses:

```java
    @Test
    public void shouldForwardReplacesBookingIdToCourtscheduler() {
        // build the event envelope with replacesBookingId set, as the file's existing tests do
        ...
        verify(provisionalBookingService).bookSlots(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().getString("replacesBookingId"), is("old-booking-1"));
    }

    @Test
    public void shouldOmitReplacesBookingIdWhenAbsent() {
        // same, with no replacesBookingId on the event
        ...
        verify(provisionalBookingService).bookSlots(payloadCaptor.capture());
        assertThat(payloadCaptor.getValue().containsKey("replacesBookingId"), is(false));
    }
```

The `containsKey(...) is false` assertion is the important one: `JsonObjectBuilder.add` throws on a null value, so "omitted" and "null" are not interchangeable here, and courtscheduler's schema has no null type.

- [ ] **Step 2: Run them to verify they fail**

```bash
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor -am test
```

Expected: compilation failure first (no `withReplacesBookingId` / `getReplacesBookingId`), then — after Step 3a adds the event field — genuine assertion failures from the processor tests. Record the assertion failures as the red.

- [ ] **Step 3a: Add the field to the event**

In `BookProvisionalHearingSlots.java`: add `private final String replacesBookingId;`, a `@JsonProperty("replacesBookingId") final String replacesBookingId` parameter on the `@JsonCreator` constructor (assign it alongside `bookingType`), a `getReplacesBookingId()` getter, and a `withReplacesBookingId(...)` builder method with its matching builder field, passing it through `build()`.

- [ ] **Step 3b: Read it in the command handler**

In `BookProvisionalHearingSlotsCommandHandler.bookProvisionalHearingSlots`, beside the existing `bookingType` / `priority` reads:

```java
        final String replacesBookingId = envelope.payloadAsJsonObject().getString("replacesBookingId", null);
```

and pass it as the final argument to `hearingAggregate.bookProvisionalHearingSlots(...)`.

- [ ] **Step 3c: Thread it through the aggregate**

`HearingAggregate.java:1261` — add a trailing `final String replacesBookingId` parameter and `.withReplacesBookingId(replacesBookingId)` to the builder chain. Change nothing else about the method.

- [ ] **Step 3d: Forward it from the processor**

In `BookProvisionalHearingSlotsProcessor.handleBookProvisionalHearingSlots`, the payload construction becomes:

```java
        final JsonObjectBuilder payloadBuilder = createObjectBuilder().add("provisionalSlots", arrayBuilder.build());
        // Omit rather than send null: JsonObjectBuilder.add rejects nulls, and courtscheduler
        // treats an absent replacesBookingId as "first pick, nothing to give back".
        if (StringUtils.isNotBlank(bookProvisionalHearingSlots.getReplacesBookingId())) {
            payloadBuilder.add("replacesBookingId", bookProvisionalHearingSlots.getReplacesBookingId());
        }
        final JsonObject payload = payloadBuilder.build();
```

`StringUtils` is already imported in this file.

- [ ] **Step 3e: Update the command API schema and example**

`schema/hearing.book-provisional-hearing-slots.json` has `"additionalProperties": false`, so an undeclared field makes the whole command fail validation. Add as a sibling of `bookingType`, and **not** to `required`:

```json
    "replacesBookingId": {
      "description": "The bookingId whose hold this pick replaces, when the clerk is changing a session they already picked. Passed straight through to courtscheduler, which releases that hold in the same transaction that takes the new sessions. Absent on a first pick.",
      "type": "string"
    }
```

Add it to the example `hearing.book-provisional-hearing-slots.json` too, matching that file's existing shape.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
mvn -q -pl hearing-domain/hearing-domain-event,hearing-event/hearing-event-processor,hearing-command/hearing-command-handler -am test
```

Expected: PASS, including pre-existing tests. `HearingAggregate`'s signature changed, so anything calling `bookProvisionalHearingSlots(...)` must be updated — find every caller (`grep -rn "bookProvisionalHearingSlots(" --include=*.java`) and fix test call sites by passing `null`. Report each one you touched.

- [ ] **Step 5: Full build**

```bash
mvn clean install -DskipTests
```

Expected: `BUILD SUCCESS` for the whole reactor.

- [ ] **Step 6: Commit** — **skipped, no commits.** Say so in your report.

---

## Deliberately not in scope

- **The front end.** Both pickers already have `resultLine` in scope (they destructure `promptChoices` from it), so the previous value is `resultLine.resultPrompts.find(p => p.promptRef === 'bookingReference')?.value`, sent as `replacesBookingId` on `bookProvisionalHearingSlots`. **UI tickets are deferred by standing instruction** — this is NEW-15's FE half and gets its own ticket. Until it ships, the backend accepts the field and nobody sends it, which is exactly the no-op first-pick path.
- **NEW-12's other cases** — result-line delete and reset-results still strand a hold until 01:00, because no new pick follows them to carry a `replacesBookingId`. NEW-15 shrinks NEW-12 to those two cases; it does not retire it.
- **`releaseOldAllocatedListings` itself** — unchanged, and its three-step shape is exactly what this relies on.
- **The purge job** — still the backstop, and still required.
