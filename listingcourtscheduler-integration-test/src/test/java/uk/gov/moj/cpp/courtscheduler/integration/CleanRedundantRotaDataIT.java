package uk.gov.moj.cpp.courtscheduler.integration;

import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.Test;

class CleanRedundantRotaDataIT extends AbstractIT {

    private static final String ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL = "/rotasl/clean-redundant-rota-data";
    private static final String ROTASL_CLEAN_REDUNDANT_ROTA_DATA_CONTENT_TYPE = "application/vnd.courtscheduler.rotasl.clean_redundant_rota_data+json";
    private static final String COURT_SCHEDULE_SEED_RESOURCE = "rotafileprocessor/seed/court_schedule_IT_Test_lja_bedfordshire_rota_20240402T180039Z.csv";
    private static final int EXPECTED_SEEDED_COURT_SCHEDULES = 623;
    private static final int DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC = 120;
    private static final int NUMBER_OF_OLD_COURT_SCHEDULES_WITH_JUDICIARY = 5;

    @Test
    void shouldCleanRedundantRotaData() throws Exception {
        final int numberOfPreviousMonthsAndOlder = 6;
        assertEquals(EXPECTED_SEEDED_COURT_SCHEDULES, databaseSeeder.seedCourtSchedulesFromCsv(COURT_SCHEDULE_SEED_RESOURCE));

        final int numberOfPreviousDaysAndOlder = numberOfPreviousMonthsAndOlder * 30;
        final LocalDate cutOffDate = LocalDate.now().minusDays(numberOfPreviousDaysAndOlder);
        final List<CourtSchedule> courtSchedulesOlderThanCutOff = databaseReader.courtSchedules().stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(cutOffDate))
                .toList();

        // A court schedule inside the retention window, with judiciary, that the clean-up must keep.
        final CourtSchedule recentCourtSchedule = RANDOM.nextObject(CourtSchedule.class);
        recentCourtSchedule.setCourtScheduleId(randomUUID().toString());
        recentCourtSchedule.setSessionDate(LocalDate.now().plusDays(30));
        databaseSeeder.insertCourtSchedule(recentCourtSchedule);
        insertJudiciaryFor(recentCourtSchedule);

        final List<CourtSchedule> oldCourtSchedulesWithJudiciary = courtSchedulesOlderThanCutOff.subList(0, NUMBER_OF_OLD_COURT_SCHEDULES_WITH_JUDICIARY);
        for (final CourtSchedule oldCourtSchedule : oldCourtSchedulesWithJudiciary) {
            insertJudiciaryFor(oldCourtSchedule);
        }
        databaseSeeder.insertAllocatedListingsBatch(courtSchedulesOlderThanCutOff.stream().map(this::getAllocatedListing).toList());
        assertEquals(courtSchedulesOlderThanCutOff.size(), databaseReader.allocatedListings().size());
        assertEquals(NUMBER_OF_OLD_COURT_SCHEDULES_WITH_JUDICIARY + 1, databaseReader.courtScheduleJudiciaries().size());

        final String payloadAsJsonString = getPayload("rota-clean-redundant-data-request.json")
                .replace("NUMBER_OF_PREVIOUS_MONTHS_AND_OLDER", String.valueOf(numberOfPreviousMonthsAndOlder));
        final Response response = postCommand(ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL, ROTASL_CLEAN_REDUNDANT_ROTA_DATA_CONTENT_TYPE,
                SYSTEM_USER_ID, payloadAsJsonString);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // Wait until cleanup has removed every schedule older than the cut-off.
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC, SECONDS).until(() ->
                databaseReader.courtSchedules().stream()
                        .noneMatch(courtSchedule -> courtSchedule.getSessionDate().isBefore(cutOffDate)));

        final List<CourtSchedule> remainingCourtSchedules = databaseReader.courtSchedules();
        assertThat(remainingCourtSchedules.size(), is(EXPECTED_SEEDED_COURT_SCHEDULES + 1 - courtSchedulesOlderThanCutOff.size()));
        assertTrue(remainingCourtSchedules.stream().anyMatch(courtSchedule -> courtSchedule.getCourtScheduleId().equals(recentCourtSchedule.getCourtScheduleId())),
                "court schedule inside the retention window must be kept");
        assertTrue(databaseReader.allocatedListings().isEmpty());

        final Set<String> courtScheduleIdsWithJudiciary = databaseReader.courtScheduleJudiciaries().stream()
                .map(courtScheduleJudiciary -> courtScheduleJudiciary.getId().getCourtScheduleId())
                .collect(Collectors.toSet());
        assertEquals(Set.of(recentCourtSchedule.getCourtScheduleId()), courtScheduleIdsWithJudiciary,
                "only judiciary on court schedules inside the retention window must be kept");
    }

    private void insertJudiciaryFor(final CourtSchedule courtSchedule) throws Exception {
        final CourtScheduleJudiciary courtScheduleJudiciary = RANDOM.nextObject(CourtScheduleJudiciary.class);
        courtScheduleJudiciary.setId(new CourtScheduleJudiciaryKey(courtSchedule.getCourtScheduleId(), randomUUID().toString()));
        databaseSeeder.saveJudiciarySchedule(courtScheduleJudiciary);
    }

    private AllocatedListing getAllocatedListing(final CourtSchedule courtSchedule) {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setOucode(courtSchedule.getOuCode());
        allocatedListing.setHearingStartTime(Date.from(courtSchedule.getSessionDate().atTime(14, 0).atZone(UTC_ZONE).toInstant()));
        return allocatedListing;
    }
}
