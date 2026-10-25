# Re-book an expired hold at share time

LPT-2611 (epic LPT-2430). Reserve-a-slot (RAS). Branches: courtscheduler and listing `team/ras`,
cpp-ui-hearing `dev/rasproto`, hearing and progression `team/ras`. Hearing and progression change
only to send a release `reason` (section 1); their share and release logic is unchanged.

## Requirement

From LPT-2611:

> If a booking has expired and the slot still has capacity, book it during share.
> Currently, booking references are created and deleted on the fly and there is no accurate way of
> tracing it. Having this recorded in a DB would be helpful. Note that older data would be cleared
> periodically.

So two outcomes: (1) re-book an expired hold at share when its session has room; (2) a durable,
periodically cleared record of every booking reference, which is also what (1) needs.

## Why

A pick takes a hold: one `allocated_listings` row per session day, under a minted `booking_id`, with
`expires_at` set to the UTC date of the pick (`ReservationService.toReservedSlot`). The nightly purge
(`POST /sessions` -> `AllocatedListingService.purgeExpiredReservedSessions` ->
`CourtScheduleRepositoryImpl.releaseExpiredReservations`) deletes every row whose `expires_at` is
before today and gives the capacity back.

So a clerk who results in the afternoon and shares the next morning loses the hold overnight. At share,
the UI's pre-share gate asks for the booking's status, gets `NOT_FOUND`, and blocks the share with
"The session you reserved has expired and been released. Select another session." The clerk has to
re-pick, even when the session they picked is still half empty.

The aim: when the session they picked can still take the booking, the share just goes ahead.

## Behaviour

At share, for each booking the gate checks that answers `NOT_FOUND` today:

- **The remembered session(s) can still take it** -> courtscheduler reserves it again, under the
  **same booking id**, and the share goes ahead. The clerk sees nothing different from a normal share.
- **A session is full, has been removed, is in the past, or nothing is remembered for this booking**
  -> unchanged from today: the share is blocked with the existing "expired and been released"
  message and the clerk re-picks. (Decided with the product owner: no new message.)

Re-booking is all or nothing per booking: a multi-day or multi-slot booking is re-booked for every
day/slot or not at all.

Every other path is unchanged: a booking still `RESERVED` or already `SHARED` is never touched.

## Design

### Shape (decided)

No new endpoint. The existing pre-share check gains a flag:

```
UI   POST /listing-query-api/query/api/rest/listing/bookingStatus
        { "bookingIds": [...], "reserveIfExpired": true }
listing  (CourtSchedulerServiceAdapter.getBookingStatus) passes the flag on
courtscheduler  /unconfirmedBooking/status  -> ProvisionalBookingService.getBookingStatus
```

Re-using the same booking id is the point: the result line's `bookingReference` prompt stays valid,
so the hearing draft, hearing's release-on-save (LPT-2617) and progression's share-time resolution
(`ProvisionalBookingServiceAdapter.getSlots` -> `fetchProvisionalSlots`) all work unchanged against
the re-booked hold.

### 1. courtscheduler: a booking log

The purge deletes the row, and nothing else records which session a hold was on. The result line
keeps only display prompts (`HDATE`, `timeOfHearing`, room name `HCROOM`, `HTYPE`, `HEST`) and the
booking id. And per LPT-2611, nothing traces a booking reference through its life at all.

New append-only table (Liquibase changeset 054 in `listingcourtscheduler-viewstore-liquibase`),
`booking_log`: one row per booking event per session row: `id` (random UUID), `event_id` (shared by
the rows of one state change), `booking_id`, `state` (enum `BookingState`), `court_schedule_id`, `hearing_id`
(when known), `oucode`, `court_room_id`, `rota_business_type`, `hearing_start_time`, `duration`,
`expires_at` (the hold's expiry as it was; null for a confirmed listing), `recorded_at` (when the
row was appended; strictly increasing per instance at microsecond precision, and the ordering key).
Written in the same transaction as the change it records. The latest row is the booking's current
state (named `state`/`BookingState` by decision, 2026-10-25). States:

