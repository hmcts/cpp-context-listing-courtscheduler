package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Objects.nonNull;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.*;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

@ApplicationScoped
public class CourtScheduleService {

    private static final String BUSINESS_TYPE_NOT_FOUND = "Business Type not found";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;

    @Inject
    private CourtMigrationRepository courtMigrationRepository;

    @Inject
    private ReferenceDataCache referenceDataCache;


    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam, Requester requester) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtScheduleRequestParam);
        courtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        return courtSchedules;
    }

    public Result update(UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.findBy(updateCourtSchedule.getCourtScheduleId());
        if (Objects.isNull(persistedCourtSchedule)) {
            return new Result("Court Schedule not found", false);
        }
        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, requester, persistedBusinessType)) {
            return new Result("Business Type cannot be changed from Slot to Non-Slot and vice versa", false);
        }
        if (maxSlotsOrDurationChanged(updateCourtSchedule, persistedCourtSchedule)) {
            updateAvailability(updateCourtSchedule, persistedCourtSchedule);
        }

        String courtRoomId = updateCourtSchedule.getCourtRoomId();

        final Optional<CourtRoom> courtRoom;
        if (courtRoomId != null && !courtRoomId.equalsIgnoreCase(persistedCourtSchedule.getCourtRoomId())) {
            courtRoom = Optional.of(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId,requester).orElseThrow(() -> new RuntimeException("Court Room not found" + courtRoomId)));
        } else {
            courtRoom = Optional.empty();
        }


        return courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule, courtRoom);
    }

    private void updateAvailability(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        final Integer totalListedDuration = allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
        //Assuming that businessType won't be changing from slot to non-slot or vice versa
        if (persistedCourtSchedule.isSlotBased()) {
            updateCourtSchedule.setAvailableSlots(updateCourtSchedule.getMaxSlots() - (nonNull(totalListedDuration) ? totalListedDuration : 0));
            updateCourtSchedule.setMaxDuration(0);
            updateCourtSchedule.setAvailableDuration(0);
        } else {
            updateCourtSchedule.setAvailableDuration(updateCourtSchedule.getMaxDuration() - (nonNull(totalListedDuration) ? totalListedDuration: 0));
            updateCourtSchedule.setMaxSlots(0);
            updateCourtSchedule.setAvailableSlots(0);

        }
    }


    private static boolean maxSlotsOrDurationChanged(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        return (!updateCourtSchedule.getMaxDuration().equals(persistedCourtSchedule.getMaxDuration())) || (!updateCourtSchedule.getMaxSlots().equals(persistedCourtSchedule.getMaxSlots()));
    }

    private  boolean isBusinessTypeChangeInvalid(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessType) {
        return !persistedBusinessType.equals(updateCourtSchedule.getBusinessType()) && !isBusinessTypeChangeAllowed(updateCourtSchedule, requester, persistedBusinessType);
    }

    private  boolean isBusinessTypeChangeAllowed(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessTypeCode) {
        final BusinessType persistedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(persistedBusinessTypeCode, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + persistedBusinessTypeCode));
        final BusinessType updatedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(updateCourtSchedule.getBusinessType(), requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + updateCourtSchedule.getBusinessType()));
        return persistedBusinessType.isSlot()==updatedBusinessType.isSlot() && isUpdateRequestParamsAreValidForUpdate(updateCourtSchedule, updatedBusinessType.isSlot());
    }

    private static boolean isUpdateRequestParamsAreValidForUpdate(final UpdateCourtSchedule updateCourtSchedule, final boolean isSlotBased) {
        return (isSlotBased && updateCourtSchedule.getMaxDuration().equals(0)) || (!isSlotBased && updateCourtSchedule.getMaxSlots().equals(0));
    }

    public JsonObject deleteCourtScheduleSessions(final SessionsParam sessionsParam) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(sessionsParam.getSessions());

        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        JsonArray jsonArray = courtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtSchedules);
        return Json.createObjectBuilder()
                .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                .build();
    }

    public boolean isMigrated(final String ouCode) {
        return courtMigrationRepository.findByOuCode(ouCode).isMigrated();
    }

    public boolean isMigratedByCourtCentreId(final String courtCentreId) {
        return courtMigrationRepository.findByCourtCentreId(courtCentreId).isMigrated();
    }

    private String enrichBusinessDescription(final String businessType, final Requester requester) {
        return referenceDataCache.getRotaBusinessTypeByCode(businessType, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + businessType)).getTypeDescription();
    }
}
