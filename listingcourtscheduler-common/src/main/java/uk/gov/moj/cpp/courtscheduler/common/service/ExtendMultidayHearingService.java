package uk.gov.moj.cpp.courtscheduler.common.service;

import static uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.FULL_DAY_DURATION_MINS;
import static uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.getNextBusinessDay;
import static uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.hasSufficientAvailability;
import static uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException.ErrorCode.INVALID_DATE_RANGE;
import static uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException.ErrorCode.NO_AVAILABILITY;
import static uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException.ErrorCode.NO_EXISTING_ALLOCATION;
import static uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException.ErrorCode.START_DATE_CHANGE_NOT_ALLOWED;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.exception.ExtendMultidayHearingException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles extend / shrink / no-op of a CROWN multi-day booking against allocated_listings.
 *
 * Concurrency note: there is no row-level pessimistic lock on allocated_listings; the @Transactional
 * boundary on {@link #extend} relies on optimistic re-check semantics. Two concurrent extend calls
 * for the same hearingId may both pass the availability check and attempt inserts; whichever loses
 * the constraint race (court_schedule_id + hearing_id) will surface a DB conflict the caller can
 * retry. Unit tests assert behaviour under a single-thread happy path; concurrent races are not
 * tested here.
 */
@Service
public class ExtendMultidayHearingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ExtendMultidayHearingService.class);
    private static final String EXTEND_SOURCE = "EXTEND_MULTIDAY";

    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Transactional
    public List<CourtSchedule> extend(final String hearingId, final LocalDate newStart,
                                      final LocalDate newEnd, final int durationInMinutes) {
        return extend(hearingId, newStart, newEnd, durationInMinutes, null, null, FULL_DAY_DURATION_MINS);
    }

    /**
     * SPRDT-1273: {@code requestedCourtRoomId} (a court_schedule.court_room_id UUID) pins the room
     * the EXTEND tail days are booked into — the hearing's main courtroom as submitted on
     * update-hearing-for-listing. When absent the tail continues in the block's LAST session's
     * room. Existing allocation rows are never touched, so per-day rooms set earlier via
     * change-court-room-for-multiday-hearing survive a resize.
     *
     * <p>SPRDT-1274: {@code earliestHearingTime} (ISO-8601 zoned timestamp) is the user-supplied
     * daily start time; when present each tail row's hearing_start_time carries that time-of-day on
     * its own date, so the listing viewstore reflects the user's chosen time rather than the
     * session's rota time. Existing rows keep whatever time they already hold.
     */
    @Transactional
    public List<CourtSchedule> extend(final String hearingId, final LocalDate newStart,
                                      final LocalDate newEnd, final int durationInMinutes,
                                      final String requestedCourtRoomId,
                                      final String earliestHearingTime,
                                      final int perDayMinutes) {

        if (newEnd.isBefore(newStart)) {
            throw new ExtendMultidayHearingException(INVALID_DATE_RANGE,
                    "newEnd " + newEnd + " is before newStart " + newStart);
        }

        final List<AllocatedListing> existing = allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(hearingId);
        if (existing.isEmpty()) {
            throw new ExtendMultidayHearingException(NO_EXISTING_ALLOCATION,
                    "No existing allocation for hearingId " + hearingId);
        }

        final List<CourtSchedule> currentSchedules = hydrateSchedules(existing);
        final LocalDate minDate = currentSchedules.get(0).getSessionDate();
        final LocalDate maxDate = currentSchedules.get(currentSchedules.size() - 1).getSessionDate();

        if (!newStart.equals(minDate)) {
            throw new ExtendMultidayHearingException(START_DATE_CHANGE_NOT_ALLOWED,
                    "newStart " + newStart + " does not match current MIN(date) " + minDate);
        }

        if (newEnd.equals(maxDate)) {
            LOGGER.info("[EXTEND-MULTIDAY] hearingId: {}, NO_CHANGE (newEnd == MAX)", hearingId);
            return currentSchedules;
        }

        if (newEnd.isBefore(maxDate)) {
            LOGGER.info("[EXTEND-MULTIDAY] hearingId: {}, SHRINK from {} to {}", hearingId, maxDate, newEnd);
            allocatedListingRepository.deleteByHearingIdAndSessionDateGreaterThan(hearingId, newEnd);
            return hydrateSchedules(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(hearingId));
        }

        LOGGER.info("[EXTEND-MULTIDAY] hearingId: {}, EXTEND from {} to {} (requested duration {} mins, requestedCourtRoomId {})",
                hearingId, maxDate, newEnd, durationInMinutes, requestedCourtRoomId);
        return doExtend(hearingId, currentSchedules, maxDate, newEnd, requestedCourtRoomId, earliestHearingTime,
                perDayMinutes > 0 ? perDayMinutes : FULL_DAY_DURATION_MINS);
    }

    private List<CourtSchedule> doExtend(final String hearingId,
                                         final List<CourtSchedule> currentSchedules,
                                         final LocalDate maxDate,
                                         final LocalDate newEnd,
                                         final String requestedCourtRoomId,
                                         final String earliestHearingTime,
                                         final int perDayMinutes) {
        final CourtSchedule lastSession = currentSchedules.get(currentSchedules.size() - 1);
        final String ouCode = lastSession.getOuCode();
        // Tail room: the caller-supplied main courtroom when present, else the block continues in
        // the last session's room. Both are court_schedule.court_room_id UUIDs — the previous
        // template, allocated_listings.court_room_id, is an Integer room NUMBER that can never
        // match the UUID column, so the tail search silently found nothing.
        final boolean roomPinnedByRequest = requestedCourtRoomId != null && !requestedCourtRoomId.isBlank();
        final String courtRoomId = roomPinnedByRequest ? requestedCourtRoomId : lastSession.getCourtRoomId();
        // Business type only constrains a same-room continuation; a caller-pinned room may run its
        // sessions under a different business type, so the filter is dropped there.
        final String businessType = roomPinnedByRequest ? null : lastSession.getBusinessType();

        final List<LocalDate> tailDays = computeBusinessDays(maxDate, newEnd);

        // The tail matches the block's own draft state: extending an ALLOCATED block must only book
        // FINAL sessions — a draft tail session has no confirmed room and, downstream, ADR-005 would
        // strip the courtroom from EVERY hearing day of the hearing.
        final Boolean blockIsDraft = lastSession.isDraft();
        final List<CourtSchedule> candidates = courtScheduleRepository.findAdSessionsInRange(
                ouCode, courtRoomId, businessType, tailDays.get(0), newEnd, blockIsDraft);

        final Map<LocalDate, CourtSchedule> bookableByDate = pickBookablePerDay(candidates, perDayMinutes);

        final List<LocalDate> unavailable = new ArrayList<>();
        for (final LocalDate day : tailDays) {
            if (!bookableByDate.containsKey(day)) {
                unavailable.add(day);
            }
        }
        if (!unavailable.isEmpty()) {
            LOGGER.info("[EXTEND-MULTIDAY] hearingId: {}, NO_AVAILABILITY on {}", hearingId, unavailable);
            throw new ExtendMultidayHearingException(NO_AVAILABILITY,
                    "Tail days unavailable: " + unavailable, unavailable);
        }

        final java.time.LocalTime userStartTime = parseUserStartTime(earliestHearingTime);
        for (final LocalDate day : tailDays) {
            insertAllocation(hearingId, bookableByDate.get(day), userStartTime, perDayMinutes);
        }

        return hydrateSchedules(allocatedListingRepository.findByHearingIdOrderBySessionDateAsc(hearingId));
    }

    private static List<LocalDate> computeBusinessDays(final LocalDate exclusiveStart, final LocalDate inclusiveEnd) {
        final List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = getNextBusinessDay(exclusiveStart);
        while (!cursor.isAfter(inclusiveEnd)) {
            days.add(cursor);
            cursor = getNextBusinessDay(cursor);
        }
        return days;
    }

    private static Map<LocalDate, CourtSchedule> pickBookablePerDay(final List<CourtSchedule> candidates,
                                                                    final int perDayMinutes) {
        final Map<LocalDate, CourtSchedule> chosen = new HashMap<>();
        for (final CourtSchedule cs : candidates) {
            // The tail day only needs ITS share of the requested total — sessions in many rotas
            // (and the IT suites) are far shorter than the 360-minute default court day.
            if (!hasSufficientAvailability(cs, perDayMinutes)) {
                continue;
            }
            chosen.merge(cs.getSessionDate(), cs, (existing, incoming) ->
                    existing.isOverbookingAllowed() ? incoming : existing);
        }
        return chosen;
    }

    /**
     * The new row is built from the BOOKED SESSION (not from an existing allocation row): when the
     * tail is pinned to the caller's main courtroom the session's room/businessType differ from the
     * block's earlier rows, and copying them from a template row would record the wrong room.
     * hearingStartTime deliberately carries the session's start time — the accepted SPRDT-1274
     * discrepancy: allocated_listings holds session time, the listing viewstore holds the
     * user-supplied time.
     */
    private static java.time.LocalTime parseUserStartTime(final String earliestHearingTime) {
        if (earliestHearingTime == null || earliestHearingTime.isBlank()) {
            return null;
        }
        try {
            return java.time.ZonedDateTime.parse(earliestHearingTime).toLocalTime();
        } catch (final java.time.format.DateTimeParseException e) {
            LOGGER.warn("[EXTEND-MULTIDAY] Unparseable earliestHearingTime '{}' — tail rows keep the session start time", earliestHearingTime);
            return null;
        }
    }

    private void insertAllocation(final String hearingId, final CourtSchedule session,
                                  final java.time.LocalTime userStartTime, final int perDayMinutes) {
        final AllocatedListing row = new AllocatedListing();
        row.setId(UUID.randomUUID().toString());
        row.setHearingId(hearingId);
        row.setCourtScheduleId(session.getCourtScheduleId());
        row.setOucode(session.getOuCode());
        row.setCourtRoomId(session.getCourtRoomNumber());
        row.setRotaBusinessType(session.getBusinessType());
        row.setDuration(perDayMinutes);
        if (userStartTime != null) {
            row.setHearingStartTime(java.util.Date.from(
                    session.getSessionDate().atTime(userStartTime).atZone(java.time.ZoneOffset.UTC).toInstant()));
        } else {
            row.setHearingStartTime(session.getSessionStartTime() != null
                    ? session.getSessionStartTime()
                    : java.util.Date.from(session.getSessionDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        }
        row.setSource(EXTEND_SOURCE);
        allocatedListingRepository.save(row);
    }

    private List<CourtSchedule> hydrateSchedules(final List<AllocatedListing> allocations) {
        if (allocations.isEmpty()) {
            return new ArrayList<>();
        }
        final List<String> ids = new ArrayList<>(allocations.size());
        for (final AllocatedListing al : allocations) {
            ids.add(al.getCourtScheduleId());
        }
        final List<CourtSchedule> hydrated = new ArrayList<>(courtScheduleRepository.getCourtSchedulesByIdList(ids));
        hydrated.sort(Comparator.comparing(CourtSchedule::getSessionDate));
        return hydrated;
    }
}
