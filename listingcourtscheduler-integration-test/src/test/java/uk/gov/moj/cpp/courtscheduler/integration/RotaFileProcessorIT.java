package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil.getPayload;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.StorageApplicationParameters;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Deprecated
class RotaFileProcessorIT extends AbstractIT {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileProcessorIT.class);

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";
    private static final String ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL = "/rotasl/clean-redundant-rota-data";

    private final AzureBlobClientService azureBlobClientService = new AzureBlobClientService();

    private final String azureBlobInputContainerName = "schedulelistinginput";
    private final String azureBlobOutputContainerName = "schedulelistingoutput";
    private static final String ROTASL_STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=sasteccmscsl;AccountKey=C5l7paX+ELY0X3aDMTrOyXxBE69CMS1pqkYX9XTGdHI7x1jP15VM1FUizCoEmwOo9ML3Bgz0IYA4+AStJIs0kQ==;EndpointSuffix=core.windows.net";

    public static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 50;
    public static final int DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC = 50;

    private LocalDateTime maxCreatedOnForCourtSchedule;
    private LocalDateTime maxUpdatedOnForCourtSchedule;
    private LocalDateTime maxCreatedOnForCourtScheduleJudiciary;

    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();
    private static final String BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE = "B40IM00";
    private static final String BEDFORD_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T180039Z";
    private static final String BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
    private static final String WESTYORK_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_westyorkshire_rota_20240827T154745Z";


    @BeforeEach
    void setUpAzureBlobClientService() {
        final StorageApplicationParameters storageApplicationParameters = new StorageApplicationParameters();

        setField(azureBlobClientService, "rotaslStorageConnectionString", ROTASL_STORAGE_CONNECTION_STRING);
        setField(azureBlobClientService, "rotaslInputContainerName", azureBlobInputContainerName);
        setField(azureBlobClientService, "rotaslArchiveContainerName", azureBlobInputContainerName);
        setField(azureBlobClientService, "storageApplicationParameters", storageApplicationParameters);
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
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedForMigrated() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, true, 623, 66, 66);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedEvenListingProfileIdNullForMigrated() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, true, 623, 66, 66);
    }

    @Test
    void shouldProcessOnlyJudiciaryInfoForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        final String fileBlobBaseName = BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME;
        processFullRotaFile(fileBlobBaseName, false, 620, 32, 0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 32, 32);
    }

    @Test
    @Disabled
    void shouldProcessOnlyJudiciaryInfoAndJudiciaryDataAlreadyExistsForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        final String fileBlobBaseName = BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME;
        processFullRotaFile(fileBlobBaseName, false, 620, 32, 0);
        databaseSeeder.deleteJudiciaryByProfileId("CS4305744");
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 32, 32);
    }

    @Test
    void shouldUpdateJudiciaryInfoAndShouldNotDeleteForTheOnesHavingAllocatedSlots() throws IOException, SQLException {
        final String fileBlobBaseName = BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME;
        processFullRotaFile(fileBlobBaseName, false, 620, 32, 0);
        final Optional<CourtSchedule> courtScheduleOptional = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305744")).findAny();
        databaseSeeder.setUpdateAvailableSlotForCourtSchedule("CS4305744");
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(fileBlobBaseName, true, 620, 32, 32);
    }

    @Test
    void shouldProcessAlsoBiggerFile() throws IOException, SQLException {
        insertCourtSchedulerMigrationStatus(List.of("B13HT00", "B13CC00", "C33LC00", "B13HD00"), false);
        processFullRotaFile(WESTYORK_SHIRE_MASTER_FILE_BASE_NAME, false, 4687, 3813, 0);
    }

    @Test
    void shouldProcessSnapshotRotaFile() throws SQLException, IOException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);

        final Optional<CourtSchedule> courtScheduleOptional = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305478")).findAny();
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final LocalDate snapshotFileStartDate = LocalDate.of(2024, 8, 1);
        final String snapshotFileBaseNamePart1 = "IT_Test_lja_bedfordshire";
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
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", SYSTEM_USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            return isNotEmpty(courtSchedulesFromSnapshotFile) && courtSchedulesFromSnapshotFile.size() == 210;
        });

        logger.info("snapshot rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        assertEquals(210, courtSchedulesFromSnapshotFile.size());
        assertEquals(210, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isAfter(snapshotFileStartDate) || courtSchedule.getSessionDate().isEqual(snapshotFileStartDate)).toList().size());
        assertEquals(0, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(snapshotFileStartDate)).toList().size());
        assertEquals(66, courtScheduleJudiciaryEntities.size());

        final Optional<CourtSchedule> allocatedSlotNotBeingInSnapshotFile = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305478")).findAny();
        assertTrue(allocatedSlotNotBeingInSnapshotFile.isPresent());
        assertTrue(allocatedSlotNotBeingInSnapshotFile.get().isActive());

        assertDefaultStartTimeAndEndTime(courtSchedulesFromSnapshotFile);

        filesToBeDeletedFromOutputContainer.add(finalSnapshotFileName);
    }

    @Test
    void shouldProcessSnapshotRotaFileWithHavingJudiciaryDataChanged() throws SQLException, IOException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);

        final Optional<CourtSchedule> courtScheduleOptional = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305478")).findAny();
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
        final Optional<CourtSchedule> courtScheduleOptionalForCS4304756 = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4304756")).findAny();
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptionalForCS4304756.get()));

        final LocalDate snapshotFileStartDate = LocalDate.of(2024, 8, 1);
        final String snapshotFileBaseNamePart1 = "IT_Test_lja_bedfordshire";
        final String snapshotFileBaseNamePart2 = "_snapshot_20240403T180039Z";
        processSnapshotFile(snapshotFileBaseNamePart2, snapshotFileStartDate, 0, 209);

        final LocalDate secondSnapshotFileStartDate = LocalDate.of(2024, 8, 2);
        final String secondSnapshotFileBaseNamePart1 = "IT_Test_lja_bedfordshire";
        final String secondSnapshotFileBaseNamePart2 = "_snapshot_20240802T180039Z";

        processSnapshotFile(secondSnapshotFileBaseNamePart2, secondSnapshotFileStartDate, 5, 204);
    }

    private void processSnapshotFile(final String snapshotFileBaseNamePart2,
                                     final LocalDate snapshotFileStartDate,
                                     final int numberOfCreatedSlotBeforeSnapshotFile,
                                     final int numberOfCreatedSlotFromSnapshot) throws IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String generatedUniqueFileId = randomUUID().toString();
        final String finalSnapshotFileName = format("%s_%s%s.xml", "IT_Test_lja_bedfordshire", generatedUniqueFileId, snapshotFileBaseNamePart2);

        final InputStream rotaFileInputStream = getClass().getClassLoader().getResourceAsStream(format("rotafileprocessor/%s%s.xml", "IT_Test_lja_bedfordshire", snapshotFileBaseNamePart2));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, finalSnapshotFileName, of(azureBlobInputContainerName));

        final String payloadAsJsonString = getPayload("rota-file-processor-request.json");
        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", SYSTEM_USER_ID, payloadAsJsonString);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            return isNotEmpty(courtSchedulesFromSnapshotFile) && courtSchedulesFromSnapshotFile.size() == 209;
        });

        logger.info("snapshot rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtSchedulesFromSnapshotFile = databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        assertEquals(209, courtSchedulesFromSnapshotFile.size());
        assertEquals(numberOfCreatedSlotFromSnapshot, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isAfter(snapshotFileStartDate) || courtSchedule.getSessionDate().isEqual(snapshotFileStartDate)).toList().size());
        assertEquals(numberOfCreatedSlotBeforeSnapshotFile, courtSchedulesFromSnapshotFile.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(snapshotFileStartDate)).toList().size());
        assertEquals(66, courtScheduleJudiciaryEntities.size());

        final Optional<CourtSchedule> allocatedSlotNotBeingInSnapshotFile = databaseReader.courtSchedules().stream().filter(courtSchedule -> courtSchedule.getListingProfileId().equals("CS4305478")).findAny();
        assertTrue(allocatedSlotNotBeingInSnapshotFile.isPresent());
        assertTrue(allocatedSlotNotBeingInSnapshotFile.get().isActive());

        filesToBeDeletedFromOutputContainer.add(finalSnapshotFileName);
    }

    @Test
    void shouldCleanRedundantRotaData() throws IOException, SQLException {
        final int numberOfPreviousMonthsAndOlder = 6;
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, false, 623, 66, 0);

        final int numberOfPreviousDaysAndOlder = numberOfPreviousMonthsAndOlder * 30;
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final List<CourtSchedule> courtSchedules180DaysOlderOrMore = courtSchedules.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(LocalDate.now().minusDays(numberOfPreviousDaysAndOlder)))
                .toList();
        insertAllocatedListingsForCourtSchedules(courtSchedules180DaysOlderOrMore);
        final int numberOf180DaysOrOlderThan = courtSchedules180DaysOlderOrMore.size();
        final int numberOfTotalCourtSchedules = courtSchedules.size();

        final List<AllocatedListing> allocatedListings = databaseReader.allocatedListings();
        assertEquals(numberOf180DaysOrOlderThan, allocatedListings.size());

        String payloadAsJsonString = getPayload("rota-clean-redundant-data-request.json");
        payloadAsJsonString = payloadAsJsonString.replace("NUMBER_OF_PREVIOUS_MONTHS_AND_OLDER", String.valueOf(numberOfPreviousMonthsAndOlder));
        // then call rota file processor api
        final Response response = postCommand(ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL, "application/vnd.courtscheduler.rotasl.clean_redundant_rota_data+json", SYSTEM_USER_ID, payloadAsJsonString);
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final LocalDate oneHundredAnd80DaysBeforeNow = LocalDate.now().minusDays(numberOfPreviousDaysAndOlder);
        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
            return courtScheduleEntities.stream()
                    .filter(courtSchedule -> courtSchedule.getSessionDate().isAfter(oneHundredAnd80DaysBeforeNow) || courtSchedule.getSessionDate().isEqual(oneHundredAnd80DaysBeforeNow))
                    .toList().size() == (numberOfTotalCourtSchedules - numberOf180DaysOrOlderThan);
        });

        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        assertTrue(courtScheduleEntities.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(oneHundredAnd80DaysBeforeNow))
                .findAny()
                .isEmpty());
        assertThat(courtScheduleEntities.size(), is(numberOfTotalCourtSchedules - numberOf180DaysOrOlderThan));

        assertTrue(databaseReader.allocatedListings().isEmpty());
    }


    private void processFullRotaFile(final String fileBlobBaseName,
                                     final boolean migrated,
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
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", SYSTEM_USER_ID, payloadAsJsonString);

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

        if (!migrated) {
            assertDefaultStartTimeAndEndTime(courtScheduleEntities);
        }

        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }

    private void insertCourtSchedulerMigrationStatus(final List<String> ouCodes, final boolean migrated) throws SQLException {
        for (final String ouCode : ouCodes) {
            final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
            courtSchedulerMigrationStatus.setOuCode(ouCode);
            courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
            courtSchedulerMigrationStatus.setMigrated(migrated);
            databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
        }
    }

    private void insertAllocatedListingsForCourtSchedules(final List<CourtSchedule> courtSchedules180DaysOlderOrMore) throws SQLException {
        if (courtSchedules180DaysOlderOrMore.isEmpty()) {
            return;
        }

        final List<AllocatedListing> allocatedListings = new ArrayList<>();

        // Use a single connection for all database operations
        try (Connection connection = databaseSeeder.getNewConnection()) {
            connection.setAutoCommit(false);

            for (final CourtSchedule courtSchedule180DaysOlderOrMore : courtSchedules180DaysOlderOrMore) {
                final CourtSchedule courtSchedule = databaseReader.courtScheduleById(courtSchedule180DaysOlderOrMore.getCourtScheduleId(), connection);
                if (nonNull(courtSchedule)) {
                    allocatedListings.add(getAllocatedListing(courtSchedule180DaysOlderOrMore));
                } else {
                    logger.info("courtScheduleId not found to be inserted to allocated_listings: {}", courtSchedule180DaysOlderOrMore.getCourtScheduleId());
                }
            }

            if (!allocatedListings.isEmpty()) {
                databaseSeeder.insertAllocatedListingsBatch(allocatedListings, connection);
            }

            connection.commit();
        }
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
    
    private static void assertDefaultStartTimeAndEndTime(final List<CourtSchedule> courtSchedules) {
        courtSchedules.forEach(courtSchedule -> {
            assertNotNull(courtSchedule.getSessionStartTime());
            assertNotNull(courtSchedule.getSessionEndTime());

            if (AM_SESSION.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_MORNING_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_MORNING_END_TIME));
            } else if (PM_SESSION.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_AFTERNOON_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_AFTERNOON_END_TIME));
            } else if (ALL_DAY.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_ALL_DAY_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_ALL_DAY_END_TIME));
            }
        });
    }
}
