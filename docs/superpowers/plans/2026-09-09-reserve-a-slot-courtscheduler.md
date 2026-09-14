# Reserve a Slot — courtscheduler Implementation Plan (Plan 1 of 5)

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make courtscheduler hold real, expiring capacity when a clerk picks a court session, reusing the existing `/provisionalBooking` endpoints, and fix two defects in the merged LPT-2432/2433 work.

**Architecture:** A reservation is an `allocated_listings` row written through the normal `saveBookedSlots` pipeline (so `court_schedule.available_slots` / `available_duration` is genuinely decremented) with `expires_at` set and `source = RESERVED_UNCONFIRMED`. Its `hearing_id` is the minted `bookingId`. `POST /provisionalBooking` creates reservations instead of `provisional_booking` rows; `GET /provisionalBooking` reads them back, falling back to legacy `provisional_booking` rows for drafts saved before go-live; confirmation at share releases the reservation and books under the real hearing id.

**Tech Stack:** Java 17, Spring Boot, Gradle, JPA/Hibernate, PostgreSQL, JUnit 5, Mockito, Hamcrest.

**Spec:** https://claude.ai/code/artifact/90e6b0eb-dc8a-4697-a8d4-8d9086d283d6 ("Reserve a Slot", design for review)

**Repo:** `cpp-context-listing-courtscheduler`, branch `team/ras`

## Global Constraints

- Build tool is **Gradle**. Never Maven. Run from the repo root.
- Reservation rows are identified by `expires_at IS NOT NULL`. Confirmed bookings have `expires_at IS NULL`. No other discriminator is authoritative — `source` is descriptive only.
- `expires_at` is a `LocalDate` (calendar day, no time-of-day), stamped as `LocalDate.now(ZoneOffset.UTC)` at reservation time.
- A reservation **holds capacity**. Availability calculations must continue to count reservations as booked. Only *hearing-listing* reads exclude them.
- Every release path must perform the full three-step release: delete the rows, `releaseCourtScheduleAllocatedSlotsForBookingId`, `releaseAllocatedSlotsOrDurationFromCourtSchedule`. A bare `DELETE` leaks capacity permanently.
- Every release must treat "no reservation found" as a **no-op, not an error** — legacy magistrates drafts have no reservation.
- `provisional_booking` is **read-only legacy** after this plan. No new rows are written to it. Existing rows must keep resolving indefinitely (drafts have no TTL).
- Multi-slot reservation is **all-or-nothing**. A partial hold is a defect.
- Do not change the path or media type of `/provisionalBooking`. Consumers (cpp-context-hearing, cpp-context-listing) call it directly over HTTP and must not need redeploying in lockstep.

**Build and test commands**

```bash
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test
./gradlew :listingcourtscheduler-common:test
./gradlew :listingcourtscheduler-api:test
./gradlew build -x integrationTest
```

---

### Task 1: Purge restores capacity (defect 1)

`AllocatedListingRepository.deleteExpiredReservedSessions` is a bare `DELETE FROM allocated_listings WHERE expires_at < :cutoff`. It removes the row and never returns the capacity to `court_schedule`, so every expired reservation permanently burns a slot. Replace it with the full three-step release that `releaseOldAllocatedListings` already performs.

