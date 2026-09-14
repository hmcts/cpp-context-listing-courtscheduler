# Reserve a Slot — DEC-1: an already-shared booking must not look expired (NEW-7a)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `GET /provisionalBooking/status` distinguish *"the hold expired"* from *"this draft was already shared"*, so the pre-share gate stops blocking a legitimate re-share.

**Architecture:** No new table, no tombstone, no new query path. `allocated_listings.booking_id` already carries the answer, and since BUG-3 the confirmed row written at share time is stamped with it. A reservation row and a confirmed row are structurally disjoint — the reservation puts the bookingId in `hearing_id` and leaves `booking_id` NULL; the confirmed row puts the real hearing id in `hearing_id` and the bookingId in `booking_id` — so one derived finder, `findByBookingId`, returns confirmed rows and nothing else. `hasLiveHold` grows from a two-way boolean into a four-way status.

**Tech Stack:** Java 17, Spring Data JPA, JUnit 5 + Mockito (strict stubs), jakarta.json, **Gradle** (this repo is Gradle — never Maven).

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6 (section 04), and DEC-1 in `cpp-context-hearing/docs/reserve-a-slot-jira.md`

**Repo:** `cpp-context-listing-courtscheduler`, branch `team/ras`

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. All work stays as unstaged working-tree edits. Step 6 of the task is therefore a no-op — read it, skip it, say so in your report.
- **Gradle only.** `./gradlew`, never `mvn`.
- This is a **response-contract change** to an endpoint NEW-7 already delivered. That is the whole point of it being its own ticket: it needs its own retest.
- **Do not touch** `fetchProvisionalSlots`, `bookProvisionalSlots`, `ReservationService`, or anything in `CourtScheduleRepositoryImpl`. The only production file that changes is `ProvisionalBookingService`, plus one finder declaration and the two API spec files.
- The four statuses are exactly `RESERVED`, `SHARED`, `LEGACY`, `NONE`. No others, no nulls.

---

## Why the boolean is renamed rather than kept

NEW-7 shipped `{"bookingId": "...", "live": true}`. `live` honestly means *"a hold exists"*. The gate needs a different question answered — *"is this draft safe to share?"* — and those two answers diverge on exactly the case DEC-1 is about: after a share, the hold is gone (`live: false`) but sharing again is perfectly safe.

Keeping `live` and adding `status` beside it would leave a field in the response that a caller can reasonably read on its own and get DEC-1's bug back. So `live` becomes **`safeToShare`**, and `status` says why. Nothing consumes the endpoint yet — NEW-10 and NEW-14 are both unstarted — so the rename costs nothing today and cannot be done later.

| `status` | Meaning | `safeToShare` |
|---|---|---|
| `RESERVED` | A reservation row exists (`expires_at` non-null, keyed on `hearing_id`). The hold is live and the result has not been shared. | `true` |
| `SHARED`   | A confirmed row (`expires_at IS NULL`) carries this `booking_id`. The result was shared; the hold was correctly released by BUG-3. | `true` |
| `LEGACY`   | No reservation, but a `provisional_booking` row exists. A draft saved before reserve-a-slot shipped. Cannot tell shared from unshared, and does not need to. | `true` |
| `NONE`     | Nothing found. The hold expired and was purged. | `false` |

---

## File Structure

| File | Responsibility | Change |
|---|---|---|
| `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/AllocatedListingRepository.java` | Spring Data finders over `allocated_listings` | **Modify** — one derived finder declaration beside `findByHearingId` |
| `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java` | The booking-status answer | **Modify** — `hasLiveHold` becomes `statusOf`; `getBookingStatus` emits two fields |
| `listingcourtscheduler-api/src/raml/json/courtscheduler.get.booking-status.json` | Response example | **Modify** |
| `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.get.booking-status.json` | Response schema | **Modify** |
| `listingcourtscheduler-api/src/raml/courtscheduler-api.raml:514-535` | Endpoint description | **Modify** — description only |
| `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingServiceTest.java` | Unit tests | **Modify** — 3 existing status tests updated, 2 added |

`courtscheduler-api.openapi.yml` needs **no change**: `CourtschedulerGetBookingStatus` is a `$ref` to `PassthroughObject` (line 820). Confirm this yourself before concluding it; do not take it on faith.

