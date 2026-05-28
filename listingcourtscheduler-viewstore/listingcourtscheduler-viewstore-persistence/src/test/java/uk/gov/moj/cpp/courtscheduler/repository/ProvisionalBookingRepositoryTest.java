package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;


class ProvisionalBookingRepositoryTest extends uk.gov.moj.cpp.courtscheduler.repository.AbstractRepositoryTest {

    @Autowired
    CourtScheduleRepository courtScheduleRepository;
    @Autowired
    private ProvisionalBookingRepository provisionalBookingRepository;
    @AfterEach
    public void tearDown() {
        List<ProvisionalBooking> all = provisionalBookingRepository.findAll();
        all.forEach(provisionalBooking -> provisionalBookingRepository.remove(provisionalBooking));
    }

    @Test
    public void shouldSave() {
        final ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        courtScheduleRepository.save(provisionalBooking.getProvisionalBookingKey().getCourtSchedule());

        provisionalBookingRepository.save(provisionalBooking);
        ProvisionalBooking by = provisionalBookingRepository.findBy(provisionalBooking.getProvisionalBookingKey());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldGetCourtScheduleInfo() {
        // given
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        courtScheduleRepository.save(courtSchedule);
        String bookingId1 = random(String.class);
        String bookingId2 = random(String.class);
        String otherBookingId = random(String.class);

        // provisional booking 1
        final ProvisionalBookingKey provisionalBookingKey1 = new ProvisionalBookingKey(courtSchedule, bookingId1);
        final ProvisionalBooking provisionalBooking1 = random(ProvisionalBooking.class);
        provisionalBooking1.setProvisionalBookingKey(provisionalBookingKey1);

        // provisional booking 2
        final ProvisionalBookingKey provisionalBookingKey2 = new ProvisionalBookingKey(courtSchedule, bookingId2);
        final ProvisionalBooking provisionalBooking2 = random(ProvisionalBooking.class);
        provisionalBooking2.setProvisionalBookingKey(provisionalBookingKey2);

        // provisional booking 3
        final ProvisionalBookingKey provisionalBookingKeyOther = new ProvisionalBookingKey(courtSchedule, otherBookingId);
        final ProvisionalBooking provisionalBooking3 = random(ProvisionalBooking.class);
        provisionalBooking3.setProvisionalBookingKey(provisionalBookingKeyOther);


        provisionalBookingRepository.save(provisionalBooking1);
        provisionalBookingRepository.save(provisionalBooking2);
        provisionalBookingRepository.save(provisionalBooking3);

        Map<String, Date> courtScheduleInfo = provisionalBookingRepository.getCourtScheduleInfo(List.of(bookingId1));
        assertThat(courtScheduleInfo, notNullValue());
        assertThat(courtScheduleInfo.get(courtSchedule.getCourtScheduleId()), is(provisionalBooking1.getHearingStartTime()));

    }

    @Test
    public void shouldFindByBookingId() {
        final ProvisionalBooking provisionalBooking = random(ProvisionalBooking.class);
        courtScheduleRepository.save(provisionalBooking.getProvisionalBookingKey().getCourtSchedule());

        provisionalBookingRepository.save(provisionalBooking);
        Optional<ProvisionalBooking> byBookingId = provisionalBookingRepository.findByBookingId(provisionalBooking.getProvisionalBookingKey().getBookingId());

        assertThat(byBookingId.isPresent(), is(true));
    }

    @Test
    public void shouldFindByProvisionalBookingList_providedBookingIds() {
        List<String> bookingIds = new ArrayList<>();
        String bookingId = random(String.class);
        bookingIds.add(bookingId);
        final ProvisionalBooking provisionalBooking1 = random(ProvisionalBooking.class);
        final ProvisionalBooking provisionalBooking2 = random(ProvisionalBooking.class);
        provisionalBooking1.getProvisionalBookingKey().setBookingId(bookingId);
        provisionalBooking2.getProvisionalBookingKey().setBookingId(bookingId);
        courtScheduleRepository.save(provisionalBooking1.getProvisionalBookingKey().getCourtSchedule());
        courtScheduleRepository.save(provisionalBooking2.getProvisionalBookingKey().getCourtSchedule());
        provisionalBookingRepository.save(provisionalBooking1);
        provisionalBookingRepository.save(provisionalBooking2);

        List<ProvisionalBooking> provisionalBookingList = provisionalBookingRepository.findByBookingIdIn(bookingIds);

        assertThat(provisionalBookingList.isEmpty(), is(false));
        assertThat(provisionalBookingList.size(), is(2));
    }

    @Test
    public void shouldSaveProvisionalBooking() {
        final CourtSchedule courtSchedule = random(CourtSchedule.class);
        final ProvisionalSlot provisionalSlot = new ProvisionalSlot(courtSchedule.getCourtScheduleId(),
                "2020-01-01T11:00:00.000Z");
        final String bookingId = randomUUID().toString();

        courtScheduleRepository.save(courtSchedule);
        provisionalBookingRepository.saveProvisionalBooking(provisionalSlot, bookingId, courtSchedule);

        assertNotNull(provisionalBookingRepository.findByBookingId(bookingId).get());
    }
}
