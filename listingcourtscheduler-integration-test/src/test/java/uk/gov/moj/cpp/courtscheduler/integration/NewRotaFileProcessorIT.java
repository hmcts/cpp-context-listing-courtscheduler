package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Date.from;
import static java.util.Objects.isNull;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.SECONDS;
import static jakarta.ws.rs.core.Response.Status.ACCEPTED;
import static java.util.UUID.randomUUID;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.TimezoneUtils.UTC_ZONE;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.payloadToObject;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

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
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NewRotaFileProcessorIT extends AbstractIT {

    private static final Logger logger = LoggerFactory.getLogger(NewRotaFileProcessorIT.class);

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";
    private static final String ROTASL_PROCESS_ROTA_FILES_CONTENT_TYPE = "application/vnd.courtscheduler.rotasl.process_rota_files+json";
    private static final String ROTAFILEPROCESSOR_RESOURCE_PATH = "rotafileprocessor/%s.xml";
    
    private static final String AZURE_BLOB_INPUT_CONTAINER_NAME = "schedulelistinginput";
    private static final String AZURE_BLOB_OUTPUT_CONTAINER_NAME = "schedulelistingoutput";
    // Azurite emulator. Override with -Dazurite.connectionString=...; defaults to docker/.env via AzuriteFixture.
    private static final String ROTASL_STORAGE_CONNECTION_STRING = System.getProperty(
            "azurite.connectionString",
            uk.gov.moj.cpp.courtscheduler.integration.utils.AzuriteFixture.connectionString());
    private static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 50;
    
    private static final String BEDFORD_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_bedfordshire_rotaa_20240401T180039Z";
    private static final String BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T180039Z";
    private static final String BEDFORD_SHIRE_MASTER_FILE_3_BASE_NAME = "IT_Test_lja_bedfordshire_rota_20240402T190039Z";
    private static final String BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE = "B40IM00";
    private static final String COURT_SCHEDULE_MANUAL_ENTRIES_JSON = "court_schedule_manual_entries.json";
    private static final String ROTA_FILE_PROCESSOR_REQUEST = "rota-file-processor-request.json";
    // Court schedules as they exist before the rota feed runs. Rota processing only assigns
    // judiciary to existing court schedules (it never creates them), so each test seeds them first.
    private static final String COURT_SCHEDULE_SEED_RESOURCE_PATH = "rotafileprocessor/seed/court_schedule_%s.csv";
    private static final String ALLOCATED_LISTING_PROFILE_ID = "CS4305744";
    private static final String NEWER_SNAPSHOT_RESOURCE = "IT_Test_lja_bedfordshire_snapshot_20240802T180039Z";
    private static final String OLDER_SNAPSHOT_RESOURCE = "IT_Test_lja_bedfordshire_snapshot_20240403T180039Z";
    
    private static final String JSON_FIELD_ID = "id";
    private static final String JSON_FIELD_OUCODE = "oucode";
    private static final String JSON_FIELD_COURT_ROOM_NUMBER = "court_room_number";
    private static final String JSON_FIELD_COURT_HOUSE_NAME = "court_house_name";
    private static final String JSON_FIELD_COURT_ROOM_NAME = "court_room_name";
    private static final String JSON_FIELD_OPERATIONAL_UNIT = "operational_unit";
    private static final String JSON_FIELD_COURT_SESSION = "court_session";
    private static final String JSON_FIELD_SESSION_START = "session_start";
    private static final String JSON_FIELD_PANEL = "panel";
    private static final String JSON_FIELD_MAX_SLOT = "max_slot";
    private static final String JSON_FIELD_MAX_DURATION_MINS = "max_duration_mins";
    private static final String JSON_FIELD_AVAILABLE_SLOT = "available_slot";
    private static final String JSON_FIELD_AVAILABLE_DURATION_MINS = "available_duration_mins";
    private static final String JSON_FIELD_ACTIVE = "active";
    private static final String JSON_FIELD_COURT_ROOM_ID = "court_room_id";
    private static final String JSON_FIELD_IS_SLOT_BASED = "is_slot_based";
    private static final String JSON_FIELD_COURT_HOUSE_ID = "court_house_id";
    private static final String JSON_FIELD_SUPPORT_AD_SPLIT = "support_ad_split";
    private static final String JSON_FIELD_MAX_AD_MORNING_DURATION = "max_ad_morning_duration";
    private static final String JSON_FIELD_MAX_AD_AFTERNOON_DURATION = "max_ad_afternoon_duration";
    private static final String JSON_FIELD_IS_OVERBOOKING_ALLOWED = "is_overbooking_allowed";
    private static final String JSON_FIELD_SESSION_START_TIME = "session_start_time";
    private static final String JSON_FIELD_SESSION_END_TIME = "session_end_time";
    private static final String JSON_FIELD_NATIONAL_BREAK_TIME = "national_break_time";
    private static final String JSON_ARRAY_COURT_SCHEDULE = "court_schedule";
    
    private static final String UPDATED_JUDICIARIES_FILE = "referencedata.judiciaries-updated.json";
    private static final String UPDATED_ROTA_BUSINESS_TYPES_FILE = "referencedata.rota-business-types-updated.json";
    // The 20240402 rota files were authored against the default reference data, not the updated set.
    private static final String DEFAULT_JUDICIARIES_FILE = "referencedata.judiciaries.json";
    private static final String DEFAULT_ROTA_BUSINESS_TYPES_FILE = "referencedata.rota-business-types.json";

    private final AzureBlobClientService azureBlobClientService = new AzureBlobClientService();
    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();
    private static final List<String> filesUploadedToInputContainer = new ArrayList<>();


    @BeforeEach
    void setUpAzureBlobClientService() {
        final StorageApplicationParameters storageApplicationParameters = new StorageApplicationParameters();
        setField(azureBlobClientService, "rotaslStorageConnectionString", ROTASL_STORAGE_CONNECTION_STRING);
        setField(azureBlobClientService, "rotaslInputContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "rotaslArchiveContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "storageApplicationParameters", storageApplicationParameters);

        // Override stubs to use updated files for NewRotaFileProcessorIT
        stubGetReferenceDataJudiciaries(UPDATED_JUDICIARIES_FILE);
        stubGetReferenceDataRotaBusinessTypes(UPDATED_ROTA_BUSINESS_TYPES_FILE);
    }

    @AfterEach
    void tearDown() {
        filesToBeDeletedFromOutputContainer.forEach(fileToBeDeleted ->
                deleteQuietly(fileToBeDeleted, AZURE_BLOB_OUTPUT_CONTAINER_NAME));
        // A run that timed out leaves its file in the input container, where the next test's trigger
        // would pick it up; remove it so one failure can't cascade into the following tests.
        filesUploadedToInputContainer.forEach(fileToBeDeleted ->
                deleteQuietly(fileToBeDeleted, AZURE_BLOB_INPUT_CONTAINER_NAME));
        filesToBeDeletedFromOutputContainer.clear();
        filesUploadedToInputContainer.clear();
    }

    private void deleteQuietly(final String blobName, final String containerName) {
        try {
            azureBlobClientService.deleteFile(blobName, of(containerName));
        } catch (final RuntimeException e) {
            logger.warn("Could not delete {} from container {}: {}", blobName, containerName, e.getMessage());
        }
    }

    @Test
    void shouldProcessTwoRotaFilesSequentially() throws IOException, SQLException {
        final int expectedSchedules = 586;
        final int expectedJudiciaries = 1284;
        seedCourtSchedules(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedSchedules);

        logger.info("Processing first rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedSchedules, expectedJudiciaries);
        validateRunResults("First run", expectedSchedules, expectedJudiciaries);

        // Clean judiciary table before second run
        cleanJudiciaryTable();

        logger.info("Processing second rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedSchedules, expectedJudiciaries);
        validateRunResults("Second run", expectedSchedules, expectedJudiciaries);
    }

    @Test
    void shouldProcessFullRotaFileWithManualEntries() throws IOException, SQLException {
        final int expectedSeededSchedules = 586;
        final int expectedJudiciaries = 1295;
        seedCourtSchedules(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedSeededSchedules);

        // Create manual entries from JSON file
        final int manualEntryCount = createManualCourtScheduleEntries();
        final int expectedTotalSchedules = expectedSeededSchedules + manualEntryCount;
        validateManualEntries(manualEntryCount, expectedTotalSchedules);

        logger.info("Processing rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedTotalSchedules, expectedJudiciaries);
        validateRunResults("Run with manual entries", expectedTotalSchedules, expectedJudiciaries);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "listing profile ids set, " + BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME + ", false, 623, 1403",
            "listing profile ids null, " + BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME + ", true, 623, 1403",
            "listing profile ids null second rota file, " + BEDFORD_SHIRE_MASTER_FILE_3_BASE_NAME + ", true, 620, 1400"
    })
    void shouldOnlyAssignJudiciaryToExistingCourtSchedulesForMigratedCourt(final String scenario,
                                                                          final String fileBlobBaseName,
                                                                          final boolean listingProfileIdNull,
                                                                          final int expectedSchedules,
                                                                          final int expectedJudiciaries) throws IOException, SQLException {
        useDefaultReferenceData();
        seedCourtSchedules(fileBlobBaseName, expectedSchedules);
        if (listingProfileIdNull) {
            databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);
        }
        final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedBeforeRun = databaseReader.getMaxCreatedOnForCourtSchedule();

        processFullRotaFile(fileBlobBaseName, true, expectedSchedules, expectedJudiciaries);

        assertTrue(isEmpty(databaseReader.courtSchedulesCreatedAfter(maxCreatedUpdatedBeforeRun.getLeft())),
                scenario + ": rota processing must not create court schedules");
        assertTrue(isEmpty(databaseReader.courtSchedulesUpdatedAfter(maxCreatedUpdatedBeforeRun.getRight())),
                scenario + ": rota processing must not update court schedules");
        validateRunResults(scenario, expectedSchedules, expectedJudiciaries);
    }

    // AC3 (SPRDT-971): the legacy rotaProcess value no longer selects a processor, so a request that
    // still sends "old" or "new" must behave exactly like one that sends nothing.
    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "no rotaProcess, rota-file-processor-request.json",
            "legacy rotaProcess old, rota-file-processor-request-legacy-old.json",
            "legacy rotaProcess new, rota-file-processor-request-legacy-new.json"
    })
    void shouldProcessRotaFileTheSameWayWhateverTheLegacyRotaProcessValue(final String scenario,
                                                                          final String requestPayloadFileName) throws IOException, SQLException {
        final int expectedSchedules = 586;
        final int expectedJudiciaries = 1284;
        seedCourtSchedules(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, expectedSchedules);
        final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedBeforeRun = databaseReader.getMaxCreatedOnForCourtSchedule();

        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, requestPayloadFileName, false, expectedSchedules, expectedJudiciaries);

        assertTrue(isEmpty(databaseReader.courtSchedulesCreatedAfter(maxCreatedUpdatedBeforeRun.getLeft())),
                scenario + ": rota processing must not create court schedules");
        assertTrue(isEmpty(databaseReader.courtSchedulesUpdatedAfter(maxCreatedUpdatedBeforeRun.getRight())),
                scenario + ": rota processing must not update court schedules");
        validateRunResults(scenario, expectedSchedules, expectedJudiciaries);
    }

    @Test
    void shouldSkipSnapshotRotaFileWhenNewerSnapshotAlreadyProcessed() throws IOException, SQLException {
        useDefaultReferenceData();
        seedCourtSchedules(BEDFORD_SHIRE_MASTER_FILE_2_BASE_NAME, 623);
        // Both snapshots share a file-name prefix (everything before the timestamp), which is what the
        // processor compares when deciding whether a newer snapshot has already been processed.
        final String snapshotFileNamePrefix = format("IT_Test_lja_bedfordshire_%s_snapshot_", randomUUID().toString().substring(0, 8));

        processSnapshotRotaFile(NEWER_SNAPSHOT_RESOURCE, snapshotFileNamePrefix + "20240802T180039Z.xml");
        assertEquals(1, databaseReader.rotaFileProcessHistoryCount(snapshotFileNamePrefix), "newer snapshot should be processed");
        final List<CourtScheduleJudiciary> judiciariesAfterNewerSnapshot = databaseReader.courtScheduleJudiciaries();
        final Pair<LocalDateTime, LocalDateTime> judiciaryMaxCreatedUpdatedAfterNewerSnapshot =
                databaseReader.getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary();

        processSnapshotRotaFile(OLDER_SNAPSHOT_RESOURCE, snapshotFileNamePrefix + "20240403T180039Z.xml");

        assertEquals(1, databaseReader.rotaFileProcessHistoryCount(snapshotFileNamePrefix), "older snapshot should be skipped");
        assertEquals(judiciariesAfterNewerSnapshot.size(), databaseReader.courtScheduleJudiciaries().size(),
                "skipped snapshot must not change judiciary assignments");
        assertEquals(judiciaryMaxCreatedUpdatedAfterNewerSnapshot, databaseReader.getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary(),
                "skipped snapshot must not create or update judiciary assignments");
    }

    @Test
    void shouldUpdateJudiciaryInfoAndShouldNotDeleteForTheOnesHavingAllocatedSlots() throws IOException, SQLException {
        final int expectedSchedules = 620;
        final int expectedJudiciaries = 1400;
        useDefaultReferenceData();
        seedCourtSchedules(BEDFORD_SHIRE_MASTER_FILE_3_BASE_NAME, expectedSchedules);
        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_3_BASE_NAME, true, expectedSchedules, expectedJudiciaries);

        final Optional<CourtSchedule> allocatedCourtSchedule = databaseReader.courtSchedules().stream()
                .filter(courtSchedule -> ALLOCATED_LISTING_PROFILE_ID.equals(courtSchedule.getListingProfileId()))
                .findAny();
        assertTrue(allocatedCourtSchedule.isPresent());
        databaseSeeder.setUpdateAvailableSlotForCourtSchedule(ALLOCATED_LISTING_PROFILE_ID);
        databaseSeeder.insertAllocatedListing(getAllocatedListing(allocatedCourtSchedule.get()));
        databaseSeeder.cleanMigrationStatusTable();
        databaseSeeder.updateCourtScheduleSetListingProfileIdAsNull(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE);

        processFullRotaFile(BEDFORD_SHIRE_MASTER_FILE_3_BASE_NAME, true, expectedSchedules, expectedJudiciaries);
        validateRunResults("Second run with allocated slot", expectedSchedules, expectedJudiciaries);
    }

    private static void useDefaultReferenceData() {
        stubGetReferenceDataJudiciaries(DEFAULT_JUDICIARIES_FILE);
        stubGetReferenceDataRotaBusinessTypes(DEFAULT_ROTA_BUSINESS_TYPES_FILE);
    }

    private void seedCourtSchedules(final String fileBlobBaseName, final int expectedSchedules) throws SQLException {
        final int seeded = databaseSeeder.seedCourtSchedulesFromCsv(format(COURT_SCHEDULE_SEED_RESOURCE_PATH, fileBlobBaseName));
        assertEquals(expectedSchedules, seeded, "seeded court schedules for " + fileBlobBaseName);
    }

    private void processFullRotaFile(final String fileBlobBaseName,
                                     final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) throws IOException, SQLException {
        processFullRotaFile(fileBlobBaseName, false, expectedNumberOfSlots, expectedNumberOfJudiciaries);
    }

    private void processFullRotaFile(final String fileBlobBaseName, final boolean migrated,
                                     final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) throws IOException, SQLException {
        processFullRotaFile(fileBlobBaseName, ROTA_FILE_PROCESSOR_REQUEST, migrated, expectedNumberOfSlots, expectedNumberOfJudiciaries);
    }

    private void processFullRotaFile(final String fileBlobBaseName, final String requestPayloadFileName, final boolean migrated,
                                     final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) throws IOException, SQLException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String finalMasterRotaFileName = uploadRotaFile(fileBlobBaseName);
        databaseSeeder.cleanMigrationStatusTable();
        insertCourtSchedulerMigrationStatus(List.of(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE), migrated);
        
        final String payloadAsJsonString = getPayload(requestPayloadFileName);
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, ROTASL_PROCESS_ROTA_FILES_CONTENT_TYPE, 
                SYSTEM_USER_ID, payloadAsJsonString);

        // The file leaving the input container is the terminal step of the async pipeline, so wait
        // for it before the count check: on a repeat run the counts can already match beforehand.
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> !isFileInInputContainer(finalMasterRotaFileName));
        awaitProcessing(expectedNumberOfSlots, expectedNumberOfJudiciaries);
        logger.info("Master rota file processing took time as seconds: {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));

        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        assertDefaultStartTimeAndEndTime(courtScheduleEntities);
        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }
    
    private void processSnapshotRotaFile(final String resourceBaseName, final String snapshotFileName) throws IOException {
        uploadRotaFile(resourceBaseName, snapshotFileName);
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, ROTASL_PROCESS_ROTA_FILES_CONTENT_TYPE,
                SYSTEM_USER_ID, getPayload(ROTA_FILE_PROCESSOR_REQUEST));
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        // Processed or skipped, the file is archived and removed from the input container last.
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> !isFileInInputContainer(snapshotFileName));
        filesToBeDeletedFromOutputContainer.add(snapshotFileName);
    }

    private String uploadRotaFile(final String fileBlobBaseName) throws IOException {
        final String generatedUniqueFileId = randomUUID().toString().substring(0, 8);
        return uploadRotaFile(fileBlobBaseName, format("%s_%s.xml", fileBlobBaseName, generatedUniqueFileId));
    }

    private String uploadRotaFile(final String resourceBaseName, final String finalMasterRotaFileName) throws IOException {
        final String resourcePath = format(ROTAFILEPROCESSOR_RESOURCE_PATH, resourceBaseName);
        final InputStream rotaFileInputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        try (rotaFileInputStream) {
            if (isNull(rotaFileInputStream)) {
                fail(format("rotaFileInputStream is null for file: %s", resourcePath));
                return null;
            }
            final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
            azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes),
                    (long) rotaFileAsBytes.length, finalMasterRotaFileName, of(AZURE_BLOB_INPUT_CONTAINER_NAME));
            filesUploadedToInputContainer.add(finalMasterRotaFileName);
            return finalMasterRotaFileName;
        }
    }
    
    private void awaitProcessing(final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) {
        // untilAsserted so a timeout reports the actual counts rather than just "not fulfilled"
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).untilAsserted(() -> {
            assertEquals(expectedNumberOfSlots, databaseReader.courtSchedules().size(), "court schedules");
            assertEquals(expectedNumberOfJudiciaries, databaseReader.courtScheduleJudiciaries().size(), "court schedule judiciaries");
        });
    }
    
    private void validateRunResults(final String runName, final int expectedSchedules, final int expectedJudiciaries) {
        final List<CourtSchedule> courtSchedules = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaries = databaseReader.courtScheduleJudiciaries();
        
        assertEquals(expectedSchedules, courtSchedules.size(), 
                format("%s should create %d court schedules", runName, expectedSchedules));
        assertEquals(expectedJudiciaries, courtScheduleJudiciaries.size(), 
                format("%s should create %d court schedule judiciaries", runName, expectedJudiciaries));
        assertDefaultStartTimeAndEndTime(courtSchedules);
        assertPositionAndRotaJudiciaryIdAreSet(courtScheduleJudiciaries, runName);
        logger.info("{} validation completed: {} court schedules, {} judiciaries", 
                runName, courtSchedules.size(), courtScheduleJudiciaries.size());
    }
    
    private int createManualCourtScheduleEntries() throws IOException, SQLException {
        logger.info("Creating manual court schedule entries from JSON file");
        final JsonObject jsonObject = payloadToObject(getPayload(COURT_SCHEDULE_MANUAL_ENTRIES_JSON));
        final JsonArray courtScheduleArray = jsonObject.getJsonArray(JSON_ARRAY_COURT_SCHEDULE);
        
        int entryCount = 0;
        for (final JsonValue jsonValue : courtScheduleArray) {
            final JsonObject entry = (JsonObject) jsonValue;
            final CourtSchedule manualCourtSchedule = createCourtScheduleFromJson(entry);
            databaseSeeder.insertCourtSchedule(manualCourtSchedule);
            entryCount++;
            logger.info("Created manual court schedule entry {}: {}", entryCount, manualCourtSchedule.getCourtScheduleId());
        }
        return entryCount;
    }
    
    private CourtSchedule createCourtScheduleFromJson(final JsonObject entry) {
        final CourtSchedule courtSchedule = RANDOM.nextObject(CourtSchedule.class);

        courtSchedule.setCourtScheduleId(entry.getString(JSON_FIELD_ID));
        courtSchedule.setOuCode(entry.getString(JSON_FIELD_OUCODE));
        courtSchedule.setCourtRoomNumber(entry.getInt(JSON_FIELD_COURT_ROOM_NUMBER));
        courtSchedule.setCourtHouseName(entry.getString(JSON_FIELD_COURT_HOUSE_NAME));
        courtSchedule.setCourtRoomName(entry.getString(JSON_FIELD_COURT_ROOM_NAME));
        courtSchedule.setOperationalUnit(entry.getString(JSON_FIELD_OPERATIONAL_UNIT));
        courtSchedule.setCourtSession(entry.getString(JSON_FIELD_COURT_SESSION));
        courtSchedule.setSessionDate(LocalDate.parse(entry.getString(JSON_FIELD_SESSION_START)));
        courtSchedule.setPanel(entry.getString(JSON_FIELD_PANEL));
        courtSchedule.setMaxSlots(entry.getInt(JSON_FIELD_MAX_SLOT));
        courtSchedule.setMaxDuration(entry.getInt(JSON_FIELD_MAX_DURATION_MINS));
        courtSchedule.setAvailableSlots(entry.getInt(JSON_FIELD_AVAILABLE_SLOT));
        courtSchedule.setAvailableDuration(entry.getInt(JSON_FIELD_AVAILABLE_DURATION_MINS));
        courtSchedule.setActive(entry.getBoolean(JSON_FIELD_ACTIVE));
        courtSchedule.setCourtRoomId(entry.getString(JSON_FIELD_COURT_ROOM_ID));
        courtSchedule.setSlotBased(entry.getBoolean(JSON_FIELD_IS_SLOT_BASED));
        courtSchedule.setCourtHouseId(entry.getString(JSON_FIELD_COURT_HOUSE_ID));
        courtSchedule.setSupportAdSplit(entry.getBoolean(JSON_FIELD_SUPPORT_AD_SPLIT));
        courtSchedule.setMaxAdMorningDuration(entry.getInt(JSON_FIELD_MAX_AD_MORNING_DURATION));
        courtSchedule.setMaxAdAfternoonDuration(entry.getInt(JSON_FIELD_MAX_AD_AFTERNOON_DURATION));
        courtSchedule.setIsOverbookingAllowed(entry.getBoolean(JSON_FIELD_IS_OVERBOOKING_ALLOWED));
        courtSchedule.setSessionStartTime(from(Instant.parse(entry.getString(JSON_FIELD_SESSION_START_TIME))));
        courtSchedule.setSessionEndTime(from(Instant.parse(entry.getString(JSON_FIELD_SESSION_END_TIME))));

        if (entry.containsKey(JSON_FIELD_NATIONAL_BREAK_TIME) && !entry.isNull(JSON_FIELD_NATIONAL_BREAK_TIME)) {
            courtSchedule.setNationalBreakTime(from(Instant.parse(entry.getString(JSON_FIELD_NATIONAL_BREAK_TIME))));
        }
        
        return courtSchedule;
    }
    
    private void validateManualEntries(final int manualEntryCount, final int expectedTotal) {
        final List<CourtSchedule> courtSchedulesAfterManualEntries = databaseReader.courtSchedules();
        assertEquals(expectedTotal, courtSchedulesAfterManualEntries.size(), 
                format("Should have %d seeded + %d manual entries = %d total",
                        expectedTotal - manualEntryCount, manualEntryCount, expectedTotal));
        logger.info("After manual entries: {} court schedules", courtSchedulesAfterManualEntries.size());
    }
    
    private void cleanJudiciaryTable() throws SQLException {
        logger.info("Cleaning court_schedule_judiciary table before second run");
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
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

    private AllocatedListing getAllocatedListing(final CourtSchedule courtSchedule) {
        final AllocatedListing allocatedListing = RANDOM.nextObject(AllocatedListing.class);
        allocatedListing.setId(randomUUID().toString());
        allocatedListing.setCourtScheduleId(courtSchedule.getCourtScheduleId());
        allocatedListing.setCourtRoomId(courtSchedule.getCourtRoomNumber());
        allocatedListing.setOucode(courtSchedule.getOuCode());
        allocatedListing.setHearingStartTime(from(courtSchedule.getSessionDate().atTime(14, 0).atZone(UTC_ZONE).toInstant()));
        return allocatedListing;
    }

    private boolean isFileInInputContainer(final String fileName) {
        try {
            final var blobServiceClient = new com.azure.storage.blob.BlobServiceClientBuilder()
                    .connectionString(ROTASL_STORAGE_CONNECTION_STRING)
                    .buildClient();
            final var containerClient = blobServiceClient.getBlobContainerClient(AZURE_BLOB_INPUT_CONTAINER_NAME);
            final var options = new com.azure.storage.blob.models.ListBlobsOptions().setPrefix(fileName);
            for (final var blobItem : containerClient.listBlobs(options, null)) {
                if (fileName.equals(blobItem.getName())) {
                    return true;
                }
            }
        } catch (final Exception e) {
            logger.warn("Error checking if file {} exists in input container: {}", fileName, e.getMessage());
        }
        return false;
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

    private static void assertPositionAndRotaJudiciaryIdAreSet(final List<CourtScheduleJudiciary> courtScheduleJudiciaries, final String runName) {
        courtScheduleJudiciaries.forEach(judiciary -> {
            assertNotNull(judiciary.getPosition(), 
                    format("%s: position should not be null for judiciary with courtScheduleId=%s, judiciaryId=%s", 
                            runName, judiciary.getId().getCourtScheduleId(), judiciary.getId().getJudiciaryId()));
            assertNotNull(judiciary.getRotaJudiciaryId(), 
                    format("%s: rotaJudiciaryId should not be null for judiciary with courtScheduleId=%s, judiciaryId=%s", 
                            runName, judiciary.getId().getCourtScheduleId(), judiciary.getId().getJudiciaryId()));
            
            final String position = judiciary.getPosition();
            final String rotaJudiciaryId = judiciary.getRotaJudiciaryId();
            
            assertTrue(!position.trim().isEmpty(), 
                    format("%s: position should not be empty for judiciary with courtScheduleId=%s, judiciaryId=%s", 
                            runName, judiciary.getId().getCourtScheduleId(), judiciary.getId().getJudiciaryId()));
            assertTrue(!rotaJudiciaryId.trim().isEmpty(), 
                    format("%s: rotaJudiciaryId should not be empty for judiciary with courtScheduleId=%s, judiciaryId=%s", 
                            runName, judiciary.getId().getCourtScheduleId(), judiciary.getId().getJudiciaryId()));
        });
        
        logger.info("{}: Validated position and rotaJudiciaryId for {} judiciaries", runName, courtScheduleJudiciaries.size());
    }
}