**Build and test commands**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest*'
./gradlew :listingcourtscheduler-api:build -x test
./gradlew build -x test
```

---

### Task 1: A shared booking reports SHARED, not expired

**Files:**
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/AllocatedListingRepository.java:49` (add beside `findByHearingId`)
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java:103-156`
- Modify: `listingcourtscheduler-api/src/raml/json/courtscheduler.get.booking-status.json`
- Modify: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.get.booking-status.json`
- Modify: `listingcourtscheduler-api/src/raml/courtscheduler-api.raml` (the `/provisionalBooking/status` description block only)
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingServiceTest.java`

**Interfaces:**
- Consumes: `AllocatedListingRepository.findByHearingId(String)` → `List<AllocatedListing>` (exists, line 49); `ProvisionalBookingRepository.findByBookingIdIn(List<String>)` (exists); `AllocatedListing.getExpiresAt()` → `LocalDate`, `AllocatedListing.getBookingId()` → `String` (entity `uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing`, `@Column(name = "booking_id")` line 27).
- Produces: `AllocatedListingRepository.findByBookingId(String bookingId)` → `List<AllocatedListing>`. Response shape `{"bookings": [{"bookingId": String, "safeToShare": boolean, "status": String}]}`. **NEW-10 consumes this shape verbatim** — it is a pass-through, so any drift here is drift in listing's query API too.

**Read first.** Open `ProvisionalBookingService.java` lines 103-156 and read the whole Javadoc on `getBookingStatus` before changing anything. Its final paragraph documents precisely the limitation this task removes — that paragraph must go, replaced by the new contract, not left contradicting the code.

Then confirm the premise for yourself, because the whole design rests on it: `ReservationService.toReservedSlot` (around line 136) sets `slot.setHearingId(bookingId)` and **never** calls `setBookingId`, while `CourtScheduleRepositoryImpl.saveAllocatedListing` (around line 2331) does `allocatedListing.setBookingId(allocatedSlot.getBookingId())`. If you find a path that writes `booking_id` onto a row that also has a non-null `expires_at`, **stop and report it** — `findByBookingId` would then return reservations too and the task needs a different shape.

- [ ] **Step 1: Write the failing tests**

Add to `ProvisionalBookingServiceTest.java`, beside the existing status tests. The file uses `@ExtendWith(MockitoExtension.class)` with strict stubs, so stub only what each test's path actually reaches — an over-stub fails as `UnnecessaryStubbingException`.

First, a helper beside the existing `aCourtSchedule` / `aLegacyProvisionalBooking` helpers:

```java
    private AllocatedListing aConfirmedRowFor(final String bookingId, final String realHearingId) {
        final AllocatedListing confirmed = new AllocatedListing();
        confirmed.setCourtScheduleId("cs-1");
        confirmed.setHearingId(realHearingId);
        confirmed.setBookingId(bookingId);
        confirmed.setExpiresAt(null);
        return confirmed;
    }
```

Then the two new tests:

```java
    @Test
    void shouldReportAnAlreadySharedBookingAsSharedAndSafeToShare() {
        when(allocatedListingRepository.findByHearingId("bk-shared")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("bk-shared"))
                .thenReturn(List.of(aConfirmedRowFor("bk-shared", "real-hearing-1")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-shared")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("SHARED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldPreferTheReservationWhenABookingSomehowHasBoth() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-both");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-both")).thenReturn(List.of(reservation));
        // Lenient by design: this stub builds the "both rows exist" world the test name describes.
        // A correct statusOf short-circuits on the reservation and never consumes it, which is the
        // behaviour the never() verification below pins.
        lenient().when(allocatedListingRepository.findByBookingId("bk-both"))
                .thenReturn(List.of(aConfirmedRowFor("bk-both", "real-hearing-2")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-both")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
        verify(allocatedListingRepository, never()).findByBookingId("bk-both");
    }
```

Add the `lenient` static import (`org.mockito.Mockito.lenient`); `never` and `verify` are already imported in this file.

The second test is the ordering guard. A live hold outranks a confirmed row: if both somehow exist the clerk still holds capacity, and reporting `SHARED` would let a second share through against a hold that is still consuming a slot.

**Both halves of that test are load-bearing, and neither is free.** The `lenient()` stub is what makes the scenario a real "both" rather than a duplicate of the plain RESERVED test — without it the test constructs no confirmed row at all and pins nothing. The `never()` verification is what pins the short-circuit: strict stubs do **not** do this for you, because `UnnecessaryStubbingException` fires only for a *configured but unused* stub, never for an *unstubbed call*. And the stub must be `lenient()` precisely because a correct implementation leaves it unconsumed — a plain `when(...)` here would fail against correct production code.

Prove the test earns its place: temporarily reorder `statusOf` so the `findByBookingId` check runs first, confirm this test FAILS, then restore the order and confirm it passes. Put both outputs in your report.

Now update the three existing status tests for the renamed field. **Update, do not delete** — each still asserts something real:

```java
    @Test
    void shouldReportABookingWithAReservationAsReserved() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-live");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-live")).thenReturn(List.of(reservation));

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-live")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("RESERVED"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }

    @Test
    void shouldReportAPurgedBookingAsNone() {
        when(allocatedListingRepository.findByHearingId("bk-gone")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("bk-gone")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("bk-gone"))).thenReturn(List.of());

        final JsonObject booking = provisionalBookingService.getBookingStatus("bk-gone")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("NONE"));
        assertThat(booking.getBoolean("safeToShare"), is(false));
    }

    @Test
    void shouldReportALegacyProvisionalBookingAsLegacy() {
        when(allocatedListingRepository.findByHearingId("legacy-bk")).thenReturn(List.of());
        when(allocatedListingRepository.findByBookingId("legacy-bk")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("legacy-bk")))
                .thenReturn(List.of(aLegacyProvisionalBooking("legacy-bk", "cs-9")));

        final JsonObject booking = provisionalBookingService.getBookingStatus("legacy-bk")
                .getJsonArray("bookings").getJsonObject(0);

        assertThat(booking.getString("status"), is("LEGACY"));
        assertThat(booking.getBoolean("safeToShare"), is(true));
    }
