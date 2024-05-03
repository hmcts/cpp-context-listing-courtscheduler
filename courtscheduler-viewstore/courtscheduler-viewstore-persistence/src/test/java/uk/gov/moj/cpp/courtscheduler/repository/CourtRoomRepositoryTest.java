package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom;

import javax.inject.Inject;
import java.util.List;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

@RunWith(CdiTestRunner.class)
public class CourtRoomRepositoryTest {
    @Inject
    private CourtRoomRepository courtRoomRepository;

    @After
    public void tearDown() {
        List<CourtRoom> all = courtRoomRepository.findAll();
        all.forEach(courtRoom -> courtRoomRepository.remove(courtRoom));
    }

    @Test
    public void shouldSave() {
        final CourtRoom courtRoomSessionAllocation = random(CourtRoom.class);

        courtRoomRepository.save(courtRoomSessionAllocation);
        CourtRoom by = courtRoomRepository.findBy(courtRoomSessionAllocation.getId());

        assertThat(by, notNullValue());

    }

    @Test
    public void shouldFindByVenue() {
        final CourtRoom courtRoomSessionAllocation = random(CourtRoom.class);

        courtRoomRepository.save(courtRoomSessionAllocation);
        CourtRoom by = courtRoomRepository.findByVenue(courtRoomSessionAllocation.getVenue());

        assertThat(by, notNullValue());
    }
}