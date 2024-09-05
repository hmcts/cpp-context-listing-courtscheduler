package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataCourtRoomSessionAllocations;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import javax.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class RotaFileProcessorIT extends AbstractIT {

    private static Logger logger = LoggerFactory.getLogger(RotaFileProcessorIT.class);

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";

    private AzureBlobClientService azureBlobClientService = new AzureBlobClientService();

    private final String azureBlobInputContainerName = "schedulelistinginput";
    private final String azureBlobOutputContainerName = "schedulelistingoutput";
    private static final String rotaslStorageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sadevcommonscsl;AccountKey=HMx/mhSuq/1Gbf7R/d+WmuP8X9w3eqvYS3Sg9rhvch0KLO5Qr+rcS70emQKRLLJptS5GzcBiOdQe+AStaKyOig==;EndpointSuffix=core.windows.net;";

    public static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 240;

    private LocalDateTime maxCreatedOnForCourtSchedule;
    private LocalDateTime maxUpdatedOnForCourtSchedule;

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
    }

    @BeforeEach
    public void setUpAzureBlobClientService() {
        setField(azureBlobClientService, "rotaslStorageConnectionString", rotaslStorageConnectionString);
    }

    @Test
    void shouldProcessFullRotaFileForNonMigrated() throws IOException, SQLException {
        processFullRotaFile(false);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedForMigrated() throws IOException, SQLException {
        processFullRotaFile(false);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        processFullRotaFile(true);
    }

    @Test
    void shouldProcessSnapshotRotaFile() throws SQLException, IOException {
        processFullRotaFile(false);

        final Stopwatch stopwatch = Stopwatch.createStarted();
        final LocalDate snapshotFileStartDate = LocalDate.of(2024, 8, 1);
        final String snapshotFileName = "IT_Test_lja_bedfodshire_snapshot_20240403T180039Z.xml";
        azureBlobClientService.deleteFile(snapshotFileName, of(azureBlobOutputContainerName));
        final InputStream rotaFileInputStream = FileUtil.class.getClassLoader().getResourceAsStream(format("rotafileprocessor/%s", snapshotFileName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, snapshotFileName, of(azureBlobInputContainerName));

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final byte[] downloadedBlobFromOutputContainer = azureBlobClientService.downloadFile(snapshotFileName, azureBlobOutputContainerName);
            return nonNull(downloadedBlobFromOutputContainer) && downloadedBlobFromOutputContainer.length > 0;
        });

        logger.info("snapshot rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        assertEquals(211, courtSchedulesFromSnapshotFile.size());
        assertEquals(211, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isAfter(snapshotFileStartDate) || courtSchedule.getSessionDate().isEqual(snapshotFileStartDate)).toList().size());
        assertEquals(0, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(snapshotFileStartDate)).toList().size());
        assertEquals(45, courtScheduleJudiciaryEntities.size());
    }

    private void processFullRotaFile(final boolean migrated) throws SQLException, IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        final String fileBlobName = "IT_Test_lja_bedfordshire_rota_20240402T180039Z.xml";
        azureBlobClientService.deleteFile(fileBlobName, of(azureBlobOutputContainerName));
        final InputStream rotaFileInputStream = FileUtil.class.getClassLoader().getResourceAsStream(format("rotafileprocessor/%s", fileBlobName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, fileBlobName, of(azureBlobInputContainerName));
        insertCourtSchedulerMigrationStatus(migrated);

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final byte[] downloadedBlobFromOutputContainer = azureBlobClientService.downloadFile(fileBlobName, azureBlobOutputContainerName);
            return nonNull(downloadedBlobFromOutputContainer) && downloadedBlobFromOutputContainer.length > 0;
        });

        logger.info("master rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        if (!migrated) {
            Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPair = databaseReader.getMaxCreatedOnFromCourtSchedule();
            maxCreatedOnForCourtSchedule = maxCreatedUpdatedPair.getLeft();
            maxUpdatedOnForCourtSchedule = maxCreatedUpdatedPair.getRight();
            assertEquals(623, courtScheduleEntities.size());
        } else {
            final List<CourtSchedule> courtSchedulesCreatedForMigrated = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            final List<CourtSchedule> courtSchedulesUpdatedForMigrated = databaseReader.courtSchedulesUpdatedAfter(maxUpdatedOnForCourtSchedule);
            assertTrue(isEmpty(courtSchedulesCreatedForMigrated));
            assertTrue(isEmpty(courtSchedulesUpdatedForMigrated));
        }
        assertEquals(45, courtScheduleJudiciaryEntities.size());
    }

    private void insertCourtSchedulerMigrationStatus(final boolean migrated) throws SQLException {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("B40IM00");
        courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
        courtSchedulerMigrationStatus.setMigrated(migrated);
        databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
    }

}
