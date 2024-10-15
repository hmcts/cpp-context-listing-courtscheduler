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
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
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
    private static final String ROTASL_STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=sasteccmscsl;AccountKey=+p3GXQguT4npJqxd6gAPfDgLu0YuJ3n1+hpTQYg1BQn0UL5Ut+bDDE7l2qrRNTt/yW5jNyf5mRUmM11F8dnkpA==;EndpointSuffix=core.windows.net;";

    public static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 300;

    private LocalDateTime maxCreatedOnForCourtSchedule;
    private LocalDateTime maxUpdatedOnForCourtSchedule;
    private LocalDateTime maxCreatedOnForCourtScheduleJudiciary;

    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();
    private static final String BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE = "B40IM00";

    @BeforeAll
    static void setupRotaFileProcessorIT() {
        setupUserAsSystemUser(USER_ID.toString());
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");
    }

    @BeforeEach
    public void setUpAzureBlobClientService() throws SQLException {
        databaseSeeder.cleanDb();
        setField(azureBlobClientService, "rotaslStorageConnectionString", ROTASL_STORAGE_CONNECTION_STRING);
        maxCreatedOnForCourtScheduleJudiciary = null;
        maxCreatedOnForCourtSchedule = null;
        maxUpdatedOnForCourtSchedule = null;
    }

    @AfterEach
    public void tearDown() {
        filesToBeDeletedFromOutputContainer.forEach(fileToBeDeleted -> azureBlobClientService.deleteFile(fileToBeDeleted, of(azureBlobOutputContainerName)));
    }

    @Test
    void shouldProcessFullRotaFileForNonMigrated() throws IOException, SQLException {
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", false, 623, 45, 0);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedForMigrated() throws IOException, SQLException {
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", false, 623, 45, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", true, 623, 45, 45);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedEvenListingProfileIdNullForMigrated() throws IOException, SQLException {
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", false, 623, 45, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", true, 623, 45, 45);
    }

    @Test
    void shouldProcessOnlyJudiciaryInfoForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        final String fileBlobBaseName = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
        processFullRotaFile(fileBlobBaseName, false, 620, 11, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 11, 11);
    }

    @Test
    void shouldProcessOnlyJudiciaryInfoAndJudiciaryDataAlreadyExistsForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        final String fileBlobBaseName = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
        processFullRotaFile(fileBlobBaseName, false, 620, 11, 0);
        databaseSeeder.deleteJudiciaryByProfileId("CS4305744");
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 11, 11);
    }

    @Test
    void shouldUpdateJudiciaryInfoAndShouldNotDeleteForTheOnesHavingAllocatedSlots() throws IOException, SQLException {
        final String fileBlobBaseName = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
        processFullRotaFile(fileBlobBaseName, false, 620, 11, 0);
        final Optional<CourtSchedule> courtScheduleOptional = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305744")).findAny();
        databaseSeeder.setUpdateAvailableSlotForCourtSchedule("CS4305744");
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 11, 10);
    }

    @Test
    void shouldProcessAlsoBiggerFile() throws IOException, SQLException {
        final String fileBlobBaseName = "IT_Test_lja_westyorkshire_rota_20240827T154745Z";
        insertCourtSchedulerMigrationStatus(List.of("B13HT00", "B13CC00", "C33LC00", "B13HD00"), false);
        processFullRotaFile(fileBlobBaseName, false, 4251, 3629, 0);
    }

    @Test
    void shouldProcessSnapshotRotaFile() throws SQLException, IOException {
        processFullRotaFile("IT_Test_lja_bedfordshire_rota_20240402T180039Z", false, 623, 45, 0);

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
            return isNotEmpty(courtSchedulesFromSnapshotFile) && courtSchedulesFromSnapshotFile.size() == 211;
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

    private void processFullRotaFile(final String fileBlobBaseName, final boolean migrated,
                                     final int expectedNumberOfSlots,
                                     final int expectedNumberOfJudiciaries,
                                     final int expectedNumberOfJudiciariesCreatedAfterMigration) throws SQLException, IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();

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
        insertCourtSchedulerMigrationStatus(List.of(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE), migrated);

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            if (isNull(maxCreatedOnForCourtScheduleJudiciary)) {
                final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();
                final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
                return courtScheduleJudiciaryEntities.size() == expectedNumberOfJudiciaries && courtScheduleEntities.size() == expectedNumberOfSlots;
            } else {
                final List<CourtScheduleJudiciary> courtScheduleJudiciariesCreatedAfter = databaseReader.courtScheduleJudiciariesCreatedAfter(maxCreatedOnForCourtScheduleJudiciary);
                return isNotEmpty(courtScheduleJudiciariesCreatedAfter) && courtScheduleJudiciariesCreatedAfter.size() == expectedNumberOfJudiciariesCreatedAfterMigration;
            }
        });

        logger.info("master rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        if (!migrated) {
            final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPair = databaseReader.getMaxCreatedOnForCourtSchedule();
            maxCreatedOnForCourtSchedule = maxCreatedUpdatedPair.getLeft();
            maxUpdatedOnForCourtSchedule = maxCreatedUpdatedPair.getRight();
            final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPairForJudiciary = databaseReader.getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary();
            maxCreatedOnForCourtScheduleJudiciary = maxCreatedUpdatedPairForJudiciary.getLeft();
            assertEquals(expectedNumberOfSlots, courtScheduleEntities.size());
            assertEquals(expectedNumberOfJudiciaries, courtScheduleJudiciaryEntities.size());
        } else {
            final List<CourtSchedule> courtSchedulesCreatedForMigrated = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            final List<CourtSchedule> courtSchedulesUpdatedForMigrated = databaseReader.courtSchedulesUpdatedAfter(maxUpdatedOnForCourtSchedule);
            assertTrue(isEmpty(courtSchedulesCreatedForMigrated));
            assertTrue(isEmpty(courtSchedulesUpdatedForMigrated));
            assertEquals(expectedNumberOfSlots, courtScheduleEntities.size());

            final List<CourtScheduleJudiciary> courtScheduleJudiciariesCreatedAfter = databaseReader.courtScheduleJudiciariesCreatedAfter(maxCreatedOnForCourtScheduleJudiciary);
            assertTrue(isNotEmpty(courtScheduleJudiciariesCreatedAfter));
            assertEquals(expectedNumberOfJudiciariesCreatedAfterMigration, courtScheduleJudiciariesCreatedAfter.size());
            assertEquals(expectedNumberOfJudiciaries, courtScheduleJudiciaryEntities.size());
        }

        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }

    private void insertCourtSchedulerMigrationStatus(final List<String> ouCodes, final boolean migrated) throws SQLException {
        for (final String ouCode: ouCodes) {
            final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
            courtSchedulerMigrationStatus.setOuCode(ouCode);
            courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
            courtSchedulerMigrationStatus.setMigrated(migrated);
            databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
        }
    }

    private AllocatedListing getAllocatedListing(final CourtSchedule courtSchedule) {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setOucode(courtSchedule.getOuCode());

        allocatedListing.setHearingStartTime(Date.from(courtSchedule.getSessionDate().atTime(14, 0 ).atZone(ZoneId.of("Europe/London")).toInstant()));

        return allocatedListing;
    }

}