| state | written by |
|---|---|
| `RESERVED` | a pick (`ReservationService.reserveAll`), one row per slot/day reserved |
| `REPICK_RELEASED` | the re-pick wipe (`releaseReservationsForBooking`) |
| `DRAFT_RELEASED` | `POST /unconfirmedBooking/release` from hearing: the line was dropped from the draft (deleted, attached to an existing hearing, results reset) |
| `SURPLUS_RELEASED` | `POST /unconfirmedBooking/release` from progression: the share merged two lines onto one session and this reservation was the surplus |
| `RELEASED` | `POST /unconfirmedBooking/release` with no reason (an old caller during the deploy window) |
| `EXPIRED` | the nightly purge (`releaseExpiredReservations`) |
| `CONFIRMED` | the share that turns the hold into a listing (stamps `hearing_id`) |
| `REBOOKED` | the re-book in section 2 |

A separate table, not rows kept in `allocated_listings`: every capacity query counts all
`allocated_listings` rows regardless of `expires_at`, so keeping dead rows there would mean touching
all of them.

The release endpoint cannot tell its two callers apart today, so its request gains an optional
`reason`: `DROPPED_FROM_DRAFT` (hearing) or `SURPLUS` (progression), mapped to the actions above;
absent -> `RELEASED`. Update the openapi model, the RAML JSON schema
(`courtscheduler.release.unconfirmed.bookings.json`) and the two callers:
- hearing (`team/ras`): the release-provisional-hearing-slots processor ->
  `ProvisionalBookingService.releaseUnconfirmedBookings` sends `DROPPED_FROM_DRAFT` (the event
  already carries `reason: DROPPED_FROM_DRAFT`, its only value since the release command and its
  `RELEASED_BY_USER` reason were removed on 2026-10-25);
- progression (`team/ras`): `SurplusReservationReleaseService.release` sends `SURPLUS`.
`SURPLUS_RELEASED` rather than `MERGED_RELEASED` (my call): it matches progression's own name for
it, and "merged" describes the line that won, not the hold that was released.

Re-book reads it: a booking is re-bookable only if its **latest** state is `EXPIRED`, and it is
re-booked onto the session rows of that `EXPIRED` event. A booking whose latest state is any
`*_RELEASED` (the clerk dropped it, or a share made it surplus) can therefore never come back at
share.

A release that finds nothing held for a booking (the purge got there first) still writes its
release row, with no session columns, so a dropped booking can never come back.

Clean-up (LPT-2611 "older data cleared periodically"): the nightly purge also deletes log rows older
than `courtscheduler.booking-log.retention-days`, default 90.

### 1b. courtscheduler: make the shared capacity rule count, like the search API

`SessionsService.evaluateAvailability` (~line 1203) answers a plain duration-based (not AD-split)
session from `cs.getAvailableDuration()`. Change that branch to
`max_duration_mins - sum(allocated_listings.duration)` for the session, the figure the search query
computes. Slot-based (`validateListModeSlotBased`, already `getAllocatedListingsByCourtScheduleId`)
and AD-split (already `getAllocatedListingEachBookedByCourtScheduleId`) need no change.

This is the rule a pick runs on reserve (`ReservationService.reserveAll`), so picks get the same
fix: decided in scope. The pick keeps its capacity check even though the search only offers
sessions with room: the search result is a snapshot, and two clerks (or one with a stale search
page) can pick the last capacity of a session; the reserve-time check is what stops the second.

### 2. courtscheduler: re-book on the status call

`getBookingStatus` takes the flag. Per booking id, when the flag is set and `statusOf` would answer
`NOT_FOUND`:

