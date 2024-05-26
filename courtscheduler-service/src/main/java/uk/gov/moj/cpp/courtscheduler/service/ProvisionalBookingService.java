package uk.gov.moj.cpp.courtscheduler.service;

import static java.util.UUID.randomUUID;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

@ApplicationScoped
public class ProvisionalBookingService {
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public JsonObject bookProvisionalSlots(final ProvisionalBookingSlots provisionalBookingSlots) {
        final String bookingId = randomUUID().toString();
        provisionalBookingSlots.getProvisionalSlots().forEach(provisionalSlot -> {
            try {
                CourtSchedule courtSchedule = courtScheduleRepository.findBy(provisionalSlot.getCourtScheduleId());
                provisionalBookingRepository.saveProvisionalBooking(provisionalSlot, bookingId, courtSchedule);
            } catch (PersistenceStoreException exception) {
                throw new SlotsBookException(exception);
            }
        });

        return Json.createObjectBuilder()
                .add(RequestParameterConstant.BOOKING_IDS.getLabel(),bookingId)
                .build();
    }
}
