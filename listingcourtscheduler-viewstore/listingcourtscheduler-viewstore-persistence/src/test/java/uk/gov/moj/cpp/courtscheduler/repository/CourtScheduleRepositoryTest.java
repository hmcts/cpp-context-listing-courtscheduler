package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
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
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
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
        setupTestDataForOptionalParams(sessionDate);

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

    // Setup methods
    private void setupTestDataForMandatoryParams(LocalDate sessionDate) {
        CourtSchedule matchingSchedule = createCourtSchedule("B01LY00", "ADULT", sessionDate, "CR01", "TRF");
        CourtSchedule differentOuCode = createCourtSchedule("B02LY00", "ADULT", sessionDate, "CR02", "TRF");
        CourtSchedule differentPanel = createCourtSchedule("B01LY00", "YOUTH", sessionDate, "CR03", "TRF");
        CourtSchedule differentDate = createCourtSchedule("B01LY00", "ADULT", sessionDate.plusDays(10), "CR04", "TRF");

        saveSchedules(List.of(matchingSchedule, differentOuCode, differentPanel, differentDate));
    }

    private void setupTestDataForOptionalParams(LocalDate sessionDate) {
        String ouCode = "B01LY00";
        String panel = "ADULT";

        CourtSchedule matchingSchedule = createCourtSchedule(ouCode, panel, sessionDate, "CR01", "TRF");
        CourtSchedule differentCourtRoom = createCourtSchedule(ouCode, panel, sessionDate, "CR02", "TRF");
        CourtSchedule differentBusinessType = createCourtSchedule(ouCode, panel, sessionDate, "CR01", "GAP");

        saveSchedules(List.of(matchingSchedule, differentCourtRoom, differentBusinessType));
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
            "TRF"
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

    private void thenPaginationWorksCorrectly(List<Pair<Integer, List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule>>> results) {
        assertEquals(10, results.get(0).getRight().size());  // First page
        assertEquals(10, results.get(1).getRight().size());  // Second page
        assertEquals(5, results.get(2).getRight().size());   // Third page

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
                                                     String courtRoomId, String businessType) {
        return new HearingSlotRequestParam(
            panel,
            sessionDate.toString(),
            sessionDate.toString(),
            ouCode,
            ouCode,
            pageSize,
            pageNumber,
            courtRoomId,
            null,
            businessType,
            null
        );
    }

    private CourtSchedule createCourtSchedule(final String ouCode, final String panel, final LocalDate sessionDate,
                                            final String courtRoomId, final String businessType) {
        CourtSchedule schedule = new CourtSchedule();

        // Required fields (not null constraints)
        schedule.setCourtScheduleId(UUID.randomUUID().toString());  // id
        schedule.setSlotBased(false);                            // is_slot_based

        // Fields used in unique indexes
        schedule.setOuCode(ouCode);                                // oucode
        schedule.setCourtRoomId(courtRoomId);                     // court_room_id
        schedule.setBusinessType(businessType);                    // rota_business_type
        schedule.setSessionDate(sessionDate);                      // session_start
        schedule.setCourtSession("AM");                           // court_session
        schedule.setActive(true);                                 // active

        // Other fields with specific types
        schedule.setCourtRoomNumber(1);                           // court_room_number (numeric)
        schedule.setCourtHouseName("Test Court House");           // court_house_name
        schedule.setCourtRoomName("Test Court Room " + courtRoomId); // court_room_name
        schedule.setOperationalUnit(ouCode);                      // operational_unit
        schedule.setPanel(panel);                                 // panel
        schedule.setMaxSlots(10);                                 // max_slot (numeric)
        schedule.setMaxDuration(240);                             // max_duration_mins (numeric)
        schedule.setAvailableSlots(10);                          // available_slot (numeric)
        schedule.setAvailableDuration(240);                       // available_duration_mins (numeric)
        schedule.setCourtHouseId("CH" + ouCode);                 // court_house_id

        // Boolean fields with defaults
        schedule.setSupportAdSplit(false);                        // support_ad_split
        schedule.setIsOverbookingAllowed(false);                  // is_overbooking_allowed

        // Numeric fields with defaults
        schedule.setMaxAdMorningDuration(0);                      // max_ad_morning_duration
        schedule.setMaxAdAfternoonDuration(0);                    // max_ad_afternoon_duration

        // Timestamp fields
        LocalDateTime now = LocalDateTime.now();
        schedule.setCreatedOn(Timestamp.valueOf(now));            // created_on
        schedule.setUpdatedOn(Timestamp.valueOf(now));            // updated_on

        // Session time fields (with time zone)
        LocalDateTime startDateTime = LocalDateTime.of(sessionDate, LocalTime.of(9, 0));
        LocalDateTime endDateTime = LocalDateTime.of(sessionDate, LocalTime.of(13, 0));
        schedule.setSessionStartTime(Date.from(startDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        schedule.setSessionEndTime(Date.from(endDateTime.atZone(ZoneId.systemDefault()).toInstant()));

        // Set national break time based on BST logic
        schedule.setNationalBreakTime(TimezoneUtils.calculateNationalBreakTime(sessionDate));

        // Optional field
        schedule.setListingProfileId(random(String.class));                  // court_listing_profile_id

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

        courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot, false);

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedules.isEmpty());
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

        courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot, false);

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtSchedule);
        assertFalse(courtSchedules.isEmpty());
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
        courtSchedule.setSlotBased(true);
        Integer currentAvailableSlots = courtSchedule.getAvailableSlots();
        courtScheduleRepository.save(courtSchedule);

        String hearingId = random(String.class);
        final AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setCourtScheduleId(courtScheduleId);


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

        // provisional booking 1
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

        courtScheduleRepository.save(courtSchedule1);

        final CourtScheduleMatcherInfo courtScheduleMatcherInfo = courtScheduleRepository.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtRoomId, sessionDate, businessType, courtSession);

        assertNotNull(courtScheduleMatcherInfo);
        assertEquals(courtScheduleMatcherInfo.getCourtScheduleId(), courtSchedule1.getCourtScheduleId());
    }

    private HearingSlotRequestParam createHearingSlotRequest(CourtSchedule courtSchedule) {
        LocalDate startDate = courtSchedule.getSessionDate().minusDays(1);
        LocalDate endDate = courtSchedule.getSessionDate().plusDays(1);
        return new HearingSlotRequestParam(
                courtSchedule.getPanel(),
                startDate.toString(),
                endDate.toString(),
                courtSchedule.getOperationalUnit(),
                courtSchedule.getOuCode(),
                "1",
                "1",
                courtSchedule.getCourtRoomId(),
                courtSchedule.getCourtRoomNumber().toString(),
                courtSchedule.getBusinessType(),
                courtSchedule.getCourtSession());
    }
}
