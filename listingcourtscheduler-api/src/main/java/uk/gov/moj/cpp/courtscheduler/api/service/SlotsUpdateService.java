package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;
import static javax.json.Json.createArrayBuilder;
import static javax.json.Json.createObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotToHearingSlotSearchResponseConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestToAllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

@ApplicationScoped
public class SlotsUpdateService {

    public static final String HEARING_DATE = "hearingDate";
    public static final String COURT_SCHEDULE_ID = "courtScheduleId";
    public static final String SCHEDULES = "schedules";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;

    public JsonObject update(final List<AllocatedSlot> slots) {
        final boolean containsMissingCourtScheduleId = false; //slots.stream().anyMatch( slot -> StringUtils.isBlank(slot.getCourtScheduleId()));
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

            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, true, containsMissingCourtScheduleId);
        } else {
            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, false, containsMissingCourtScheduleId);
        }
        final JsonArrayBuilder hearingDaysJsonArrBuilder = createArrayBuilder();
        slotUpdateResult.getHearingDayCourtSchedules().forEach( (day, schedule) -> {
            JsonObject hearingDaySchedule = createObjectBuilder().add(HEARING_DATE, day).add(COURT_SCHEDULE_ID, schedule).build();
            hearingDaysJsonArrBuilder.add(hearingDaySchedule);
        });
        return createObjectBuilder().add(SCHEDULES, hearingDaysJsonArrBuilder).build();
    }

    public ListHearingSlotsResponse updateListHearingSlots(final RequestedSlots hearingSlots) {

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

    public HearingSlotSearchResponse searchAndBook(final HearingSlotSearchRequest hearingSlotSearchRequest) {
        HearingSlotSearchResponse hearingSlotSearchResponse = null;
        AllocatedSlot allocatedSlot = HearingSlotSearchRequestToAllocatedSlotConverter.convert(hearingSlotSearchRequest);
        List<AllocatedSlot> allocatedSlots = List.of(allocatedSlot);
        courtScheduleRepository.searchBookHearingSlots(allocatedSlots);
        if(CollectionUtils.isNotEmpty(allocatedSlots))
            hearingSlotSearchResponse = AllocatedSlotToHearingSlotSearchResponseConverter.convert(allocatedSlots.get(0), hearingSlotSearchRequest.hearingId());
        return hearingSlotSearchResponse;
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
