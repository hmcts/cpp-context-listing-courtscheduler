package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PROVISIONAL_SLOTS;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
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
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciariesArrayList = new ArrayList<>();
        ModelMapper modelMapper = new ModelMapper();
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
            courtScheduleJudiciaries.forEach(courtScheduleJudiciary ->
                    courtScheduleJudiciariesArrayList.add(modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class)));
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
        final ProvisionalBookingInfo.ProvisionalBookingInfoBuilder provisionalBookingInfoBuilder = new ProvisionalBookingInfo.ProvisionalBookingInfoBuilder();
        CourtSchedule courtSchedule = provisionalBooking.getProvisionalBookingKey().getCourtSchedule();
        provisionalBookingInfoBuilder.withCourtScheduleId(courtSchedule.getCourtScheduleId())
                .withListingProfileId(courtSchedule.getListingProfileId())
                .withOuCode(courtSchedule.getOuCode())
                .withCourtHouseId(courtSchedule.getCourtHouseId())
                .withCourtHouseName(courtSchedule.getCourtHouseName())
                .withCourtRoomId(courtSchedule.getCourtRoomId())
                .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                .withCourtRoomName(courtSchedule.getCourtRoomName())
                .withBusinessType(courtSchedule.getBusinessType())
                .withCourtSession(courtSchedule.getCourtSession())
                .withSessionDate(courtSchedule.getSessionDate())
                .withPanel(courtSchedule.getPanel())
                .withOperationalUnit(courtSchedule.getOperationalUnit())
                .withAvailableSlots(courtSchedule.getAvailableSlots())
                .withAvailableDuration(courtSchedule.getAvailableDuration())
                .withMaxSlots(courtSchedule.getMaxSlots())
                .withMaxDuration(courtSchedule.getMaxDuration())
                .withBookingId(provisionalBooking.getProvisionalBookingKey().getBookingId())
                .withHearingStartTime(provisionalBooking.getHearingStartTime());
        return provisionalBookingInfoBuilder.build();
    }
}
