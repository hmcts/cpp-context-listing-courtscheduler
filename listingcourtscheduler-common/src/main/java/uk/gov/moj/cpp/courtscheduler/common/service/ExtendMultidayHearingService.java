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

        LOGGER.info("[EXTEND-MULTIDAY] hearingId: {}, EXTEND from {} to {} (requested duration {} mins)",
                hearingId, maxDate, newEnd, durationInMinutes);
        return doExtend(hearingId, existing, maxDate, newEnd);
    }

    private List<CourtSchedule> doExtend(final String hearingId,
                                         final List<AllocatedListing> existing,
                                         final LocalDate maxDate,
                                         final LocalDate newEnd) {
        final AllocatedListing latest = existing.get(existing.size() - 1);
        final String ouCode = latest.getOucode();
        final String courtRoomId = String.valueOf(latest.getCourtRoomId());
        final String businessType = latest.getRotaBusinessType();

        final List<LocalDate> tailDays = computeBusinessDays(maxDate, newEnd);

        final List<CourtSchedule> candidates = courtScheduleRepository.findAdSessionsInRange(
                ouCode, courtRoomId, businessType, tailDays.get(0), newEnd);

        final Map<LocalDate, CourtSchedule> bookableByDate = pickBookablePerDay(candidates);

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

        for (final LocalDate day : tailDays) {
            insertAllocation(hearingId, latest, bookableByDate.get(day));
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

    private static Map<LocalDate, CourtSchedule> pickBookablePerDay(final List<CourtSchedule> candidates) {
        final Map<LocalDate, CourtSchedule> chosen = new HashMap<>();
        for (final CourtSchedule cs : candidates) {
            if (!hasSufficientAvailability(cs, FULL_DAY_DURATION_MINS)) {
                continue;
            }
            chosen.merge(cs.getSessionDate(), cs, (existing, incoming) ->
                    existing.isOverbookingAllowed() ? incoming : existing);
        }
        return chosen;
    }

    private void insertAllocation(final String hearingId, final AllocatedListing template,
                                  final CourtSchedule session) {
        final AllocatedListing row = new AllocatedListing();
        row.setId(UUID.randomUUID().toString());
        row.setHearingId(hearingId);
        row.setCourtScheduleId(session.getCourtScheduleId());
        row.setOucode(template.getOucode());
        row.setCourtRoomId(template.getCourtRoomId());
        row.setRotaBusinessType(template.getRotaBusinessType());
        row.setBookingId(template.getBookingId());
        row.setDuration(FULL_DAY_DURATION_MINS);
        row.setHearingStartTime(session.getSessionStartTime() != null
                ? session.getSessionStartTime()
                : java.util.Date.from(session.getSessionDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
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
