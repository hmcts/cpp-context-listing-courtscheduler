package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PROVISIONAL_SLOTS;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.modelmapper.ModelMapper;

@Service
@org.springframework.transaction.annotation.Transactional
public class ProvisionalBookingService {
    private static final String BOOKING_ID = "bookingId";
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    private final ListToJsonArrayConverter<ProvisionalBookingInfo> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

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
                .add(BOOKING_ID, bookingId)
                .build();
    }

    public JsonObject fetchProvisionalSlots(final String bookingIds) {
        final List<ProvisionalBookingInfo> provisionalBookingInfoArrayList = new ArrayList<>();
        final List<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary> courtScheduleJudiciariesArrayList = new ArrayList<>();
        final ModelMapper modelMapper = new ModelMapper();
        final List<String> bookingIdList = Stream.of(bookingIds.split(","))
                .map(String::trim)
                .toList();

        List<ProvisionalBooking> provisionalBookings = provisionalBookingRepository.findByBookingIdIn(bookingIdList);
        List<CourtSchedule> courtScheduleList =
                provisionalBookings.stream().map(provisionalBooking -> provisionalBooking.getProvisionalBookingKey().getCourtSchedule()).toList();
        final List<CourtSchedule> courtSchedulesWithListingProfile = courtScheduleList.stream()
                .filter(courtSchedule -> courtSchedule.getListingProfileId() != null)
                .toList();
        //judiciary details are not required for provisional bookings without listing profile(ghost rota)
        if (isNotEmpty(courtSchedulesWithListingProfile)) {
            List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(courtSchedulesWithListingProfile);
            courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> {
                final uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary mapped =
                        modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary.class);
                mapped.setEmailAddress(courtScheduleJudiciary.getEmail());
                courtScheduleJudiciariesArrayList.add(mapped);
            });
        }

        provisionalBookings.forEach(provisionalBooking -> provisionalBookingInfoArrayList.add(buildProvisionalInfo(provisionalBooking)));

        provisionalBookingInfoArrayList.forEach(provisionalBooking ->
                Optional.of(courtScheduleJudiciariesArrayList.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtListingProfileId().equals(provisionalBooking.getListingProfileId()))
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtScheduleId().equals(provisionalBooking.getCourtScheduleId()))
                        .toList()).ifPresent(provisionalBooking.getJudiciaries()::addAll));

        return Json.createObjectBuilder()
                .add(PROVISIONAL_SLOTS.getLabel(), listToJsonArrayConverter.convert(provisionalBookingInfoArrayList))
                .build();
    }

    private ProvisionalBookingInfo buildProvisionalInfo(final ProvisionalBooking provisionalBooking) {
        CourtSchedule courtSchedule = provisionalBooking.getProvisionalBookingKey().getCourtSchedule();
        return new ProvisionalBookingInfo()
                .courtScheduleId(courtSchedule.getCourtScheduleId())
                .listingProfileId(courtSchedule.getListingProfileId())
                .ouCode(courtSchedule.getOuCode())
                .courtHouseId(courtSchedule.getCourtHouseId())
                .courtHouseName(courtSchedule.getCourtHouseName())
                .courtRoomId(courtSchedule.getCourtRoomId())
                .courtRoomNumber(courtSchedule.getCourtRoomNumber())
                .courtRoomName(courtSchedule.getCourtRoomName())
                .businessType(courtSchedule.getBusinessType())
                .courtSession(courtSchedule.getCourtSession())
                .sessionDate(courtSchedule.getSessionDate())
                .panel(courtSchedule.getPanel())
                .operationalUnit(courtSchedule.getOperationalUnit())
                .availableSlots(courtSchedule.getAvailableSlots())
                .availableDuration(courtSchedule.getAvailableDuration())
                .maxSlots(courtSchedule.getMaxSlots())
                .maxDuration(courtSchedule.getMaxDuration())
                .bookingId(provisionalBooking.getProvisionalBookingKey().getBookingId())
                .hearingStartTime(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(provisionalBooking.getHearingStartTime()));
    }
}
