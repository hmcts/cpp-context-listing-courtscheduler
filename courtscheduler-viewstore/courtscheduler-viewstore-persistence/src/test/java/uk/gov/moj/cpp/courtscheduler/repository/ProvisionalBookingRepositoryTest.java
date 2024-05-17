package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBookingKey;

import javax.inject.Inject;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;


@RunWith(CdiTestRunner.class)
public class ProvisionalBookingRepositoryTest {

    @Inject
    CourtScheduleRepository courtScheduleRepository;

    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;

    @After
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

}