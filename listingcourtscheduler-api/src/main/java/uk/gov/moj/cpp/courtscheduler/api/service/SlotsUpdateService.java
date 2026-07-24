package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotToHearingSlotSearchResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestToAllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.exception.MoveHearingToPastDateNoSessionException;
import uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.apache.commons.collections.CollectionUtils;

@Service
@org.springframework.transaction.annotation.Transactional
public class SlotsUpdateService {

    public static final String HEARING_DATE = "hearingDate";
    public static final String COURT_SCHEDULE_ID = "courtScheduleId";
    public static final String SCHEDULES = "schedules";
    public static final String MOVE_TO_PAST_DATE_SOURCE = "MOVE_TO_PAST_DATE";
    private static final String MAGISTRATES_JURISDICTION = "MAGISTRATES";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;

    public JsonObject update(final List<AllocatedSlot> slots) {
        final Result slotUpdateResult;
        if (isBookingBasedSlot(slots)) {
            final AllocatedSlot singleBookingSlot = slots.get(0);
            final List<String> bookingSlots = List.of(singleBookingSlot.getBookingId());
            final Map<String, Date> provisionalBookingCourtScheduleInfo = provisionalBookingRepository.getCourtScheduleInfo(bookingSlots);

            final List<String> provisionalBookingCourtScheduleIdList = new ArrayList<>(provisionalBookingCourtScheduleInfo.keySet());

            if (CollectionUtils.isEmpty(provisionalBookingCourtScheduleIdList)) {
                throw new ProvisionalSlotNotFoundException(format("There is no provisional slot with this bookingId : %s", bookingSlots));
            }

            final List<String> slotsCourtScheduleIdList = slots.stream()
                    .map(AllocatedSlot::getCourtScheduleId)
                    .sorted()
                    .toList();

            if (!isCourtScheduleIdsMatching(slotsCourtScheduleIdList, provisionalBookingCourtScheduleIdList)) {
                throw new CourtScheduleIdNotMatchingException(format("courtScheduleIds not matching. slotsCourtScheduleIdList : %s, provisionalBookingCourtScheduleIdList : %s",
                        slotsCourtScheduleIdList, provisionalBookingCourtScheduleIdList));
            }

            slots.forEach(allocatedSlot -> {
                Date date = provisionalBookingCourtScheduleInfo.get(allocatedSlot.getCourtScheduleId());
                String isoString = DateUtils.toIsoString(new Timestamp(date.getTime()));
                allocatedSlot.setHearingStartTime(isoString);
            });

            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, true, false);
        } else {
            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, false, false);
        }

        final JsonArrayBuilder hearingDaysJsonArrBuilder = createArrayBuilder();
        slotUpdateResult.getHearingDayCourtSchedules().forEach( (day, schedule) -> {
            JsonObject hearingDaySchedule = createObjectBuilder().add(HEARING_DATE, day).add(COURT_SCHEDULE_ID, schedule).build();
            hearingDaysJsonArrBuilder.add(hearingDaySchedule);
        });

        return createObjectBuilder().add(SCHEDULES, hearingDaysJsonArrBuilder).build();
    }

    public ListHearingSlotsResponse listHearingSlots(final RequestedSlots hearingSlots) {

        ListHearingSlotsResponse response = new ListHearingSlotsResponse();

        List<Hearing> listHearingSlots = courtScheduleRepository.updateListHearingSlots(hearingSlots);

        response.setHearings(listHearingSlots);

        return response;
    }

    public Result searchUpdate(final List<AllocatedSlot> slots) {
        Result result;
        if("Police".equalsIgnoreCase(slots.get(0).getProsecutor())) {
            result = courtScheduleRepository.saveBookedSlots(slots, false, true);
        } else {
            result = courtScheduleRepository.saveBookedSlots(slots, false, false);
        }
        if(result.isSuccess()) {
            result.setCourtRoomId(slots.get(0).getCourtRoomUUId());
            result.setCourtRoomName(slots.get(0).getCourtRoom());
        }
        return result;
    }

    public HearingSlotSearchAndBookResponse searchAndBook(final HearingSlotSearchRequest hearingSlotSearchRequest) {
        HearingSlotSearchAndBookResponse hearingSlotSearchAndBookResponse = new HearingSlotSearchAndBookResponse();
        AllocatedSlot allocatedSlot = HearingSlotSearchRequestToAllocatedSlotConverter.convert(hearingSlotSearchRequest);
        List<AllocatedSlot> allocatedSlots = new ArrayList<>(List.of(allocatedSlot));
        boolean isSearchSuccessful = courtScheduleRepository.searchBookHearingSlots(allocatedSlots);
        if(isSearchSuccessful && CollectionUtils.isNotEmpty(allocatedSlots))
            hearingSlotSearchAndBookResponse = AllocatedSlotToHearingSlotSearchResponseConverter.convert(allocatedSlots.get(0), hearingSlotSearchRequest.hearingId());
        return hearingSlotSearchAndBookResponse;
    }

    /**
     * Expands [startDate, endDate] into sitting (Mon-Fri) days and books a session on every day in a
     * single atomic call. All sessions are resolved first, so if any day has no matching session the
     * whole move is rejected before any allocation is written (saveBookedSlots releases prior
     * allocations for the hearing exactly once and then books every slot). Supports both jurisdictions
     * and an optional room-scoped, time-of-day (range-containment) search.
     */
    public List<MoveHearingToPastDateResponse> moveHearingToPastDate(final String hearingId,
                                                                     final String courtCentreId,
                                                                     final String courtRoomId,
                                                                     final LocalDate startDate,
                                                                     final LocalDate endDate,
                                                                     final String hearingStartTime,
                                                                     final String hearingEndTime,
                                                                     final String jurisdiction,
                                                                     final int durationInMinutes) {
        final String effectiveJurisdiction = (jurisdiction == null || jurisdiction.isBlank())
                ? MAGISTRATES_JURISDICTION : jurisdiction.toUpperCase();
        final LocalDate effectiveEndDate = endDate == null ? startDate : endDate;
        final List<LocalDate> sittingDays = workingDays(startDate, effectiveEndDate);
        if (sittingDays.isEmpty()) {
            throw new MoveHearingToPastDateNoSessionException(
                    "No sitting (weekday) day between " + startDate + " and " + effectiveEndDate);
        }

        final List<CourtSchedule> sessions = new ArrayList<>();
        for (final LocalDate day : sittingDays) {
            final LocalDateTime sessionStartDateTime = (hearingStartTime == null || hearingStartTime.isBlank())
                    ? null : LocalDateTime.of(day, LocalTime.parse(hearingStartTime));
            final CourtSchedule session = courtScheduleRepository
                    .findSessionForMoveToPastDate(courtCentreId, courtRoomId, day, sessionStartDateTime, effectiveJurisdiction)
                    .orElseThrow(() -> new MoveHearingToPastDateNoSessionException(
                            "No session available at courtCentreId=" + courtCentreId + " on " + day));
            sessions.add(session);
        }

        final List<AllocatedSlot> slots = new ArrayList<>();
        for (int i = 0; i < sittingDays.size(); i++) {
            slots.add(buildAllocatedSlotForPastDateMove(hearingId, durationInMinutes,
                    sittingDays.get(i), hearingStartTime, sessions.get(i)));
        }
        final Result persistResult = courtScheduleRepository.saveBookedSlots(slots, false, false);
        if (!persistResult.isSuccess()) {
            throw new MoveHearingToPastDateNoSessionException(
                    "Move hearing to past date failed to persist allocation for hearingId " + hearingId + ": " + persistResult.getMsg());
        }

        final List<MoveHearingToPastDateResponse> responses = new ArrayList<>();
        for (int i = 0; i < sittingDays.size(); i++) {
            responses.add(toMoveHearingToPastDateResponse(hearingId, durationInMinutes,
                    sittingDays.get(i), hearingStartTime, hearingEndTime, sessions.get(i)));
        }
        return responses;
    }

    private static List<LocalDate> workingDays(final LocalDate startDate, final LocalDate endDate) {
        final List<LocalDate> days = new ArrayList<>();
        LocalDate cursor = startDate;
        while (!cursor.isAfter(endDate)) {
            final DayOfWeek dow = cursor.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                days.add(cursor);
            }
            cursor = cursor.plusDays(1);
        }
        return days;
    }

    private static AllocatedSlot buildAllocatedSlotForPastDateMove(final String hearingId, final int durationInMinutes,
                                                                   final LocalDate day, final String hearingStartTime,
                                                                   final CourtSchedule session) {
        final AllocatedSlot slot = new AllocatedSlot();
        slot.setHearingId(hearingId);
        slot.setCourtScheduleId(session.getCourtScheduleId());
        slot.setOuCode(session.getOuCode());
        slot.setDuration(durationInMinutes);
        slot.setSessionDate(day.toString());
        slot.setSource(MOVE_TO_PAST_DATE_SOURCE);
        // Booked slot reflects the SUBMITTED start time-of-day on this day, not the session window.
        final String submittedStart = submittedIso(day, hearingStartTime);
        if (submittedStart != null) {
            slot.setHearingStartTime(submittedStart);
        } else if (session.getSessionStartTime() != null) {
            slot.setHearingStartTime(DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())));
        }
        return slot;
    }

    private static MoveHearingToPastDateResponse toMoveHearingToPastDateResponse(final String hearingId, final int durationInMinutes,
                                                                                 final LocalDate day, final String hearingStartTime,
                                                                                 final String hearingEndTime, final CourtSchedule session) {
        final boolean overbooked = session.isSlotBased()
                ? (session.getAvailableSlots() != null && session.getAvailableSlots() <= 0)
                : (session.getAvailableDuration() != null && session.getAvailableDuration() < durationInMinutes);

        // sessionStartTime/sessionEndTime carry the SUBMITTED start/end time-of-day on this sitting
        // day (falling back to the court-schedule window only if a time-of-day was not supplied).
        final String submittedStart = submittedIso(day, hearingStartTime);
        final String submittedEnd = submittedIso(day, hearingEndTime);
        return new MoveHearingToPastDateResponse(
                hearingId,
                session.getCourtScheduleId(),
                session.getCourtRoomId(),
                day.toString(),
                submittedStart != null ? submittedStart
                        : (session.getSessionStartTime() != null ? DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())) : null),
                submittedEnd != null ? submittedEnd
                        : (session.getSessionEndTime() != null ? DateUtils.toIsoString(new Timestamp(session.getSessionEndTime().getTime())) : null),
                durationInMinutes,
                session.getIsDraft(),
                session.getBusinessType(),
                MOVE_TO_PAST_DATE_SOURCE,
                overbooked);
    }

    private static String submittedIso(final LocalDate day, final String timeOfDay) {
        if (timeOfDay == null || timeOfDay.isBlank()) {
            return null;
        }
        return DateUtils.toIsoString(LocalDateTime.of(day, LocalTime.parse(timeOfDay)));
    }

    private boolean isCourtScheduleIdsMatching(final List<String> slotsCourtScheduleIdList, final List<String> provisionalBookingCourtScheduleIdList) {
        Collections.sort(provisionalBookingCourtScheduleIdList);

        return slotsCourtScheduleIdList.equals(provisionalBookingCourtScheduleIdList);
    }

    private boolean isBookingBasedSlot(List<AllocatedSlot> slots) {
        return slots.stream()
                .anyMatch(slot -> Objects.nonNull(slot.getBookingId()));
    }
}
