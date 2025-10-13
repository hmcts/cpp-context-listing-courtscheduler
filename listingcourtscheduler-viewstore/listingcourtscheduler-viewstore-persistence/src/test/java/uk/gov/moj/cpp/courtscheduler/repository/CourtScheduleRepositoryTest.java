package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlot;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class CourtScheduleRepositoryTest {

    private static final SimpleDateFormat SIMPLE_DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;
    @Inject
    ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    AllocatedListingRepository allocatedListingRepository;

    private static final String COURT_SCHEDULE_ID = randomUUID().toString();
    private static final int SLOT_DEFAULT = 1;

    @Before
    public void setUp() {
        List<AllocatedListing> allocatedListingRepositoryAll = allocatedListingRepository.findAll();
        allocatedListingRepositoryAll.forEach(allocatedListing -> allocatedListingRepository.remove(allocatedListing));

        List<CourtScheduleJudiciary> courtScheduleJudiciaryRepositoryAll = courtScheduleJudiciaryRepository.findAll();
        courtScheduleJudiciaryRepositoryAll.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));

        List<ProvisionalBooking> provisionalBookingRepositoryAll = provisionalBookingRepository.findAll();
        provisionalBookingRepositoryAll.forEach(provisionalBooking -> provisionalBookingRepository.remove(provisionalBooking));

        List<CourtSchedule> courtScheduleRepositoryAll = courtScheduleRepository.findAll();
        courtScheduleRepositoryAll.forEach(courtSchedule -> courtScheduleRepository.removeAndFlush(courtSchedule));
    }

    @Test
    public void shouldUpdateCourtSchedule() {
        // given
        String panel = random(String.class);
        String businessType = random(String.class);
        CourtRoom courtRoom = random(CourtRoom.class);

        CourtSchedule courtScheduleEntity = random(CourtSchedule.class);
        courtScheduleRepository.save(courtScheduleEntity);

        uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule updatedCourtSchedule = new uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule.UpdateCourtScheduleBuilder()
                .withCourtScheduleId(courtScheduleEntity.getCourtScheduleId())
                .withBusinessType(businessType)
                .withSessionType(courtScheduleEntity.getCourtSession())
                .withCourtRoomId(courtScheduleEntity.getCourtRoomId())
                .withSessionType(ALL_DAY)
                .withPanel(panel)
                .withIsOverbookingAllowed(true)
                .build();

        Result result = courtScheduleRepository.update(courtScheduleEntity, updatedCourtSchedule, Optional.of(courtRoom));
        assertTrue(result.isSuccess());
        assertThat(result.getHearingDayCourtSchedules().size(), is(0));
    }

    @Test
    public void shouldFindCourtSchedulesUpdatedBetweenDates() {
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);

        courtScheduleRepository.save(courtSchedule);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtScheduleList = courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertFalse(courtScheduleList.isEmpty());
    }

    @Test
    public void shouldUpdateSlotBasedSchedulesByOuCode_onlyTargetSchedulesUpdated() {
        // Given
        String ouCode = "OU1234";

        CourtSchedule slotBasedSchedule = random(CourtSchedule.class);
        slotBasedSchedule.setMaxSlots(2);
        slotBasedSchedule.setAvailableSlots(2);
        slotBasedSchedule.setMaxDuration(0);
        slotBasedSchedule.setAvailableDuration(0);

        slotBasedSchedule.setSlotBased(true);
        slotBasedSchedule.setSupportAdSplit(false);
        slotBasedSchedule.setActive(true);
        slotBasedSchedule.setOuCode(ouCode);
        slotBasedSchedule.setSessionDate(LocalDate.now().plusDays(1));
        courtScheduleRepository.save(slotBasedSchedule);

        AllocatedListing listing1 = random(AllocatedListing.class);
        listing1.setCourtScheduleId(slotBasedSchedule.getCourtScheduleId());
        listing1.setCourtRoomId(slotBasedSchedule.getCourtRoomNumber());
        listing1.setHearingId(randomUUID().toString());
        listing1.setBookingId(randomUUID().toString());
        listing1.setOucode(slotBasedSchedule.getOuCode());
        listing1.setDuration(1);
        listing1.setHearingStartTime(new Date());
        listing1.setCreatedOn(new Date());
        listing1.setUpdatedOn(new Date());
        allocatedListingRepository.save(listing1);

        AllocatedListing listing2 = random(AllocatedListing.class);
        listing2.setCourtScheduleId(slotBasedSchedule.getCourtScheduleId());
        listing2.setCourtRoomId(slotBasedSchedule.getCourtRoomNumber());
        listing2.setHearingId(randomUUID().toString());
        listing2.setBookingId(randomUUID().toString());
        listing2.setOucode(slotBasedSchedule.getOuCode());
        listing2.setDuration(1);
        listing2.setHearingStartTime(new Date());
        listing2.setCreatedOn(new Date());
        listing2.setUpdatedOn(new Date());
        allocatedListingRepository.save(listing2);

        // Same ouCode but future, slotBased=false and supportAdSplit=true (skip updating)
        CourtSchedule adSplitSchedule = random(CourtSchedule.class);
        adSplitSchedule.setSlotBased(false);
        adSplitSchedule.setSupportAdSplit(true);
        adSplitSchedule.setActive(true);
        adSplitSchedule.setOuCode(ouCode);
        adSplitSchedule.setSessionDate(LocalDate.now().plusDays(1));
        courtScheduleRepository.save(adSplitSchedule);

        // Same ouCode but past date
        CourtSchedule pastSchedule = random(CourtSchedule.class);
        pastSchedule.setSlotBased(true);
        pastSchedule.setSupportAdSplit(false);
        pastSchedule.setActive(true);
        pastSchedule.setOuCode(ouCode);
        pastSchedule.setSessionDate(LocalDate.now().minusDays(5));
        courtScheduleRepository.save(pastSchedule);

        // Save initial copies
        CourtSchedule adSplitBefore = courtScheduleRepository.findBy(adSplitSchedule.getCourtScheduleId());
        CourtSchedule pastBefore = courtScheduleRepository.findBy(pastSchedule.getCourtScheduleId());

        // When
        courtScheduleRepository.getInconsistentCourtSchedulersByOucode(ouCode);

        // Then
        // Only slot based matching one should update
        CourtSchedule updatedSlotBased = courtScheduleRepository.findBy(slotBasedSchedule.getCourtScheduleId());
        assertEquals(Integer.valueOf(2), updatedSlotBased.getMaxSlots());

        // All others should remain exactly same
        assertEquals(adSplitBefore.getMaxDuration(), courtScheduleRepository.findBy(adSplitSchedule.getCourtScheduleId()).getMaxDuration());
        assertEquals(pastBefore.getSessionDate(), courtScheduleRepository.findBy(pastSchedule.getCourtScheduleId()).getSessionDate());
    }

    @Test
    public void shouldFindCourtSchedulesByCourtScheduleRequestParam() {
        // given
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        CourtSchedule matchingCourtSchedule2 = random(CourtSchedule.class);

        matchingCourtSchedule2.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule2.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule2.setBusinessType(matchingCourtSchedule1.getBusinessType());

        CourtSchedule matchingCourtSchedule3 = random(CourtSchedule.class);
        matchingCourtSchedule3.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule3.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule3.setBusinessType(matchingCourtSchedule1.getBusinessType());

        // and
        matchingCourtSchedule1.setCourtRoomName("Courtroom 01");
        matchingCourtSchedule2.setCourtRoomName("Courtroom 02");
        matchingCourtSchedule3.setCourtRoomName("Courtroom 03");

        matchingCourtSchedule1.setActive(true);
        matchingCourtSchedule2.setActive(true);
        matchingCourtSchedule3.setActive(true);

        courtScheduleRepository.save(matchingCourtSchedule1);
        courtScheduleRepository.save(matchingCourtSchedule2);
        courtScheduleRepository.save(matchingCourtSchedule3);

        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setBookingId(bookingId);
        allocatedListing1.setCourtScheduleId(matchingCourtSchedule1.getCourtScheduleId());
        allocatedListing1.setDuration(10);
        allocatedListingRepository.save(allocatedListing1);

        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        allocatedListing2.setBookingId(bookingId);
        allocatedListing2.setCourtScheduleId(matchingCourtSchedule2.getCourtScheduleId());
        allocatedListing2.setDuration(20);
        allocatedListingRepository.save(allocatedListing2);

        AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setHearingId(hearingId);
        allocatedListing3.setBookingId(bookingId);
        allocatedListing3.setCourtScheduleId(matchingCourtSchedule3.getCourtScheduleId());
        allocatedListing3.setDuration(30);
        allocatedListingRepository.save(allocatedListing3);
        // and
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule1.getSessionDate());
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleRequestParam(matchingCourtSchedule1, courtCentreId);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
        results.forEach(result -> {
            if (result.getCourtScheduleId().equals(matchingCourtSchedule1.getCourtScheduleId())) {
                assertThat(10, is(result.getTotalBooked()));
            } else if (result.getCourtScheduleId().equals(matchingCourtSchedule2.getCourtScheduleId())) {
                assertThat(20, is(result.getTotalBooked()));
            } else if (result.getCourtScheduleId().equals(matchingCourtSchedule3.getCourtScheduleId())) {
                assertThat(30, is(result.getTotalBooked()));
            }
        });
    }

    @Test
    public void shouldFindCourtSchedulesByMandatoryParameters() {
        // given
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2024,10,22));
        CourtSchedule matchingCourtSchedule2 = random(CourtSchedule.class);

        matchingCourtSchedule2.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule2.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule2.setBusinessType(random(String.class));

        CourtSchedule matchingCourtSchedule3 = random(CourtSchedule.class);
        matchingCourtSchedule3.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule3.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule3.setBusinessType(random(String.class));

        // and
        matchingCourtSchedule1.setCourtRoomName("Courtroom 01");
        matchingCourtSchedule2.setCourtRoomName("Courtroom 02");
        matchingCourtSchedule3.setCourtRoomName("Courtroom 03");

        matchingCourtSchedule1.setActive(true);
        matchingCourtSchedule2.setActive(true);
        matchingCourtSchedule3.setActive(true);

        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setBookingId(bookingId);
        allocatedListing1.setCourtScheduleId(matchingCourtSchedule1.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing1);

        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        allocatedListing2.setBookingId(bookingId);
        allocatedListing2.setCourtScheduleId(matchingCourtSchedule2.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing2);

        AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setHearingId(hearingId);
        allocatedListing3.setBookingId(bookingId);
        allocatedListing3.setCourtScheduleId(matchingCourtSchedule3.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing3);

        courtScheduleRepository.save(matchingCourtSchedule1);
        courtScheduleRepository.save(matchingCourtSchedule2);
        courtScheduleRepository.save(matchingCourtSchedule3);
        // and
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule1.getSessionDate().plusDays(1));
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleRequestMandatoryParams(matchingCourtSchedule1, courtCentreId,matchingCourtSchedule1.getSessionDate(),matchingCourtSchedule1.getSessionDate());

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
    }

    @Test
    public void shouldReplaceOldAllocatedListingsWithNewOneForSameHearingId() {
        // Given
        String hearingId = randomUUID().toString();

        CourtSchedule day1Schedule = createSlotBasedCourtSchedule("OU123", "ADULT", LocalDate.of(2025, 6, 10), "CR01", "TRF");
        CourtSchedule day2Schedule = createSlotBasedCourtSchedule("OU123", "ADULT", LocalDate.of(2025, 6, 11), "CR01", "TRF");
        courtScheduleRepository.save(day1Schedule);
        courtScheduleRepository.save(day2Schedule);

        AllocatedListing listing1 = random(AllocatedListing.class);
        listing1.setHearingId(hearingId);
        listing1.setCourtScheduleId(day1Schedule.getCourtScheduleId());
        listing1.setHearingStartTime(DateUtils.combineDateAndTime(day1Schedule.getSessionDate(), "10:00"));
        listing1.setDuration(1);
        allocatedListingRepository.save(listing1);

        AllocatedListing listing2 = random(AllocatedListing.class);
        listing2.setHearingId(hearingId);
        listing2.setCourtScheduleId(day2Schedule.getCourtScheduleId());
        listing2.setHearingStartTime(DateUtils.combineDateAndTime(day2Schedule.getSessionDate(), "10:00"));
        listing2.setDuration(1);
        allocatedListingRepository.save(listing2);

        // New court schedule for the updated hearing
        CourtSchedule newSchedule = createSlotBasedCourtSchedule("OU123", "ADULT", LocalDate.of(2025, 6, 12), "CR01", "TRF");
        newSchedule.setSessionStartTime(DateUtils.combineDateAndTime(newSchedule.getSessionDate(), "10:00"));
        newSchedule.setSessionEndTime(DateUtils.combineDateAndTime(newSchedule.getSessionDate(), "12:00"));
        newSchedule.setMaxSlots(10);
        newSchedule.setAvailableSlots(10);
        courtScheduleRepository.save(newSchedule);

        // When
        RequestedSlots requestedSlots = new RequestedSlots();
        RequestedCourtSchedule requestedCourtSchedule = new RequestedCourtSchedule();
        requestedCourtSchedule.setCourtScheduleId(newSchedule.getCourtScheduleId());
        requestedCourtSchedule.setHearingStartTime(DateUtils.toResponseDateString(DateUtils.combineDateAndTime(newSchedule.getSessionDate(), "11:29")));
        requestedCourtSchedule.setDurationInMinutes(30);

        HearingSlot hearingSlot = new HearingSlot();
        hearingSlot.setHearingId(hearingId);
        hearingSlot.setCourtScheduleIds(List.of(requestedCourtSchedule));
        requestedSlots.setHearingSlots(List.of(hearingSlot));

        courtScheduleRepository.updateListHearingSlots(requestedSlots);

        // Then
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);
        assertEquals(1, allocatedListings.size());

        AllocatedListing newListing = allocatedListings.get(0);
        assertEquals(newSchedule.getCourtScheduleId(), newListing.getCourtScheduleId());
        assertEquals(DateUtils.combineDateAndTime(newSchedule.getSessionDate(), "11:00"), newListing.getHearingStartTime());
    }

    @Test
    public void shouldUpdateListHearingSlotsForSlotBased() {
        // given
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2025,4,14));
        matchingCourtSchedule1.setSessionStartTime(DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(), "10:00"));
        matchingCourtSchedule1.setSessionEndTime(DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(), "12:00"));
        matchingCourtSchedule1.setSlotBased(true);
        matchingCourtSchedule1.setMaxSlots(2);
        matchingCourtSchedule1.setAvailableSlots(2);
        courtScheduleRepository.save(matchingCourtSchedule1);

        String courtScheduleId1 = matchingCourtSchedule1.getCourtScheduleId();

        RequestedSlots slotsWrapper = new RequestedSlots();
        HearingSlot hearingSlot = new HearingSlot();
        String hearingId = randomUUID().toString();
        String courtScheduleId =  courtScheduleId1;
        RequestedCourtSchedule requestedCourtSchedule = new RequestedCourtSchedule();
        requestedCourtSchedule.setCourtScheduleId(courtScheduleId);
        final Date hearingStartTime = DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(),"11:00");
        requestedCourtSchedule.setHearingStartTime(DateUtils.toResponseDateString(hearingStartTime));
        requestedCourtSchedule.setDurationInMinutes(180);
        List<RequestedCourtSchedule> courtScheduleIds = new ArrayList<>();
        courtScheduleIds.add(requestedCourtSchedule);

        hearingSlot.setHearingId(hearingId);
        hearingSlot.setCourtScheduleIds(courtScheduleIds);
        List<HearingSlot> hearingSlots = new ArrayList<>();
        hearingSlots.add(hearingSlot);
        slotsWrapper.setHearingSlots(hearingSlots);

        //when
        courtScheduleRepository.updateListHearingSlots(slotsWrapper);

        //then
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);
        uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing allocatedListing = allocatedListings.get(0);

        List<CourtSchedule> updatedSchedules = courtScheduleRepository.findBy(matchingCourtSchedule1);
        assertFalse(updatedSchedules.isEmpty());
        CourtSchedule updatedSchedule = updatedSchedules.get(0);
        assertThat(1, is(updatedSchedule.getAvailableSlots())); //available slots deducted

        assertEquals(allocatedListing.getHearingId(), hearingId);
        assertEquals(SLOT_DEFAULT,allocatedListing.getDuration().intValue());
        assertEquals(hearingStartTime,allocatedListing.getHearingStartTime());
        assertEquals(allocatedListing.getCourtScheduleId(), matchingCourtSchedule1.getCourtScheduleId());
    }

    @Test
    public void shouldUpdateListHearingStartTimeToSessionStartTime() {
        // given
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2025,4,14));
        matchingCourtSchedule1.setSessionStartTime(DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(), "10:00"));
        matchingCourtSchedule1.setSessionEndTime(DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(), "12:00"));
        matchingCourtSchedule1.setSlotBased(true);
        matchingCourtSchedule1.setMaxSlots(2);
        matchingCourtSchedule1.setAvailableSlots(2);
        courtScheduleRepository.save(matchingCourtSchedule1);

        String courtScheduleId1 = matchingCourtSchedule1.getCourtScheduleId();

        RequestedSlots slotsWrapper = new RequestedSlots();
        HearingSlot hearingSlot = new HearingSlot();
        String hearingId = randomUUID().toString();
        String courtScheduleId =  courtScheduleId1;
        RequestedCourtSchedule requestedCourtSchedule = new RequestedCourtSchedule();
        requestedCourtSchedule.setCourtScheduleId(courtScheduleId);
        final Date hearingStartTime = DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(),"14:00");
        requestedCourtSchedule.setHearingStartTime(DateUtils.toResponseDateString(hearingStartTime));
        requestedCourtSchedule.setDurationInMinutes(180);
        List<RequestedCourtSchedule> courtScheduleIds = new ArrayList<>();
        courtScheduleIds.add(requestedCourtSchedule);

        hearingSlot.setHearingId(hearingId);
        hearingSlot.setCourtScheduleIds(courtScheduleIds);
        List<HearingSlot> hearingSlots = new ArrayList<>();
        hearingSlots.add(hearingSlot);
        slotsWrapper.setHearingSlots(hearingSlots);

        //when
        courtScheduleRepository.updateListHearingSlots(slotsWrapper);

        //then
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);
        uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing allocatedListing = allocatedListings.get(0);

        List<CourtSchedule> updatedSchedules = courtScheduleRepository.findBy(matchingCourtSchedule1);
        assertFalse(updatedSchedules.isEmpty());
        CourtSchedule updatedSchedule = updatedSchedules.get(0);
        assertThat(1, is(updatedSchedule.getAvailableSlots())); //available slots deducted

        assertEquals(allocatedListing.getHearingId(), hearingId);
        assertEquals(SLOT_DEFAULT,allocatedListing.getDuration().intValue());
        assertEquals(updatedSchedules.get(0).getSessionStartTime(),allocatedListing.getHearingStartTime());
        assertEquals(allocatedListing.getCourtScheduleId(), matchingCourtSchedule1.getCourtScheduleId());
    }

    @Test
    public void shouldUpdateListHearingSlotsForDurationBased() {
        // given
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2025,4,16));
        matchingCourtSchedule1.setSessionStartTime(DateUtils.combineDateAndTime(matchingCourtSchedule1.getSessionDate(), "10:00"));
        matchingCourtSchedule1.setSlotBased(false);
        matchingCourtSchedule1.setMaxSlots(2);
        matchingCourtSchedule1.setAvailableSlots(2);
        matchingCourtSchedule1.setAvailableDuration(180);
        courtScheduleRepository.save(matchingCourtSchedule1);

        String courtScheduleId1 = matchingCourtSchedule1.getCourtScheduleId();

        RequestedSlots slotsWrapper = new RequestedSlots();
        HearingSlot hearingSlot = new HearingSlot();
        String hearingId = randomUUID().toString();
        String courtScheduleId =  courtScheduleId1;
        RequestedCourtSchedule requestedCourtSchedule = new RequestedCourtSchedule();
        requestedCourtSchedule.setCourtScheduleId(courtScheduleId);
        requestedCourtSchedule.setHearingStartTime("2025-04-16T10:00:00Z");
        requestedCourtSchedule.setDurationInMinutes(120);
        List<RequestedCourtSchedule> courtScheduleIds = new ArrayList<>();
        courtScheduleIds.add(requestedCourtSchedule);

        hearingSlot.setHearingId(hearingId);
        hearingSlot.setCourtScheduleIds(courtScheduleIds);
        List<HearingSlot> hearingSlots = new ArrayList<>();
        hearingSlots.add(hearingSlot);
        slotsWrapper.setHearingSlots(hearingSlots);

        //when
        courtScheduleRepository.updateListHearingSlots(slotsWrapper);

        //then
        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);
        uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing allocatedListing = allocatedListings.get(0);

        List<CourtSchedule> updatedSchedules = courtScheduleRepository.findBy(matchingCourtSchedule1);
        assertFalse(updatedSchedules.isEmpty());
        CourtSchedule updatedSchedule = updatedSchedules.get(0);
        assertThat(60, is(updatedSchedule.getAvailableDuration())); //available duration deducted

        assertEquals(allocatedListing.getHearingId(), hearingId);
        assertEquals(allocatedListing.getCourtScheduleId(), matchingCourtSchedule1.getCourtScheduleId());
    }

    @Test
    @Ignore("Test disabled - will be fixed with h2 removal")
    public void shouldReturnCourtSchedulesByIdListWithAllFields() {
        // given
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setActive(true);
        courtSchedule.setSlotBased(true);
        courtSchedule.setSupportAdSplit(true);
        courtSchedule.setIsOverbookingAllowed(true);
        courtSchedule.setMaxAdMorningDuration(120);
        courtSchedule.setMaxAdAfternoonDuration(180);
        courtSchedule.setMaxSlots(10);
        courtSchedule.setMaxDuration(240);
        courtSchedule.setAvailableSlots(8);
        courtSchedule.setAvailableDuration(200);
        courtSchedule.setSessionDate(LocalDate.of(2024, 10, 1));
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(9, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(13, 0)));
        courtSchedule.setCourtSession("AM");
        courtSchedule.setPanel("ADULT");
        courtSchedule.setBusinessType("TRF");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setOperationalUnit("UNIT123");
        courtSchedule.setCourtHouseId("CH001");
        courtSchedule.setCourtHouseName("Test Court");
        courtSchedule.setCourtRoomId("CR001");
        courtSchedule.setCourtRoomName("Courtroom 1");
        courtSchedule.setCourtRoomNumber(123);
        courtSchedule.setListingProfileId("LIST-001");

        courtScheduleRepository.save(courtSchedule);

        AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setHearingId(randomUUID().toString());
        allocatedListing.setBookingId(randomUUID().toString());
        allocatedListing.setOucode(courtSchedule.getOuCode());
        allocatedListing.setDuration(30);
        allocatedListing.setHearingStartTime(new Date());
        allocatedListing.setCreatedOn(new Date());
        allocatedListing.setUpdatedOn(new Date());

        allocatedListingRepository.save(allocatedListing);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results =
                courtScheduleRepository.getCourtSchedulesByIdList(List.of(courtSchedule.getCourtScheduleId()));

        // then
        assertEquals(1, results.size());
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule result = results.get(0);

        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals(courtSchedule.getOuCode(), result.getOuCode());
        assertEquals(courtSchedule.getCourtRoomId(), result.getCourtRoomId());
        assertEquals(courtSchedule.getCourtRoomNumber(), result.getCourtRoomNumber());
        assertEquals(courtSchedule.getCourtHouseId(), result.getCourtHouseId());
        assertEquals(courtSchedule.getCourtHouseName(), result.getCourtHouseName());
        assertEquals(courtSchedule.getCourtRoomName(), result.getCourtRoomName());
        assertEquals(courtSchedule.getOperationalUnit(), result.getOperationalUnit());
        assertEquals(courtSchedule.getBusinessType(), result.getBusinessType());
        assertEquals(courtSchedule.getPanel(), result.getPanel());
        assertEquals(courtSchedule.getCourtSession(), result.getCourtSession());
        assertEquals(courtSchedule.getSessionDate(), result.getSessionDate());
        assertEquals(courtSchedule.getAvailableSlots(), result.getAvailableSlots());
        assertEquals(courtSchedule.getAvailableDuration(), result.getAvailableDuration());
        assertEquals(courtSchedule.getMaxSlots(), result.getMaxSlots());
        assertEquals(courtSchedule.getMaxDuration(), result.getMaxDuration());
        assertEquals(courtSchedule.getMaxAdMorningDuration(), result.getMaxDurationForMorning());
        assertEquals(courtSchedule.getMaxAdAfternoonDuration(), result.getMaxDurationForAfternoon());
        assertEquals(courtSchedule.getSupportAdSplit(), result.isAllDaySplit());
        assertEquals(courtSchedule.isSlotBased(), result.isSlotBased());
        assertEquals(courtSchedule.getSessionStartTime(), result.getSessionStartTime());
        assertEquals(courtSchedule.getSessionEndTime(), result.getSessionEndTime());
        assertEquals(courtSchedule.getListingProfileId(), result.getListingProfileId());
        assertEquals(30, result.getTotalBooked().intValue());
    }

    @Test
    public void shouldFindCourtSchedulesByAllParameters() {
        // given
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2024,10,22));
        CourtSchedule matchingCourtSchedule2 = random(CourtSchedule.class);

        matchingCourtSchedule2.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule2.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule2.setCourtRoomId(matchingCourtSchedule1.getCourtRoomId());
        matchingCourtSchedule2.setBusinessType(matchingCourtSchedule1.getBusinessType());

        CourtSchedule matchingCourtSchedule3 = random(CourtSchedule.class);
        matchingCourtSchedule3.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule3.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule3.setCourtRoomId(matchingCourtSchedule1.getCourtRoomId());
        matchingCourtSchedule3.setBusinessType(matchingCourtSchedule1.getBusinessType());

        // and
        matchingCourtSchedule1.setCourtRoomName("Courtroom 01");
        matchingCourtSchedule2.setCourtRoomName("Courtroom 02");
        matchingCourtSchedule3.setCourtRoomName("Courtroom 03");

        matchingCourtSchedule1.setActive(true);
        matchingCourtSchedule2.setActive(true);
        matchingCourtSchedule3.setActive(true);

        courtScheduleRepository.save(matchingCourtSchedule1);
        courtScheduleRepository.save(matchingCourtSchedule2);
        courtScheduleRepository.save(matchingCourtSchedule3);

        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setBookingId(bookingId);
        allocatedListing1.setCourtScheduleId(matchingCourtSchedule1.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing1);

        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        allocatedListing2.setBookingId(bookingId);
        allocatedListing2.setCourtScheduleId(matchingCourtSchedule2.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing2);

        AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setHearingId(hearingId);
        allocatedListing3.setBookingId(bookingId);
        allocatedListing3.setCourtScheduleId(matchingCourtSchedule3.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing3);
        // and
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule1.getSessionDate());
        unMatchingCourtSchedule.setBusinessType(matchingCourtSchedule1.getBusinessType());
        unMatchingCourtSchedule.setCourtRoomId(randomUUID().toString());
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleAllRequestParams(matchingCourtSchedule1,matchingCourtSchedule1.getCourtRoomId(), courtCentreId);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
    }

    @Test
    public void shouldFindCourtSchedulesAndHasHearingsBookedInfo() {
        // given
        //schedule 1  with allocated hearing --should return with hearings booked as true
        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2024,10,22));
        CourtSchedule matchingCourtSchedule2 = random(CourtSchedule.class);
        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setCourtScheduleId(matchingCourtSchedule1.getCourtScheduleId());

        //schedule 2 with allocated hearing matches same attributes as schedule 1 --should return with hearings booked as true
        matchingCourtSchedule2.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule2.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule2.setCourtRoomId(matchingCourtSchedule1.getCourtRoomId());
        matchingCourtSchedule2.setBusinessType(matchingCourtSchedule1.getBusinessType());
        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setCourtScheduleId(matchingCourtSchedule2.getCourtScheduleId());

        //schedule 3 with no allocated hearing matches same attributes as schedule 1 --should return with hearings booked as false
        CourtSchedule matchingCourtSchedule3 = random(CourtSchedule.class);
        matchingCourtSchedule3.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule3.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule3.setCourtRoomId(matchingCourtSchedule1.getCourtRoomId());
        matchingCourtSchedule3.setBusinessType(matchingCourtSchedule1.getBusinessType());
        AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setCourtScheduleId(matchingCourtSchedule3.getCourtScheduleId());
        // and

        matchingCourtSchedule1.setActive(true);
        matchingCourtSchedule2.setActive(true);
        matchingCourtSchedule3.setActive(true);

        courtScheduleRepository.save(matchingCourtSchedule1);
        courtScheduleRepository.save(matchingCourtSchedule2);
        courtScheduleRepository.save(matchingCourtSchedule3);
        allocatedListingRepository.save(allocatedListing1);
        allocatedListingRepository.save(allocatedListing2);
        allocatedListingRepository.save(allocatedListing3);
        // and unmatching schedule with its own courtroom and allocated listing - should not be returned
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule1.getSessionDate());
        unMatchingCourtSchedule.setBusinessType(matchingCourtSchedule1.getBusinessType());
        unMatchingCourtSchedule.setCourtRoomId(randomUUID().toString());
        AllocatedListing allocatedListing4 = random(AllocatedListing.class);
        allocatedListing4.setCourtScheduleId(unMatchingCourtSchedule.getCourtScheduleId());
        courtScheduleRepository.save(unMatchingCourtSchedule);
        allocatedListingRepository.save(allocatedListing4);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleAllRequestParams(matchingCourtSchedule1,matchingCourtSchedule1.getCourtRoomId(), courtCentreId);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
    }

    @Test
    public void shouldFilterSchedulesThatAreInactive() {
        // given
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();

        CourtSchedule matchingCourtSchedule1 = random(CourtSchedule.class);
        matchingCourtSchedule1.setSessionDate(LocalDate.of(2024,10,22));
        CourtSchedule matchingCourtSchedule2 = random(CourtSchedule.class);

        matchingCourtSchedule2.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule2.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule2.setBusinessType(random(String.class));

        CourtSchedule matchingCourtSchedule3 = random(CourtSchedule.class);
        matchingCourtSchedule3.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        matchingCourtSchedule3.setSessionDate(matchingCourtSchedule1.getSessionDate());
        matchingCourtSchedule3.setBusinessType(random(String.class));

        // and
        matchingCourtSchedule1.setCourtRoomName("Courtroom 01");
        matchingCourtSchedule2.setCourtRoomName("Courtroom 02");
        matchingCourtSchedule3.setCourtRoomName("Courtroom 03");

        matchingCourtSchedule1.setActive(true);
        matchingCourtSchedule2.setActive(true);
        matchingCourtSchedule3.setActive(true);

        courtScheduleRepository.save(matchingCourtSchedule1);
        courtScheduleRepository.save(matchingCourtSchedule2);
        courtScheduleRepository.save(matchingCourtSchedule3);

        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setBookingId(bookingId);
        allocatedListing1.setCourtScheduleId(matchingCourtSchedule1.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing1);

        AllocatedListing allocatedListing2 = random(AllocatedListing.class);
        allocatedListing2.setHearingId(hearingId);
        allocatedListing2.setBookingId(bookingId);
        allocatedListing2.setCourtScheduleId(matchingCourtSchedule2.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing2);

        AllocatedListing allocatedListing3 = random(AllocatedListing.class);
        allocatedListing3.setHearingId(hearingId);
        allocatedListing3.setBookingId(bookingId);
        allocatedListing3.setCourtScheduleId(matchingCourtSchedule3.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing3);
        // and
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule1.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule1.getSessionDate());
        unMatchingCourtSchedule.setActive(false);
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleRequestMandatoryParams(matchingCourtSchedule1, courtCentreId,matchingCourtSchedule1.getSessionDate(),matchingCourtSchedule1.getSessionDate());

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
    }

    @Test
    @Ignore("Will be fixed in a separate story")
    public void shouldFilterCourtSchedulesByMandatoryParams() {
        // given
        LocalDate sessionDate = LocalDate.of(2024, 4, 15);
        setupTestDataForMandatoryParams(sessionDate);

        // when
        Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result =
            whenSearchingWithMandatoryParams(sessionDate);

        // then
        thenOnlyMatchingMandatoryParamsAreReturned(result);
    }

    @Test
    @Ignore("Will be fixed in a separate story")
    public void shouldFilterCourtSchedulesByOptionalParams() {
        // given
        LocalDate sessionDate = LocalDate.of(2024, 4, 15);
        setupTestDataForOptionalParams(sessionDate, false);

        // when
        Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result =
            whenSearchingWithOptionalParams(sessionDate);

        // then
        thenOnlyMatchingOptionalParamsAreReturned(result);
    }

    @Test
    @Ignore("Will be fixed in a separate story")
    public void shouldPaginateCourtSchedules() {
        // given
        LocalDate sessionDate = LocalDate.of(2024, 4, 15);
        setupTestDataForPagination(sessionDate);

        // when
        List<Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>> results =
            whenFetchingMultiplePages(sessionDate);

        // then
        thenPaginationWorksCorrectly(results);
    }

    @Test
    @Ignore("requires standalone postgres, fails with h2")
    public void shouldFilterCourtSchedulesByOptionalParamsIsSlotBased() {
        // given
        LocalDate sessionDate = LocalDate.of(2024, 4, 15);
        setupTestDataForOptionalParams(sessionDate, true);

        // when
        Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result =
                whenSearchingWithOptionalParamsSlotBased(sessionDate);

        // then
        thenOnlyMatchingOptionalParamsAreReturnedSlotBased(result);
    }

    // Setup methods
    private void setupTestDataForMandatoryParams(LocalDate sessionDate) {
        CourtSchedule matchingSchedule = createCourtSchedule("B01LY00", "ADULT", sessionDate, "CR01", "TRF");
        CourtSchedule differentOuCode = createCourtSchedule("B02LY00", "ADULT", sessionDate, "CR02", "TRF");
        CourtSchedule differentPanel = createCourtSchedule("B01LY00", "YOUTH", sessionDate, "CR03", "TRF");
        CourtSchedule differentDate = createCourtSchedule("B01LY00", "ADULT", sessionDate.plusDays(10), "CR04", "TRF");

        saveSchedules(List.of(matchingSchedule, differentOuCode, differentPanel, differentDate));
    }

    private void setupTestDataForOptionalParams(LocalDate sessionDate, boolean isSlotBased) {
        String ouCode = "B01LY00";
        String panel = "ADULT";

        if ((isSlotBased)) {
            CourtSchedule matchingSchedule = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR01", "TRF");
            CourtSchedule differentCourtRoom = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR02", "TRF");
            CourtSchedule differentBusinessType = createSlotBasedCourtSchedule(ouCode, panel, sessionDate, "CR01", "GAP");
            saveSchedules(List.of(matchingSchedule, differentCourtRoom, differentBusinessType));
        } else {
            CourtSchedule matchingSchedule = createCourtSchedule(ouCode, panel, sessionDate, "CR01", "TRF");
            CourtSchedule differentCourtRoom = createCourtSchedule(ouCode, panel, sessionDate, "CR02", "TRF");
            CourtSchedule differentBusinessType = createCourtSchedule(ouCode, panel, sessionDate, "CR01", "GAP");
            saveSchedules(List.of(matchingSchedule, differentCourtRoom, differentBusinessType));
        }
    }

    private void setupTestDataForPagination(LocalDate sessionDate) {
        List<CourtSchedule> schedules = new ArrayList<>();
        for (int i = 1; i <= 25; i++) {
            schedules.add(createCourtSchedule(
                "B01LY00",
                "ADULT",
                sessionDate,
                "CR" + String.format("%02d", i),
                "TRF"
            ));
        }
        saveSchedules(schedules);
    }

    // When methods
    private Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> whenSearchingWithMandatoryParams(LocalDate sessionDate) {
        HearingSlotRequestParam requestParam = createRequestParam(
            "ADULT",
            sessionDate,
            "B01LY00",
            "1",
            "10",
            null,
            null,
                null,
                null
        );
        return courtScheduleRepository.getCourtSchedules(requestParam);
    }

    private Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> whenSearchingWithOptionalParams(LocalDate sessionDate) {
        HearingSlotRequestParam requestParam = createRequestParam(
            "ADULT",
            sessionDate,
            "B01LY00",
            "1",
            "10",
            "CR01",
            "TRF",
                null,
                null
        );
        return courtScheduleRepository.getCourtSchedules(requestParam);
    }

    private Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> whenSearchingWithOptionalParamsSlotBased(LocalDate sessionDate) {
        HearingSlotRequestParam requestParam = createRequestParam(
            "ADULT",
            sessionDate,
            "B01LY00",
            "1",
            "10",
            "CR01",
            null,
                true,
                null
        );
        return courtScheduleRepository.getCourtSchedules(requestParam);
    }

    private List<Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>> whenFetchingMultiplePages(LocalDate sessionDate) {
        List<Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>> results = new ArrayList<>();
        for (int page = 1; page <= 3; page++) {
            results.add(courtScheduleRepository.getCourtSchedules(createRequestParam(
                "ADULT",
                sessionDate,
                "B01LY00",
                String.valueOf(page),
                "10",
                null,
                null,
                null,
                    null
            )));
        }
        return results;
    }

    // Then methods
    private void thenOnlyMatchingMandatoryParamsAreReturned(Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result) {
        assertEquals(1, result.getRight().size());
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule schedule = result.getRight().get(0);
        assertEquals("B01LY00", schedule.getOuCode());
        assertEquals("ADULT", schedule.getPanel());
    }

    private void thenOnlyMatchingOptionalParamsAreReturned(Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result) {
        assertEquals(1, result.getRight().size());
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule schedule = result.getRight().get(0);
        assertEquals("CR01", schedule.getCourtRoomId());
        assertEquals("TRF", schedule.getBusinessType());
    }

    private void thenOnlyMatchingOptionalParamsAreReturnedSlotBased(Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> result) {
        assertEquals(2, result.getRight().size());
        uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule schedule = result.getRight().get(0);
        assertEquals("CR01", schedule.getCourtRoomId());
        assertEquals("GAP", schedule.getBusinessType());
    }

    private void thenPaginationWorksCorrectly(List<Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>> results) {
        assertEquals(10, results.get(0).getRight().size());
        assertEquals(10, results.get(1).getRight().size());
        assertEquals(5, results.get(2).getRight().size());

        // Verify total count consistency
        results.forEach(result -> assertEquals(Integer.valueOf(25), result.getLeft()));

        // Verify no duplicates
        Set<String> allIds = results.stream()
            .flatMap(result -> result.getRight().stream())
            .map(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getCourtScheduleId)
            .collect(Collectors.toSet());
        assertEquals(25, allIds.size());
    }

    // Helper methods
    private void saveSchedules(List<CourtSchedule> schedules) {
        schedules.forEach(courtScheduleRepository::save);
    }

    private HearingSlotRequestParam createRequestParam(String panel, LocalDate sessionDate, String ouCode,
                                                     String pageNumber, String pageSize,
                                                     String courtRoomId, String businessType, Boolean isSlotBased, String hearingStartTime) {
        return new HearingSlotRequestParam(
            panel,
            sessionDate.toString(),
            sessionDate.toString(),
            null,
            ouCode,
            ouCode,
            pageSize,
            pageNumber,
            courtRoomId,
            null,
            businessType,
            null,
            isSlotBased,
                hearingStartTime
        );
    }

    private CourtSchedule createCourtSchedule(final String ouCode, final String panel, final LocalDate sessionDate,
                                            final String courtRoomId, final String businessType) {
        CourtSchedule schedule = new CourtSchedule();
        schedule.setCourtScheduleId(UUID.randomUUID().toString());
        schedule.setSlotBased(false);
        schedule.setOuCode(ouCode);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setBusinessType(businessType);
        schedule.setSessionDate(sessionDate);
        schedule.setCourtSession("AM");
        schedule.setActive(true);
        schedule.setCourtRoomNumber(1);
        schedule.setCourtHouseName("Test Court House");
        schedule.setCourtRoomName("Test Court Room " + courtRoomId);
        schedule.setOperationalUnit(ouCode);
        schedule.setPanel(panel);
        schedule.setMaxSlots(10);
        schedule.setMaxDuration(240);
        schedule.setAvailableSlots(10);
        schedule.setAvailableDuration(240);
        schedule.setCourtHouseId("CH" + ouCode);

        // Boolean fields with defaults
        schedule.setSupportAdSplit(false);
        schedule.setIsOverbookingAllowed(false);

        // Numeric fields with defaults
        schedule.setMaxAdMorningDuration(0);
        schedule.setMaxAdAfternoonDuration(0);

        // Timestamp fields
        LocalDateTime now = LocalDateTime.now();
        schedule.setCreatedOn(Timestamp.valueOf(now));
        schedule.setUpdatedOn(Timestamp.valueOf(now));

        // Session time fields (with time zone)
        LocalDateTime startDateTime = LocalDateTime.of(sessionDate, LocalTime.of(9, 0));
        LocalDateTime endDateTime = LocalDateTime.of(sessionDate, LocalTime.of(13, 0));
        schedule.setSessionStartTime(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setSessionEndTime(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        schedule.setListingProfileId(random(String.class));

        return schedule;
    }

    private CourtSchedule createSlotBasedCourtSchedule(String ouCode, String panel, LocalDate sessionDate,
                                            String courtRoomId, String businessType) {
        CourtSchedule schedule = new CourtSchedule();
        schedule.setCourtScheduleId(UUID.randomUUID().toString());
        schedule.setSlotBased(true);
        schedule.setOuCode(ouCode);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setBusinessType(businessType);
        schedule.setSessionDate(sessionDate);
        schedule.setCourtSession("AM");
        schedule.setActive(true);
        schedule.setCourtRoomNumber(1);
        schedule.setCourtHouseName("Test Court House");
        schedule.setCourtRoomName("Test Court Room " + courtRoomId);
        schedule.setOperationalUnit(ouCode);
        schedule.setPanel(panel);
        schedule.setMaxSlots(10);
        schedule.setMaxDuration(240);
        schedule.setAvailableSlots(10);
        schedule.setAvailableDuration(240);
        schedule.setCourtHouseId("CH" + ouCode);
        schedule.setIsDraft(false);

        // Boolean fields with defaults
        schedule.setSupportAdSplit(false);
        schedule.setIsOverbookingAllowed(false);

        // Numeric fields with defaults
        schedule.setMaxAdMorningDuration(0);
        schedule.setMaxAdAfternoonDuration(0);

        // Timestamp fields
        LocalDateTime now = LocalDateTime.now();
        schedule.setCreatedOn(Timestamp.valueOf(now));
        schedule.setUpdatedOn(Timestamp.valueOf(now));

        // Session time fields (with time zone)
        LocalDateTime startDateTime = LocalDateTime.of(sessionDate, LocalTime.of(9, 0));
        LocalDateTime endDateTime = LocalDateTime.of(sessionDate, LocalTime.of(13, 0));
        schedule.setSessionStartTime(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setSessionEndTime(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setListingProfileId(random(String.class));

        return schedule;
    }

    private static CourtScheduleRequestParam getCourtScheduleRequestParam(final CourtSchedule courtSchedule, final String courtCentreId) {
        String courtRoomId = null;
        String businessType = courtSchedule.getBusinessType();
        String sessionStartDate = courtSchedule.getSessionDate().minusDays(1).toString();
        String sessionEndDate = courtSchedule.getSessionDate().plusDays(2).toString();
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }

    private static CourtScheduleRequestParam getCourtScheduleAllRequestParams(final CourtSchedule courtSchedule,final String courtroomId, final String courtCentreId) {
        String businessType = courtSchedule.getBusinessType();
        String sessionStartDate = courtSchedule.getSessionDate().toString();
        String sessionEndDate = courtSchedule.getSessionDate().toString();
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtroomId, businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }

    private static CourtScheduleRequestParam getCourtScheduleRequestMandatoryParams(final CourtSchedule courtSchedule, final String courtCentreId,final LocalDate startDate,final LocalDate endDate) {
        String courtRoomId = null;
        String businessType = null;
        String sessionStartDate = startDate.toString();
        String sessionEndDate = endDate.toString();
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
    }

    @Test
    public void shouldSaveSlots() {
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtRoomNumber(1234);
        courtScheduleRepository.save(courtSchedule);


        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        provisionalBookingRepository.save(provisionalBooking);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(1501);
        allocatedListingRepository.save(allocatedListing);

        AllocatedSlot allocatedSlot1 = getAllocatedSlot(allocatedListing);
        List<AllocatedSlot> slots = Lists.newArrayList(allocatedSlot1);
        boolean isProvisionalSlot = false;

        final Result result = courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot, false);

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedules.isEmpty());

        checkSlotUpdateResult(result);
    }

    @Test
    public void shouldFindByUpdatedOnGreaterThanAndUpdatedOnLessThan(){
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        LocalDate fromDate = LocalDate.of(2024, 7, 15);
        LocalDate toDate = LocalDate.of(2024, 7, 16);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);

        courtScheduleRepository.save(courtSchedule);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule> courtScheduleList = courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertTrue(courtScheduleList.isEmpty());
    }


    @Test
    public void shouldSaveSlotsFoSPI() {
        final Date sessionDate = DateUtils.getDate(LocalDate.of(2024, 7, 15));
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(LocalDate.of(2024, 7, 15));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomId("1234");
        courtScheduleRepository.save(courtSchedule);


        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        provisionalBookingRepository.save(provisionalBooking);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setOucode("B01LY00");
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setHearingStartTime(sessionDate);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing);

        AllocatedSlot allocatedSlot1 = getAllocatedSlotForSPI(allocatedListing);
        List<AllocatedSlot> slots = Lists.newArrayList(allocatedSlot1);
        boolean isProvisionalSlot = true;

        final Result result = courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot, false);

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedules.isEmpty());
//        checkSlotUpdateResult(result);
    }

    private static void checkSlotUpdateResult(final Result result) {
        assertThat(result, is(notNullValue()));
        assertThat(result.isSuccess(), is(true));
        assertThat(result.getMsg(), is("Success"));
        assertThat(result.getHearingDayCourtSchedules().size(), is(1));
        assertThat(result.getHearingDayCourtSchedules().get("2024-07-15"), is(notNullValue()));
    }

    private static AllocatedSlot getAllocatedSlot(AllocatedListing allocatedListing) {
        AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setHearingId(allocatedListing.getHearingId());
        allocatedSlot.setCourtScheduleId(allocatedListing.getCourtScheduleId());
        allocatedSlot.setBookingId(allocatedListing.getBookingId());
        allocatedSlot.setCourtRoomId(allocatedListing.getCourtRoomId().toString());
        allocatedSlot.setHearingStartTime(SIMPLE_DATE_FORMAT.format(allocatedListing.getHearingStartTime()));
        allocatedSlot.setSessionDate(LocalDate.of(2024, 7, 15).toString());
        return allocatedSlot;
    }

    private static AllocatedSlot getAllocatedSlotForSPI(AllocatedListing allocatedListing) {
        AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setHearingId(allocatedListing.getHearingId());
        allocatedSlot.setCourtScheduleId(null);
        allocatedSlot.setBookingId(allocatedListing.getBookingId());
        allocatedSlot.setOuCode(allocatedListing.getOucode());
        allocatedSlot.setCourtRoomId(allocatedListing.getCourtRoomId().toString());
        allocatedSlot.setSessionDate(LocalDate.of(2024, 7, 15).toString());
        allocatedSlot.setHearingStartTime(SIMPLE_DATE_FORMAT.format(allocatedListing.getHearingStartTime()));
        return allocatedSlot;
    }

    @Test
    public void shouldSave() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);

        courtScheduleRepository.save(courtSchedule);
        CourtSchedule by = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());

        assertNotNull(by);

    }

    @Test
    public void shouldReleaseAllocatedSlotsFromCourtSchedule() {

        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        String courtScheduleId = courtSchedule.getCourtScheduleId();
        courtSchedule.setAvailableSlots(100);
        courtSchedule.setSlotBased(true);
        Integer currentAvailableSlots = courtSchedule.getAvailableSlots();
        courtScheduleRepository.save(courtSchedule);

        String hearingId = random(String.class);
        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListing.setDuration(1); //slotbased
        allocatedListingRepository.save(allocatedListing);


        List<AllocatedListing> allocatedListings = Lists.newArrayList(allocatedListing);
        courtScheduleRepository.releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);

        CourtSchedule courtSchedule1 = courtScheduleRepository.findBy(courtScheduleId);
        assertEquals((Integer)(currentAvailableSlots + 1), courtSchedule1.getAvailableSlots());
    }

    @Test
    public void shouldReleaseDurationFromCourtSchedule() {

        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        String courtScheduleId = courtSchedule.getCourtScheduleId();
        courtSchedule.setSlotBased(false);
        Integer availableDuration = courtSchedule.getAvailableDuration();
        courtScheduleRepository.save(courtSchedule);

        String hearingId = random(String.class);
        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);


        List<AllocatedListing> allocatedListings = Lists.newArrayList(allocatedListing);
        courtScheduleRepository.releaseAllocatedSlotsOrDurationFromCourtSchedule(allocatedListings);

        CourtSchedule courtSchedule1 = courtScheduleRepository.findBy(courtScheduleId);
        assertEquals((Integer)(availableDuration + allocatedListing.getDuration()), courtSchedule1.getAvailableDuration());
    }

    @Test
    public void shouldReleaseCourtScheduleAllocatedSlotsForBookingId() {
        // given
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtScheduleRepository.save(courtSchedule);
        String bookingId1 = random(String.class);
        final ProvisionalBookingKey provisionalBookingKey1 = new ProvisionalBookingKey(courtSchedule, bookingId1);
        final ProvisionalBooking provisionalBooking1 = random(ProvisionalBooking.class);
        provisionalBooking1.setProvisionalBookingKey(provisionalBookingKey1);
        provisionalBooking1.setActive(false);


        provisionalBookingRepository.save(provisionalBooking1);

        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setBookingId(bookingId1);

        courtScheduleRepository.releaseCourtScheduleAllocatedSlotsForBookingId(Lists.newArrayList(allocatedListing));

        Optional<ProvisionalBooking> byBookingId = provisionalBookingRepository.findByBookingId(bookingId1);

        assertTrue(byBookingId.isPresent());
        assertTrue(byBookingId.get().getActive());
    }

    @Test
    public void shouldReleaseOldListingsFromAllocatedListings() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        String courtScheduleId = courtSchedule.getCourtScheduleId();
        String hearingId = random(String.class);
        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);
        allocatedListingRepository.save(allocatedListing);


        courtScheduleRepository.releaseOldListingsFromAllocatedListings(hearingId);

        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(hearingId);

        assertTrue(allocatedListings.isEmpty());
    }

    @Test
    public void shouldUpdateCourtScheduleWithAvailableSlots() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSlotBased(true);
        Integer availableSlots = courtSchedule.getAvailableSlots();
        courtScheduleRepository.save(courtSchedule);
        final AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleRepository.updateCourtSchedule(Lists.newArrayList(allocatedSlot));

        assertEquals((Integer)(availableSlots - 1), courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId()).getAvailableSlots());
    }

    @Test
    public void shouldUpdateCourtScheduleWithAvailableDuration() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        Integer availableDuration = courtSchedule.getAvailableDuration();
        courtSchedule.setSlotBased(false);
        courtScheduleRepository.save(courtSchedule);
        final AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleRepository.updateCourtSchedule(Lists.newArrayList(allocatedSlot));

        final Integer expectedDuration = availableDuration - allocatedSlot.getDuration();
        assertEquals(expectedDuration, courtSchedule.getAvailableDuration());
    }

    @Test
    public void shouldSaveAllocatedListing() {
        AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setCourtRoomId(random(Integer.class).toString());
        allocatedSlot.setHearingStartTime(DateUtils.toIsoString(OffsetDateTime.now()));
        courtScheduleRepository.saveAllocatedListing(Lists.newArrayList(allocatedSlot));

        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(allocatedSlot.getHearingId());

        assertFalse(allocatedListings.isEmpty());
    }

    @Test
    public void shouldDeleteProvisionalBooking() {
        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        courtScheduleRepository.save(provisionalBooking.getProvisionalBookingKey().getCourtSchedule());
        provisionalBooking.setActive(true);
        provisionalBookingRepository.save(provisionalBooking);

        courtScheduleRepository.deleteProvisionalBooking(provisionalBooking.getProvisionalBookingKey().getBookingId());

        Optional<ProvisionalBooking> byBookingId = provisionalBookingRepository.findByBookingId(provisionalBooking.getProvisionalBookingKey().getBookingId());
        assertTrue(byBookingId.isPresent());
        assertFalse(byBookingId.get().getActive());
    }

    @Test
    @Ignore
    public void shouldGetCourtSchedules() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setPanel("ADULT");
        courtSchedule.setSessionDate(LocalDate.now());
        courtSchedule.setOperationalUnit("BA124");
        courtSchedule.setOuCode("BA124");
        courtSchedule.setCourtSession("AM");
        courtScheduleRepository.saveAndFlush(courtSchedule);
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryKey = random(CourtScheduleJudiciaryKey.class);
        courtScheduleJudiciaryKey.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciaryKey.setJudiciaryId(courtSchedule.getCourtScheduleId());
        courtScheduleJudiciary.setId(courtScheduleJudiciaryKey);
        courtScheduleJudiciary.setCourtListingProfileId(courtScheduleJudiciary.getCourtListingProfileId());
        courtScheduleJudiciaryRepository.saveAndFlush(courtScheduleJudiciary);
        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.saveAndFlush(allocatedListing);
        HearingSlotRequestParam hearingSlotRequestParam = createHearingSlotRequest(courtSchedule);

        Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>> response = courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam);

        assertNotNull(response);
    }

    @Test
    public void shouldDeleteCourtSchedule() {
        String courtScheduleId1 = random(String.class);
        String courtScheduleId2 = random(String.class);
        List<String> courtScheduleIdList = List.of(courtScheduleId1, courtScheduleId2);
        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId(courtScheduleId1);
        courtSchedule1.setPanel("ADULT");
        courtSchedule1.setSessionDate(LocalDate.now());
        courtSchedule1.setOperationalUnit("BA124");
        courtSchedule1.setOuCode("BA124");
        courtSchedule1.setSessionDate(LocalDate.now());
        courtScheduleRepository.saveAndFlush(courtSchedule1);
        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setPanel("ADULT");
        courtSchedule2.setSessionDate(LocalDate.now());
        courtSchedule2.setOperationalUnit("BA124");
        courtSchedule2.setOuCode("BA124");
        courtSchedule2.setSessionDate(LocalDate.now());
        courtScheduleRepository.saveAndFlush(courtSchedule2);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(courtScheduleIdList);

        assertNull(courtScheduleRepository.findBy(courtScheduleId1));
        assertNull(courtScheduleRepository.findBy(courtScheduleId2));
        assertEquals(true, courtSchedules.isEmpty());
    }

    @Test
    public void shouldUpdateMultipleSessions_OnMaxSlotsValue_GreaterThanZero() {
        String courtHouseId = random(String.class);
        String courtRoomId = random(String.class);
        String businessType = random(String.class);
        String panel = random(String.class);
        String courtSession = random(String.class);
        LocalDate date = LocalDate.now();

        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtHouseId(courtHouseId);
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setBusinessType(businessType);
        courtSchedule.setPanel(panel);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setSessionDate(date);
        courtSchedule.setMaxSlots(2);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableSlots(4);
        courtSchedule.setAvailableDuration(5);
        courtSchedule.setSupportAdSplit(false);

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(date);
        courtSchedule1.setMaxSlots(6);
        courtSchedule1.setMaxDuration(7);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(9);
        courtSchedule1.setSupportAdSplit(false);

        courtScheduleRepository.save(courtSchedule);
        CourtSchedule by = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(2, by.getMaxSlots().intValue());

        courtScheduleRepository.update(courtSchedule1, false);
        CourtSchedule by1 = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(6, by1.getMaxSlots().intValue());
        assertEquals(7, by1.getMaxDuration().intValue());
        assertEquals(8, by1.getAvailableSlots().intValue());
        assertEquals(9, by1.getAvailableDuration().intValue());
    }

    @Test
    public void shouldNotUpdateFieldsWhenDifferentAdSplits() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = new CourtSchedule();
        courtSchedule1.setCourtScheduleId(COURT_SCHEDULE_ID);
        courtSchedule1.setCourtHouseName("Lavender Hill Magistrates' Court");
        courtSchedule1.setCourtRoomName("Courtroom 1");
        courtSchedule1.setListingProfileId("CS4436822");
        courtSchedule1.setOperationalUnit("6");
        courtSchedule1.setCourtRoomNumber(1234);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setActive(true);
        courtSchedule1.setSlotBased(false);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setMaxAdMorningDuration(240);
        courtSchedule1.setMaxAdAfternoonDuration(120);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setIsOverbookingAllowed(true);
        courtSchedule1.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));
        courtSchedule1.setIsDraft(false);
        courtScheduleRepository.save(courtSchedule1);

        CourtSchedule updateRequest = new CourtSchedule();
        updateRequest.setCourtScheduleId(COURT_SCHEDULE_ID);
        updateRequest.setMaxSlots(12);
        updateRequest.setMaxDuration(100);
        updateRequest.setAvailableSlots(10);
        updateRequest.setAvailableDuration(100);
        updateRequest.setMaxAdMorningDuration(200);
        updateRequest.setMaxAdAfternoonDuration(100);
        updateRequest.setSupportAdSplit(false);

        courtScheduleRepository.update(updateRequest, false);

        CourtSchedule updatedCourtSchedule = courtScheduleRepository.findBy(COURT_SCHEDULE_ID);
        assertNotNull(updatedCourtSchedule);
        assertEquals(10, updatedCourtSchedule.getMaxSlots().intValue());
        assertEquals(0, updatedCourtSchedule.getMaxDuration().intValue());
        assertEquals(8, updatedCourtSchedule.getAvailableSlots().intValue());
        assertEquals(0, updatedCourtSchedule.getAvailableDuration().intValue());
        assertEquals(240, updatedCourtSchedule.getMaxAdMorningDuration().intValue());
        assertEquals(120, updatedCourtSchedule.getMaxAdAfternoonDuration().intValue());
        assertTrue(updatedCourtSchedule.getSupportAdSplit());
    }

    @Test
    public void shouldUpdateMaxDurationForMorningAndAfternoonFieldsWhenAdSplitsTrue() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = new CourtSchedule();
        courtSchedule1.setCourtScheduleId(COURT_SCHEDULE_ID);
        courtSchedule1.setCourtHouseName("Lavender Hill Magistrates' Court");
        courtSchedule1.setCourtRoomName("Courtroom 1");
        courtSchedule1.setListingProfileId("CS4436822");
        courtSchedule1.setOperationalUnit("6");
        courtSchedule1.setCourtRoomNumber(1234);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableSlots(0);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setActive(true);
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setMaxAdMorningDuration(240);
        courtSchedule1.setMaxAdAfternoonDuration(120);
        courtSchedule1.setSupportAdSplit(false);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setIsOverbookingAllowed(true);
        courtSchedule1.setNationalBreakTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule1.setIsDraft(false);
        courtScheduleRepository.save(courtSchedule1);

        CourtSchedule updateRequest = new CourtSchedule();
        updateRequest.setCourtScheduleId(COURT_SCHEDULE_ID);
        updateRequest.setMaxSlots(0);
        updateRequest.setMaxDuration(100);
        updateRequest.setAvailableSlots(10);
        updateRequest.setAvailableDuration(100);
        updateRequest.setMaxAdMorningDuration(200);
        updateRequest.setMaxAdAfternoonDuration(100);
        updateRequest.setSupportAdSplit(false);

        courtScheduleRepository.update(updateRequest, false);

        CourtSchedule updatedCourtSchedule = courtScheduleRepository.findBy(COURT_SCHEDULE_ID);
        assertNotNull(updatedCourtSchedule);
        assertEquals(0, updatedCourtSchedule.getMaxSlots().intValue());
        assertEquals(100, updatedCourtSchedule.getMaxDuration().intValue());
        assertEquals(10, updatedCourtSchedule.getAvailableSlots().intValue());
        assertEquals(100, updatedCourtSchedule.getAvailableDuration().intValue());
        assertEquals(240, updatedCourtSchedule.getMaxAdMorningDuration().intValue());
        assertEquals(120, updatedCourtSchedule.getMaxAdAfternoonDuration().intValue());
        assertFalse(updatedCourtSchedule.getSupportAdSplit());
    }

    @Test
    public void shouldUpdateMaxDurationFieldWhenAdSplitsFalse() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = new CourtSchedule();
        courtSchedule1.setCourtScheduleId(COURT_SCHEDULE_ID);
        courtSchedule1.setCourtHouseName("Lavender Hill Magistrates' Court");
        courtSchedule1.setCourtRoomName("Courtroom 1");
        courtSchedule1.setListingProfileId("CS4436822");
        courtSchedule1.setOperationalUnit("6");
        courtSchedule1.setCourtRoomNumber(1234);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setActive(true);
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setMaxAdMorningDuration(240);
        courtSchedule1.setMaxAdAfternoonDuration(120);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setIsOverbookingAllowed(true);
        courtSchedule1.setNationalBreakTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule1.setIsDraft(false);
        courtScheduleRepository.save(courtSchedule1);

        CourtSchedule updateRequest = new CourtSchedule();
        updateRequest.setCourtScheduleId(COURT_SCHEDULE_ID);
        updateRequest.setMaxSlots(12);
        updateRequest.setMaxDuration(100);
        updateRequest.setAvailableSlots(10);
        updateRequest.setAvailableDuration(100);
        updateRequest.setMaxAdMorningDuration(200);
        updateRequest.setMaxAdAfternoonDuration(100);
        updateRequest.setSupportAdSplit(true);

        courtScheduleRepository.update(updateRequest, false);

        CourtSchedule updatedCourtSchedule = courtScheduleRepository.findBy(COURT_SCHEDULE_ID);
        assertNotNull(updatedCourtSchedule);
        assertEquals(12, updatedCourtSchedule.getMaxSlots().intValue());
        assertEquals(0, updatedCourtSchedule.getMaxDuration().intValue());
        assertEquals(10, updatedCourtSchedule.getAvailableSlots().intValue());
        assertEquals(100, updatedCourtSchedule.getAvailableDuration().intValue());
        assertEquals(200, updatedCourtSchedule.getMaxAdMorningDuration().intValue());
        assertEquals(100, updatedCourtSchedule.getMaxAdAfternoonDuration().intValue());
        assertTrue(updatedCourtSchedule.getSupportAdSplit());
    }

    @Test
    public void shouldUpdateMultipleSessions_OnMaxDurationValue_GreaterThanZero() {
        String courtHouseId = random(String.class);
        String courtRoomId = random(String.class);
        String businessType = random(String.class);
        String panel = random(String.class);
        String courtSession = random(String.class);
        LocalDate date = LocalDate.now();

        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtHouseId(courtHouseId);
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setBusinessType(businessType);
        courtSchedule.setPanel(panel);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setSessionDate(date);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setMaxDuration(3);
        courtSchedule.setAvailableSlots(4);
        courtSchedule.setAvailableDuration(5);
        courtSchedule.setSupportAdSplit(false);

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(date);
        courtSchedule1.setMaxSlots(6);
        courtSchedule1.setMaxDuration(7);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(9);
        courtSchedule1.setSupportAdSplit(false);

        courtScheduleRepository.save(courtSchedule);
        CourtSchedule by = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(3, by.getMaxDuration().intValue());

        courtScheduleRepository.update(courtSchedule1, false);
        CourtSchedule by1 = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(6, by1.getMaxSlots().intValue());
        assertEquals(7, by1.getMaxDuration().intValue());
        assertEquals(8, by1.getAvailableSlots().intValue());
        assertEquals(9, by1.getAvailableDuration().intValue());
    }

    @Test
    public void shouldUpdateMultipleSessions_OnUpdateUI_GreaterThanPersisted() {
        String courtHouseId = random(String.class);
        String courtRoomId = random(String.class);
        String businessType = random(String.class);
        String panel = random(String.class);
        String courtSession = random(String.class);
        LocalDate date = LocalDate.now();

        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setCourtHouseId(courtHouseId);
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setBusinessType(businessType);
        courtSchedule.setPanel(panel);
        courtSchedule.setCourtSession(courtSession);
        courtSchedule.setSessionDate(date);
        courtSchedule.setMaxSlots(0);
        courtSchedule.setMaxDuration(0);
        courtSchedule.setAvailableSlots(4);
        courtSchedule.setAvailableDuration(5);
        courtSchedule.setSupportAdSplit(false);

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(date);
        courtSchedule1.setMaxSlots(6);
        courtSchedule1.setMaxDuration(7);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(9);
        courtSchedule1.setSupportAdSplit(false);

        courtScheduleRepository.save(courtSchedule);
        CourtSchedule by = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(0, by.getMaxDuration().intValue());

        courtScheduleRepository.update(courtSchedule1, false);
        CourtSchedule by1 = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(6, by1.getMaxSlots().intValue());
        assertEquals(7, by1.getMaxDuration().intValue());
        assertEquals(8, by1.getAvailableSlots().intValue());
        assertEquals(9, by1.getAvailableDuration().intValue());
    }

    @Test
    public void shouldGetExtractedCourtSchedules() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 4,15);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(6);
        courtSchedule1.setMaxDuration(7);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(9);
        courtSchedule1.setActive(true);
        courtSchedule1.setOuCode(ouCode);

        courtScheduleRepository.save(courtSchedule1);

        final LocalDate startDate = LocalDate.of(2024, 4, 1);
        final LocalDate endDate = LocalDate.of(2024, 9, 16);
        final List<CourtSchedule> extractedCourtSchedules = courtScheduleRepository.getExtractedCourtSchedules(List.of(ouCode), startDate, endDate);

        assertEquals(1, extractedCourtSchedules.size());
        final CourtSchedule actualCourtSchedule = extractedCourtSchedules.get(0);
        assertEquals(courtHouseId, actualCourtSchedule.getCourtHouseId());
        assertEquals(courtRoomId, actualCourtSchedule.getCourtRoomId());
        assertEquals(businessType, actualCourtSchedule.getBusinessType());
        assertEquals(panel, actualCourtSchedule.getPanel());
        assertEquals(courtSession, actualCourtSchedule.getCourtSession());
        assertEquals(sessionDate, actualCourtSchedule.getSessionDate());
        assertEquals(ouCode, actualCourtSchedule.getOuCode());
    }

    @Test
    public void shouldGetExtractedCourtSchedulesForGhostRota() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(6);
        courtSchedule1.setMaxDuration(7);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(9);
        courtSchedule1.setOuCode(ouCode);

        courtScheduleRepository.save(courtSchedule1);

        final LocalDate startDate = LocalDate.of(2024, 9, 17);
        final LocalDate endDate = LocalDate.of(2025, 9, 30);
        final List<CourtSchedule> extractedCourtSchedulesForGhostRota = courtScheduleRepository.getExtractedCourtSchedulesForGhostRota(List.of(ouCode), startDate, endDate);

        assertEquals(1, extractedCourtSchedulesForGhostRota.size());
        final CourtSchedule actualCourtSchedule = extractedCourtSchedulesForGhostRota.get(0);
        assertEquals(courtHouseId, actualCourtSchedule.getCourtHouseId());
        assertEquals(courtRoomId, actualCourtSchedule.getCourtRoomId());
        assertEquals(businessType, actualCourtSchedule.getBusinessType());
        assertEquals(panel, actualCourtSchedule.getPanel());
        assertEquals(courtSession, actualCourtSchedule.getCourtSession());
        assertEquals(sessionDate, actualCourtSchedule.getSessionDate());
        assertEquals(ouCode, actualCourtSchedule.getOuCode());
    }

    @Test
    public void shouldDeactivateSlots() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 = new CourtSchedule();
        courtSchedule1.setCourtScheduleId(COURT_SCHEDULE_ID);
        courtSchedule1.setCourtHouseName("Lavender Hill Magistrates' Court");
        courtSchedule1.setCourtRoomName("Courtroom 1");
        courtSchedule1.setListingProfileId("CS4436822");
        courtSchedule1.setOperationalUnit("6");
        courtSchedule1.setCourtRoomNumber(1234);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setActive(true);
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setMaxAdMorningDuration(240);
        courtSchedule1.setMaxAdAfternoonDuration(120);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setIsOverbookingAllowed(true);
        courtSchedule1.setIsDraft(false);
        courtSchedule1.setNationalBreakTime(convertToDate(LocalTime.of(12, 0)));

        courtScheduleRepository.save(courtSchedule1);

        final CourtSchedule courtScheduleInserted = courtScheduleRepository.findBy(COURT_SCHEDULE_ID);

        assertEquals(courtHouseId, courtScheduleInserted.getCourtHouseId());
        assertEquals(courtRoomId, courtScheduleInserted.getCourtRoomId());
        assertEquals(businessType, courtScheduleInserted.getBusinessType());
        assertEquals(panel, courtScheduleInserted.getPanel());
        assertEquals(courtSession, courtScheduleInserted.getCourtSession());
        assertEquals(sessionDate, courtScheduleInserted.getSessionDate());
        assertEquals(ouCode, courtScheduleInserted.getOuCode());
        assertTrue(courtScheduleInserted.isActive());

        final Date updatedOn = Calendar.getInstance().getTime();
        courtScheduleRepository.deactivateSlots(List.of(COURT_SCHEDULE_ID), updatedOn);

        final CourtSchedule courtScheduleAfterDeactivation = courtScheduleRepository.findBy(courtScheduleInserted.getCourtScheduleId());

        courtScheduleRepository.refresh(courtScheduleAfterDeactivation);

        assertEquals(courtHouseId, courtScheduleAfterDeactivation.getCourtHouseId());
        assertEquals(courtRoomId, courtScheduleAfterDeactivation.getCourtRoomId());
        assertEquals(businessType, courtScheduleAfterDeactivation.getBusinessType());
        assertEquals(panel, courtScheduleAfterDeactivation.getPanel());
        assertEquals(courtSession, courtScheduleAfterDeactivation.getCourtSession());
        assertEquals(sessionDate, courtScheduleAfterDeactivation.getSessionDate());
        assertEquals(ouCode, courtScheduleAfterDeactivation.getOuCode());
        assertFalse(courtScheduleAfterDeactivation.isActive());
        assertEquals(updatedOn, courtScheduleAfterDeactivation.getUpdatedOn());
    }

    private Date convertToDate(LocalTime localTime) {
        return Date.from(localTime.atDate(LocalDate.of(1970, 1, 1))
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }

    private Date convertToDate(LocalTime localTime, int year, int month, int day) {
        return Date.from(localTime.atDate(LocalDate.of(year, month, day))
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }

    @Test
    public void shouldFindByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession() {
        final String courtHouseId = randomUUID().toString();
        final String courtRoomId = randomUUID().toString();
        final String businessType = "TRF";
        final String panel = "ADULT";
        final String courtSession = "PM";
        final LocalDate sessionDate = LocalDate.of(2024, 9,30);
        final String ouCode = "B01LY00";

        final CourtSchedule courtSchedule1 =   new CourtSchedule();
        courtSchedule1.setCourtScheduleId(COURT_SCHEDULE_ID);
        courtSchedule1.setCourtHouseName("Lavender Hill Magistrates' Court");
        courtSchedule1.setCourtRoomName("Courtroom 1");
        courtSchedule1.setListingProfileId("CS4436822");
        courtSchedule1.setOperationalUnit("6");
        courtSchedule1.setCourtRoomNumber(1234);
        courtSchedule1.setCourtHouseId(courtHouseId);
        courtSchedule1.setCourtRoomId(courtRoomId);
        courtSchedule1.setBusinessType(businessType);
        courtSchedule1.setPanel(panel);
        courtSchedule1.setCourtSession(courtSession);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableSlots(8);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setActive(true);
        courtSchedule1.setSlotBased(true);
        courtSchedule1.setOuCode(ouCode);
        courtSchedule1.setMaxAdMorningDuration(240);
        courtSchedule1.setMaxAdAfternoonDuration(120);
        courtSchedule1.setSupportAdSplit(true);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setIsOverbookingAllowed(true);
        courtSchedule1.setNationalBreakTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule1.setIsDraft(false);

        courtScheduleRepository.save(courtSchedule1);

        final CourtScheduleMatcherInfo courtScheduleMatcherInfo = courtScheduleRepository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);

        assertNotNull(courtScheduleMatcherInfo);
        assertEquals(courtScheduleMatcherInfo.getCourtScheduleId(), courtSchedule1.getCourtScheduleId());
    }

    @Test
    public void shouldSearchAndList_ForAllRequiredParams() {
        final Date sessionDate = DateUtils.getDate(LocalDate.of(2024, 7, 15));
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(LocalDate.of(2024, 7, 15));
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("NGAP");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomId("1234");
        courtScheduleRepository.save(courtSchedule);


        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        provisionalBookingRepository.save(provisionalBooking);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setOucode("B01LY00");
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setHearingStartTime(sessionDate);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing);

        courtScheduleRepository.searchListHearingSlotFilterCriteria(courtSchedule.getOuCode(), courtSchedule.getSessionDate(), courtSchedule.getSessionDate().plusDays(5),
                courtSchedule.getSessionStartTime().toInstant().atOffset(ZoneOffset.UTC)
                .toLocalDateTime(), courtSchedule.getCourtRoomId(), true);

        List<CourtSchedule> courtSchedulesQueryList = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedulesQueryList.isEmpty());
    }

    @Test
    public void shouldSearchAndList_ChooseCorrectBusinessType_ForPolice() {
        final Date sessionDate = DateUtils.getDate(LocalDate.of(2024, 7, 15));
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        String courtCentreId = randomUUID().toString();
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(LocalDate.of(2025, 4, 15));
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("NGAP");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtHouseId(courtCentreId);
        courtSchedule.setCourtRoomId("1234");
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setSessionDate(LocalDate.of(2025, 4, 15));
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule1.setCourtSession("AD");
        courtSchedule1.setBusinessType("DAFL");
        courtSchedule1.setOuCode("B01LY00");
        courtSchedule1.setCourtHouseId(courtCentreId);
        courtSchedule1.setCourtRoomId("1234");
        courtSchedule1.setActive(true);
        courtScheduleRepository.save(courtSchedule1);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setOucode("B01LY00");
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setHearingStartTime(sessionDate);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing);

        AllocatedListing allocatedListing1 = random(AllocatedListing.class);
        allocatedListing1.setOucode("B01LY00");
        allocatedListing1.setCourtRoomId(courtSchedule1.getCourtRoomNumber());
        allocatedListing1.setHearingStartTime(sessionDate);
        allocatedListing1.setHearingId(hearingId);
        allocatedListing1.setBookingId(bookingId);
        allocatedListing1.setCourtScheduleId(courtSchedule1.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing1);

        CourtSchedule slotFound = courtScheduleRepository.searchListHearingSlotFilterCriteria(courtSchedule.getCourtHouseId(), courtSchedule.getSessionDate(), courtSchedule.getSessionDate().plusDays(5),
                courtSchedule.getSessionStartTime().toInstant().atOffset(ZoneOffset.UTC)
                        .toLocalDateTime(), courtSchedule.getCourtRoomId(), true);

        assertNotNull(slotFound);
        assertEquals("DAFL", slotFound.getBusinessType());
    }

    @Test
    public void shouldSearchAndListNonSpi_ForAllRequiredParams() {
        final Date sessionDate = DateUtils.getDate(LocalDate.of(2024, 7, 15));
        String hearingId = randomUUID().toString();
        String bookingId = randomUUID().toString();
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(LocalDate.of(2024, 7, 15));
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("NGAP");
        courtSchedule.setOuCode("B01LY00");
        courtSchedule.setCourtRoomId("1234");
        courtScheduleRepository.save(courtSchedule);


        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        provisionalBookingRepository.save(provisionalBooking);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setOucode("B01LY00");
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setHearingStartTime(sessionDate);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing);

        courtScheduleRepository.searchListHearingSlotFilterCriteria(courtSchedule.getOuCode(), courtSchedule.getSessionDate(), courtSchedule.getSessionDate().plusDays(5),
                courtSchedule.getSessionStartTime().toInstant().atOffset(ZoneOffset.UTC)
                        .toLocalDateTime(), courtSchedule.getCourtRoomId(), false);

        List<CourtSchedule> courtSchedulesQueryList = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedulesQueryList.isEmpty());
    }

    // Tests for getCourtSchedulesForPolice method (via searchListHearingSlotFilterCriteria with isPolice=true)
    @Test
    public void shouldFindCourtScheduleForPolice_WithAllParameters() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
    }

    @Test
    public void shouldFindCourtScheduleForPolice_OnNextDayWhenNoResultsOnFirstDay() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate firstDay = LocalDate.of(2024, 7, 15);
        LocalDate secondDay = LocalDate.of(2024, 7, 16);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // No court schedule on first day
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(secondDay); // Schedule on second day
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("GAP"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, firstDay, secondDay, sessionStartTime, courtRoomId, true);

        // then
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("GAP", result.getBusinessType());
        assertEquals(secondDay, result.getSessionDate());
    }

    @Test
    public void shouldReturnNullForPolice_WhenNoCourtScheduleFound() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // No court schedule exists

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNull(result);
    }

    @Test
    public void shouldReturnNullForPolice_WhenCourtScheduleIsInactive() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("REM"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(false); // Inactive schedule
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNull(result);
    }

    @Test
    public void shouldReturnNullForPolice_WhenBusinessTypeIsNotPolice() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("NCFL"); // Non-police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNull(result);
    }

    @Test
    public void shouldFindCourtScheduleForPolice_WithSessionStartTimeWithinRange() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 15, 0); // Within session time
        String courtRoomId = "1234";

        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0))); // Session 14:00-16:00
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("AD");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
    }

    @Test
    public void shouldFindCourtScheduleForPolice_WithAllPoliceBusinessTypes() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        String[] policeBusinessTypes = {"YFL", "TRFL", "DAFL", "NGAP", "GAP", "REM"};

        for (String businessType : policeBusinessTypes) {
            CourtSchedule courtSchedule = random(CourtSchedule.class);
            courtSchedule.setSessionDate(sessionDate);
            courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
            courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
            courtSchedule.setCourtSession("AD");
            courtSchedule.setBusinessType(businessType);
            courtSchedule.setOuCode(courtCentreId);
            courtSchedule.setCourtHouseId(courtCentreId); // Set court house ID to match the query filter
            courtSchedule.setCourtRoomId(courtRoomId);
            courtSchedule.setActive(true);
            courtScheduleRepository.save(courtSchedule);
        }

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then
        assertNotNull(result);
        assertTrue(List.of(policeBusinessTypes).contains(result.getBusinessType()));
    }

    @Test
    public void shouldFindClosestAMCourtScheduleForPolice_WithSameBusinessType_WhenSessionStartTimeIsNull() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 8, 0); // Requested time
        String courtRoomId = "1234";

        // Create multiple court schedules with different start times and business types
        CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtScheduleId("5771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule1.setSessionDate(sessionDate);
        schedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15)); // 14:00 - closest to 15:30
        schedule1.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        schedule1.setCourtSession("PM");
        schedule1.setBusinessType("TRFL"); // Same business type as schedule3
        schedule1.setOuCode(courtCentreId);
        schedule1.setCourtHouseId(courtCentreId);
        schedule1.setCourtRoomId(courtRoomId);
        schedule1.setActive(true);
        courtScheduleRepository.save(schedule1);

        CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtScheduleId("6771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule2.setSessionDate(sessionDate);
        schedule2.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15)); // 10:00 - further from 15:30
        schedule2.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        schedule2.setCourtSession("AM");
        schedule2.setBusinessType("TRFL"); // Different business type
        schedule2.setOuCode(courtCentreId);
        schedule2.setCourtHouseId(courtCentreId);
        schedule2.setCourtRoomId(courtRoomId);
        schedule2.setActive(true);
        courtScheduleRepository.save(schedule2);

        CourtSchedule schedule3 = random(CourtSchedule.class);
        schedule3.setCourtScheduleId("7771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule3.setSessionDate(sessionDate);
        schedule3.setSessionStartTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15)); // 16:00 - closer to 15:30 than schedule1
        schedule3.setSessionEndTime(convertToDate(LocalTime.of(18, 0), 2024, 7, 15));
        schedule3.setCourtSession("PM");
        schedule3.setBusinessType("GAP"); // Same business type as schedule1
        schedule3.setOuCode(courtCentreId);
        schedule3.setCourtHouseId(courtCentreId);
        schedule3.setCourtRoomId(courtRoomId);
        schedule3.setActive(true);
        courtScheduleRepository.save(schedule3);

        // when - First call with exact time should fail, then fallback to null sessionStartTime
        // and find closest schedule within same business type
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should return the closest schedule with same business type (schedule2 at 10:00, TRFL)
        // even though schedule1 is also TRFL, schedule2 should be selected because it's closer
        // and has the same business type as the first result
        assertNotNull(result);
        assertEquals("TRFL", result.getBusinessType());
        // The result should be either schedule1 or schedule2 (both YFL), but schedule2 should be preferred
        // as it's closer to the requested time (08:00)
        assertEquals(result.getCourtScheduleId(), schedule2.getCourtScheduleId());
    }

    @Test
    public void shouldFindClosestPMCourtScheduleForPolice_WithSameBusinessType_WhenSessionStartTimeIsNull() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 18, 0); // Requested time
        String courtRoomId = "1234";

        // Create multiple court schedules with different start times and business types
        CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtScheduleId("5771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule1.setSessionDate(sessionDate);
        schedule1.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15)); // 14:00 - closest to 15:30
        schedule1.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        schedule1.setCourtSession("PM");
        schedule1.setBusinessType("DAFL"); // Same business type as schedule3
        schedule1.setOuCode(courtCentreId);
        schedule1.setCourtHouseId(courtCentreId);
        schedule1.setCourtRoomId(courtRoomId);
        schedule1.setActive(true);
        courtScheduleRepository.save(schedule1);

        CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtScheduleId("6771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule2.setSessionDate(sessionDate);
        schedule2.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15)); // 10:00 - further from 15:30
        schedule2.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        schedule2.setCourtSession("AM");
        schedule2.setBusinessType("DAFL"); // Different business type
        schedule2.setOuCode(courtCentreId);
        schedule2.setCourtHouseId(courtCentreId);
        schedule2.setCourtRoomId(courtRoomId);
        schedule2.setActive(true);
        courtScheduleRepository.save(schedule2);

        CourtSchedule schedule3 = random(CourtSchedule.class);
        schedule3.setCourtScheduleId("7771a96b-1c5a-45d1-b647-1bec5212cafc");
        schedule3.setSessionDate(sessionDate);
        schedule3.setSessionStartTime(convertToDate(LocalTime.of(15, 0), 2024, 7, 15)); // 16:00 - closer to 15:30 than schedule1
        schedule3.setSessionEndTime(convertToDate(LocalTime.of(17, 0), 2024, 7, 15));
        schedule3.setCourtSession("PM");
        schedule3.setBusinessType("NGAP"); // Same business type as schedule1
        schedule3.setOuCode(courtCentreId);
        schedule3.setCourtHouseId(courtCentreId);
        schedule3.setCourtRoomId(courtRoomId);
        schedule3.setActive(true);
        courtScheduleRepository.save(schedule3);

        // when - First call with exact time should fail, then fallback to null sessionStartTime
        // and find closest schedule within same business type
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should return the closest schedule with same business type (schedule1 at 14:00, DAFL)
        // even though schedule1 is also DAFL, schedule2 should be selected because it's closer
        // and has the same business type as the first result
        assertNotNull(result);
        assertEquals("DAFL", result.getBusinessType());
        // The result should be either schedule1 or schedule2 (both DAFL), but schedule1 should be preferred
        // as it's closer to the requested time (18:00)
        assertEquals(result.getCourtScheduleId(), schedule1.getCourtScheduleId());
    }

    private HearingSlotRequestParam createHearingSlotRequest(CourtSchedule courtSchedule) {
        LocalDate startDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate endDate = courtSchedule.getSessionDate().plusDays(1);
        return new HearingSlotRequestParam(
                courtSchedule.getPanel(),
                startDate.toString(),
                endDate.toString(),
                null,
                courtSchedule.getOperationalUnit(),
                courtSchedule.getOuCode(),
                "1",
                "1",
                courtSchedule.getCourtRoomId(),
                courtSchedule.getCourtRoomNumber().toString(),
                courtSchedule.getBusinessType(),
                courtSchedule.getCourtSession(),
                courtSchedule.isSlotBased(),
                null);
    }

    // ========== Additional Unit Tests for findClosestCourtScheduleByTimeAndBusinessType ==========

    @Test
    public void shouldReturnNullWhenCourtSchedulesIsNull() {
        // given
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 10, 0);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                "B01LY00", LocalDate.of(2024, 7, 15), LocalDate.of(2024, 7, 16), requestedTime, "1234", true);

        // then
        assertNull(result);
    }

    @Test
    public void shouldReturnNullWhenCourtSchedulesIsEmpty() {
        // given
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 10, 0);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                "B01LY00", LocalDate.of(2024, 7, 15), LocalDate.of(2024, 7, 16), requestedTime, "1234", true);

        // then
        assertNull(result);
    }

    @Test
    public void shouldReturnCourtScheduleWhenRequestedTimeIsNull() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        String courtRoomId = "1234";

        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId("test-schedule-id");
        schedule.setSessionDate(sessionDate);
        schedule.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        schedule.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        schedule.setCourtSession("AM");
        schedule.setBusinessType("YFL");
        schedule.setOuCode(courtCentreId);
        schedule.setCourtHouseId(courtCentreId);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), null, courtRoomId, true);

        // then
        assertNotNull(result);
    }

    @Test
    public void shouldReturnSingleScheduleWhenOnlyOneScheduleExists() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 10, 0);
        String courtRoomId = "1234";

        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId("single-schedule-id");
        schedule.setSessionDate(sessionDate);
        schedule.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        schedule.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        schedule.setCourtSession("AM");
        schedule.setBusinessType("YFL");
        schedule.setOuCode(courtCentreId);
        schedule.setCourtHouseId(courtCentreId);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then
        assertNotNull(result);
        assertEquals("single-schedule-id", result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
    }

    @Test
    public void shouldSelectFirstScheduleFromBeforeBreakList_WhenRequestedTimeIsBeforeBreak() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 9, 0); // Before break (break is typically around 13:00)
        String courtRoomId = "1234";

        // Create schedules before break
        CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtScheduleId("before-break-1");
        schedule1.setSessionDate(sessionDate);
        schedule1.setSessionStartTime(convertToDate(LocalTime.of(8, 0), 2024, 7, 15));
        schedule1.setSessionEndTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        schedule1.setCourtSession("AM");
        schedule1.setBusinessType("YFL");
        schedule1.setOuCode(courtCentreId);
        schedule1.setCourtHouseId(courtCentreId);
        schedule1.setCourtRoomId(courtRoomId);
        schedule1.setActive(true);
        courtScheduleRepository.save(schedule1);

        CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtScheduleId("before-break-2");
        schedule2.setSessionDate(sessionDate);
        schedule2.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        schedule2.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        schedule2.setCourtSession("AM");
        schedule2.setBusinessType("YFL");
        schedule2.setOuCode(courtCentreId);
        schedule2.setCourtHouseId(courtCentreId);
        schedule2.setCourtRoomId(courtRoomId);
        schedule2.setActive(true);
        courtScheduleRepository.save(schedule2);

        // Create schedules after break
        CourtSchedule schedule3 = random(CourtSchedule.class);
        schedule3.setCourtScheduleId("after-break-1");
        schedule3.setSessionDate(sessionDate);
        schedule3.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15));
        schedule3.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        schedule3.setCourtSession("PM");
        schedule3.setBusinessType("YFL");
        schedule3.setOuCode(courtCentreId);
        schedule3.setCourtHouseId(courtCentreId);
        schedule3.setCourtRoomId(courtRoomId);
        schedule3.setActive(true);
        courtScheduleRepository.save(schedule3);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should return the first schedule from before-break list
        assertNotNull(result);
        assertEquals("YFL", result.getBusinessType());
        // Should be one of the before-break schedules (schedule1 or schedule2)
        assertTrue("before-break-1".equals(result.getCourtScheduleId()) || 
                  "before-break-2".equals(result.getCourtScheduleId()));
    }

    @Test
    public void shouldSelectFirstScheduleFromAfterBreakList_WhenRequestedTimeIsAfterBreak() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 15, 0); // After break
        String courtRoomId = "1234";

        // Create schedules before break
        CourtSchedule schedule1 = random(CourtSchedule.class);
        schedule1.setCourtScheduleId("before-break-1");
        schedule1.setSessionDate(sessionDate);
        schedule1.setSessionStartTime(convertToDate(LocalTime.of(8, 0), 2024, 7, 15));
        schedule1.setSessionEndTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        schedule1.setCourtSession("AM");
        schedule1.setBusinessType("YFL");
        schedule1.setOuCode(courtCentreId);
        schedule1.setCourtHouseId(courtCentreId);
        schedule1.setCourtRoomId(courtRoomId);
        schedule1.setActive(true);
        courtScheduleRepository.save(schedule1);

        // Create schedules after break
        CourtSchedule schedule2 = random(CourtSchedule.class);
        schedule2.setCourtScheduleId("after-break-1");
        schedule2.setSessionDate(sessionDate);
        schedule2.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15));
        schedule2.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        schedule2.setCourtSession("PM");
        schedule2.setBusinessType("YFL");
        schedule2.setOuCode(courtCentreId);
        schedule2.setCourtHouseId(courtCentreId);
        schedule2.setCourtRoomId(courtRoomId);
        schedule2.setActive(true);
        courtScheduleRepository.save(schedule2);

        CourtSchedule schedule3 = random(CourtSchedule.class);
        schedule3.setCourtScheduleId("after-break-2");
        schedule3.setSessionDate(sessionDate);
        schedule3.setSessionStartTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        schedule3.setSessionEndTime(convertToDate(LocalTime.of(18, 0), 2024, 7, 15));
        schedule3.setCourtSession("PM");
        schedule3.setBusinessType("YFL");
        schedule3.setOuCode(courtCentreId);
        schedule3.setCourtHouseId(courtCentreId);
        schedule3.setCourtRoomId(courtRoomId);
        schedule3.setActive(true);
        courtScheduleRepository.save(schedule3);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should return the first schedule from after-break list
        assertNotNull(result);
        assertEquals("YFL", result.getBusinessType());
        // Should be one of the after-break schedules (schedule2 or schedule3)
        assertTrue("after-break-1".equals(result.getCourtScheduleId()) || 
                  "after-break-2".equals(result.getCourtScheduleId()));
    }

    @Test
    public void shouldIncludeADSchedulesInBothBeforeAndAfterLists() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 9, 0); // Before break
        String courtRoomId = "1234";

        // Create AD schedule (should be included in both lists)
        CourtSchedule adSchedule = random(CourtSchedule.class);
        adSchedule.setCourtScheduleId("ad-schedule");
        adSchedule.setSessionDate(sessionDate);
        adSchedule.setSessionStartTime(convertToDate(LocalTime.of(9, 0), 2024, 7, 15));
        adSchedule.setSessionEndTime(convertToDate(LocalTime.of(17, 0), 2024, 7, 15));
        adSchedule.setCourtSession("AD"); // All Day session
        adSchedule.setBusinessType("YFL");
        adSchedule.setOuCode(courtCentreId);
        adSchedule.setCourtHouseId(courtCentreId);
        adSchedule.setCourtRoomId(courtRoomId);
        adSchedule.setActive(true);
        courtScheduleRepository.save(adSchedule);

        // Create regular AM schedule
        CourtSchedule amSchedule = random(CourtSchedule.class);
        amSchedule.setCourtScheduleId("am-schedule");
        amSchedule.setSessionDate(sessionDate);
        amSchedule.setSessionStartTime(convertToDate(LocalTime.of(8, 0), 2024, 7, 15));
        amSchedule.setSessionEndTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15));
        amSchedule.setCourtSession("AM");
        amSchedule.setBusinessType("YFL");
        amSchedule.setOuCode(courtCentreId);
        amSchedule.setCourtHouseId(courtCentreId);
        amSchedule.setCourtRoomId(courtRoomId);
        amSchedule.setActive(true);
        courtScheduleRepository.save(amSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should return a schedule (either AD or AM)
        assertNotNull(result);
        assertEquals("YFL", result.getBusinessType());
        // Should be one of the schedules
        assertTrue("ad-schedule".equals(result.getCourtScheduleId()) || 
                  "am-schedule".equals(result.getCourtScheduleId()));
    }

    @Test
    public void shouldUseFallbackWhenSelectedListIsEmpty() {
        // given
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime requestedTime = LocalDateTime.of(2024, 7, 15, 9, 0);
        String courtRoomId = "1234";

        // Create only PM schedules (no AM schedules for before-break list)
        CourtSchedule pmSchedule = random(CourtSchedule.class);
        pmSchedule.setCourtScheduleId("pm-schedule");
        pmSchedule.setSessionDate(sessionDate);
        pmSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15));
        pmSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        pmSchedule.setCourtSession("PM");
        pmSchedule.setBusinessType("YFL");
        pmSchedule.setOuCode(courtCentreId);
        pmSchedule.setCourtHouseId(courtCentreId);
        pmSchedule.setCourtRoomId(courtRoomId);
        pmSchedule.setActive(true);
        courtScheduleRepository.save(pmSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestedTime, courtRoomId, true);

        // then - Should fallback to all schedules and return the PM schedule
        assertNotNull(result);
        assertEquals("pm-schedule", result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
    }

    // Tests for performFallbackSearchForPolice method - covering all 4 fallback conditions
    @Test
    public void shouldPerformFallbackSearchForPolice_Condition1_AllParametersSuccessful() {
        // given - Test 1st condition: All parameters successful
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("PM");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId);
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should find result on 1st attempt with all parameters
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
    }

    @Test
    public void shouldPerformFallbackSearchForPolice_Condition2_RemoveSessionStartTime() {
        // given - Test 2nd condition: Remove sessionStartTime when 1st attempt fails
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // Create court schedule with different session start time (so 1st attempt fails)
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(10, 0))); // Different time
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule.setCourtSession("AM");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId);
        courtSchedule.setCourtRoomId(courtRoomId);
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should find result on 2nd attempt (without sessionStartTime) and apply closest time filter
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
    }

    @Test
    public void shouldPerformFallbackSearchForPolice_Condition3_RemoveCourtRoomId() {
        // given - Test 3rd condition: Remove courtRoomId when 1st and 2nd attempts fail
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // Create court schedule with different court room ID (so 1st and 2nd attempts fail)
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(14, 0)));
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule.setCourtSession("PM");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId);
        courtSchedule.setCourtRoomId("9999"); // Different court room ID
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should find result on 3rd attempt (without courtRoomId)
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
    }

    @Test
    public void shouldPerformFallbackSearchForPolice_Condition4_RemoveBothSessionStartTimeAndCourtRoomId() {
        // given - Test 4th condition: Remove both sessionStartTime and courtRoomId when 1st, 2nd, 3rd attempts fail
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // Create court schedule with different session start time AND different court room ID
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtSchedule.setSessionDate(sessionDate);
        courtSchedule.setSessionStartTime(convertToDate(LocalTime.of(10, 0))); // Different time
        courtSchedule.setSessionEndTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule.setCourtSession("AM");
        courtSchedule.setBusinessType("YFL"); // Police business type
        courtSchedule.setOuCode(courtCentreId);
        courtSchedule.setCourtHouseId(courtCentreId);
        courtSchedule.setCourtRoomId("9999"); // Different court room ID
        courtSchedule.setActive(true);
        courtScheduleRepository.save(courtSchedule);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should find result on 4th attempt (without both sessionStartTime and courtRoomId) and apply closest time filter
        assertNotNull(result);
        assertEquals(courtSchedule.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
    }

    @Test
    public void shouldPerformFallbackSearchForPolice_WithMultipleSchedules_ApplyClosestTimeFilter() {
        // given - Test that closest time filter is applied when multiple schedules exist with same business type
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "9999";

        // Create multiple court schedules with same business type but different times
        CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15)); // 4 hours difference
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        courtSchedule1.setCourtSession("AM");
        courtSchedule1.setBusinessType("YFL"); // Same business type
        courtSchedule1.setOuCode(courtCentreId);
        courtSchedule1.setCourtHouseId(courtCentreId);
        courtSchedule1.setCourtRoomId("9999"); // Different court room ID
        courtSchedule1.setActive(true);
        courtScheduleRepository.save(courtSchedule1);

        CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setSessionStartTime(convertToDate(LocalTime.of(13, 30), 2024, 7, 15)); // 30 minutes difference - closest
        courtSchedule2.setSessionEndTime(convertToDate(LocalTime.of(15, 30), 2024, 7, 15));
        courtSchedule2.setCourtSession("PM");
        courtSchedule2.setBusinessType("YFL"); // Same business type
        courtSchedule2.setOuCode(courtCentreId);
        courtSchedule2.setCourtHouseId(courtCentreId);
        courtSchedule2.setCourtRoomId("9999"); // Different court room ID
        courtSchedule2.setActive(true);
        courtScheduleRepository.save(courtSchedule2);

        CourtSchedule courtSchedule3 = random(CourtSchedule.class);
        courtSchedule3.setSessionDate(sessionDate);
        courtSchedule3.setSessionStartTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15)); // 2 hours difference
        courtSchedule3.setSessionEndTime(convertToDate(LocalTime.of(18, 0), 2024, 7, 15));
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setBusinessType("YFL"); // Same business type
        courtSchedule3.setOuCode(courtCentreId);
        courtSchedule3.setCourtHouseId(courtCentreId);
        courtSchedule3.setCourtRoomId("9999"); // Different court room ID
        courtSchedule3.setActive(true);
        courtScheduleRepository.save(courtSchedule3);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should find the schedule with closest time (13:30)
        assertNotNull(result);
        assertEquals(courtSchedule2.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
    }

    @Test
    public void shouldPerformFallbackSearchForPolice_WithDifferentBusinessTypes_ReturnFirstSchedule() {
        // given - Test that when business types differ, first schedule is returned without time comparison
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15);
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 14, 0);
        String courtRoomId = "1234";

        // Create court schedules with different business types
        CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(16, 0))); // Further from requested time
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(18, 0)));
        courtSchedule1.setCourtSession("PM");
        courtSchedule1.setBusinessType("YFL"); // First business type
        courtSchedule1.setOuCode(courtCentreId);
        courtSchedule1.setCourtHouseId(courtCentreId);
        courtSchedule1.setCourtRoomId("9999"); // Different court room ID
        courtSchedule1.setActive(true);
        courtScheduleRepository.save(courtSchedule1);

        CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setSessionStartTime(convertToDate(LocalTime.of(13, 30))); // Closer to requested time
        courtSchedule2.setSessionEndTime(convertToDate(LocalTime.of(15, 30)));
        courtSchedule2.setCourtSession("PM");
        courtSchedule2.setBusinessType("GAP"); // Different business type
        courtSchedule2.setOuCode(courtCentreId);
        courtSchedule2.setCourtHouseId(courtCentreId);
        courtSchedule2.setCourtRoomId("9999"); // Different court room ID
        courtSchedule2.setActive(true);
        courtScheduleRepository.save(courtSchedule2);

        // when
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should return first schedule (YFL) even though second (GAP) is closer in time
        assertNotNull(result);
        assertEquals(courtSchedule1.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
    }

    @Test
    public void shouldFilterSchedulesIntoSeparateListsBasedOnNationalBreakoutTime() {
        // given - Test filtering schedules into separate lists based on national breakout time
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15); // Summer time - national break at 12:00 UTC
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 10, 0); // Before national break
        String courtRoomId = "9999";

        // Create court schedules with same business type but different times relative to national break
        // Schedule 1: Before national break (10:00)
        CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(10, 0))); // Before national break
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(12, 0)));
        courtSchedule1.setCourtSession("AM");
        courtSchedule1.setBusinessType("YFL"); // Same business type
        courtSchedule1.setOuCode(courtCentreId);
        courtSchedule1.setCourtHouseId(courtCentreId);
        courtSchedule1.setCourtRoomId("9999");
        courtSchedule1.setActive(true);
        courtScheduleRepository.save(courtSchedule1);

        // Schedule 2: Before national break (11:30)
        CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setSessionStartTime(convertToDate(LocalTime.of(11, 30))); // Before national break
        courtSchedule2.setSessionEndTime(convertToDate(LocalTime.of(13, 30)));
        courtSchedule2.setCourtSession("PM");
        courtSchedule2.setBusinessType("YFL"); // Same business type
        courtSchedule2.setOuCode(courtCentreId);
        courtSchedule2.setCourtHouseId(courtCentreId);
        courtSchedule2.setCourtRoomId("9999");
        courtSchedule2.setActive(true);
        courtScheduleRepository.save(courtSchedule2);

        // Schedule 3: After national break (14:00)
        CourtSchedule courtSchedule3 = random(CourtSchedule.class);
        courtSchedule3.setSessionDate(sessionDate);
        courtSchedule3.setSessionStartTime(convertToDate(LocalTime.of(14, 0))); // After national break
        courtSchedule3.setSessionEndTime(convertToDate(LocalTime.of(16, 0)));
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setBusinessType("YFL"); // Same business type
        courtSchedule3.setOuCode(courtCentreId);
        courtSchedule3.setCourtHouseId(courtCentreId);
        courtSchedule3.setCourtRoomId("9999");
        courtSchedule3.setActive(true);
        courtScheduleRepository.save(courtSchedule3);

        // Schedule 4: After national break (15:30)
        CourtSchedule courtSchedule4 = random(CourtSchedule.class);
        courtSchedule4.setSessionDate(sessionDate);
        courtSchedule4.setSessionStartTime(convertToDate(LocalTime.of(15, 30))); // After national break
        courtSchedule4.setSessionEndTime(convertToDate(LocalTime.of(17, 30)));
        courtSchedule4.setCourtSession("PM");
        courtSchedule4.setBusinessType("YFL"); // Same business type
        courtSchedule4.setOuCode(courtCentreId);
        courtSchedule4.setCourtHouseId(courtCentreId);
        courtSchedule4.setCourtRoomId("9999");
        courtSchedule4.setActive(true);
        courtScheduleRepository.save(courtSchedule4);

        // when - Request time is before national break, should filter to schedules before break
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should return a schedule from before national break (closest to 10:00)
        assertNotNull(result);
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
        
        // Verify the returned schedule is from before national break
        LocalDateTime resultTime = convertToLocalDateTime(result.getSessionStartTime());
        assertTrue("Result should be before national break time (12:00)", 
                  resultTime.isBefore(LocalDateTime.of(2024, 7, 15, 12, 0)));
    }

    @Test
    public void shouldFilterSchedulesIntoSeparateListsBasedOnNationalBreakoutTime_RequestAfterBreak() {
        // given - Test filtering schedules when request time is after national break
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15); // Summer time - national break at 12:00 UTC
        LocalDateTime sessionStartTime = LocalDateTime.of(2024, 7, 15, 15, 0); // After national break
        String courtRoomId = "9999";

        // Create court schedules with same business type but different times relative to national break
        // Schedule 1: Before national break (10:00)
        CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setSessionDate(sessionDate);
        courtSchedule1.setSessionStartTime(convertToDate(LocalTime.of(10, 0), 2024, 7, 15)); // Before national break
        courtSchedule1.setSessionEndTime(convertToDate(LocalTime.of(12, 0), 2024, 7, 15));
        courtSchedule1.setCourtSession("AM");
        courtSchedule1.setBusinessType("YFL"); // Same business type
        courtSchedule1.setOuCode(courtCentreId);
        courtSchedule1.setCourtHouseId(courtCentreId);
        courtSchedule1.setCourtRoomId("9999");
        courtSchedule1.setActive(true);
        courtScheduleRepository.save(courtSchedule1);

        // Schedule 2: After national break (14:00) - closest to request time
        CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setSessionDate(sessionDate);
        courtSchedule2.setSessionStartTime(convertToDate(LocalTime.of(14, 0), 2024, 7, 15)); // After national break
        courtSchedule2.setSessionEndTime(convertToDate(LocalTime.of(16, 0), 2024, 7, 15));
        courtSchedule2.setCourtSession("PM");
        courtSchedule2.setBusinessType("YFL"); // Same business type
        courtSchedule2.setOuCode(courtCentreId);
        courtSchedule2.setCourtHouseId(courtCentreId);
        courtSchedule2.setCourtRoomId("9999");
        courtSchedule2.setActive(true);
        courtScheduleRepository.save(courtSchedule2);

        // Schedule 3: After national break (16:30)
        CourtSchedule courtSchedule3 = random(CourtSchedule.class);
        courtSchedule3.setSessionDate(sessionDate);
        courtSchedule3.setSessionStartTime(convertToDate(LocalTime.of(16, 30), 2024, 7, 15)); // After national break
        courtSchedule3.setSessionEndTime(convertToDate(LocalTime.of(18, 30), 2024, 7, 15));
        courtSchedule3.setCourtSession("PM");
        courtSchedule3.setBusinessType("YFL"); // Same business type
        courtSchedule3.setOuCode(courtCentreId);
        courtSchedule3.setCourtHouseId(courtCentreId);
        courtSchedule3.setCourtRoomId("9999");
        courtSchedule3.setActive(true);
        courtScheduleRepository.save(courtSchedule3);

        // when - Request time is after national break, should use all schedules
        CourtSchedule result = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), sessionStartTime, courtRoomId, true);

        // then - Should return the closest schedule to request time (14:00)
        assertNotNull(result);
        assertEquals(courtSchedule2.getCourtScheduleId(), result.getCourtScheduleId());
        assertEquals("YFL", result.getBusinessType());
        assertEquals(sessionDate, result.getSessionDate());
        assertEquals("9999", result.getCourtRoomId());
        
        // Verify the returned schedule is the closest to request time (15:00)
        LocalDateTime resultTime = convertToLocalDateTime(result.getSessionStartTime());
        assertEquals("Result should be 14:00 (closest to 15:00)", 
                    LocalDateTime.of(2024, 7, 15, 14, 0), resultTime);
    }

    @Test
    public void shouldDemonstrateNationalBreakoutTimeFilteringLogic() {
        // given - Test that demonstrates how schedules are filtered into separate lists based on national breakout time
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15); // Summer time - national break at 12:00 UTC
        String courtRoomId = "9999";

        // Create multiple court schedules with same business type but different times
        // Morning schedules (before national break)
        createTestSchedule("MORNING_1", sessionDate, LocalTime.of(9, 0), LocalTime.of(11, 0), "YFL", courtCentreId, courtRoomId, "AM");
        createTestSchedule("MORNING_2", sessionDate, LocalTime.of(11, 30), LocalTime.of(12, 0), "YFL", courtCentreId, courtRoomId, "AM");
        
        // Afternoon schedules (after national break)
        createTestSchedule("AFTERNOON_1", sessionDate, LocalTime.of(13, 0), LocalTime.of(15, 0), "YFL", courtCentreId, courtRoomId, "PM");
        createTestSchedule("AFTERNOON_2", sessionDate, LocalTime.of(15, 30), LocalTime.of(17, 30), "YFL", courtCentreId, courtRoomId, "PM");

        // Test Case 1: Request time before national break (10:00) - should filter to morning schedules only
        LocalDateTime requestTimeBeforeBreak = LocalDateTime.of(2024, 7, 15, 10, 0);
        CourtSchedule resultBeforeBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeBeforeBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request before break", resultBeforeBreak);
        LocalDateTime resultTimeBeforeBreak = convertToLocalDateTime(resultBeforeBreak.getSessionStartTime());
        assertTrue("Result should be before national break time (12:00) when request is before break", 
                  resultTimeBeforeBreak.isBefore(LocalDateTime.of(2024, 7, 15, 12, 0)));

        // Test Case 2: Request time after national break (14:00) - should use all schedules
        LocalDateTime requestTimeAfterBreak = LocalDateTime.of(2024, 7, 15, 14, 0);
        CourtSchedule resultAfterBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeAfterBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request after break", resultAfterBreak);
        LocalDateTime resultTimeAfterBreak = convertToLocalDateTime(resultAfterBreak.getSessionStartTime());
        // Should return the closest schedule to 14:00, which would be 13:00 (afternoonSchedule1)
        assertEquals("Result should be 13:00 (closest to 14:00)", 
                    LocalDateTime.of(2024, 7, 15, 13, 0), resultTimeAfterBreak);

        // Test Case 3: Request time exactly at national break (12:00) - should use all schedules
        LocalDateTime requestTimeAtBreak = LocalDateTime.of(2024, 7, 15, 12, 0);
        CourtSchedule resultAtBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeAtBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request at break", resultAtBreak);
        LocalDateTime resultTimeAtBreak = convertToLocalDateTime(resultAtBreak.getSessionStartTime());
        // Should return the closest schedule to 12:00, which would be 11:30 (morningSchedule2)
        assertEquals("Result should be 11:30 (closest to 12:00)", 
                    LocalDateTime.of(2024, 7, 15, 11, 30), resultTimeAtBreak);
    }

    @Test
    public void shouldCreateTwoSeparateFilterListsBasedOnNationalBreakoutTime() {
        // given - Test the new implementation with two separate filter lists
        String courtCentreId = "B01LY00";
        LocalDate sessionDate = LocalDate.of(2024, 7, 15); // Summer time - national break at 12:00 UTC
        String courtRoomId = "9999";

        // Create schedules with same business type but different times relative to national break
        // Morning schedules (before national break)
        createTestSchedule("MORNING_1", sessionDate, LocalTime.of(9, 0), LocalTime.of(11, 0), "YFL", courtCentreId, courtRoomId, "AM");
        createTestSchedule("MORNING_2", sessionDate, LocalTime.of(11, 30), LocalTime.of(12, 0), "YFL", courtCentreId, courtRoomId, "AM");
        
        // Afternoon schedules (after national break)
        createTestSchedule("AFTERNOON_1", sessionDate, LocalTime.of(13, 0), LocalTime.of(15, 0), "YFL", courtCentreId, courtRoomId, "PM");
        createTestSchedule("AFTERNOON_2", sessionDate, LocalTime.of(15, 30), LocalTime.of(17, 30), "YFL", courtCentreId, courtRoomId, "PM");

        // Test Case 1: Request time before national break (10:00) - should select schedulesBeforeBreak list
        LocalDateTime requestTimeBeforeBreak = LocalDateTime.of(2024, 7, 15, 10, 0);
        CourtSchedule resultBeforeBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeBeforeBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request before break", resultBeforeBreak);
        LocalDateTime resultTimeBeforeBreak = convertToLocalDateTime(resultBeforeBreak.getSessionStartTime());
        assertTrue("Result should be before national break time (12:00) when request is before break", 
                  resultTimeBeforeBreak.isBefore(LocalDateTime.of(2024, 7, 15, 12, 0)));
        
        // Verify it's from the morning schedules (should be 11:30 as closest to 10:00)
        assertTrue("Result should be from morning schedules", 
                  resultTimeBeforeBreak.isEqual(LocalDateTime.of(2024, 7, 15, 11, 30)) ||
                  resultTimeBeforeBreak.isEqual(LocalDateTime.of(2024, 7, 15, 9, 0)));

        // Test Case 2: Request time after national break (14:00) - should select schedulesAfterBreak list
        LocalDateTime requestTimeAfterBreak = LocalDateTime.of(2024, 7, 15, 14, 0);
        CourtSchedule resultAfterBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeAfterBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request after break", resultAfterBreak);
        LocalDateTime resultTimeAfterBreak = convertToLocalDateTime(resultAfterBreak.getSessionStartTime());
        assertTrue("Result should be after or equal to national break time (12:00) when request is after break", 
                  resultTimeAfterBreak.isAfter(LocalDateTime.of(2024, 7, 15, 12, 0)) ||
                  resultTimeAfterBreak.isEqual(LocalDateTime.of(2024, 7, 15, 12, 0)));
        
        // Verify it's from the afternoon schedules (should be 13:00 as closest to 14:00)
        assertTrue("Result should be from afternoon schedules", 
                  resultTimeAfterBreak.isEqual(LocalDateTime.of(2024, 7, 15, 13, 0)) ||
                  resultTimeAfterBreak.isEqual(LocalDateTime.of(2024, 7, 15, 15, 30)));

        // Test Case 3: Request time exactly at national break (12:00) - should select schedulesAfterBreak list
        LocalDateTime requestTimeAtBreak = LocalDateTime.of(2024, 7, 15, 12, 30);
        CourtSchedule resultAtBreak = courtScheduleRepository.searchListHearingSlotFilterCriteria(
                courtCentreId, sessionDate, sessionDate.plusDays(1), requestTimeAtBreak, courtRoomId, true);

        assertNotNull("Result should not be null for request at break", resultAtBreak);
        LocalDateTime resultTimeAtBreak = convertToLocalDateTime(resultAtBreak.getSessionStartTime());
        assertTrue("Result should be after or equal to national break time (12:00) when request is at break", 
                  resultTimeAtBreak.isAfter(LocalDateTime.of(2024, 7, 15, 12, 0)));
    }

    /**
     * Helper method to create test court schedules
     */
    private CourtSchedule createTestSchedule(String id, LocalDate sessionDate, LocalTime startTime, LocalTime endTime,
                                           String businessType, String courtCentreId, String courtRoomId, String courtSession) {
        CourtSchedule schedule = random(CourtSchedule.class);
        schedule.setCourtScheduleId(id);
        schedule.setSessionDate(sessionDate);
        schedule.setSessionStartTime(convertToDate(startTime, 2024, 7, 15));
        schedule.setSessionEndTime(convertToDate(endTime, 2024, 7, 15));
        schedule.setCourtSession(courtSession);
        schedule.setBusinessType(businessType);
        schedule.setOuCode(courtCentreId);
        schedule.setCourtHouseId(courtCentreId);
        schedule.setCourtRoomId(courtRoomId);
        schedule.setActive(true);
        courtScheduleRepository.save(schedule);
        return schedule;
    }

    /**
     * Helper method to convert Date to LocalDateTime for assertions
     */
    private LocalDateTime convertToLocalDateTime(Date date) {
        if (date == null) {
            return null;
        }
        return date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
    }


}
