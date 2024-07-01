package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

@ApplicationScoped
public class CourtScheduleService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam) {
        return courtScheduleRepository.findBy(courtScheduleRequestParam);
    }

    public Result update(UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.findBy(updateCourtSchedule.getCourtScheduleId());
        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, requester, persistedBusinessType)) {
            return new Result("Business Type cannot be changed from Slot to Non-Slot and vice versa", false);
        }
        if (maxSlotsOrDurationChanged(updateCourtSchedule, persistedCourtSchedule)) {
            updateAvailability(updateCourtSchedule, persistedCourtSchedule);
        }
        final Result result= courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule);
        return result;
    }

    private void updateAvailability(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        final Long totalListedDuration = allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
        //Assuming that businessType won't be changing from slot to non-slot or vice versa
        if (persistedCourtSchedule.isSlotBased()) {
            updateCourtSchedule.setAvailableSlots(updateCourtSchedule.getMaxSlots() - totalListedDuration.intValue());
        } else {
            updateCourtSchedule.setAvailableDuration(updateCourtSchedule.getMaxDuration() - totalListedDuration.intValue());
        }
    }


    private static boolean maxSlotsOrDurationChanged(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        return (!updateCourtSchedule.getMaxDuration().equals(persistedCourtSchedule.getMaxDuration())) || (updateCourtSchedule.getMaxSlots().equals(persistedCourtSchedule.getMaxSlots()));
    }

    private static boolean isBusinessTypeChangeInvalid(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessType) {
        return !persistedBusinessType.equals(updateCourtSchedule.getBusinessType()) && !isBusinessTypeChangeAllowed(updateCourtSchedule.getBusinessType(), requester, persistedBusinessType);
    }

    private static boolean isBusinessTypeChangeAllowed(final String updatedBusinessTypeCode, final Requester requester, final String persistedBusinessTypeCode) {
        final ReferenceDataCache referenceDataCache = new ReferenceDataCache();
        final BusinessType persistedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(persistedBusinessTypeCode, requester).orElseThrow(() -> new RuntimeException("Business Type not found" + persistedBusinessTypeCode));
        final BusinessType updatedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(updatedBusinessTypeCode, requester).orElseThrow(() -> new RuntimeException("Business Type not found" + updatedBusinessTypeCode));
        return (persistedBusinessType.isSlot() && !updatedBusinessType.isSlot()) || (!persistedBusinessType.isSlot() && updatedBusinessType.isSlot());
    }

    public JsonObject deleteCourtScheduleSessions(final SessionsParam sessionsParam) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(sessionsParam.getSessions());

        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        JsonArray jsonArray = courtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtSchedules);
        return Json.createObjectBuilder()
                .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                .build();
    }
}
