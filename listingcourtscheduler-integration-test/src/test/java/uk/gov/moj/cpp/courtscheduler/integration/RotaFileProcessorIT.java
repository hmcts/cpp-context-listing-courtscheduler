package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.apache.activemq.artemis.utils.RandomUtil.randomSimpleString;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
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
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Disabled
class RotaFileProcessorIT extends AbstractIT {

    private static Logger logger = LoggerFactory.getLogger(RotaFileProcessorIT.class);

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";

    private AzureBlobClientService azureBlobClientService = new AzureBlobClientService();

    private final String azureBlobInputContainerName = "schedulelistinginput";
    private final String azureBlobOutputContainerName = "schedulelistingoutput";
    private static final String rotaslStorageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sasteccmscsl;AccountKey=+p3GXQguT4npJqxd6gAPfDgLu0YuJ3n1+hpTQYg1BQn0UL5Ut+bDDE7l2qrRNTt/yW5jNyf5mRUmM11F8dnkpA==;EndpointSuffix=core.windows.net;";

    public static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 240;

    private LocalDateTime maxCreatedOnForCourtSchedule;
    private LocalDateTime maxUpdatedOnForCourtSchedule;

    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();

    @BeforeAll
    static void setupRotaFileProcessorIT() {
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

    @AfterEach
    public void tearDown() {
        filesToBeDeletedFromOutputContainer.forEach(fileToBeDeleted -> azureBlobClientService.deleteFile(fileToBeDeleted, of(azureBlobOutputContainerName)));
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
        final String snapshotFileBaseNamePart1 = "IT_Test_lja_bedfodshire";
        final String snapshotFileBaseNamePart2 = "_snapshot_20240403T180039Z";
        final String generatedUniqueFileId = randomUUID().toString();
        final String finalSnapshotFileName = format("%s_%s%s.xml", snapshotFileBaseNamePart1, generatedUniqueFileId, snapshotFileBaseNamePart2);

        final InputStream rotaFileInputStream = getClass().getClassLoader().getResourceAsStream(format("rotafileprocessor/%s%s.xml", snapshotFileBaseNamePart1, snapshotFileBaseNamePart2));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, finalSnapshotFileName, of(azureBlobInputContainerName));

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            return isNotEmpty(courtSchedulesFromSnapshotFile);
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

        filesToBeDeletedFromOutputContainer.add(finalSnapshotFileName);
    }

    private void processFullRotaFile(final boolean migrated) throws SQLException, IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        final String fileBlobBaseName = "IT_Test_lja_bedfordshire_rota_20240402T180039Z";
        final String generatedUniqueFileId = randomSimpleString().toString();
        final String finalMasterRotaFileName = format("%s_%s.xml", fileBlobBaseName, generatedUniqueFileId);
        final InputStream rotaFileInputStream = getClass().getClassLoader().getResourceAsStream(format("rotafileprocessor/%s.xml", fileBlobBaseName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, finalMasterRotaFileName, of(azureBlobInputContainerName));
        insertCourtSchedulerMigrationStatus(migrated);

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();
            return isNotEmpty(courtScheduleJudiciaryEntities);
        });

        logger.info("master rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        if (!migrated) {
            final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPair = databaseReader.getMaxCreatedOnFromCourtSchedule();
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

        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }

    private void insertCourtSchedulerMigrationStatus(final boolean migrated) throws SQLException {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("B40IM00");
        courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
        courtSchedulerMigrationStatus.setMigrated(migrated);
        databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
    }

}