1. Read the booking's latest `booking_log` state. Not `EXPIRED` (or no rows) -> `NOT_FOUND`. Otherwise take the session rows of that `EXPIRED` event.
2. Any remembered session in the past, or whose `court_schedule_id` no longer exists or is not
   active -> `NOT_FOUND`. **Decided: no lookup by court/room/date/business type.** A rota reload
   deletes active sessions with no `allocated_listings` and re-creates them under a new id
   (`DELETE_UNALLOCATED_COURT_SCHEDULE_QUERY`, `RotaDataEnricher` `randomUUID()`), so a session
   emptied by the purge can lose its id; such a booking is treated as gone and the clerk re-picks.
   The log keeps `oucode`/`court_room_id`/`rota_business_type`/`session_date` for tracing only.
3. Check capacity for **every** remembered row - including each day of a Crown multi-day run - then
   reserve all rows again under the same booking id, with their original session, start time and
   duration, in one transaction, with a fresh `expires_at` = today (UTC) and
   `source = RESERVE_UNCONFIRMED_SLOT`.

   Why every row, Crown included: a pick is preceded by a search that only offers sessions the
   hearing fits in (Crown and magistrates alike), so the clerk never picks a full session. A re-book
   has no search in front of it; it has to make that check itself.

   **Capacity is computed from `allocated_listings`, never from `court_schedule.available_slot` /
   `available_duration_mins`** - those columns are being removed. Use the shared rule after the fix
   in section 1b (`SessionsService.validateSessionAvailabilityListMode` / `evaluateAvailability`),
   which then counts exactly as the search API does (`CourtScheduleRepositoryImpl` ~line 140):
   - slot-based: `max_slot - count(allocated_listings)` must be >= 1;
   - duration-based, not AD-split: `max_duration_mins - sum(allocated_listings.duration)` must be
     >= the row's duration;
   - AD-split: the morning/afternoon maximums against counted bookings.
   Whole-session, like the search; there is no per-start-time limit. For a Crown multi-day run,
   check each day's row on its own session with that day's duration (do not route it through
   `validateListModeMultiDay`, which re-derives the run).

   **`isOverbookingAllowed` is honoured, per session:** a row whose session has
   `isOverbookingAllowed = true` passes the capacity check whatever the count says, exactly as the
   shared rule already does for a pick (`validateListModeSlotBased` / `validateListModeDurationBased`
   `continue` on it). This applies to every row, each day of a Crown multi-day run included, and the
   flag is read from the session as it is at re-book time, not as it was at the pick. A full session
   that allows overbooking is therefore re-booked; a full session that does not is `NOT_FOUND`.

   The counter columns are not written by the re-book, nor by any other booking path (decided
   2026-10-25, see "Counters" below); nothing reads them.
4. Success -> write `REBOOKED` log rows, answer status **`REBOOKED`**,
   `safeToShare: true`. Capacity refusal -> roll back, answer `NOT_FOUND`.