**Files:**
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/AllocatedListingRepository.java`
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryCustom.java`
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryImpl.java`
- Modify: `listingcourtscheduler-common/src/main/java/uk/gov/moj/cpp/courtscheduler/common/service/AllocatedListingService.java`
- Test: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/test/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryTest.java`
- Test: `listingcourtscheduler-common/src/test/java/uk/gov/moj/cpp/courtscheduler/common/service/AllocatedListingServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `AllocatedListingRepository.findExpiredReservedSessions(LocalDate cutoff)` → `List<AllocatedListing>`
  - `CourtScheduleRepositoryCustom.releaseExpiredReservations(LocalDate cutoff)` → `int` (rows released)
  - `AllocatedListingService.purgeExpiredReservedSessions()` → `int` (unchanged signature, new behaviour)

- [ ] **Step 1: Write the failing repository test**

Add to `CourtScheduleRepositoryTest`:

```java
    @Test
    void shouldReleaseExpiredReservationsAndRestoreCapacity() {
        final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        final CourtSchedule session = persistRandomCourtSchedule();
        session.setSlotBased(true);
        session.setMaxSlots(4);
        session.setAvailableSlots(3); // one slot already consumed by the reservation below
        courtScheduleRepository.saveAndFlush(session);

        final AllocatedListing expired = createAllocateListing(
                "AL-EXPIRED", "BK-EXPIRED", session, "BK-EXPIRED");
        expired.setExpiresAt(today.minusDays(1));
        allocatedListingRepository.saveAndFlush(expired);

        final int released = courtScheduleRepository.releaseExpiredReservations(today);

        assertThat(released, is(1));
        assertThat(allocatedListingRepository.findByHearingId("BK-EXPIRED").isEmpty(), is(true));
        assertThat(courtScheduleRepository.findBy(session.getCourtScheduleId()).getAvailableSlots(), is(4));
    }

    @Test
    void shouldNotReleaseReservationsExpiringToday() {
        final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        final CourtSchedule session = persistRandomCourtSchedule();
        final AllocatedListing expiringToday = createAllocateListing(
                "AL-TODAY", "BK-TODAY", session, "BK-TODAY");
        expiringToday.setExpiresAt(today);
        allocatedListingRepository.saveAndFlush(expiringToday);

        assertThat(courtScheduleRepository.releaseExpiredReservations(today), is(0));
        assertThat(allocatedListingRepository.findByHearingId("BK-TODAY").size(), is(1));
    }

    @Test
    void shouldNotReleaseConfirmedBookings() {
        final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        final CourtSchedule session = persistRandomCourtSchedule();
        final AllocatedListing confirmed = createAllocateListing(
                "AL-CONFIRMED", "BK-CONFIRMED", session, "HEARING-CONFIRMED");
        confirmed.setExpiresAt(null);
        allocatedListingRepository.saveAndFlush(confirmed);

        assertThat(courtScheduleRepository.releaseExpiredReservations(today), is(0));
        assertThat(allocatedListingRepository.findByHearingId("HEARING-CONFIRMED").size(), is(1));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*CourtScheduleRepositoryTest'`
Expected: FAIL — `cannot find symbol: method releaseExpiredReservations(LocalDate)`.

- [ ] **Step 3: Add the finder to `AllocatedListingRepository`**

Replace the `deleteExpiredReservedSessions` declaration (and its `@Modifying`/`@Query` annotations) with a read:

```java
    /**
     * Reservation rows whose expiresAt (a calendar date, no time-of-day) is strictly before the
     * cutoff. Returns rows rather than deleting them so the caller can perform the full
     * three-step release — a bare DELETE would strand the capacity on court_schedule.
     * Self-healing: not scoped to "yesterday", so a missed daily run's backlog is still swept.
     */
    @Query(value = """
            SELECT al.*
            FROM allocated_listings al
            WHERE al.expires_at IS NOT NULL
              AND al.expires_at < :cutoff
            """, nativeQuery = true)
    List<AllocatedListing> findExpiredReservedSessions(@Param("cutoff") LocalDate cutoff);
```

- [ ] **Step 4: Declare the release on `CourtScheduleRepositoryCustom`**

Add next to `releaseOldAllocatedListings`:

```java
    /**
     * Releases every reservation whose expiresAt is before {@code cutoff}, restoring each
     * session's capacity. Returns the number of rows released.
     */
    int releaseExpiredReservations(java.time.LocalDate cutoff);
```

- [ ] **Step 5: Implement it in `CourtScheduleRepositoryImpl`**

Add beside `releaseOldAllocatedListings`:

```java
    @Override
    @Transactional
    public int releaseExpiredReservations(final LocalDate cutoff) {
        final List<AllocatedListing> expired = allocatedListingRepository.findExpiredReservedSessions(cutoff);
        if (expired.isEmpty()) {
            return 0;
        }
        expired.forEach(allocatedListingRepository::remove);
        releaseCourtScheduleAllocatedSlotsForBookingId(expired);
        releaseAllocatedSlotsOrDurationFromCourtSchedule(expired);
        return expired.size();
    }
```

- [ ] **Step 6: Point the purge service at it**

In `AllocatedListingService`, inject `CourtScheduleRepository` and replace the body:

```java
    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    /**
     * Releases allocated_listings rows whose expiresAt is before today, restoring each session's
     * capacity. ZoneOffset.UTC keeps the cutoff deterministic regardless of the JVM's default
     * zone. Self-healing against a missed daily run, since it isn't scoped to exactly "yesterday".
     */
    @Transactional
    public int purgeExpiredReservedSessions() {
        return courtScheduleRepository.releaseExpiredReservations(LocalDate.now(ZoneOffset.UTC));
    }
```

- [ ] **Step 7: Update the service unit test**

Replace `shouldPurgeExpiredReservedSessions` in `AllocatedListingServiceTest` (and add the `@Mock CourtScheduleRepository courtScheduleRepository;` field):

```java
    @Test
    void shouldPurgeExpiredReservedSessionsRestoringCapacity() {
        final int numberOfReleased = 3;
        when(courtScheduleRepository.releaseExpiredReservations(any(LocalDate.class))).thenReturn(numberOfReleased);

        final int actual = allocatedListingService.purgeExpiredReservedSessions();

        final org.mockito.ArgumentCaptor<LocalDate> cutoffCaptor = org.mockito.ArgumentCaptor.forClass(LocalDate.class);
        verify(courtScheduleRepository, atLeastOnce()).releaseExpiredReservations(cutoffCaptor.capture());
        assertEquals(LocalDate.now(ZoneOffset.UTC), cutoffCaptor.getValue());
        assertThat(actual, is(numberOfReleased));
    }
```

- [ ] **Step 8: Delete the old repository test for the bare delete**

Remove `AllocatedListingRepositoryTest`'s test that calls `deleteExpiredReservedSessions(today)` — the method no longer exists and its behaviour is now covered by `CourtScheduleRepositoryTest`.

- [ ] **Step 9: Run all three test suites**

```bash
./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test
./gradlew :listingcourtscheduler-common:test
```
Expected: PASS.

- [ ] **Step 10: Commit**

```bash
git add listingcourtscheduler-viewstore listingcourtscheduler-common
git commit -m "fix: purge expired reservations through the full release so capacity is restored

A bare DELETE removed the allocated_listings row but left court_schedule
available_slots/available_duration decremented, permanently burning a slot
per expired reservation."
```

---

### Task 2: Reservations must not read as listed hearings (defect 2)

`AllocatedHearingsQueryBuilder` has no `expires_at` filter, so reservations are returned by the allocated-hearings query that backs listing's `range-search` and the cpp-ui-listing court calendar. Their `hearing_id` is a booking id matching no hearing, so listing drops them — but the total count is passed straight through, so pagination overstates and pages render short.

**Availability queries are deliberately left alone.** A reservation holds capacity, so it must keep counting as booked in `getCountBasedAllocatedListing` and friends. Only the hearing-listing query changes.

**Files:**
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/AllocatedHearingsQueryBuilder.java`
- Test: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/test/java/uk/gov/moj/cpp/courtscheduler/repository/AllocatedHearingsQueryBuilderTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: no new API. `AllocatedHearingsQueryBuilder.getAllocatedHearingsQuery()` now excludes reservations.

- [ ] **Step 1: Write the failing test**

Add to `AllocatedHearingsQueryBuilderTest`:

```java
    @Test
    void shouldExcludeReservationsFromAllocatedHearings() {
        final HearingSlotRequestParam req = new HearingSlotRequestParam("ADULT,YOUTH",
                "2026-10-01", "2026-10-31", null, null, null, null, null, null, null, null, "20", "1");

        final String query = new AllocatedHearingsQueryBuilder(req).getAllocatedHearingsQuery();

        assertThat(query, containsString("al.expires_at is null"));
        assertThat(query, containsString("al2.expires_at is null"));
    }
```

Match the existing constructor arity used elsewhere in that test class; copy the argument list from a neighbouring test rather than the placeholder above if it differs.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*AllocatedHearingsQueryBuilderTest'`
Expected: FAIL — the query contains no `expires_at` predicate.

- [ ] **Step 3: Add the predicate in both places**

In `generateAllocatedHearingsQuery()`, the day-count subquery becomes:

```java
        queryStrBuilder.append("(select count(1) from allocated_listings al2 where al2.hearing_id = al.hearing_id and al2.expires_at is null) as hearing_day_count, ");
```

and immediately after the existing `where` clause line, add:

```java
        queryStrBuilder.append("where al.court_schedule_id = cs.id and cs.active = true ");
        // A reservation (expires_at set) holds capacity but has no hearing behind it — its
        // hearing_id is a bookingId. Including it here would inflate the paged total that
        // listing passes straight through to the court calendar, so pages render short.
        queryStrBuilder.append("and al.expires_at is null ");
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*AllocatedHearingsQueryBuilderTest'`
Expected: PASS.

- [ ] **Step 5: Run the whole persistence suite for regressions**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add listingcourtscheduler-viewstore
git commit -m "fix: exclude reservations from the allocated-hearings query

A reservation's hearing_id is a bookingId with no hearing behind it. Listing
drops the row but passes courtscheduler's total through unchanged, so the
court calendar pager overstated and pages rendered short."
```

---

### Task 3: Extract a shared `ReservationService`

`SlotsUpdateService.reserveUnconfirmedHearing` holds the reservation logic worth keeping — the confirmed-allocation guard, the `expires_at` stamp, and the `saveBookedSlots` call. Move it somewhere `ProvisionalBookingService` can also call.

**Files:**
- Create: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationService.java`
- Create: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/exception/NoCapacityException.java`
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/SlotsUpdateService.java`
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ReservationServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces:
  - `ReservationService.reserve(String sessionId, String bookingId, String hearingStartTime, int duration)` → `AllocatedSlot` (the persisted slot, with `expiresAt` populated)
  - `NoCapacityException extends RuntimeException`
  - `ConfirmedBookingExistsException` — existing, rethrown unchanged

- [ ] **Step 1: Write the failing test**

Create `ReservationServiceTest`:

```java
package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReservationServiceTest {

    private static final String SESSION_ID = "cs-1";
    private static final String BOOKING_ID = "bk-1";

    @InjectMocks
    private ReservationService reservationService;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Test
    void shouldReserveWithTodaysExpiryAndUnconfirmedSource() {
        givenSessionExists();
        when(allocatedListingRepository.findByHearingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean())).thenReturn(Result.SUCCESS());

        final var slot = reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60);

        assertThat(slot, is(notNullValue()));
        assertThat(slot.getExpiresAt(), is(LocalDate.now(ZoneOffset.UTC)));
        assertThat(slot.getHearingId(), is(BOOKING_ID));
        assertThat(slot.getSource(), is("RESERVED_UNCONFIRMED"));
    }

    @Test
    void shouldRejectWhenBookingAlreadyHasAConfirmedAllocation() {
        final AllocatedListing confirmed = new AllocatedListing();
        confirmed.setExpiresAt(null);
        when(allocatedListingRepository.findByHearingId(BOOKING_ID)).thenReturn(List.of(confirmed));

        assertThrows(ConfirmedBookingExistsException.class,
                () -> reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60));
    }

    @Test
    void shouldThrowNoCapacityWhenPersistFails() {
        givenSessionExists();
        when(allocatedListingRepository.findByHearingId(BOOKING_ID)).thenReturn(List.of());
        when(courtScheduleRepository.saveBookedSlots(anyList(), anyBoolean(), anyBoolean()))
                .thenReturn(Result.FAILED("no capacity"));

        assertThrows(NoCapacityException.class,
                () -> reservationService.reserve(SESSION_ID, BOOKING_ID, "2026-10-14T10:00:00.000Z", 60));
    }

    private void givenSessionExists() {
        final CourtSchedule session = new CourtSchedule();
        session.setCourtScheduleId(SESSION_ID);
        session.setActive(true);
        session.setOuCode("B01LY");
        session.setSessionDate(LocalDate.of(2026, 10, 14));
        session.setSlotBased(true);
        when(courtScheduleRepository.getCourtSchedulesByIdList(List.of(SESSION_ID))).thenReturn(List.of(session));
    }
}
```

If `CourtSchedule` (domain) has no no-arg setters, build it with whatever builder the class exposes — copy the construction style from `SlotsUpdateServiceTest`.

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest'`
Expected: FAIL — `ReservationService` and `NoCapacityException` do not exist.

- [ ] **Step 3: Create `NoCapacityException`**

```java
package uk.gov.moj.cpp.courtscheduler.exception;

/**
 * Raised when a session cannot take the requested hold — the capacity-decrementing pipeline
 * refused the slot. Distinct from a persistence failure: this is an expected, user-facing
 * outcome that the clerk sees as "No session available, please try again".
 */
public class NoCapacityException extends RuntimeException {

    public NoCapacityException(final String message) {
        super(message);
    }
}
```

- [ ] **Step 4: Create `ReservationService`**

```java
package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import jakarta.inject.Inject;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates capacity-holding, expiring reservations. A reservation is an ordinary
 * allocated_listings row written through the same pipeline as a real booking — so
 * court_schedule capacity is genuinely decremented — but stamped with expires_at so the
 * nightly purge sweeps it if the result is never shared.
 *
 * <p>The row's hearing_id is the minted bookingId, which is unique per pick. That matters:
 * saveBookedSlots begins with a hearing-wide releaseOldAllocatedListings(hearing_id), so a
 * key shared between two picks would silently release the other's hold.
 */
@Service
public class ReservationService {

    public static final String SOURCE_RESERVED_UNCONFIRMED = "RESERVED_UNCONFIRMED";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Transactional
    public AllocatedSlot reserve(final String sessionId,
                                 final String bookingId,
                                 final String hearingStartTime,
                                 final int duration) {

        final boolean hasConfirmedAllocation = allocatedListingRepository.findByHearingId(bookingId).stream()
                .anyMatch(allocation -> allocation.getExpiresAt() == null);
        if (hasConfirmedAllocation) {
            throw new ConfirmedBookingExistsException(
                    "Booking " + bookingId + " already has a confirmed allocation — cannot reserve");
        }

        final CourtSchedule session = courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)).stream()
                .filter(CourtSchedule::isActive)
                .findFirst()
                .orElseThrow(() -> new NoSessionAvailableException("No session found for sessionId " + sessionId));

        final AllocatedSlot slot = new AllocatedSlot();
        slot.setCourtScheduleId(sessionId);
        slot.setHearingId(bookingId);
        slot.setOuCode(session.getOuCode());
        slot.setSessionDate(session.getSessionDate().toString());
        slot.setDuration(duration);
        slot.setHearingStartTime(hearingStartTime);
        slot.setSlotBased(session.isSlotBased());
        slot.setSource(SOURCE_RESERVED_UNCONFIRMED);
        slot.setExpiresAt(LocalDate.now(ZoneOffset.UTC));

        final Result result = courtScheduleRepository.saveBookedSlots(new ArrayList<>(List.of(slot)), false, false);
        if (!result.isSuccess()) {
            throw new NoCapacityException(
                    "Could not reserve session " + sessionId + " for booking " + bookingId + ": " + result.getMessage());
        }
        return slot;
    }
}
```

Check `Result`'s accessors before writing the failure branch — if it exposes something other than `isSuccess()` / `getMessage()`, use the same predicate `SlotsUpdateService.throwIfPersistFailed` uses.

- [ ] **Step 5: Delegate from `SlotsUpdateService`**

Replace the body of `reserveUnconfirmedHearing` with a call to the new service, keeping the endpoint working until Task 7 removes it:

```java
    @Inject
    private ReservationService reservationService;

    public ReserveUnconfirmedHearingResponse reserveUnconfirmedHearing(final String sessionId, final String hearingId,
                                                                       final ReserveUnconfirmedHearingRequest request) {
        final AllocatedSlot slot = reservationService.reserve(
                sessionId, hearingId, request.getHearingStartTime(), request.getDuration());

        return new ReserveUnconfirmedHearingResponse(
                sessionId, hearingId, slot.getExpiresAt().toString(),
                slot.isSlotBased(), slot.getDuration(), slot.getHearingStartTime(), slot.getSource());
    }
```

- [ ] **Step 6: Run the api tests**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest' --tests '*SlotsUpdateServiceTest'`
Expected: PASS. `SlotsUpdateServiceTest`'s existing reserve tests will need their mocks moved onto `ReservationService`; update them rather than deleting them.

- [ ] **Step 7: Commit**

```bash
git add listingcourtscheduler-api listingcourtscheduler-domain
git commit -m "refactor: extract ReservationService from reserveUnconfirmedHearing

One place that creates a capacity-holding, expiring reservation, so the
provisional booking endpoint can reuse it. Adds NoCapacityException for the
expected 'session full' outcome."
```

---

### Task 4: Add `duration` to the provisional booking contract

Reserving decrements capacity, and a duration-based session decrements `available_duration` by the hearing's estimated minutes. The request carries only `courtScheduleId` and `hearingStartTime`. `isSlotBased` is **not** added — it is a property of the session and `ReservationService` reads it from there.

This task is contract-only: no behaviour change yet, so it can ship ahead of the callers.

**Files:**
- Modify: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/ProvisionalSlot.java`
- Modify: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.create.provisional.booking.json`
- Modify: `listingcourtscheduler-api/src/raml/json/courtscheduler.create.provisional.booking.json`
- Modify: `listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml`
- Test: `listingcourtscheduler-domain/src/test/java/uk/gov/moj/cpp/courtscheduler/domain/ProvisionalSlotTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `ProvisionalSlot.getDuration()` → `Integer` (null when the caller omits it), `ProvisionalSlot.setDuration(Integer)`, `ProvisionalSlotBuilder.withDuration(Integer)`.

- [ ] **Step 1: Write the failing test**

Create `ProvisionalSlotTest`:

```java
package uk.gov.moj.cpp.courtscheduler.domain;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import org.junit.jupiter.api.Test;

class ProvisionalSlotTest {

    @Test
    void shouldCarryDuration() {
        final ProvisionalSlot slot = ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId("cs-1")
                .withHearingStartTime("2026-10-14T10:00:00.000Z")
                .withDuration(90)
                .build();

        assertThat(slot.getDuration(), is(90));
    }

    @Test
    void shouldDefaultDurationToNullWhenOmitted() {
        final ProvisionalSlot slot = ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId("cs-1")
                .withHearingStartTime("2026-10-14T10:00:00.000Z")
                .build();

        assertThat(slot.getDuration(), is(nullValue()));
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :listingcourtscheduler-domain:test --tests '*ProvisionalSlotTest'`
Expected: FAIL — `withDuration` / `getDuration` do not exist.

- [ ] **Step 3: Add the field to `ProvisionalSlot`**

Add alongside `hearingStartTime` — the field, getter, setter, and builder method:

```java
    private Integer duration;

    public Integer getDuration() {
        return duration;
    }

    public void setDuration(final Integer duration) {
        this.duration = duration;
    }
```

and in `ProvisionalSlotBuilder`, the field, the `withDuration` method, and `provisionalSlot.setDuration(duration);` inside `build()`:

```java
        public ProvisionalSlotBuilder withDuration(Integer duration) {
            this.duration = duration;
            return this;
        }
```

- [ ] **Step 4: Add `duration` to the request schema**

In `src/raml/json/schema/courtscheduler.create.provisional.booking.json`, inside the `provisionalSlots` item `properties`:

```json
          "duration": {
            "description": "Hearing duration in minutes. Required for duration-based sessions, where the reservation decrements available_duration by this amount.",
            "type": "integer"
          }
```

Leave it out of `required` — omitted means "slot-based session, no duration needed", and older callers keep working.

- [ ] **Step 5: Add it to the example and the OpenAPI spec**

In `src/raml/json/courtscheduler.create.provisional.booking.json`, add `"duration": 60` to each slot. Mirror the same optional integer property in the request schema inside `openapi/courtscheduler-api.openapi.yml`.

- [ ] **Step 6: Run the tests**

Run: `./gradlew :listingcourtscheduler-domain:test :listingcourtscheduler-api:test`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add listingcourtscheduler-domain listingcourtscheduler-api
git commit -m "feat: accept optional duration on the provisional booking request

Reserving decrements capacity, and duration-based sessions decrement
available_duration by the hearing's estimated minutes. Additive and optional,
so existing callers are unaffected."
```

---

### Task 5: `bookProvisionalSlots` reserves, all-or-nothing

**Files:**
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java`
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/CourtSchedulerApi.java`
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingServiceTest.java`

**Interfaces:**
- Consumes: `ReservationService.reserve(...)` and `NoCapacityException` from Task 3; `ProvisionalSlot.getDuration()` from Task 4.
- Produces: `bookProvisionalSlots` keeps its signature — `JsonObject` containing `bookingId` — and now throws `NoCapacityException` when any slot cannot be held.

- [ ] **Step 1: Write the failing test**

Create `ProvisionalBookingServiceTest`:

```java
package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.List;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalBookingServiceTest {

    @InjectMocks
    private ProvisionalBookingService provisionalBookingService;

    @Mock
    private ReservationService reservationService;

    @Mock
    private ProvisionalBookingRepository provisionalBookingRepository;

    // Used from Task 6 onwards; declared here so the class is stable across tasks.
    @Mock
    private AllocatedListingRepository allocatedListingRepository;

    @Mock
    private CourtScheduleRepository courtScheduleRepository;

    @Test
    void shouldReserveEverySlotUnderOneBookingIdAndWriteNoProvisionalBookingRow() {
        when(reservationService.reserve(anyString(), anyString(), anyString(), anyInt()))
                .thenReturn(new AllocatedSlot());

        final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2"));

        assertThat(response.getString("bookingId"), is(notNullValue()));
        final String bookingId = response.getString("bookingId");
        verify(reservationService).reserve(eq("cs-1"), eq(bookingId), anyString(), anyInt());
        verify(reservationService).reserve(eq("cs-2"), eq(bookingId), anyString(), anyInt());
        verify(provisionalBookingRepository, never()).saveProvisionalBooking(any(), anyString(), any());
    }

    @Test
    void shouldFailTheWholeBookingWhenAnySlotCannotBeHeld() {
        when(reservationService.reserve(eq("cs-1"), anyString(), anyString(), anyInt()))
                .thenReturn(new AllocatedSlot());
        when(reservationService.reserve(eq("cs-2"), anyString(), anyString(), anyInt()))
                .thenThrow(new NoCapacityException("full"));

        assertThrows(NoCapacityException.class,
                () -> provisionalBookingService.bookProvisionalSlots(slots("cs-1", "cs-2")));

        verify(reservationService, times(2)).reserve(anyString(), anyString(), anyString(), anyInt());
    }

    private ProvisionalBookingSlots slots(final String... courtScheduleIds) {
        final ProvisionalBookingSlots booking = new ProvisionalBookingSlots();
        booking.setProvisionalSlots(java.util.Arrays.stream(courtScheduleIds)
                .map(id -> ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                        .withCourtScheduleId(id)
                        .withHearingStartTime("2026-10-14T10:00:00.000Z")
                        .withDuration(60)
                        .build())
                .toList());
        return booking;
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest'`
Expected: FAIL — the service still calls `saveProvisionalBooking`.

- [ ] **Step 3: Rewrite `bookProvisionalSlots`**

```java
    @Inject
    private ReservationService reservationService;

    /**
     * Mints a bookingId and reserves every requested session under it. The reservation replaces
     * the provisional_booking row that used to be written here: it holds real capacity and
     * carries an expiry, which a provisional booking never did.
     *
     * <p>All or nothing. The method is transactional, so a NoCapacityException on the third of
     * three slots rolls back the first two — a partial hold would take capacity for a booking the
     * clerk never gets.
     */
    public JsonObject bookProvisionalSlots(final ProvisionalBookingSlots provisionalBookingSlots) {
        final String bookingId = randomUUID().toString();
        provisionalBookingSlots.getProvisionalSlots().forEach(provisionalSlot ->
                reservationService.reserve(
                        provisionalSlot.getCourtScheduleId(),
                        bookingId,
                        provisionalSlot.getHearingStartTime(),
                        provisionalSlot.getDuration() == null ? 0 : provisionalSlot.getDuration()));

        return Json.createObjectBuilder()
                .add(BOOKING_ID, bookingId)
                .build();
    }
```

Remove the now-unused `PersistenceStoreException` / `SlotsBookException` imports if nothing else in the class uses them. The class is already annotated `@org.springframework.transaction.annotation.Transactional`, which is what makes the rollback work — do not remove it.

- [ ] **Step 4: Map the failure to a response in `CourtSchedulerApi`**

In the `postProvisionalBooking` handler, wrap the call:

```java
        try {
            final JsonObject response = provisionalBookingService.bookProvisionalSlots(slots);
            return ResponseEntity.ok(toResponseMap(response));
        } catch (NoCapacityException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        }
```

Match the surrounding handler's existing return style — copy it from `putReserveUnconfirmedHearing`, which already maps `ConfirmedBookingExistsException` to `CONFLICT`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :listingcourtscheduler-api:test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add listingcourtscheduler-api
git commit -m "feat: provisional booking now creates capacity-holding reservations

POST /provisionalBooking reserves each session through ReservationService
instead of inserting a provisional_booking row. All-or-nothing: any slot that
cannot be held rolls the whole booking back and returns 409."
```

---

### Task 6: `fetchProvisionalSlots` reads reservations, falling back to legacy rows

**Files:**
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java`
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingServiceTest.java`

**Interfaces:**
- Consumes: `AllocatedListingRepository.findByHearingId(String)` (existing).
- Produces: `fetchProvisionalSlots(String bookingIds)` — unchanged signature and unchanged response shape.

- [ ] **Step 1: Write the failing tests**

Add to `ProvisionalBookingServiceTest`:

```java
    @Test
    void shouldReadSlotsFromReservationRows() {
        final CourtSchedule session = aCourtSchedule("cs-1");
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-1");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-1")).thenReturn(List.of(reservation));
        when(courtScheduleRepository.findBy("cs-1")).thenReturn(session);

        final JsonObject response = provisionalBookingService.fetchProvisionalSlots("bk-1");

        final JsonArray slots = response.getJsonArray("provisionalSlots");
        assertThat(slots.size(), is(1));
        assertThat(slots.getJsonObject(0).getString("courtScheduleId"), is("cs-1"));
        assertThat(slots.getJsonObject(0).getString("bookingId"), is("bk-1"));
    }

    @Test
    void shouldFallBackToLegacyProvisionalBookingWhenNoReservationExists() {
        when(allocatedListingRepository.findByHearingId("legacy-bk")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("legacy-bk")))
                .thenReturn(List.of(aLegacyProvisionalBooking("legacy-bk", "cs-9")));

        final JsonObject response = provisionalBookingService.fetchProvisionalSlots("legacy-bk");

        assertThat(response.getJsonArray("provisionalSlots").size(), is(1));
        assertThat(response.getJsonArray("provisionalSlots").getJsonObject(0).getString("courtScheduleId"), is("cs-9"));
    }
```

Write the `aCourtSchedule` and `aLegacyProvisionalBooking` helpers against the real entity constructors — copy the construction style from `AllocatedListingRepositoryTest.createAllocateListing`.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest'`
Expected: FAIL — the method reads only `provisional_booking`.

- [ ] **Step 3: Read reservations first, fall back per booking id**

Restructure `fetchProvisionalSlots` so each booking id resolves independently:

```java
    /**
     * Resolves booking ids to their sessions. Reservations are the current representation;
     * provisional_booking rows are the legacy one, written before reserve-a-slot shipped.
     * Drafts have no TTL, so the fallback is permanent — a pre-go-live magistrates draft can be
     * shared at any point in the future and must still resolve.
     */
    public JsonObject fetchProvisionalSlots(final String bookingIds) {
        final List<String> bookingIdList = Stream.of(bookingIds.split(","))
                .map(String::trim)
                .toList();

        final List<ProvisionalBookingInfo> infos = new ArrayList<>();
        final List<String> unresolved = new ArrayList<>();

        for (final String bookingId : bookingIdList) {
            final List<AllocatedListing> reservations = allocatedListingRepository.findByHearingId(bookingId).stream()
                    .filter(row -> row.getExpiresAt() != null)
                    .toList();
            if (reservations.isEmpty()) {
                unresolved.add(bookingId);
            } else {
                reservations.forEach(row -> infos.add(buildInfoFromReservation(row)));
            }
        }

        if (!unresolved.isEmpty()) {
            provisionalBookingRepository.findByBookingIdIn(unresolved)
                    .forEach(legacy -> infos.add(buildProvisionalInfo(legacy)));
        }

        attachJudiciaries(infos);

        return Json.createObjectBuilder()
                .add(PROVISIONAL_SLOTS.getLabel(), listToJsonArrayConverter.convert(infos))
                .build();
    }

    private ProvisionalBookingInfo buildInfoFromReservation(final AllocatedListing reservation) {
        final CourtSchedule courtSchedule = courtScheduleRepository.findBy(reservation.getCourtScheduleId());
        return new ProvisionalBookingInfo.ProvisionalBookingInfoBuilder()
                .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                .withListingProfileId(courtSchedule.getListingProfileId())
                .withOuCode(courtSchedule.getOuCode())
                .withCourtHouseId(courtSchedule.getCourtHouseId())
                .withCourtHouseName(courtSchedule.getCourtHouseName())
                .withCourtRoomId(courtSchedule.getCourtRoomId())
                .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                .withCourtRoomName(courtSchedule.getCourtRoomName())
                .withBusinessType(courtSchedule.getBusinessType())
                .withCourtSession(courtSchedule.getCourtSession())
                .withSessionDate(courtSchedule.getSessionDate())
                .withPanel(courtSchedule.getPanel())
                .withOperationalUnit(courtSchedule.getOperationalUnit())
                .withAvailableSlots(courtSchedule.getAvailableSlots())
                .withAvailableDuration(courtSchedule.getAvailableDuration())
                .withMaxSlots(courtSchedule.getMaxSlots())
                .withMaxDuration(courtSchedule.getMaxDuration())
                .withBookingId(reservation.getHearingId())
                .withHearingStartTime(reservation.getHearingStartTime())
                .build();
    }
```

Extract the existing judiciary-attachment block into `attachJudiciaries(List<ProvisionalBookingInfo>)` unchanged, so both sources get judiciaries the same way. Add the `AllocatedListingRepository` `@Inject` field.

- [ ] **Step 4: Run the tests**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add listingcourtscheduler-api
git commit -m "feat: resolve booking ids from reservations, falling back to legacy rows

GET /provisionalBooking now reads allocated_listings reservations. Booking ids
with no reservation fall back to provisional_booking, which pre-go-live
magistrates drafts still point at. Response shape is unchanged."
```

---

### Task 7: Confirmation releases the reservation; drop the redundant endpoint

At share, listing books the hearing under its real id. The reservation is keyed on the bookingId, so the pipeline's hearing-wide release does not touch it — it must be released explicitly, or the session stays decremented twice until 01:00.

**Files:**
- Modify: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/main/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryImpl.java`
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/CourtSchedulerApi.java`
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/SlotsUpdateService.java`
- Modify: `listingcourtscheduler-api/src/raml/courtscheduler-api.raml`
- Modify: `listingcourtscheduler-api/src/main/resources/openapi/courtscheduler-api.openapi.yml`
- Delete: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/ReserveUnconfirmedHearingRequest.java`
- Delete: `listingcourtscheduler-domain/src/main/java/uk/gov/moj/cpp/courtscheduler/domain/ReserveUnconfirmedHearingResponse.java`
- Test: `listingcourtscheduler-viewstore/listingcourtscheduler-viewstore-persistence/src/test/java/uk/gov/moj/cpp/courtscheduler/repository/CourtScheduleRepositoryTest.java`

**Interfaces:**
- Consumes: `releaseOldAllocatedListings(String)` (existing).
- Produces: `persistHearingSlots` releases the reservation for `slots.get(0).getBookingId()` when one is present.

- [ ] **Step 1: Write the failing test**

Add to `CourtScheduleRepositoryTest`:

```java
    @Test
    void shouldReleaseTheReservationWhenTheBookingIsConfirmed() {
        final LocalDate today = LocalDate.now(java.time.ZoneOffset.UTC);
        final CourtSchedule session = persistRandomCourtSchedule();
        session.setSlotBased(true);
        session.setMaxSlots(4);
        session.setAvailableSlots(3);
        courtScheduleRepository.saveAndFlush(session);

        final AllocatedListing reservation = createAllocateListing("AL-RES", "BK-RES", session, "BK-RES");
        reservation.setExpiresAt(today);
        allocatedListingRepository.saveAndFlush(reservation);

        courtScheduleRepository.releaseOldAllocatedListings("BK-RES");

        assertThat(allocatedListingRepository.findByHearingId("BK-RES").isEmpty(), is(true));
        assertThat(courtScheduleRepository.findBy(session.getCourtScheduleId()).getAvailableSlots(), is(4));
    }

    @Test
    void shouldTolerateReleasingABookingWithNoReservation() {
        courtScheduleRepository.releaseOldAllocatedListings("BK-DOES-NOT-EXIST");
        // no exception — legacy drafts have no reservation to release
    }
```

- [ ] **Step 2: Run them to verify the second one is the risk**

Run: `./gradlew :listingcourtscheduler-viewstore:listingcourtscheduler-viewstore-persistence:test --tests '*CourtScheduleRepositoryTest'`
Expected: both PASS if `releaseOldAllocatedListings` is already tolerant. If the no-reservation case throws, fix it before continuing — every legacy draft depends on it.

- [ ] **Step 3: Release the reservation in `persistHearingSlots`**

Replace the `deleteProvisionalBooking` call:

```java
    private void persistHearingSlots(final List<AllocatedSlot> slots, final boolean isProvisionalSlot, final List<AllocatedSlot> updateAllocatedSlots) {
        updateCourtSchedule(updateAllocatedSlots);
        saveAllocatedListing(updateAllocatedSlots);
        if (isProvisionalSlot) {
            final String bookingId = slots.get(0).getBookingId();
            // Release the hold the clerk took at slot-pick time. Keyed on the bookingId, so the
            // pipeline's hearing-wide release (which uses the real hearing id) does not cover it.
            // A no-op when there is nothing to release — legacy drafts have only a
            // provisional_booking row, which deleteProvisionalBooking still soft-deletes.
            releaseOldAllocatedListings(bookingId);
            deleteProvisionalBooking(bookingId);
        }
    }
```

- [ ] **Step 4: Delete the redundant reserve endpoint**

Remove from `CourtSchedulerApi`: the `putReserveUnconfirmedHearing` method and its imports. Remove `reserveUnconfirmedHearing` from `SlotsUpdateService` and `reserveUnconfirmedHearingValidation` from `HearingSlotsApiValidator`. Remove the `/sessions/{sessionId}/hearings/{unconfirmedHearingId}` block from the RAML and the matching path from the OpenAPI yml, along with the four `courtscheduler.reserve-unconfirmed-hearing*` json files. Delete the two domain classes listed above.

Nothing calls this endpoint — verified across all repos. Its logic now lives in `ReservationService`. Leaving it published would offer a second way to create a reservation under a caller-chosen key, which is exactly the collision hazard the minted booking id avoids.

- [ ] **Step 5: Remove the orphaned tests**

Delete the `reserveUnconfirmedHearing` tests from `SlotsUpdateServiceTest` and the reserve-endpoint scenario from `CourtSchedulerIT` — `ReservationServiceTest` covers the behaviour now.

- [ ] **Step 6: Full build**

Run: `./gradlew build -x integrationTest`
Expected: PASS with no unresolved references.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: release the reservation on confirmation; drop the unused reserve endpoint

persistHearingSlots now releases the bookingId-keyed hold before the hearing is
booked under its real id, so the session is never decremented twice. The
PUT /sessions/{id}/hearings/{id} endpoint had no callers and its logic now
lives in ReservationService."
```

---

### Task 8: A live-reservation check for the pre-share gate

The UI blocks sharing when a hold has expired. Sharing is asynchronous, so the check has to happen before the share command is sent — listing needs a synchronous way to ask "are these booking ids still held?".

**Files:**
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingService.java`
- Modify: `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/CourtSchedulerApi.java`
- Modify: `listingcourtscheduler-api/src/raml/courtscheduler-api.raml`
- Create: `listingcourtscheduler-api/src/raml/json/schema/courtscheduler.get.booking-status.json`
- Create: `listingcourtscheduler-api/src/raml/json/courtscheduler.get.booking-status.json`
- Test: `listingcourtscheduler-api/src/test/java/uk/gov/moj/cpp/courtscheduler/api/service/ProvisionalBookingServiceTest.java`

**Interfaces:**
- Consumes: `AllocatedListingRepository.findByHearingId(String)`; the legacy fallback from Task 6.
- Produces: `ProvisionalBookingService.getBookingStatus(String bookingIds)` → `JsonObject` of shape `{"bookings":[{"bookingId":"...","live":true}]}`, served at `GET /provisionalBooking/status?bookingIds=`.

- [ ] **Step 1: Write the failing test**

```java
    @Test
    void shouldReportABookingWithAReservationAsLive() {
        final AllocatedListing reservation = new AllocatedListing();
        reservation.setCourtScheduleId("cs-1");
        reservation.setHearingId("bk-live");
        reservation.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        when(allocatedListingRepository.findByHearingId("bk-live")).thenReturn(List.of(reservation));

        final JsonObject response = provisionalBookingService.getBookingStatus("bk-live");

        assertThat(response.getJsonArray("bookings").getJsonObject(0).getBoolean("live"), is(true));
    }

    @Test
    void shouldReportAPurgedBookingAsNotLive() {
        when(allocatedListingRepository.findByHearingId("bk-gone")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("bk-gone"))).thenReturn(List.of());

        final JsonObject response = provisionalBookingService.getBookingStatus("bk-gone");

        assertThat(response.getJsonArray("bookings").getJsonObject(0).getBoolean("live"), is(false));
    }

    @Test
    void shouldReportALegacyProvisionalBookingAsLive() {
        when(allocatedListingRepository.findByHearingId("legacy-bk")).thenReturn(List.of());
        when(provisionalBookingRepository.findByBookingIdIn(List.of("legacy-bk")))
                .thenReturn(List.of(aLegacyProvisionalBooking("legacy-bk", "cs-9")));

        final JsonObject response = provisionalBookingService.getBookingStatus("legacy-bk");

        assertThat(response.getJsonArray("bookings").getJsonObject(0).getBoolean("live"), is(true));
    }
```

The third test is the important one: a legacy draft must not be reported as expired, or every pre-go-live magistrates draft is blocked at share.

- [ ] **Step 2: Run them to verify they fail**

Run: `./gradlew :listingcourtscheduler-api:test --tests '*ProvisionalBookingServiceTest'`
Expected: FAIL — `getBookingStatus` does not exist.

- [ ] **Step 3: Implement `getBookingStatus`**

```java
    /**
     * Reports whether each booking id still has a hold behind it. Used by the pre-share gate:
     * sharing is asynchronous, so the clerk has to be told before the command is sent.
     *
     * <p>A legacy provisional_booking row counts as live. Those drafts never had a reservation,
     * and reporting them as expired would block every pre-go-live magistrates draft at share.
     */
    public JsonObject getBookingStatus(final String bookingIds) {
        final List<String> bookingIdList = Stream.of(bookingIds.split(","))
                .map(String::trim)
                .toList();

        final JsonArrayBuilder bookings = Json.createArrayBuilder();
        for (final String bookingId : bookingIdList) {
            final boolean hasReservation = allocatedListingRepository.findByHearingId(bookingId).stream()
                    .anyMatch(row -> row.getExpiresAt() != null);
            final boolean hasLegacyBooking = !hasReservation
                    && !provisionalBookingRepository.findByBookingIdIn(List.of(bookingId)).isEmpty();
            bookings.add(Json.createObjectBuilder()
                    .add("bookingId", bookingId)
                    .add("live", hasReservation || hasLegacyBooking));
        }
        return Json.createObjectBuilder().add("bookings", bookings).build();
    }
```

- [ ] **Step 4: Expose it**

Add to `src/raml/courtscheduler-api.raml`, immediately after the existing `/provisionalBooking` block:

```yaml
/provisionalBooking/status:
  get:
    description:  |
      Reports whether each booking id still has a hold behind it — a reservation, or a legacy
      provisional booking. Used by the pre-share gate in the results UI, which must block the
      share synchronously because sharing itself is asynchronous.
      ...
        (mapping):
            responseType: application/vnd.courtscheduler.get.booking-status+json
            name: courtscheduler.get.booking-status
      ...
    queryParameters:
      bookingIds:
        description: booking ids as comma separated
        type: string
        required: true
    responses:
      200:
        body:
          application/vnd.courtscheduler.get.booking-status+json:
            example: !include json/courtscheduler.get.booking-status.json
            schema:  !include json/schema/courtscheduler.get.booking-status.json
```

`src/raml/json/schema/courtscheduler.get.booking-status.json`:

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
          "live": { "type": "boolean" }
        },
        "required": ["bookingId", "live"]
      }
    }
  },
  "required": ["bookings"]
}
```

`src/raml/json/courtscheduler.get.booking-status.json`:

```json
{
  "bookings": [
    { "bookingId": "4e29c1fa-7d3b-4f21-9c88-1a2b3c4d7ac1", "live": true },
    { "bookingId": "b7c2f04e-55a1-4c19-8f0d-9e7a6b5c9d31", "live": false }
  ]
}
```

Add the handler to `CourtSchedulerApi` beside `getProvisionalBooking`:

```java
    /** GET /provisionalBooking/status — is each booking id still held? */
    @Override
    public ResponseEntity<Map<String, Object>> getBookingStatus(final String bookingIds) {
        LOG.info("courtscheduler.get.booking-status bookingIds={}", Encode.forJava(bookingIds));
        return ResponseEntity.ok(toResponseMap(provisionalBookingService.getBookingStatus(bookingIds)));
    }
```

Mirror the same path in `openapi/courtscheduler-api.openapi.yml` so the generated `ProvisionalBookingOpenApi` interface declares `getBookingStatus`.

- [ ] **Step 5: Run the tests**

Run: `./gradlew :listingcourtscheduler-api:test`
Expected: PASS.

- [ ] **Step 6: Full build**

Run: `./gradlew build -x integrationTest`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add -A
git commit -m "feat: expose a live-reservation check for the pre-share gate

GET /provisionalBooking/status?bookingIds= reports whether each booking still
has a hold. Legacy provisional_booking rows count as live so pre-go-live
magistrates drafts are not blocked at share."
```

---

## Deliberately unchanged

**Eager release on re-pick needs no courtscheduler change.** When the clerk picks a different session, deletes the result line or resets results, the UI releases the previous hold through the existing `DELETE /sessions/{hearingId}` → `SlotsRemoveService.remove` → `releaseOldAllocatedListings`. Passing a bookingId there already works — the row's `hearing_id` is the bookingId — and that path already performs the full three-step release including capacity restoration. Task 7's `shouldTolerateReleasingABookingWithNoReservation` covers the no-op case.

**Availability queries keep counting reservations.** `getCountBasedAllocatedListing` and the other capacity reads must continue to see reservation rows: a hold that did not reduce availability would not be a hold. Only the hearing-listing query in Task 2 filters them out.

## Follow-on plans

| Plan | Repo | Depends on |
|---|---|---|
| 2 | `cpp-context-hearing` — thread `duration` through the command, `ProvisionalHearingSlotInfo` and the domain event | Task 4 |
| 3 | `cpp-context-listing` — Crown resolves `bookingReference` as a booking id; expose the pre-share check on the query API | Tasks 6, 8 |
| 4 | `cpp-ui-hearing` — Crown and related-hearings reserve at pick; release on re-pick/delete/reset; the three clerk messages; swap the pre-share check | Plans 2, 3 |
| 5 | `cpp.static-data.patches` — register the 01:00 `courtscheduler.purge-expired-reserved-sessions` job | Task 1 |

`cpp-ui-listing` and `cpp-context-progression` need no changes.
