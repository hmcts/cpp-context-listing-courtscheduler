package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.inject.Inject;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.Before;
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
                .withPanel(panel)
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
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule1.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleRequestParam(matchingCourtSchedule1, courtCentreId);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.getCourtSchedulesBy(courtScheduleRequestParam);

        // then
        assertEquals(3, results.size());
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
        courtScheduleRepository.save(courtSchedule);


        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        provisionalBooking.setProvisionalBookingKey(new ProvisionalBookingKey(courtSchedule, bookingId));
        provisionalBookingRepository.save(provisionalBooking);

        AllocatedListing allocatedListing = random(AllocatedListing.class);
        allocatedListing.setHearingId(hearingId);
        allocatedListing.setBookingId(bookingId);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListingRepository.save(allocatedListing);

        AllocatedSlot allocatedSlot1 = getAllocatedSlot(allocatedListing);
        List<AllocatedSlot> slots = Lists.newArrayList(allocatedSlot1);
        boolean isProvisionalSlot = false;

        courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot);

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

        courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot);

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
