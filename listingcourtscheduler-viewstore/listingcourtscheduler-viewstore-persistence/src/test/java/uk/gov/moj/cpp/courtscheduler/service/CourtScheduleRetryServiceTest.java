package uk.gov.moj.cpp.courtscheduler.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.criteria.CourtScheduleCriteria;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CourtScheduleRetryServiceTest {

    private CourtScheduleRetryService service;

    private EntityManager entityManager;
    private CourtScheduleCriteria criteria;

    @BeforeEach
    void setUp() {
        entityManager = mock(EntityManager.class);
        criteria = mock(CourtScheduleCriteria.class);

        service = spy(new CourtScheduleRetryService());
        service.entityManager = entityManager;
        service.courtScheduleCriteria = criteria;
    }

    @Test
    void testRetryAndSave_CourtScheduleExistsAndUpdated() {
        CourtSchedule inputSchedule = createCourtSchedule();
        CourtSchedule persistedSchedule = createPersistedSchedule();

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<CourtSchedule> cq = mock(CriteriaQuery.class);
        TypedQuery<CourtSchedule> tq = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(CourtSchedule.class)).thenReturn(cq);
        when(entityManager.createQuery(cq)).thenReturn(tq);
        when(tq.getResultList()).thenReturn(List.of(persistedSchedule));

        CourtSchedule result = service.retryAndSave(inputSchedule, true);

        assertNotNull(result);
        assertEquals(inputSchedule.getMaxSlots(), persistedSchedule.getMaxSlots());
        assertEquals(inputSchedule.getAvailableSlots(), persistedSchedule.getAvailableSlots());
        assertEquals(inputSchedule.getAvailableDuration(), persistedSchedule.getAvailableDuration());
        assertTrue(persistedSchedule.isActive());

        verify(entityManager).flush();
    }

    @Test
    void testRetryAndSave_NoPersistedSchedules() {
        CourtSchedule inputSchedule = createCourtSchedule();

        CriteriaBuilder cb = mock(CriteriaBuilder.class);
        CriteriaQuery<CourtSchedule> cq = mock(CriteriaQuery.class);
        TypedQuery<CourtSchedule> tq = mock(TypedQuery.class);

        when(entityManager.getCriteriaBuilder()).thenReturn(cb);
        when(cb.createQuery(CourtSchedule.class)).thenReturn(cq);
        when(entityManager.createQuery(cq)).thenReturn(tq);
        when(tq.getResultList()).thenReturn(Collections.emptyList());

        CourtSchedule result = service.retryAndSave(inputSchedule, true);

        assertNull(result);
        verify(entityManager, never()).flush();
    }

    private CourtSchedule createCourtSchedule() {
        CourtSchedule cs = new CourtSchedule();
        cs.setMaxSlots(10);
        cs.setAvailableSlots(8);
        cs.setAvailableDuration(60);
        cs.setMaxDuration(120);
        cs.setMaxAdMorningDuration(30);
        cs.setMaxAdAfternoonDuration(30);
        cs.setSupportAdSplit(false);
        return cs;
    }

    private CourtSchedule createPersistedSchedule() {
        CourtSchedule cs = new CourtSchedule();
        cs.setMaxSlots(5);
        cs.setAvailableSlots(5);
        cs.setAvailableDuration(30);
        cs.setMaxDuration(60);
        cs.setMaxAdMorningDuration(20);
        cs.setMaxAdAfternoonDuration(20);
        cs.setSupportAdSplit(false);
        cs.setCreatedOn(new Date());
        return cs;
    }
}
