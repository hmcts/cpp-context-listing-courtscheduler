package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.moj.cpp.courtscheduler.api.validator.ValidationException;
import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.NoCapacityException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.inject.Inject;
import jakarta.json.Json;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates capacity-holding, expiring reservations. A reservation is an ordinary
 * allocated_listings row written through the same pipeline as a real booking — so
 * court_schedule capacity is genuinely decremented — but stamped with expires_at so the
 * nightly purge sweeps it if the result is never shared.
 *
 * <p>The row's hearing_id is the bookingId. That is load-bearing in two directions.
 * saveBookedSlots begins with a hearing-wide releaseOldAllocatedListings(hearing_id), so when a
 * clerk re-picks under the same bookingId, the previous pick's rows are released before the new
 * ones are taken — which is exactly how a re-pick gives back the session it abandons, with no
 * separate release call. Two different next-hearings can never collide on one id: each
 * NHCC/NHMC result line mints its own bookingReference and carries it in its own prompt.
 *
 * <p><b>Why one call per booking, not one per slot.</b> Because every slot of a booking shares
 * the minted bookingId as its hearing_id, calling {@code saveBookedSlots} once per slot makes
 * each call release the previous slots of the <em>same</em> booking — a three-day pick would end
 * up holding only its last day. {@link #reserveAll} therefore builds every {@link AllocatedSlot}
 * first and hands the whole list to a single {@code saveBookedSlots}: the hearing-wide release
 * fires once, before anything of this booking exists. That makes all-or-nothing structural rather
 * than only transactional. This is exactly as true of a Crown multi-day run's per-day rows as it
 * is of an ordinary pick — {@link #expandCrownMultiDay} builds every day's {@link AllocatedSlot}
 * up front and none of them are persisted until the single {@code saveBookedSlots} call below.
 *
 * <p><b>Capacity is enforced here, on reserve — except for a Crown multi-day run.</b> Before the
 * reserve-a-slot feature, overbooking was prevented at share time, by the results UI's own call to
 * {@code validateSessionAvailability} (reaching {@link SessionsService#validateSessionAvailabilityListMode}).
 * That UI call was later replaced by a hold-expiry check that says nothing about capacity, so
 * nothing enforced it any more. {@link #reserveAll} now calls {@code validateSessionAvailabilityListMode}
 * itself — the same shared rule, not a copy, so {@code isOverbookingAllowed} keeps working exactly
 * as it did at share time — and does so before any {@link AllocatedSlot} is built or {@code
 * saveBookedSlots} is called, so a rejection never leaves capacity decremented.
 *
 * <p>A Crown multi-day pick (a duration-based session whose requested duration exceeds one court
 * day) is the one exception: {@link #expandCrownMultiDay} resolves the whole run up front via
 * {@link ConsecutiveSessionSelector} — the SAME selector {@code SlotsUpdateService} uses at share
 * time — and that selector's court-calendar rule (F1) never rejects for lack of capacity, only for
 * a structural failure (too few session days, or non-consecutive dates). Reserving must not be
 * stricter than sharing, so the ordinary per-session capacity gate below is skipped for the days
 * a Crown multi-day run resolves to; {@link SessionsService#validateSessionAvailabilityListMode}'s
 * own separate BUG-9 refusal keeps applying, unchanged, to slot-based and single-day picks.
 */
@Service
public class ReservationService {

    public static final String SOURCE_RESERVED_UNCONFIRMED = "RESERVED_UNCONFIRMED";

    private static final String ERROR_MESSAGE = "errorMessage";
    private static final String JURISDICTION_CROWN = "CROWN";
    private static final int MINUTES_IN_DAY = 360;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Inject
    private SessionsService sessionsService;

    /**
     * Single-slot entry point, kept for callers that hold exactly one session. Delegates to
     * {@link #reserveAll} so both paths share the guard, the stamping and the single
     * {@code saveBookedSlots} call.
     */
    @Transactional
    public AllocatedSlot reserve(final String sessionId,
                                 final String bookingId,
                                 final String hearingStartTime,
                                 final Integer duration) {
        return reserveAll(bookingId, List.of(ProvisionalSlot.ProvisionalSlotBuilder.aProvisionalSlot()
                .withCourtScheduleId(sessionId)
                .withHearingStartTime(hearingStartTime)
                .withDuration(duration)
                .build())).get(0);
    }

    /**
     * Reserves every requested session under one bookingId, atomically. When the bookingId is
     * one the caller already holds a reservation under (a clerk re-picking a different session
     * for the same draft), the pipeline's own hearing-wide release — {@code saveBookedSlots}
     * opens with {@code releaseOldAllocatedListings(hearing_id)}, and a reservation's hearing_id
     * is its bookingId — gives back the abandoned pick's capacity before these new slots are
     * taken, in the same transaction. No separate release call is needed or made here.
     *
     * <p>Each slot still derives {@code slotBased} from its own session and is individually
     * stamped with {@code expires_at = today (UTC)} and {@code source = RESERVED_UNCONFIRMED}.
     *
     * <p>A requested slot that is a Crown multi-day pick (see {@link #expandCrownMultiDay}) is
     * expanded here, before any slot is persisted, into one {@link AllocatedSlot} per day of the
     * run — still added to the SAME {@code slots} list, so the whole booking (single-day picks,
     * slot-based picks and every day of a Crown multi-day run alike) still goes over in the one
     * {@code saveBookedSlots} call below.
     */
    @Transactional
    public List<AllocatedSlot> reserveAll(final String bookingId, final List<ProvisionalSlot> requestedSlots) {

        final Set<String> ownHeldCourtScheduleIds = guardAgainstConfirmedAllocation(bookingId);

        final List<AllocatedSlot> slots = new ArrayList<>();
        for (final ProvisionalSlot requested : requestedSlots) {
            final Optional<List<AllocatedSlot>> crownMultiDayRun = expandCrownMultiDay(bookingId, requested);
            if (crownMultiDayRun.isPresent()) {
                slots.addAll(crownMultiDayRun.get());
            } else {
                slots.add(toReservedSlot(bookingId, requested, ownHeldCourtScheduleIds));
            }
        }

        // The re-pick wipe. This used to come for free: a reservation's hearing_id WAS the
        // bookingId, so saveBookedSlots' own hearing-wide release covered it. Reservations now
        // live in booking_id and leave hearing_id null, so that release no longer sees them and
        // this call takes its place - explicitly, before anything of the new pick is written, so
        // a re-pick still gives back the session it abandons in the same transaction.
        //
        // It releases only unconfirmed rows (see releaseReservationsForBooking), so a bookingId
        // whose earlier pick was already shared keeps its confirmed listing untouched.
        courtScheduleRepository.releaseReservationsForBooking(bookingId);

        // releaseExistingHearingAllocations = false: there is no hearing_id on these rows to
        // release by, and the booking-scoped release above has already run.
        final Result result = courtScheduleRepository.saveBookedSlots(slots, false, false, false);
        if (!result.isSuccess()) {
            throw new NoCapacityException(
                    "Could not reserve sessions for booking " + bookingId + ": " + result.getMsg());
        }
        return slots;
    }

    /**
     * Expands a Crown multi-day pick (a duration-based session whose requested duration exceeds
     * one court day) into the full consecutive run, resolving it the same way the share path does
     * — {@code findConsecutiveSessions} from the anchor, then {@link ConsecutiveSessionSelector}
     * with {@code perDayDuration(total, daysNeeded)} as the per-day minutes — so pick and share
     * can never disagree about which run this booking holds.
     *
     * <p>Not a Crown multi-day pick (no duration, duration at or under one court day, or the
     * anchor session's jurisdiction is not CROWN) returns empty and {@link #reserveAll} falls back
     * to the ordinary single-session {@link #toReservedSlot}. Non-Crown and single-day requests
     * are therefore completely unaffected by this method.
     *
     * <p>{@code daysNeeded} is derived by the identical {@code duration / 360} arithmetic
     * {@code SlotsUpdateService#crownDaysNeeded} and {@code SessionsService#validateListModeMultiDay}
     * both use — not a different rule.
     *
     * @throws NoCapacityException only for a STRUCTURAL failure (too few session days, or
     *         non-consecutive dates) — the selector's F1 court-calendar rule means a full day
     *         never causes this; capacity is never a reason to refuse a Crown multi-day run.
     */
    private Optional<List<AllocatedSlot>> expandCrownMultiDay(final String bookingId, final ProvisionalSlot requested) {
        final Integer duration = requested.getDuration();
        if (duration == null || duration <= MINUTES_IN_DAY) {
            return Optional.empty();
        }

        final String anchorId = requested.getCourtScheduleId();
        final CourtSchedule anchor = courtScheduleRepository.getCourtSchedulesByIdList(List.of(anchorId)).stream()
                .filter(CourtSchedule::isActive)
                .findFirst()
                .orElseThrow(() -> new NoSessionAvailableException("No session found for sessionId " + anchorId));

        // The 360-arithmetic multi-day rule only ever applies to duration-based sessions —
        // SessionsService#validateSessionAvailabilityListMode branches to its slot-based validator
        // before it ever reaches validateListModeDurationBased/validateListModeMultiDay, so a
        // slot-based anchor is never a Crown multi-day pick, whatever duration happens to be sent.
        if (anchor.isSlotBased() || !JURISDICTION_CROWN.equalsIgnoreCase(anchor.getJurisdiction())) {
            return Optional.empty();
        }

        final int daysNeeded = Math.max((int) Math.ceil(duration / (double) MINUTES_IN_DAY), 1);
        final int perDay = ConsecutiveSessionSelector.perDayDuration(duration, daysNeeded);
        final List<CourtSchedule> candidates = courtScheduleRepository.findConsecutiveSessions(anchorId, daysNeeded);
        final List<CourtSchedule> sessions =
                ConsecutiveSessionSelector.selectConsecutiveSessions(candidates, daysNeeded, bookingId, perDay);

        if (sessions.isEmpty()) {
            throw new NoCapacityException("Could not resolve a " + daysNeeded
                    + "-day consecutive run for booking " + bookingId + " anchored at session " + anchorId);
        }

        return Optional.of(sessions.stream()
                .map(session -> toReservedSlotForCrownMultiDayRun(bookingId, session, perDay))
                .toList());
    }

    /**
     * Builds one already-selected Crown multi-day day into an {@link AllocatedSlot}, stamped the
     * same way {@link #toReservedSlot} stamps every reservation ({@code expires_at}, {@code source
     * = RESERVED_UNCONFIRMED}) — but WITHOUT that method's per-session capacity gate: {@code
     * expandCrownMultiDay}'s {@link ConsecutiveSessionSelector} call has already resolved this
     * session under the F1 court-calendar rule (never reject for capacity), so re-running the
     * ordinary capacity check here would make reserving stricter than sharing.
     */
    private AllocatedSlot toReservedSlotForCrownMultiDayRun(final String bookingId, final CourtSchedule session,
                                                            final int perDayMinutes) {
        final AllocatedSlot slot = new AllocatedSlot();
        slot.setCourtScheduleId(session.getCourtScheduleId());
        // booking_id, not hearing_id: this row holds no hearing yet. hearing_id stays null until
        // the share confirms the booking and stamps the real next-hearing id onto it.
        slot.setBookingId(bookingId);
        slot.setOuCode(session.getOuCode());
        slot.setSessionDate(session.getSessionDate().toString());
        slot.setDuration(perDayMinutes);
        if (session.getSessionStartTime() != null) {
            slot.setHearingStartTime(DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())));
        }
        slot.setSlotBased(session.isSlotBased());
        slot.setSource(SOURCE_RESERVED_UNCONFIRMED);
        slot.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        return slot;
    }

    /**
     * A bookingId that already carries a confirmed allocation ({@code expires_at IS NULL}) must
     * not be reserved over: the pipeline's hearing-wide release would silently drop the real
     * booking. Re-reserving over an existing UNCONFIRMED reservation is allowed — it refreshes or
     * moves the hold.
     *
     * <p>Returns the court schedule ids this bookingId already holds an (unconfirmed) reservation
     * against, reusing the same {@code findByHearingId} fetch the guard itself needed rather than
     * querying a second time — see {@link #toReservedSlot} for why that set matters for capacity.
     */
    private Set<String> guardAgainstConfirmedAllocation(final String bookingId) {
        final List<AllocatedListing> existingAllocations = allocatedListingRepository.findByBookingId(bookingId);
        final boolean hasConfirmedAllocation = existingAllocations.stream()
                .anyMatch(allocation -> allocation.getExpiresAt() == null);
        if (hasConfirmedAllocation) {
            throw new ConfirmedBookingExistsException(
                    "Booking " + bookingId + " already has a confirmed allocation — cannot reserve");
        }
        return existingAllocations.stream()
                .map(AllocatedListing::getCourtScheduleId)
                .collect(Collectors.toSet());
    }

    private AllocatedSlot toReservedSlot(final String bookingId, final ProvisionalSlot requested,
                                         final Set<String> ownHeldCourtScheduleIds) {
        final String sessionId = requested.getCourtScheduleId();

        // findCourtScheduleById projects via convertForOverbooking, which only populates
        // courtScheduleId/isOverbookingAllowed/active — not ouCode/sessionDate, which this method
        // needs. getCourtSchedulesByIdList uses the full-fields projection instead.
        final CourtSchedule session = courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)).stream()
                .filter(CourtSchedule::isActive)
                .findFirst()
                .orElseThrow(() -> new NoSessionAvailableException("No session found for sessionId " + sessionId));

        final Integer duration = requested.getDuration();
        // A duration-based session decrements available_duration by exactly this value. Coercing a
        // missing duration to 0 would write a row that holds no capacity at all, so the session
        // stays bookable by the next clerk while this booking believes it holds it. Reject instead
        // of guessing a default.
        if (!session.isSlotBased() && (duration == null || duration <= 0)) {
            throw new ValidationException(Json.createObjectBuilder()
                    .add(ERROR_MESSAGE, "duration is required and must be greater than zero for duration-based session "
                            + sessionId)
                    .build());
        }

        // Restores the capacity guarantee that used to live at share time in the UI's
        // validateSessionAvailability call, delegating to the same shared, already-tested rule
        // (SessionsService#validateSessionAvailabilityListMode) rather than a second copy of it —
        // a copy would drift, and isOverbookingAllowed is exactly the exemption a copy would lose.
        //
        // Skipped when this bookingId already holds a reservation on this exact session: a re-pick
        // under the same bookingId (NEW-15) has saveBookedSlots release every one of its own rows,
        // hearing-wide, before taking the new ones — so this session's own hold is about to be
        // given back regardless of what the shared validator would say about it right now. Calling
        // the validator anyway would count that soon-to-be-released row against the clerk's own
        // re-pick and falsely reject a legal change of mind. A session the booking does NOT already
        // hold gets the ordinary check, in full, including against this booking's OTHER sessions.
        if (!ownHeldCourtScheduleIds.contains(sessionId)) {
            final Optional<String> capacityProblem =
                    sessionsService.validateSessionAvailabilityListMode(List.of(sessionId), duration);
            if (capacityProblem.isPresent()) {
                throw new NoCapacityException(capacityProblem.get());
            }
        }

        final AllocatedSlot slot = new AllocatedSlot();
        slot.setCourtScheduleId(sessionId);
        // booking_id, not hearing_id: this row holds no hearing yet. hearing_id stays null until
        // the share confirms the booking and stamps the real next-hearing id onto it.
        slot.setBookingId(bookingId);
        slot.setOuCode(session.getOuCode());
        slot.setSessionDate(session.getSessionDate().toString());
        slot.setDuration(duration == null ? 0 : duration);
        slot.setHearingStartTime(requested.getHearingStartTime());
        slot.setSlotBased(session.isSlotBased());
        slot.setSource(SOURCE_RESERVED_UNCONFIRMED);
        // Expires at the end of the day the reservation is made, as a calendar date rather than an
        // instant — the purge job only cares whether "today" has moved past this date, not the
        // time of day. ZoneOffset.UTC keeps this deterministic regardless of the JVM's default zone.
        slot.setExpiresAt(LocalDate.now(ZoneOffset.UTC));
        return slot;
    }
}