Each booking is its own unit: one booking failing does not undo another that succeeded. The gate
blocks the share if any booking is still `NOT_FOUND` (today's behaviour), and a booking re-booked in
that call simply holds until it is shared on the retry or purged again.

Without the flag the endpoint behaves exactly as today (read only).

The courtscheduler leg is a `POST` on `/unconfirmedBooking/status` with
`{bookingIds, reserveIfExpired}` in the body (`application/vnd.courtscheduler.query.booking.status+json`,
access rule `courtscheduler.query.booking.status`): a GET that books capacity can be retried or
cached by anything in between. The earlier `GET` form (`courtscheduler.get.booking-status`) was
never released and is removed (decided 2026-10-25); listing always posts, passing the flag as the UI
sent it.

### Counters (decided 2026-10-25)

No booking path writes `court_schedule.available_slot` / `available_duration_mins` any more:
reserve, save, share-confirm, every release, the purge and the batch release's capacity `UPDATE`
are all gone. Every decision that read them now counts `allocated_listings`: the rota reload's
"delete only empty sessions", move-to-past's `overbooked`, and Crown fallback's `lacksCapacity`
(the session-edit guards were already a join). The columns are still set on session create,
admin edit and rota re-sync, and echoed by some responses that nothing reads; dropping them is a
separate change.

### 3. listing

- `listing.query.booking.status.json`: add optional boolean `reserveIfExpired`
  (`additionalProperties: false` today, so this is required).
- `CourtSchedulerServiceAdapter.getBookingStatus` / `HearingSlotsService.getBookingStatus`: pass the
  flag through (POST when set).
- `listing.query.booking.status.response.json` and the RAML description: add `REBOOKED`. (The
  description still lists `LEGACY`/`NONE`, which courtscheduler no longer returns; correct it to
  `RESERVED | SHARED | NOT_FOUND | REBOOKED | UNKNOWN`.)
- Fail-open stays as it is: if courtscheduler cannot be reached, listing answers `UNKNOWN`,
  `safeToShare: true`.

### 4. cpp-ui-hearing

- `ListingService.getBookingStatus` sends `reserveIfExpired: true`. The gate is the only caller.
- `REBOOKED` arrives with `safeToShare: true`, so `checkBookingStatus` already lets it through;
  add it to the status type and a test.
- No new message.

## Constraints

- Re-book never overbooks a session that does not allow it: capacity from `allocated_listings`
  counts (the search API's logic); a session with `isOverbookingAllowed = true` may be overbooked,
  as a pick may. Never read `available_slot` / `available_duration_mins`.
- Never touches a booking that has a confirmed row (`statusOf` answers `SHARED` before `NOT_FOUND`,
  and `guardAgainstConfirmedAllocation` still applies).
- Same booking id, so nothing downstream of the UI changes.
- Deploy order: courtscheduler, then listing, then UI. Each step is backward compatible (the flag
  is optional; an old caller never sends it).
- The `booking_log` only fills from the first purge after courtscheduler deploys, so
  holds purged before that cannot be re-booked. Acceptable.

## Non-goals

- Re-booking anywhere other than the pre-share gate (not in progression, not at draft load).
- Picking a *different* session when the remembered one is full; the clerk re-picks as today.
- Changing hold lifetime or purge timing.
- Fixing the gate's fail-open, or progression silently not listing a magistrates offence whose
  booking it cannot resolve (`HearingToHearingListingNeedsTransformer`, warn and return). Both
  exist today and this change does not make them worse.
- Dropping the court_schedule counter columns (separate planned change).
- Finding a rota-reloaded session again by its details.

## Anti-goals

- A re-booked hold that overbooks a session whose `isOverbookingAllowed` is not true.
- A re-book refused for capacity on a session that allows overbooking.
- A booking the clerk released (deleted the line, attached it elsewhere) coming back at share.
- A share that goes ahead with a hold on a session other than the one the clerk picked.

## Testing

- courtscheduler unit + IT: purge writes history; re-book succeeds with capacity; refuses when full,
  past, removed, or no history; all-or-nothing for multi-day/multi-slot; released booking cannot be
  re-booked; flag absent = unchanged read-only behaviour; a session whose
  id a rota reload replaced -> `NOT_FOUND`; Crown multi-day run refused when any day is full;
  every booking state writes its log row; log retention clean-up; a full session with
  `isOverbookingAllowed = true` is re-booked (slot-based, duration-based, AD-split, and one day of
  a Crown run), and the same session with it false is refused.
- courtscheduler 1b: a pick on a plain duration-based session is refused when
  `sum(allocated_listings.duration)` leaves too little room even if `available_duration_mins` says
  otherwise, and accepted in the reverse case (proves the column is no longer read).
- listing: schema accepts the flag; adapter passes it; `REBOOKED` round-trips.
- UI: request carries the flag; `REBOOKED` lets the share through; `NOT_FOUND` still blocks.
- Serenity (cpp-ui-e2e-serenity `team/ras`): pick, let the hold expire (purge it), share -> listed in
  the picked session; and the same with the session filled in between -> blocked with today's
  message. Needs a way to run the purge on demand on STE.

## Left to the builder

- Indexes.
- How the re-book reuses `ReservationService` (a variant of `reserveAll` that takes the remembered
  per-day rows as they are, rather than re-expanding a Crown multi-day run).
- Logging and metrics.
