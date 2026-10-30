# Reserve a Slot — NEW-17: a Crown multi-day pick holds every day, not just the anchor

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans. Steps use checkbox (`- [ ]`) syntax.

**Goal:** A Crown multi-day pick reserves **all N days** immediately, each stamped with `expires_at`, so the whole run is genuinely held and cannot be taken by someone else between pick and share.

**Blocking a deployment to STE.** Correct and self-contained matters more than elegant.

**Repo:** `cpp-context-listing-courtscheduler` (**Gradle**), branch `team/ras`

## Global Constraints

- **NO COMMITS.** Everything stays unstaged. Any Commit step is an explicit no-op.
- **Gradle only.** Never `mvn`.
- Nothing in this feature is committed; `git diff` shows ~45 files of prior work. Ignore it.
- **Do not duplicate the consecutive-run logic.** Pick-time and share-time must resolve the *same* run. If they disagree, the reservation holds days the share will not use, and the days the share does use were never held — which defeats the entire point of this ticket.

## Today's behaviour and why it is wrong

A Crown multi-day pick reserves only its **anchor** day. Days 2..N are discovered by a consecutive-session search at share time, which may be hours later. So a five-day trial holds one day, and four of its five days can be taken by other clerks in the meantime.

## The capacity rule for this path — decided, and deliberately not BUG-9's

`selectConsecutiveSessions` carries the documented **court-calendar rule (F1)**: *a lack of session capacity must NEVER block the assignment; only STRUCTURAL failures (too few session days, non-consecutive dates) reject the run.* Within a single date `preferBookable` picks a session with room if one exists, and when none does it deliberately keeps a full one — because dropping the date would break consecutiveness.

**A Crown multi-day reservation must follow that same rule**, not BUG-9's refusal. The reserve must never be stricter than the share: refusing a pick the share path would happily place would block clerks from listings the platform permits. BUG-9's capacity refusal continues to apply to slot-based and single-day picks, where no F1 rule exists and the pre-feature share-time check genuinely did reject.

So for Crown multi-day: **reserve all N days even when a day is full**, logging the overbooked days advisorily exactly as `logSessionsBookedBeyondCapacity` already does. Refuse only structurally.

---

### Task 1: Share the consecutive-run selection, then reserve the whole run

**Files:**
- Create: a small collaborator, e.g. `listingcourtscheduler-api/src/main/java/uk/gov/moj/cpp/courtscheduler/api/service/ConsecutiveSessionSelector.java`
- Modify: `.../api/service/SlotsUpdateService.java` — delegate to it, behaviour unchanged
- Modify: `.../api/service/ReservationService.java` — expand a Crown multi-day request
- Tests: `ReservationServiceTest`, plus whatever covers `SlotsUpdateService`'s multi-day paths

**Read first.** `SlotsUpdateService` lines ~470-520 (the multi-day book path), ~826 (`crownDaysNeeded`), ~878 (`selectConsecutiveSessions`), ~936 (`bookConsecutiveSessions`), ~951 (`perDayDuration`), ~1039 (`dedupeByDatePreferringBookable`) and `preferBookable`. Then `ReservationService.reserveAll` and `toReservedSlot`.

- [ ] **Step 1: Extract the selection, changing no behaviour**

Move `selectConsecutiveSessions`, `dedupeByDatePreferringBookable`, `preferBookable`, `getEffectiveAvailableDuration`, `areConsecutiveBusinessDays`, `logSessionsBookedBeyondCapacity` and `perDayDuration` into the new collaborator — whichever of them the selection genuinely needs; leave `bookConsecutiveSessions`, `persistSessions` and `reclaimHearingsOwnCapacity` where they are, since they are about *booking*, not selecting.

`SlotsUpdateService` then delegates. **This step must be behaviour-preserving**: run its existing tests before and after and show both runs in your report. If any test needs changing, stop — that means the extraction altered behaviour.

Keep the F1 Javadoc with the code it describes. It is the only written record of why capacity does not reject here.

- [ ] **Step 2: Expand a Crown multi-day reservation**

In `ReservationService`, before building slots, expand any requested slot that is a Crown multi-day pick into the full run:

- **Detect it** the way the rest of the codebase does: a **duration-based** session with a requested `duration` greater than one court day (360). Derive `daysNeeded` by the same `/ 360` arithmetic `validateListModeMultiDay` and `crownDaysNeeded` use — do not invent a different rule. Confirm the anchor session's jurisdiction is CROWN before treating it as multi-day.
- **Resolve the run** with `courtScheduleRepository.findConsecutiveSessions(anchorCourtScheduleId, daysNeeded)` followed by the extracted selector, using `perDayDuration(total, daysNeeded)` as the per-day minutes — the identical calls the share path makes.
- **Refuse only structurally.** If the selector returns empty (too few session days, or non-consecutive dates) throw `NoCapacityException` with a message naming the anchor and the days needed. Do **not** add a capacity refusal — see the rule above.
- **Build one `AllocatedSlot` per selected session**, each with that session's id, `perDay` duration, and the slot's own session date. `toReservedSlot` already stamps `expires_at` and `source = RESERVED_UNCONFIRMED` on every row, so each day is held and each day expires.
- **Keep it a single `saveBookedSlots` call for the whole booking.** `reserveAll` already does this and the class Javadoc explains why: every slot shares the bookingId as its `hearing_id`, and `saveBookedSlots` opens with a hearing-wide release keyed on that id — so reserving day by day would make each day release the previous ones and a five-day pick would hold one day. **Do not loop.**

Non-Crown and single-day requests must be completely unaffected.

- [ ] **Step 3: Tests**

In `ReservationServiceTest`, reusing the file's existing fixtures and helpers:

- a Crown duration-based request of 1800 minutes reserves **5** sessions under one bookingId, each carrying `expires_at`
- exactly **one** `saveBookedSlots` call carries all 5 (guards the release-each-other trap)
- a run where one day is **full** is still reserved in full (the F1 rule) — this is the test that pins the decision above
- a structurally impossible run (selector returns empty) throws `NoCapacityException` and calls `saveBookedSlots` **never**
- a single-day Crown request (360 or less) reserves exactly one session, unchanged
- a slot-based request is untouched by any of this

Make the 5-session test fail against an implementation that reserves only the anchor — if it passes either way it is not testing this ticket.

- [ ] **Step 4: Run**

```bash
./gradlew :listingcourtscheduler-api:test --tests '*ReservationServiceTest*' --tests '*SlotsUpdateService*'
./gradlew build -x test
./gradlew :listingcourtscheduler-integration-test:test --tests '*ProvisionalBookingIT*'
```

`ProvisionalBookingIT` must stay **8/8** — its bookings are single-day and slot-based, so this must be invisible to them. If any of the 8 fails, that is the most important thing in your report.

- [ ] **Step 5: Commit** — **skipped, no commits.**

---

## Deliberately not in scope

- **The no-anchor variant** (`findConsecutiveSessionsForCentre`). A pick always has an anchor — the clerk chose a session. Do not wire the centre-wide search into the reserve path.
- **Changing BUG-9's refusal for slot-based or single-day picks.** That rule stands where it applies.
- **The share path's own behaviour.** Step 1 is a pure extraction; share-time booking must be byte-for-byte the same.
