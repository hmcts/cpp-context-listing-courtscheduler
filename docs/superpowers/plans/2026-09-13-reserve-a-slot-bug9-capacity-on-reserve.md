# Reserve a Slot — BUG-9: enforce capacity at reserve time

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the capacity guarantee this feature removed, at a better point in time — the clerk learns a session is full when they pick it, not hours later when they share.

**Architecture:** `ReservationService` calls the **existing** `SessionsService.validateSessionAvailabilityListMode` before reserving, and throws `NoCapacityException` when it reports a problem. No second comparison is written: the rule, including the `isOverbookingAllowed` exemption, stays in one place.

**Spec:** BUG-9 in `cpp-context-hearing/docs/reserve-a-slot-jira.md`
**Repo:** `cpp-context-listing-courtscheduler` (**Gradle**), branch `team/ras`

## Global Constraints

- **NO COMMITS.** No `git add`, `git commit`, `git stash`, `git rm`. Everything stays unstaged. Any "Commit" step is an explicit no-op.
- **Gradle only.** `./gradlew`, never `mvn`.
- Nothing in this feature is committed; `git diff` shows ~45 files of prior reviewed work. Ignore it.
- **Do not write a second capacity comparison.** Reuse `validateSessionAvailabilityListMode`. Duplicating the rule guarantees the two copies drift, and the `isOverbookingAllowed` exemption is exactly the kind of thing that gets lost in a copy.

## Why this exists

Before reserve-a-slot, overbooking was prevented at **share** time: the results UI called `validateSessionAvailability`, which reaches `SessionsService.validateListModeSlotBased` — `totalBooked >= maxSlots` rejects, unless the session's `isOverbookingAllowed` flag is set. That flag makes overbooking a deliberate, per-session, configurable exception.

BUG-4 replaced that UI call with a booking-status check. The new check is right for expiry but asks nothing about capacity, so nothing enforces it any more:

- `getUpdatedAllocatedSlots` only resolves the schedule — no comparison
- `CourtScheduleCriteria` filters on id / ouCode / date / room / active — never on `available_slot`
- the search query's `WHERE` is `active` + `panel` + `session_start` — no filter, no `HAVING`
- `bookSlotsWithCourtScheduleId` returns `FAILED` only when the schedule cannot be **resolved**

So today a clerk can reserve into a full session, `available_slots` goes negative, and the share proceeds. (The `@cpp/scheduling` picker already clamps its display with `availableSlots > 0 ? availableSlots : 0` — written defensively because negative availability was already reachable.)

---

### Task 1: Reject a reservation that would overbook

**Files:**
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationService.java`
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationServiceTest.java`

**Interfaces:**
- Consumes: `SessionsService.validateSessionAvailabilityListMode(List<String> courtScheduleIds, Integer requestedDuration)` → `Optional<String>` — **public**, in `listingcourtscheduler-common`, which `listingcourtscheduler-api` already depends on (`build.gradle:44`). Empty means "fine"; a present value is the human-readable reason. It already handles slot-based and duration-based sessions and already honours `isOverbookingAllowed`.
- Produces: `reserveAll` throws the existing `NoCapacityException` when the session cannot take the booking. `CourtSchedulerApi:829` already maps that to **409 CONFLICT**, so no API change is needed — verify that mapping still covers the `POST /provisionalBooking` path before relying on it.

**Read first.** Read `ReservationService` in full — particularly `reserveAll`, `guardAgainstConfirmedAllocation` and the class Javadoc — and `SessionsService.validateSessionAvailabilityListMode` with its two branches.

#### The trap: a re-pick must not be rejected by its own hold

`reserveAll` supports re-picking under the **same bookingId** (NEW-15): the previous pick's rows are released inside `saveBookedSlots`, which runs **after** any check you add. So a naive check sees the clerk's own reservation still counted as booked.

Concretely — a 1-slot session, clerk picks it, then re-picks the same session at a different time:

1. hold taken: `totalBooked = 1`, `maxSlots = 1`
2. re-pick arrives under the same bookingId
3. a naive check sees `1 >= 1` → **rejects**, even though the release would immediately have freed it

That is a false rejection of a perfectly legal action, and it would be reported as "the system won't let me change my mind".

