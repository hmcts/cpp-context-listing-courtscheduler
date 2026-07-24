package uk.gov.moj.cpp.courtscheduler.repository;

import static java.util.UUID.randomUUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

/**
 * Reproduces the priming-job 500: a second {@code POST /courtschedule} for the
 * same {@code (oucode, court_room_id, rota_business_type, session_start,
 * court_session)} combination causes Hibernate's batch flush to throw a
 * unique-constraint violation, which marks the surrounding JPA transaction
 * rollback-only. The catch-and-retry path in {@code processIndividualRecordsFast}
 * recovers each individual record via {@code @Transactional(REQUIRES_NEW)}, but
 * the outer transaction is already poisoned and Spring throws
 * {@link org.springframework.transaction.UnexpectedRollbackException} at
 * commit time — surfacing as HTTP 500 to priming.
 *
 * <p>This test asserts the contract priming relies on: calling
 * {@link CourtScheduleRepository#saveCourtSchedules(java.util.List)} with a
 * payload whose natural key collides with an existing row must complete
 * cleanly (the existing row is silently updated), not throw.</p>
 */
class SaveCourtSchedulesDuplicateTest extends AbstractRepositoryTest {

    @Autowired
    private CourtScheduleRepository repository;

    @Test
    @DisplayName("saveCourtSchedules silently upserts when the natural key already exists, without poisoning the transaction")
    // NOT_SUPPORTED so saveCourtSchedules opens its own transaction and the
    // commit happens before this method returns — that's where the bug
    // surfaces. With the default test-managed transaction, the commit is
    // deferred to test cleanup and the rollback-only flag is never tested.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void doesNotPoisonTransactionOnDuplicateNaturalKey() {
        final String oucode = "OU01";
        final String courtRoomId = randomUUID().toString();
        final String businessType = "DVLA";
        final LocalDate sessionDate = LocalDate.of(2099, 1, 1);
        final String courtSession = "PM";

        // Arrange: an existing active schedule that the duplicate's natural key will collide with.
        final CourtSchedule existing = buildSchedule(oucode, courtRoomId, businessType, sessionDate, courtSession);
        repository.save(existing);

        // Act: a second schedule with a different PK but the same natural key.
        final CourtSchedule duplicate = buildSchedule(oucode, courtRoomId, businessType, sessionDate, courtSession);

        // Assert: must not throw. Under the bug this raises
        // UnexpectedRollbackException because the batch-flush failure marked
        // the outer transaction rollback-only.
        assertThatNoException()
                .isThrownBy(() -> repository.saveCourtSchedules(List.of(duplicate)));

        // And the existing row is still present (not deleted by a phantom rollback).
        assertThat(repository.findById(existing.getCourtScheduleId())).isPresent();
    }

    private CourtSchedule buildSchedule(final String oucode,
                                        final String courtRoomId,
                                        final String businessType,
                                        final LocalDate sessionDate,
                                        final String courtSession) {
        final CourtSchedule cs = random(CourtSchedule.class);
        cs.setCourtScheduleId(randomUUID().toString());
        cs.setOuCode(oucode);
        cs.setCourtRoomId(courtRoomId);
        cs.setBusinessType(businessType);
        cs.setSessionDate(sessionDate);
        cs.setCourtSession(courtSession);
        cs.setActive(true);
        cs.setSlotBased(true);
        cs.setIsDraft(false);
        cs.setMaxSlots(20);
        cs.setMaxDuration(0);
        cs.setAvailableSlots(20);
        cs.setAvailableDuration(0);
        cs.setMaxAdMorningDuration(0);
        cs.setMaxAdAfternoonDuration(0);
        cs.setSupportAdSplit(false);
        cs.setIsOverbookingAllowed(true);
        cs.setSessionStartTime(new Date());
        cs.setSessionEndTime(new Date());
        cs.setNationalBreakTime(new Date());
        return cs;
    }
}
