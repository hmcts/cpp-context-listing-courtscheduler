package uk.gov.moj.cpp.courtscheduler.repository;

import static io.github.benas.randombeans.api.EnhancedRandom.random;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.persist.entity.SessionAllocationKey;

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

    @Test
    public void shouldFindByOuCodeAndRoomIdAndListingSessionAndBusinessType() {
        final CourtRoomSessionAllocation courtRoomSessionAllocation = random(CourtRoomSessionAllocation.class);
        final SessionAllocationKey sessionAllocationKey = courtRoomSessionAllocation.getSessionAllocationKey();
        sessionAllocationKey.setOuCode("B01LY00");
        sessionAllocationKey.setBusinessType("TRF");
        sessionAllocationKey.setListingSession("WEDAM");
        sessionAllocationKey.setRoomId(1234);

        courtRoomSessionAllocation.setSessionAllocationKey(sessionAllocationKey);

        courtRoomSessionAllocationRepository.save(courtRoomSessionAllocation);

        final CourtRoomSessionAllocation courtRoomSessionAllocationFound = courtRoomSessionAllocationRepository.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(sessionAllocationKey.getOuCode(), sessionAllocationKey.getRoomId(), sessionAllocationKey.getListingSession(), sessionAllocationKey.getBusinessType());

        assertThat(courtRoomSessionAllocationFound, notNullValue());
        assertEquals(courtRoomSessionAllocationFound.getSessionAllocationKey().getRoomId(), sessionAllocationKey.getRoomId());
        assertEquals(courtRoomSessionAllocationFound.getSessionAllocationKey().getListingSession(), sessionAllocationKey.getListingSession());
        assertEquals(courtRoomSessionAllocationFound.getSessionAllocationKey().getBusinessType(), sessionAllocationKey.getBusinessType());
        assertEquals(courtRoomSessionAllocationFound.getSessionAllocationKey().getOuCode(), sessionAllocationKey.getOuCode());
    }
}