```

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest*'
```

Expected: **compilation failure** — `findByBookingId` does not exist on the repository. That is a legitimate red: the test cannot express the behaviour without the finder. Add only the finder declaration (Step 3a), re-run, and you should then get a genuine assertion failure — `No such key: 'status'` / `getString` on a missing key — from all five tests. Record *that* output as the red, not the compile error.

- [ ] **Step 3a: Add the finder**

In `AllocatedListingRepository.java`, directly beneath `findByHearingId` (line 49), matching the surrounding derived-query style:

```java
    /**
     * Confirmed rows only, by construction. A reservation carries its bookingId in
     * {@code hearing_id} and leaves {@code booking_id} null (see
     * {@code ReservationService.toReservedSlot}); only the row written when a booking is
     * confirmed is stamped with {@code booking_id}. So a non-empty result here means the
     * booking was shared, with no {@code expires_at} filter needed to say so.
     */
    List<AllocatedListing> findByBookingId(String bookingId);
```

- [ ] **Step 3b: Replace the boolean with a status**

In `ProvisionalBookingService.java`, add the status constants beside the existing `BOOKING_ID` field (line 34):

```java
    private static final String STATUS = "status";
    private static final String SAFE_TO_SHARE = "safeToShare";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_SHARED = "SHARED";
    private static final String STATUS_LEGACY = "LEGACY";
    private static final String STATUS_NONE = "NONE";
```

Replace the body of `getBookingStatus`'s loop:

```java
        final JsonArrayBuilder bookings = Json.createArrayBuilder();
        for (final String bookingId : bookingIdList) {
            final String status = statusOf(bookingId);
            bookings.add(Json.createObjectBuilder()
                    .add(BOOKING_ID, bookingId)
                    .add(SAFE_TO_SHARE, !STATUS_NONE.equals(status))
                    .add(STATUS, status));
        }
        return Json.createObjectBuilder().add("bookings", bookings).build();
```

and replace `hasLiveHold` entirely with:

```java
    /**
     * Resolves a booking id to one of four states, in precedence order.
     *
     * <p><b>Order matters.</b> A live reservation outranks a confirmed row: if a booking somehow
     * carried both, the clerk is still holding capacity, and reporting it as already shared would
     * wave a second share through against a hold that is still consuming a slot.
     *
     * <p>Each check is a separate round trip rather than one batched lookup, matching the existing
     * shape of this method — it needs only a per-id verdict, not the session detail that justifies
     * batching in {@link #fetchProvisionalSlots}.
     */
    private String statusOf(final String bookingId) {
        final boolean hasReservation = allocatedListingRepository.findByHearingId(bookingId).stream()
                .anyMatch(ProvisionalBookingService::isReservation);
        if (hasReservation) {
            return STATUS_RESERVED;
        }
        // The confirmed row BUG-3 stamps at share time. Its mere existence is the answer: a
        // reservation never carries booking_id, so nothing expiring can appear here.
        if (!allocatedListingRepository.findByBookingId(bookingId).isEmpty()) {
            return STATUS_SHARED;
        }
        // Drafts saved before reserve-a-slot shipped. findByBookingIdIn does not filter on the
        // active flag, so a soft-deleted row still answers — which is what we want: a legacy
        // draft is safe to share whether or not it already was.
        if (!provisionalBookingRepository.findByBookingIdIn(List.of(bookingId)).isEmpty()) {
            return STATUS_LEGACY;
        }
        return STATUS_NONE;
    }
```

