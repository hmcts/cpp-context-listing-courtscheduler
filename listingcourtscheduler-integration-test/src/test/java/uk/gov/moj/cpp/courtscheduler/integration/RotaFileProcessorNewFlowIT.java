package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.apache.activemq.artemis.utils.RandomUtil.randomSimpleString;
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

class RotaFileProcessorNewFlowIT extends AbstractIT {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileProcessorNewFlowIT.class);

    // ============================================================================
    // Constants - API URLs
    // ============================================================================
    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";
    private static final String ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL = "/rotasl/clean-redundant-rota-data";
    private static final String CONTENT_TYPE_PROCESS_ROTA_FILES = "application/vnd.courtscheduler.rotasl.process_rota_files+json";
    private static final String CONTENT_TYPE_CLEAN_REDUNDANT_DATA = "application/vnd.courtscheduler.rotasl.clean_redundant_rota_data+json";

    // ============================================================================
    // Constants - Azure Blob Storage
    // ============================================================================
    private static final String AZURE_BLOB_INPUT_CONTAINER_NAME = "schedulelistinginput";
    private static final String AZURE_BLOB_OUTPUT_CONTAINER_NAME = "schedulelistingoutput";
    private static final String ROTASL_STORAGE_CONNECTION_STRING = "DefaultEndpointsProtocol=https;AccountName=sasteccmscsl;AccountKey=C5l7paX+ELY0X3aDMTrOyXxBE69CMS1pqkYX9XTGdHI7x1jP15VM1FUizCoEmwOo9ML3Bgz0IYA4+AStJIs0kQ==;EndpointSuffix=core.windows.net";

    // ============================================================================
    // Constants - Timeouts
    // ============================================================================
    private static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 50;
    private static final int DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC = 50;

    // ============================================================================
    // Constants - Test Data Files
    // ============================================================================
    private static final String PAYLOAD_FILE_NEW_FLOW = "rota-file-processor-request-new.json";
    private static final String PAYLOAD_FILE_CLEAN_REDUNDANT_DATA = "rota-clean-redundant-data-request.json";
    private static final String ROTA_FILE_RESOURCE_PATH = "rotafileprocessor/%s.xml";
    private static final String SNAPSHOT_FILE_RESOURCE_PATH = "rotafileprocessor/%s%s.xml";
    private static final String SNAPSHOT_FILE_NAME_PREFIX = "IT_Test_lja_bedfordshire";
    private static final String FILE_NAME_FORMAT = "%s_%s.xml";

    // ============================================================================
    // Constants - Test File Names
    // ============================================================================
    private static final String BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE = "B40IM00";
    private static final String BEDFORD_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T180039Z";
    private static final String BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
    private static final String WESTYORK_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_westyorkshire_rota_20240827T154745Z";

    // ============================================================================
    // Constants - Test Data Expectations
    // ============================================================================
    private static final int BEDFORD_SHIRE_EXPECTED_SLOTS = 623;
    private static final int BEDFORD_SHIRE_EXPECTED_JUDICIARIES = 66;
    private static final int BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS = 620;
    private static final int BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES = 32;
    private static final int WESTYORK_SHIRE_EXPECTED_SLOTS = 4687;
    private static final int WESTYORK_SHIRE_EXPECTED_JUDICIARIES = 3813;
    private static final int SNAPSHOT_EXPECTED_SLOTS_FIRST = 210;
    private static final int SNAPSHOT_EXPECTED_SLOTS_SECOND = 209;
    private static final int SNAPSHOT_EXPECTED_SLOTS_THIRD = 204;
    private static final int SNAPSHOT_EXPECTED_JUDICIARIES = 66;

    // ============================================================================
    // Constants - Listing Profile IDs
    // ============================================================================
    private static final String LISTING_PROFILE_ID_CS4305478 = "CS4305478";
    private static final String LISTING_PROFILE_ID_CS4305744 = "CS4305744";
    private static final String LISTING_PROFILE_ID_CS4304756 = "CS4304756";

    // ============================================================================
    // Constants - Snapshot File Dates
    // ============================================================================
    private static final LocalDate SNAPSHOT_FILE_START_DATE_1 = LocalDate.of(2024, 8, 1);
    private static final LocalDate SNAPSHOT_FILE_START_DATE_2 = LocalDate.of(2024, 8, 2);
    private static final String SNAPSHOT_FILE_SUFFIX_1 = "_snapshot_20240403T180039Z";
    private static final String SNAPSHOT_FILE_SUFFIX_2 = "_snapshot_20240802T180039Z";

    // ============================================================================
    // Constants - Clean Redundant Data
    // ============================================================================
    private static final int DAYS_PER_MONTH = 30;
    private static final String PLACEHOLDER_NUMBER_OF_PREVIOUS_MONTHS = "NUMBER_OF_PREVIOUS_MONTHS_AND_OLDER";

    // ============================================================================
    // Instance Variables
    // ============================================================================
    private final AzureBlobClientService azureBlobClientService = new AzureBlobClientService();
    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();

    private LocalDateTime maxCreatedOnForCourtSchedule;
    private LocalDateTime maxCreatedOnForCourtScheduleJudiciary;

    // ============================================================================
    // Test Setup and Teardown
    // ============================================================================

    @BeforeEach
    void setUpAzureBlobClientService() {
        final StorageApplicationParameters storageApplicationParameters = new StorageApplicationParameters();

        setField(azureBlobClientService, "rotaslStorageConnectionString", ROTASL_STORAGE_CONNECTION_STRING);
        setField(azureBlobClientService, "rotaslInputContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "rotaslArchiveContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "storageApplicationParameters", storageApplicationParameters);
        
        resetMaxTimestamps();
    }

    @AfterEach
    void tearDown() {
        filesToBeDeletedFromOutputContainer.forEach(fileToBeDeleted -> 
            azureBlobClientService.deleteFile(fileToBeDeleted, of(AZURE_BLOB_OUTPUT_CONTAINER_NAME)));
    }

    private void resetMaxTimestamps() {
        maxCreatedOnForCourtScheduleJudiciary = null;
        maxCreatedOnForCourtSchedule = null;
    }

    // ============================================================================
    // Test Methods - Full Rota File Processing
    // ============================================================================

    @Test
    void shouldProcessFullRotaFileForNonMigrated() throws IOException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedForMigrated() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES);
    }

    @Test
    void shouldProcessFullRotaFileAndOnlyCourtScheduleJudiciaryProcessedEvenListingProfileIdNullForMigrated() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES);
    }

    @Test
    void shouldProcessOnlyJudiciaryInfoForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                0);
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES);
    }

    @Test
    @Disabled("Test disabled - requires specific judiciary data setup that may not be available in all test environments")
    void shouldProcessOnlyJudiciaryInfoAndJudiciaryDataAlreadyExistsForMigratedEvenListingProfileIdNull() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                0);
        databaseSeeder.deleteJudiciaryByProfileId(LISTING_PROFILE_ID_CS4305744);
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES);
    }

    @Test
    void shouldUpdateJudiciaryInfoAndShouldNotDeleteForTheOnesHavingAllocatedSlots() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                0);
        
        final Optional<CourtSchedule> courtScheduleOptional = findCourtScheduleByListingProfileId(LISTING_PROFILE_ID_CS4305744);
        databaseSeeder.setUpdateAvailableSlotForCourtSchedule(LISTING_PROFILE_ID_CS4305744);
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES, 
                BEDFORD_SHIRE_FILE_2_EXPECTED_JUDICIARIES);
    }

    @Test
    void shouldProcessAlsoBiggerFile() throws IOException {
        processFullRotaFile(WESTYORK_SHIRE_MASTER_FILE_BASE_NAME, 
                WESTYORK_SHIRE_EXPECTED_SLOTS, 
                WESTYORK_SHIRE_EXPECTED_JUDICIARIES, 
                0);
    }

    // ============================================================================
    // Test Methods - Snapshot File Processing
    // ============================================================================

    @Test
    void shouldProcessSnapshotRotaFile() throws SQLException, IOException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);

        insertAllocatedListingForListingProfile(LISTING_PROFILE_ID_CS4305478);
        
        final String snapshotFileName = uploadAndProcessSnapshotFile(
                SNAPSHOT_FILE_SUFFIX_1, 
                SNAPSHOT_EXPECTED_SLOTS_FIRST);

        validateSnapshotFileResults(SNAPSHOT_FILE_START_DATE_1, 
                SNAPSHOT_EXPECTED_SLOTS_FIRST, 
                0
        );
        
        filesToBeDeletedFromOutputContainer.add(snapshotFileName);
    }

    @Test
    void shouldProcessSnapshotRotaFileWithHavingJudiciaryDataChanged() throws SQLException, IOException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);

        insertAllocatedListingForListingProfile(LISTING_PROFILE_ID_CS4305478);
        insertAllocatedListingForListingProfile(LISTING_PROFILE_ID_CS4304756);

        processSnapshotFile(SNAPSHOT_FILE_SUFFIX_1, SNAPSHOT_FILE_START_DATE_1, 0, SNAPSHOT_EXPECTED_SLOTS_SECOND);
        processSnapshotFile(SNAPSHOT_FILE_SUFFIX_2, SNAPSHOT_FILE_START_DATE_2, 5, SNAPSHOT_EXPECTED_SLOTS_THIRD);
    }

    // ============================================================================
    // Test Methods - Clean Redundant Data
    // ============================================================================

    @Test
    void shouldCleanRedundantRotaData() throws IOException, SQLException {
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, 
                BEDFORD_SHIRE_EXPECTED_SLOTS, 
                BEDFORD_SHIRE_EXPECTED_JUDICIARIES, 
                0);

        final int numberOfPreviousDaysAndOlder = 6 * DAYS_PER_MONTH;
        
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final List<CourtSchedule> courtSchedulesOlderThanThreshold = filterCourtSchedulesOlderThan(
                courtSchedules, 
                LocalDate.now().minusDays(numberOfPreviousDaysAndOlder));
        
        insertAllocatedListingsForCourtSchedules(courtSchedulesOlderThanThreshold);
        
        final int numberOfOlderThanThreshold = courtSchedulesOlderThanThreshold.size();
        final int numberOfTotalCourtSchedules = courtSchedules.size();

        validateAllocatedListingsCount(numberOfOlderThanThreshold);

        final Response response = callCleanRedundantRotaDataApi();
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final LocalDate thresholdDate = LocalDate.now().minusDays(numberOfPreviousDaysAndOlder);
        awaitCleanRedundantDataCompletion(thresholdDate, numberOfTotalCourtSchedules, numberOfOlderThanThreshold);

        validateCleanRedundantDataResults(thresholdDate, numberOfTotalCourtSchedules, numberOfOlderThanThreshold);
    }

    // ============================================================================
    // Helper Methods - File Processing
    // ============================================================================

    private void processFullRotaFile(final String fileBlobBaseName,
                                     final int expectedNumberOfSlots,
                                     final int expectedNumberOfJudiciaries,
                                     final int expectedNumberOfJudiciariesCreatedAfterMigration) throws IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();

        final String finalMasterRotaFileName = generateUniqueFileName(fileBlobBaseName);
        final byte[] rotaFileAsBytes = loadRotaFileFromResources(fileBlobBaseName);
        
        uploadRotaFileToBlobStorage(finalMasterRotaFileName, rotaFileAsBytes);
        
        final Response response = callProcessRotaFilesApi();
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        awaitRotaFileProcessing(expectedNumberOfSlots, expectedNumberOfJudiciaries, expectedNumberOfJudiciariesCreatedAfterMigration);

        logger.info("master rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        validateFullRotaFileResults(expectedNumberOfSlots, expectedNumberOfJudiciaries, expectedNumberOfJudiciariesCreatedAfterMigration);

        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }

    private String generateUniqueFileName(final String fileBlobBaseName) {
        final String generatedUniqueFileId = randomSimpleString().toString();
        return format(FILE_NAME_FORMAT, fileBlobBaseName, generatedUniqueFileId);
    }

    private byte[] loadRotaFileFromResources(final String fileBlobBaseName) throws IOException {
        final InputStream rotaFileInputStream = getClass().getClassLoader()
                .getResourceAsStream(format(ROTA_FILE_RESOURCE_PATH, fileBlobBaseName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null for file: " + fileBlobBaseName);
            return new byte[0];
        }
        
        return IOUtils.toByteArray(rotaFileInputStream);
    }

    private void uploadRotaFileToBlobStorage(final String fileName, final byte[] fileContent) {
        azureBlobClientService.uploadProcessedFile(
                new ByteArrayInputStream(fileContent), 
                (long) fileContent.length, 
                fileName, 
                of(AZURE_BLOB_INPUT_CONTAINER_NAME));
    }

    private Response callProcessRotaFilesApi() {
        final String payloadAsJsonString = getPayload(PAYLOAD_FILE_NEW_FLOW);
        return postCommand(ROTASL_FILE_PROCESSOR_URL, CONTENT_TYPE_PROCESS_ROTA_FILES, SYSTEM_USER_ID, payloadAsJsonString);
    }

    private void awaitRotaFileProcessing(final int expectedNumberOfSlots,
                                         final int expectedNumberOfJudiciaries,
                                         final int expectedNumberOfJudiciariesCreatedAfterMigration) {
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            if (isNull(maxCreatedOnForCourtScheduleJudiciary) || expectedNumberOfJudiciariesCreatedAfterMigration == 0) {
                final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();
                final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
                return courtScheduleJudiciaryEntities.size() == expectedNumberOfJudiciaries 
                        && courtScheduleEntities.size() == expectedNumberOfSlots;
            } else {
                final List<CourtScheduleJudiciary> courtScheduleJudiciariesCreatedAfter = 
                        databaseReader.courtScheduleJudiciariesCreatedAfter(maxCreatedOnForCourtScheduleJudiciary);
                return isNotEmpty(courtScheduleJudiciariesCreatedAfter) 
                        && courtScheduleJudiciariesCreatedAfter.size() == expectedNumberOfJudiciariesCreatedAfterMigration;
            }
        });
    }

    private void validateFullRotaFileResults(final int expectedNumberOfSlots,
                                             final int expectedNumberOfJudiciaries,
                                             final int expectedNumberOfJudiciariesCreatedAfterMigration) {
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        updateMaxTimestamps();

        assertEquals(expectedNumberOfSlots, courtScheduleEntities.size());
        assertEquals(expectedNumberOfJudiciaries, courtScheduleJudiciaryEntities.size());

        if (expectedNumberOfJudiciariesCreatedAfterMigration > 0) {
            final List<CourtScheduleJudiciary> courtScheduleJudiciariesCreatedAfter = 
                    databaseReader.courtScheduleJudiciariesCreatedAfter(maxCreatedOnForCourtScheduleJudiciary);
            assertTrue(isNotEmpty(courtScheduleJudiciariesCreatedAfter));
            assertEquals(expectedNumberOfJudiciariesCreatedAfterMigration, courtScheduleJudiciariesCreatedAfter.size());
        }

        assertDefaultStartTimeAndEndTime(courtScheduleEntities);
    }

    private void updateMaxTimestamps() {
        final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPair = databaseReader.getMaxCreatedOnForCourtSchedule();
        maxCreatedOnForCourtSchedule = maxCreatedUpdatedPair.getLeft();
        maxCreatedUpdatedPair.getRight();

        if (isNull(maxCreatedOnForCourtScheduleJudiciary)) {
            final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPairForJudiciary = 
                    databaseReader.getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary();
            maxCreatedOnForCourtScheduleJudiciary = maxCreatedUpdatedPairForJudiciary.getLeft();
        }
    }

    // ============================================================================
    // Helper Methods - Snapshot File Processing
    // ============================================================================

    private String uploadAndProcessSnapshotFile(final String snapshotFileSuffix,
                                                final int expectedSlots) throws IOException {
        final String snapshotFileName = generateSnapshotFileName(snapshotFileSuffix);
        final byte[] rotaFileAsBytes = loadSnapshotFileFromResources(snapshotFileSuffix);
        
        uploadRotaFileToBlobStorage(snapshotFileName, rotaFileAsBytes);
        
        final Response response = callProcessRotaFilesApi();
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        awaitSnapshotFileProcessing(expectedSlots);

        return snapshotFileName;
    }

    private String generateSnapshotFileName(final String snapshotFileSuffix) {
        final String generatedUniqueFileId = randomUUID().toString();
        return format(FILE_NAME_FORMAT, 
                format("%s_%s%s", SNAPSHOT_FILE_NAME_PREFIX, generatedUniqueFileId, snapshotFileSuffix), 
                "");
    }

    private byte[] loadSnapshotFileFromResources(final String snapshotFileSuffix) throws IOException {
        final InputStream rotaFileInputStream = getClass().getClassLoader()
                .getResourceAsStream(format(SNAPSHOT_FILE_RESOURCE_PATH, SNAPSHOT_FILE_NAME_PREFIX, snapshotFileSuffix));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null for snapshot file: " + snapshotFileSuffix);
            return new byte[0];
        }
        
        return IOUtils.toByteArray(rotaFileInputStream);
    }

    private void awaitSnapshotFileProcessing(final int expectedSlots) {
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtSchedulesFromSnapshotFile = 
                    databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
            return isNotEmpty(courtSchedulesFromSnapshotFile) && courtSchedulesFromSnapshotFile.size() == expectedSlots;
        });
    }

    private void processSnapshotFile(final String snapshotFileSuffix,
                                     final LocalDate snapshotFileStartDate,
                                     final int numberOfCreatedSlotBeforeSnapshotFile,
                                     final int numberOfCreatedSlotFromSnapshot) throws IOException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String snapshotFileName = uploadAndProcessSnapshotFile(snapshotFileSuffix, SNAPSHOT_EXPECTED_SLOTS_SECOND);

        logger.info("snapshot rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        validateSnapshotFileResults(snapshotFileStartDate, 
                numberOfCreatedSlotFromSnapshot, 
                numberOfCreatedSlotBeforeSnapshotFile
        );

        filesToBeDeletedFromOutputContainer.add(snapshotFileName);
    }

    private void validateSnapshotFileResults(final LocalDate snapshotFileStartDate,
                                             final int expectedSlotsFromSnapshot,
                                             final int expectedSlotsBeforeSnapshot) {
        final List<CourtSchedule> courtSchedulesFromSnapshotFile = 
                databaseReader.courtSchedulesCreatedAfter(maxCreatedOnForCourtSchedule);
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        assertEquals(SNAPSHOT_EXPECTED_SLOTS_SECOND, courtSchedulesFromSnapshotFile.size());
        assertEquals(expectedSlotsFromSnapshot, countCourtSchedulesOnOrAfterDate(courtSchedulesFromSnapshotFile, snapshotFileStartDate));
        assertEquals(expectedSlotsBeforeSnapshot, countCourtSchedulesBeforeDate(courtSchedulesFromSnapshotFile, snapshotFileStartDate));
        assertEquals(RotaFileProcessorNewFlowIT.SNAPSHOT_EXPECTED_JUDICIARIES, courtScheduleJudiciaryEntities.size());

        validateAllocatedSlotNotInSnapshotFile();
    }

    private int countCourtSchedulesOnOrAfterDate(final List<CourtSchedule> courtSchedules, final LocalDate date) {
        return (int) courtSchedules.stream()
                .filter(courtSchedule -> !courtSchedule.getSessionDate().isBefore(date))
                .count();
    }

    private int countCourtSchedulesBeforeDate(final List<CourtSchedule> courtSchedules, final LocalDate date) {
        return (int) courtSchedules.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(date))
                .count();
    }

    private void validateAllocatedSlotNotInSnapshotFile() {
        final Optional<CourtSchedule> allocatedSlotNotBeingInSnapshotFile = 
                findCourtScheduleByListingProfileId(RotaFileProcessorNewFlowIT.LISTING_PROFILE_ID_CS4305478);
        assertTrue(allocatedSlotNotBeingInSnapshotFile.isPresent());
        assertTrue(allocatedSlotNotBeingInSnapshotFile.get().isActive());
    }

    // ============================================================================
    // Helper Methods - Clean Redundant Data
    // ============================================================================

    private List<CourtSchedule> filterCourtSchedulesOlderThan(final List<CourtSchedule> courtSchedules, 
                                                               final LocalDate thresholdDate) {
        return courtSchedules.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(thresholdDate))
                .toList();
    }

    private void validateAllocatedListingsCount(final int expectedCount) {
        final List<AllocatedListing> allocatedListings = databaseReader.allocatedListings();
        assertEquals(expectedCount, allocatedListings.size());
    }

    private Response callCleanRedundantRotaDataApi() {
        String payloadAsJsonString = getPayload(PAYLOAD_FILE_CLEAN_REDUNDANT_DATA);
        payloadAsJsonString = payloadAsJsonString.replace(PLACEHOLDER_NUMBER_OF_PREVIOUS_MONTHS, 
                String.valueOf(6));
        return postCommand(ROTASL_CLEAN_REDUNDANT_ROTA_DATA_URL, 
                CONTENT_TYPE_CLEAN_REDUNDANT_DATA, 
                SYSTEM_USER_ID, 
                payloadAsJsonString);
    }

    private void awaitCleanRedundantDataCompletion(final LocalDate thresholdDate,
                                                   final int numberOfTotalCourtSchedules,
                                                   final int numberOfOlderThanThreshold) {
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_CLEAN_REDUNDANT_ROTA_DATA_IN_SEC, SECONDS).until(() -> {
            final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
            return countCourtSchedulesOnOrAfterDate(courtScheduleEntities, thresholdDate) == 
                    (numberOfTotalCourtSchedules - numberOfOlderThanThreshold);
        });
    }

    private void validateCleanRedundantDataResults(final LocalDate thresholdDate,
                                                   final int numberOfTotalCourtSchedules,
                                                   final int numberOfOlderThanThreshold) {
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        
        assertTrue(courtScheduleEntities.stream()
                .filter(courtSchedule -> courtSchedule.getSessionDate().isBefore(thresholdDate))
                .findAny()
                .isEmpty());
        assertThat(courtScheduleEntities.size(), is(numberOfTotalCourtSchedules - numberOfOlderThanThreshold));

        assertTrue(databaseReader.allocatedListings().isEmpty());
    }

    // ============================================================================
    // Helper Methods - Allocated Listings
    // ============================================================================

    private void insertAllocatedListingForListingProfile(final String listingProfileId) throws SQLException {
        final Optional<CourtSchedule> courtScheduleOptional = findCourtScheduleByListingProfileId(listingProfileId);
        databaseSeeder.insertAllocatedListing(getAllocatedListing(courtScheduleOptional.get()));
    }

    private Optional<CourtSchedule> findCourtScheduleByListingProfileId(final String listingProfileId) {
        return databaseReader.courtSchedules().stream()
                .filter(courtSchedule -> courtSchedule.getListingProfileId().equals(listingProfileId))
                .findAny();
    }

    private void insertAllocatedListingsForCourtSchedules(final List<CourtSchedule> courtSchedules) throws SQLException {
        if (courtSchedules.isEmpty()) {
            return;
        }

        final List<AllocatedListing> allocatedListings = new ArrayList<>();

        try (Connection connection = databaseSeeder.getNewConnection()) {
            connection.setAutoCommit(false);

            for (final CourtSchedule courtSchedule : courtSchedules) {
                final CourtSchedule foundCourtSchedule = databaseReader.courtScheduleById(
                        courtSchedule.getCourtScheduleId(), connection);
                if (nonNull(foundCourtSchedule)) {
                    allocatedListings.add(getAllocatedListing(courtSchedule));
                } else {
                    logger.info("courtScheduleId not found to be inserted to allocated_listings: {}", 
                            courtSchedule.getCourtScheduleId());
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
        allocatedListing.setHearingStartTime(Date.from(courtSchedule.getSessionDate()
                .atTime(14, 0)
                .atZone(UTC_ZONE)
                .toInstant()));

        return allocatedListing;
    }

    // ============================================================================
    // Helper Methods - Validation
    // ============================================================================

    private static void assertDefaultStartTimeAndEndTime(final List<CourtSchedule> courtSchedules) {
        courtSchedules.forEach(courtSchedule -> {
            assertNotNull(courtSchedule.getSessionStartTime());
            assertNotNull(courtSchedule.getSessionEndTime());

            if (AM_SESSION.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_MORNING_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_MORNING_END_TIME));
            } else if (PM_SESSION.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_AFTERNOON_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_AFTERNOON_END_TIME));
            } else if (ALL_DAY.equals(courtSchedule.getCourtSession())) {
                assertEquals(courtSchedule.getSessionStartTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_ALL_DAY_START_TIME));
                assertEquals(courtSchedule.getSessionEndTime(), 
                        DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DateUtils.DEFAULT_ALL_DAY_END_TIME));
            }
        });
    }
}
