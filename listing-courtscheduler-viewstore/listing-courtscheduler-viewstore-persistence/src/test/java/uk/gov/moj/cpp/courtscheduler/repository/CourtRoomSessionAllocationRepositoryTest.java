package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;

import java.util.List;

import javax.inject.Inject;

import org.apache.deltaspike.testcontrol.api.junit.CdiTestRunner;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(CdiTestRunner.class)
public class CourtRoomSessionAllocationRepositoryTest {
    @Inject
    private CourtRoomSessionAllocationRepository courtRoomSessionAllocationRepository;

    @After
    public void tearDown() {
        List<CourtRoomSessionAllocation> all = courtRoomSessionAllocationRepository.findAll();
        all.forEach(courtRoomSessionAllocation -> courtRoomSessionAllocationRepository.remove(courtRoomSessionAllocation));
    }

    @Test
    public void shouldSave() {
        final CourtRoomSessionAllocation courtRoomSessionAllocation = random(CourtRoomSessionAllocation.class);

        courtRoomSessionAllocationRepository.save(courtRoomSessionAllocation);
        CourtRoomSessionAllocation by = courtRoomSessionAllocationRepository.findBy(courtRoomSessionAllocation.getSessionAllocationKey());

        assertThat(by, notNullValue());

    }
}