Leave `isReservation` exactly as it is — it is shared with `fetchProvisionalSlots`.

Now rewrite the `getBookingStatus` Javadoc. Delete the existing final paragraph beginning *"**Contract.** {@code live: true} means…"* in full — it documents the limitation this task removes — and put in its place:

```java
     * <p><b>Contract.</b> {@code status} is one of {@code RESERVED} (a reservation still holds
     * capacity), {@code SHARED} (a confirmed row carries this booking id, so the result was
     * already shared and its hold correctly released), {@code LEGACY} (a pre-reserve-a-slot
     * provisional_booking row, active or not), or {@code NONE} (nothing found — the hold expired
     * and was purged). {@code safeToShare} is {@code status != NONE}.
     *
     * <p>Unlike the {@code live} flag this replaces, the answer no longer conflates an expired
     * hold with an already-shared booking, so a caller may gate a re-share on it directly.
```

- [ ] **Step 4: Run the tests to verify they pass**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest*'
```

Expected: PASS, all five status tests plus every pre-existing test in the class. `fetchProvisionalSlots`'s tests must be untouched and green — if one moved, you changed something you should not have.

- [ ] **Step 5: Update the API spec, then build**

`courtscheduler.get.booking-status.json` (the example) becomes:

```json
{
  "bookings": [
    { "bookingId": "4e29c1fa-7d3b-4f21-9c88-1a2b3c4d7ac1", "safeToShare": true,  "status": "RESERVED" },
    { "bookingId": "b7c2f04e-55a1-4c19-8f0d-9e7a6b5c9d31", "safeToShare": true,  "status": "SHARED" },
    { "bookingId": "e1d4a903-2f77-4b60-a1c5-3d8e9f0b2a44", "safeToShare": false, "status": "NONE" }
  ]
}
```

`schema/courtscheduler.get.booking-status.json` becomes:

```json
{
  "$schema": "http://json-schema.org/draft-04/schema#",
  "type": "object",
  "properties": {
    "bookings": {
      "type": "array",
      "items": {
        "type": "object",
        "properties": {
          "bookingId": { "type": "string" },
          "safeToShare": { "type": "boolean" },
          "status": { "type": "string", "enum": ["RESERVED", "SHARED", "LEGACY", "NONE"] }
        },
        "required": ["bookingId", "safeToShare", "status"]
      }
    }
  },
  "required": ["bookings"]
}
```

In `courtscheduler-api.raml`, replace the `/provisionalBooking/status` `description` text (around line 516) with:

```
      Reports, for each booking id, whether the draft that carries it is still safe to share, and
      why. Used by the pre-share gate in the results UI, which must block the share synchronously
      because sharing itself is asynchronous.

      status is RESERVED (a reservation still holds capacity), SHARED (already shared — the
      confirmed row carries this booking id), LEGACY (a draft saved before reserve-a-slot
      shipped), or NONE (the hold expired and was purged). safeToShare is status != NONE, so a
      re-share of an already-shared result is not blocked.
```

Leave the `(mapping)`, `queryParameters` and media type untouched.

Then:

```bash
./gradlew :listingcourtscheduler-api:build -x test
./gradlew build -x test
```

Expected: `BUILD SUCCESSFUL` for both. Check whether the RAML example is validated against the schema during the build — if the build fails on the example, the two are out of step and it is the example or schema that is wrong, not the service.

- [ ] **Step 6: Commit**

**Skipped — the controller has instructed no commits.** Leave every change unstaged and say in your report that you skipped this step.

---

## Deliberately not in scope

- **NEW-10** — exposing this on `cpp-context-listing`'s query API. It is a pass-through of the shape above and is the next ticket, not this one.
- **`fetchProvisionalSlots`** — an already-shared booking returns no slots from it, because the reservation is gone. That is correct and nothing needs it to change.
- **`ReservationService.guardAgainstConfirmedAllocation`** — it looks up `findByHearingId(bookingId)`, and after a share the confirmed row is keyed on the real hearing id, so the guard does not fire for a shared booking. Every pick mints a fresh bookingId, so this cannot be reached in practice. Do not "fix" it.
- **DEC-2** (whether reserving into a full session is allowed) and the **Crown multi-day anchor-only hold** — both product decisions, neither blocked by nor blocking this.
