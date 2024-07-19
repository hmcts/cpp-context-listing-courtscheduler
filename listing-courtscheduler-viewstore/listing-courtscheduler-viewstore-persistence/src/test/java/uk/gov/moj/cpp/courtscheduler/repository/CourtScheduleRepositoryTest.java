package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.inject.Inject;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

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
    @Mock
    ConstraintViolationException constraintViolationException;

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
        LocalDate sessionDate = random(LocalDate.class);
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
        assertThat(result.isSuccess(), is(true));
    }

    @Test
    public void shouldFindCourtSchedulesUpdatedBetweenDates() {
        CourtSchedule courtSchedule = random(CourtSchedule.class);
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);

        courtScheduleRepository.save(courtSchedule);

        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> courtScheduleList = courtScheduleRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertThat(courtScheduleList.isEmpty(), is(false));
    }

    @Test
    public void shouldFindCourtSchedulesByCourtScheduleRequestParam() {
        // given
        CourtSchedule matchingCourtSchedule = random(CourtSchedule.class);
        courtScheduleRepository.save(matchingCourtSchedule);
        // and
        CourtSchedule unMatchingCourtSchedule = random(CourtSchedule.class);
        unMatchingCourtSchedule.setCourtHouseId(matchingCourtSchedule.getCourtHouseId());
        unMatchingCourtSchedule.setSessionDate(matchingCourtSchedule.getSessionDate());
        courtScheduleRepository.save(unMatchingCourtSchedule);

        String courtCentreId = matchingCourtSchedule.getCourtHouseId();
        final CourtScheduleRequestParam courtScheduleRequestParam = getCourtScheduleRequestParam(matchingCourtSchedule, courtCentreId);

        // when
        List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> results = courtScheduleRepository.findBy(courtScheduleRequestParam);

        // then
        assertThat(results.size(), is(1));
        assertThat(results.get(0).getCourtRoomId(), is(matchingCourtSchedule.getCourtRoomId()));
    }

    private static CourtScheduleRequestParam getCourtScheduleRequestParam(final CourtSchedule courtSchedule, final String courtCentreId) {
        String courtRoomId = courtSchedule.getCourtRoomId();
        String businessType = courtSchedule.getBusinessType();
        String sessionStartDate = courtSchedule.getSessionDate().minusDays(1).toString();
        String sessionEndDate = courtSchedule.getSessionDate().plusDays(2).toString();
        String pageSize = "10";
        String pageNumber = "1";
        CourtScheduleRequestParam courtScheduleRequestParam = new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, pageSize, pageNumber);
        return courtScheduleRequestParam;
    }

    @Test
    public void shouldSaveSlots() {
        String hearingId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
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
        assertThat(courtSchedules.isEmpty(), is(false));
    }


    @Test
    public void shouldSaveSlotsFoSPI() {
        String hearingId = UUID.randomUUID().toString();
        String bookingId = UUID.randomUUID().toString();
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

        AllocatedSlot allocatedSlot1 = getAllocatedSlotForSPI(allocatedListing);
        List<AllocatedSlot> slots = Lists.newArrayList(allocatedSlot1);
        boolean isProvisionalSlot = false;

        courtScheduleRepository.saveBookedSlots(slots, isProvisionalSlot);

        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtSchedule);
        assertThat(courtSchedules.isEmpty(), is(false));
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

        assertThat(by, notNullValue());

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
        assertThat(courtSchedule1.getAvailableSlots(), is(currentAvailableSlots + 1));
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
        assertThat(courtSchedule1.getAvailableDuration(), is(availableDuration + allocatedListing.getDuration()));
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

        assertThat(byBookingId.isPresent(), is(true));
        assertThat(byBookingId.get().getActive(), is(true));
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

        assertThat(allocatedListings.isEmpty(), is(true));
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

        assertThat(courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId()).getAvailableSlots(), is(availableSlots - 1));
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


        assertThat(courtSchedule.getAvailableDuration(), is(availableDuration - allocatedSlot.getDuration()));
    }

    @Test
    public void shouldSaveAllocatedListing() {
        AllocatedSlot allocatedSlot = random(AllocatedSlot.class);
        allocatedSlot.setCourtRoomId(random(Integer.class).toString());
        allocatedSlot.setHearingStartTime(DateUtils.toIsoString(OffsetDateTime.now()));
        courtScheduleRepository.saveAllocatedListing(Lists.newArrayList(allocatedSlot));

        List<AllocatedListing> allocatedListings = allocatedListingRepository.findByHearingId(allocatedSlot.getHearingId());

        assertThat(allocatedListings.isEmpty(), is(false));
    }

    @Test
    public void shouldDeleteProvisionalBooking() {
        ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        courtScheduleRepository.save(provisionalBooking.getProvisionalBookingKey().getCourtSchedule());
        provisionalBooking.setActive(true);
        provisionalBookingRepository.save(provisionalBooking);

        courtScheduleRepository.deleteProvisionalBooking(provisionalBooking.getProvisionalBookingKey().getBookingId());

        Optional<ProvisionalBooking> byBookingId = provisionalBookingRepository.findByBookingId(provisionalBooking.getProvisionalBookingKey().getBookingId());
        assertThat(byBookingId.isPresent(), is(true));
        assertThat(byBookingId.get().getActive(), is(false));
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

        courtScheduleRepository.update(courtSchedule1);
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

        courtScheduleRepository.update(courtSchedule1);
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

        courtScheduleRepository.update(courtSchedule1);
        CourtSchedule by1 = courtScheduleRepository.findBy(courtSchedule.getCourtScheduleId());
        assertEquals(6, by1.getMaxSlots().intValue());
        assertEquals(7, by1.getMaxDuration().intValue());
        assertEquals(8, by1.getAvailableSlots().intValue());
        assertEquals(9, by1.getAvailableDuration().intValue());
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
