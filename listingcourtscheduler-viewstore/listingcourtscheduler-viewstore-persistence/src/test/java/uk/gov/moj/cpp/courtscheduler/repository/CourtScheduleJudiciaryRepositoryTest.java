package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.core.Is.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class CourtScheduleJudiciaryRepositoryTest {
    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @After
    public void tearDown() {
        List<CourtScheduleJudiciary> all = courtScheduleJudiciaryRepository.findAll();
        all.forEach(courtScheduleJudiciary -> courtScheduleJudiciaryRepository.remove(courtScheduleJudiciary));
    }

    @Test
    public void shouldSave() {
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);
        CourtScheduleJudiciary by = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindByEmail() {
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);
        CourtScheduleJudiciary by = courtScheduleJudiciaryRepository.findByEmail(courtScheduleJudiciary.getEmail());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindCourtScheduleJudiciariesUpdatedBetweenDates() {
        CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        LocalDate fromDate = LocalDate.now().minusDays(1);
        LocalDate toDate = LocalDate.now().plusDays(1);
        MiFilterCriteria miFilterCriteria = new MiFilterCriteria(fromDate, toDate);


        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        List<uk.gov.moj.cpp.courtscheduler.domain.mi.CourtScheduleJudiciary> courtScheduleJudiciaryList = courtScheduleJudiciaryRepository.findByUpdatedOnGreaterThanAndUpdatedOnLessThan(miFilterCriteria);
        assertThat(courtScheduleJudiciaryList.isEmpty(), is(false));
    }

    @Test
    public void shouldDeactivateSchedules() {
        final String courtScheduleId = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary.getId().setCourtScheduleId(courtScheduleId);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        final Date updatedOn = Calendar.getInstance().getTime();
        courtScheduleJudiciaryRepository.deactivateSchedules(List.of(courtScheduleId), updatedOn);

        final CourtScheduleJudiciary courtScheduleJudiciaryAfterDeactivation = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());
        courtScheduleJudiciaryRepository.refresh(courtScheduleJudiciaryAfterDeactivation);

        assertEquals(false, courtScheduleJudiciaryAfterDeactivation.getActive());
        assertEquals(courtScheduleJudiciaryAfterDeactivation.getUpdatedOn().getTime(), updatedOn.getTime());
    }

    @Test
    public void shouldUpdateCourtScheduleJudiciaryPosition() {
        final String courtScheduleId = randomUUID().toString();
        final String judiciaryId = randomUUID().toString();

        final CourtScheduleJudiciary courtScheduleJudiciary = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary.setPosition("6");
        courtScheduleJudiciary.setUpdatedOn(Calendar.getInstance().getTime());
        courtScheduleJudiciary.setActive(false);
        courtScheduleJudiciary.getId().setCourtScheduleId(courtScheduleId);
        courtScheduleJudiciary.getId().setJudiciaryId(judiciaryId);


        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary);

        final String newPosition = "10";
        final Date updatedOn = Calendar.getInstance().getTime();
        courtScheduleJudiciaryRepository.updateCourtScheduleJudiciaryPosition(newPosition, updatedOn, courtScheduleId, judiciaryId);

        final CourtScheduleJudiciary courtScheduleJudiciaryUpdated = courtScheduleJudiciaryRepository.findBy(courtScheduleJudiciary.getId());
        courtScheduleJudiciaryRepository.refresh(courtScheduleJudiciaryUpdated);

        assertEquals(courtScheduleJudiciaryUpdated.getPosition(), newPosition);
        assertEquals(courtScheduleJudiciaryUpdated.getUpdatedOn().getTime(), updatedOn.getTime());
        assertEquals(true, courtScheduleJudiciaryUpdated.getActive());
    }

    @Test
    public void shouldFindInCourtSchedules() {
        final String courtScheduleId1 = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary1 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary1.getId().setCourtScheduleId(courtScheduleId1);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary1);

        final String courtScheduleId2 = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary2 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary2.getId().setCourtScheduleId(courtScheduleId2);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary2);

        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findInCourtScheduleIds(List.of(courtScheduleId1, courtScheduleId2));

        assertEquals(2, courtScheduleJudiciaries.size());
    }

    @Ignore("when removing transactional ut not working, otherwise it receives an exception whilst runtime")
    @Test
    public void shouldDeleteSchedules() {
        final String courtScheduleId1 = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary1 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary1.getId().setCourtScheduleId(courtScheduleId1);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary1);

        final String courtScheduleId2 = randomUUID().toString();
        final CourtScheduleJudiciary courtScheduleJudiciary2 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary2.getId().setCourtScheduleId(courtScheduleId2);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary2);

        courtScheduleJudiciaryRepository.deleteSchedules(List.of(courtScheduleId1, courtScheduleId2));

        final List<CourtScheduleJudiciary> expectedCourtScheduleJudiciary1 = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId1);

        assertTrue(isEmpty(expectedCourtScheduleJudiciary1));

        final List<CourtScheduleJudiciary> expectedCourtScheduleJudiciary2 = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId1);

        assertTrue(isEmpty(expectedCourtScheduleJudiciary2));
    }


    @Ignore("when we remove transactional annotation, then it is causing the assertion to fail - will fix later")
    @Test
    public void shouldDeleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod() {
        final String courtScheduleId1 = randomUUID().toString();
        final String ouCode1 = "B53DT00";
        final CourtScheduleJudiciary courtScheduleJudiciary1 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary1.getId().setCourtScheduleId(courtScheduleId1);
        courtScheduleJudiciary1.setActive(true);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary1);

        final CourtSchedule courtSchedule1 = random(CourtSchedule.class);
        courtSchedule1.setCourtScheduleId(courtScheduleId1);
        courtSchedule1.setMaxSlots(10);
        courtSchedule1.setAvailableSlots(10);
        courtSchedule1.setMaxDuration(0);
        courtSchedule1.setAvailableDuration(0);
        courtSchedule1.setSessionDate(LocalDate.of(2024, 10, 21));
        courtSchedule1.setListingProfileId("CS2995299");
        courtSchedule1.setOuCode(ouCode1);

        courtScheduleRepository.save(courtSchedule1);

        final String courtScheduleId2 = randomUUID().toString();
        final String ouCode2 = "B52BB00";
        final CourtScheduleJudiciary courtScheduleJudiciary2 = random(CourtScheduleJudiciary.class);
        courtScheduleJudiciary2.getId().setCourtScheduleId(courtScheduleId2);
        courtScheduleJudiciary2.setActive(true);

        courtScheduleJudiciaryRepository.save(courtScheduleJudiciary2);

        final CourtSchedule courtSchedule2 = random(CourtSchedule.class);
        courtSchedule2.setCourtScheduleId(courtScheduleId2);
        courtSchedule2.setMaxSlots(10);
        courtSchedule2.setAvailableSlots(10);
        courtSchedule2.setMaxDuration(0);
        courtSchedule2.setAvailableDuration(0);
        courtSchedule2.setSessionDate(LocalDate.of(2024, 10, 15));
        courtSchedule2.setListingProfileId("CS2995299");
        courtSchedule2.setOuCode(ouCode2);

        courtScheduleRepository.save(courtSchedule2);

        final LocalDate startDate = LocalDate.of(2024, 10, 3);
        final LocalDate endDate = LocalDate.of(2024, 10, 31);

        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleJudiciaryRepository.findAll();
        assertEquals(2, courtScheduleJudiciaries.size());
        courtScheduleJudiciaryRepository.deleteUnAllocatedCourtScheduleJudiciariesEntriesForRotaPeriod(startDate, endDate, List.of(ouCode1, ouCode2));

        final List<CourtScheduleJudiciary> courtSchedules = courtScheduleJudiciaryRepository.findByCourtScheduleId(courtScheduleId1);

        assertTrue(isEmpty(courtSchedules));
    }

}