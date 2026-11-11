package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.service.CourtScheduleRetryService;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SPRDT-1550: a week commencing row and an ordinary draft session can share oucode, room, business
 * type and Monday, because is_week_commencing is part of the unique indexes. The try-insert-then-update
 * fallback must only ever update a row of the same kind as the incoming one; otherwise re-submitting
 * one would overwrite the other's capacity.
 */
class WeekCommencingUpsertTest extends AbstractRepositoryTest {

    private static final String OU_CODE = "C55WC00";
    private static final String BUSINESS_TYPE = "TRL";
    private static final int ORDINARY_CAPACITY = 360;
    private static final int WEEK_COMMENCING_CAPACITY = 1800;

    @Autowired
    private CourtScheduleRepository repository;

    @Autowired
    private CourtScheduleRetryService retryService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistedScheduleLookupShouldOnlyReturnRowsOfTheIncomingKind() {
        final String courtRoomId = randomUUID().toString();
        final LocalDate monday = LocalDate.of(2099, 1, 5);
        final CourtSchedule ordinary = repository.save(session(courtRoomId, monday, false, ORDINARY_CAPACITY));
        final CourtSchedule weekCommencing = repository.save(session(courtRoomId, monday, true, WEEK_COMMENCING_CAPACITY));

        assertThat(retryService.findPersistedSchedules(session(courtRoomId, monday, false, 1)))
                .extracting(CourtSchedule::getCourtScheduleId).containsExactly(ordinary.getCourtScheduleId());
        assertThat(retryService.findPersistedSchedules(session(courtRoomId, monday, true, 1)))
                .extracting(CourtSchedule::getCourtScheduleId).containsExactly(weekCommencing.getCourtScheduleId());
    }

    @Test
    // NOT_SUPPORTED so saveCourtSchedules runs its real insert / constraint violation / update path
    // in its own transactions, as it does behind POST /courtschedule.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void resubmittingAnOrdinarySessionShouldUpdateItAndLeaveTheWeekCommencingRowAlone() {
        final String courtRoomId = randomUUID().toString();
        final LocalDate monday = LocalDate.of(2099, 1, 12);
        final CourtSchedule ordinary = repository.save(session(courtRoomId, monday, false, ORDINARY_CAPACITY));
        final CourtSchedule weekCommencing = repository.save(session(courtRoomId, monday, true, WEEK_COMMENCING_CAPACITY));

        repository.saveCourtSchedules(List.of(session(courtRoomId, monday, false, 420)));

        assertThat(repository.findById(ordinary.getCourtScheduleId()).orElseThrow().getMaxDuration()).isEqualTo(420);
        assertThat(repository.findById(weekCommencing.getCourtScheduleId()).orElseThrow().getMaxDuration())
                .isEqualTo(WEEK_COMMENCING_CAPACITY);
    }

    /**
     * The week commencing creation lock is held by the caller's transaction: another transaction cannot
     * take it for the same court centre and business type, but can for a different business type.
     */
    @Test
    void weekCommencingCreationLockShouldBeHeldUntilTheCallersTransactionEnds() {
        final String courtCentreId = randomUUID().toString();
        repository.lockWeekCommencingCreation(courtCentreId, BUSINESS_TYPE);

        final TransactionTemplate otherTransaction = new TransactionTemplate(transactionManager);
        otherTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final Boolean sameKeyAcquired = otherTransaction.execute(status -> tryLock(courtCentreId + "|" + BUSINESS_TYPE));
        final Boolean otherKeyAcquired = otherTransaction.execute(status -> tryLock(courtCentreId + "|LGT"));
        assertThat(sameKeyAcquired).isFalse();
        assertThat(otherKeyAcquired).isTrue();
    }

    private Boolean tryLock(final String lockKey) {
        return (Boolean) entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(1550, hashtext(:lockKey))")
                .setParameter("lockKey", lockKey)
                .getSingleResult();
    }

    private CourtSchedule session(final String courtRoomId, final LocalDate monday, final boolean weekCommencing,
                                  final int capacity) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(randomUUID().toString());
        cs.setOuCode(OU_CODE);
        cs.setCourtHouseId("CH" + OU_CODE);
        cs.setCourtRoomId(courtRoomId);
        cs.setBusinessType(BUSINESS_TYPE);
        cs.setSessionDate(monday);
        cs.setCourtSession("AD");
        cs.setPanel("ADULT");
        cs.setJurisdiction("CROWN");
        cs.setActive(true);
        cs.setSlotBased(false);
        cs.setIsDraft(true);
        cs.setWeekCommencing(weekCommencing);
        cs.setMaxSlots(0);
        cs.setAvailableSlots(0);
        cs.setMaxDuration(capacity);
        cs.setAvailableDuration(capacity);
        cs.setMaxAdMorningDuration(0);
        cs.setMaxAdAfternoonDuration(0);
        cs.setSupportAdSplit(false);
        cs.setIsOverbookingAllowed(false);
        cs.setSessionStartTime(Instant.now());
        cs.setSessionEndTime(Instant.now());
        cs.setNationalBreakTime(Instant.now());
        return cs;
    }
}