**Required behaviour:** the caller's own holds must not count against it. Exclude `allocated_listings` rows whose `hearing_id` equals this `bookingId` from the booked count — a reservation's `hearing_id` **is** its bookingId. Prefer doing this without changing `SessionsService`'s shared signature: compute the caller's own held-row count per session and compare against the validator's verdict, or perform the release before validating. **State in your report which approach you chose and why**, because both have consequences and the brief deliberately leaves the choice to whoever has the code in front of them.

- [ ] **Step 1: Write the failing tests**

Add to `ReservationServiceTest`, reusing its existing fixtures and mock style (`courtScheduleRepository`, `allocatedListingRepository`, `slotRequest(...)`, `SESSION_ID`, `BOOKING_ID`, and whatever `givenSessionExists()`-style helper the file already has — read them, do not invent).

```java
    @Test
    void shouldRejectAReservationWhenTheSessionIsFull() {
        // validator reports the session cannot take it
        ...
        final NoCapacityException thrown = assertThrows(NoCapacityException.class,
                () -> reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60))));

        assertThat(thrown.getMessage(), containsString("no longer available"));
        verify(courtScheduleRepository, never()).saveBookedSlots(anyList(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldReserveWhenTheSessionHasCapacity() {
        // validator reports no problem
        ...
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldAllowAnOverbookingExemptSessionToBeReservedWhenFull() {
        // the validator itself skips sessions whose isOverbookingAllowed is true, so it
        // reports no problem even at capacity — assert we do not add a second rule that
        // overrides that exemption
        ...
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean());
    }

    @Test
    void shouldNotCountTheCallersOwnHoldAgainstItOnARePick() {
        // session at capacity, but every booked row belongs to THIS bookingId
        ...
        reservationService.reserveAll(BOOKING_ID, List.of(slotRequest(SESSION_ID, 60)));

        verify(courtScheduleRepository).saveBookedSlots(anyList(), anyBoolean(), anyBoolean());
    }
```

The `never()` in the first test matters: rejecting *after* `saveBookedSlots` would already have decremented capacity, so the check must come first.

The last test is the most important one here. Write it so it genuinely fails against a naive implementation — if it passes whether or not you exclude the caller's own holds, it is not testing anything.

- [ ] **Step 2: Run them to verify they fail**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*'
```

Record the genuine assertion failures as the red (a first compile error from the new mock is fine, but is not the red).

- [ ] **Step 3: Implement**

Inject `SessionsService` into `ReservationService` and call it at the top of `reserveAll`, before `guardAgainstConfirmedAllocation`. Throw `NoCapacityException` carrying the validator's message.

Add a Javadoc paragraph in the style of the surrounding ones, saying: this restores the guarantee that used to live at share time in the UI's `validateSessionAvailability` call; it is deliberately the same rule and not a copy, so `isOverbookingAllowed` keeps working; and it runs before `saveBookedSlots` so a rejection never leaves capacity decremented.

Update the class Javadoc if it now understates what `reserveAll` does.

- [ ] **Step 4: Run**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*' --tests '*ProvisionalBookingServiceTest*'
./gradlew build -x test
```

Expected: PASS, every pre-existing test included. `ProvisionalBookingServiceTest` mocks `ReservationService`, so it should be unaffected — if it is not, say why in your report rather than adjusting it silently.

- [ ] **Step 5: Prove the integration path still works**

The reserve flow has a real end-to-end test. Run it:

```bash
./gradlew :listingcourtscheduler-integration-test:test --tests '*ProvisionalBookingIT*'
```

Expected: **8/8**, unchanged. Its sessions are seeded with capacity to spare, so the new check must be invisible to them. **If any of those 8 now fails, that is the single most important thing in your report** — it would mean the check rejects legitimate bookings.

If the docker-compose stack will not start, say so plainly and fall back to compile-only; never report an unrun test as passing.

- [ ] **Step 6: Commit** — **skipped, no commits.**

---

## Deliberately not in scope

- **Preventing selection in the UI.** Worth doing, but it belongs in `@cpp/scheduling` (a shared package used by other applications), it must honour `isOverbookingAllowed`, and it can never replace this server-side check — the slot list goes stale while the clerk reads it. Recorded separately as NEW-16.
- **Reviving NEW-13's `SESSION_NOT_AVAILABLE` message.** This change makes that branch reachable again, but wiring the 409 through to the clerk is front-end work and its own ticket.
- **`validateSessionAvailability` being dead code in the UI** — still dead, still deliberate, still not this ticket's business.
