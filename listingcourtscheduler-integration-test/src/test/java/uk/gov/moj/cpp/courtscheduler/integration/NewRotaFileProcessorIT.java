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
import static uk.gov.moj.cpp.courtscheduler.integration.utils.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.AM_SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.PM_SESSION;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.getPayload;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.payloadToObject;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.common.StorageApplicationParameters;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
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

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;
import jakarta.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class NewRotaFileProcessorIT extends AbstractIT {

    private static final Logger logger = LoggerFactory.getLogger(NewRotaFileProcessorIT.class);

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";
    private static final String ROTASL_PROCESS_ROTA_FILES_CONTENT_TYPE = "application/vnd.courtscheduler.rotasl.process_rota_files+json";
    private static final String ROTAFILEPROCESSOR_RESOURCE_PATH = "rotafileprocessor/%s.xml";
    
    private static final String AZURE_BLOB_INPUT_CONTAINER_NAME = "schedulelistinginput";
    private static final String AZURE_BLOB_OUTPUT_CONTAINER_NAME = "schedulelistingoutput";
    // Azurite emulator (see RotaFileProcessorIT.ROTASL_STORAGE_CONNECTION_STRING for details).
    private static final String ROTASL_STORAGE_CONNECTION_STRING = System.getProperty(
            "azurite.connectionString",
            uk.gov.moj.cpp.courtscheduler.integration.utils.AzuriteFixture.connectionString());
    private static final int DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC = 50;
    
    private static final String BEDFORD_SHIRE_MASTER_FILE_BASE_NAME = "IT_Test_lja_bedfordshire_rotaa_20240401T180039Z";
    private static final String BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE = "B40IM00";
    private static final String COURT_SCHEDULE_MANUAL_ENTRIES_JSON = "court_schedule_manual_entries.json";
    private static final String ROTA_FILE_PROCESSOR_REQUEST_OLD = "rota-file-processor-request.json";
    private static final String ROTA_FILE_PROCESSOR_REQUEST_NEW = "rota-file-processor-request-new.json";
    
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

    private final AzureBlobClientService azureBlobClientService = new AzureBlobClientService();
    private LocalDateTime maxCreatedOnForCourtScheduleJudiciary;
    private static final List<String> filesToBeDeletedFromOutputContainer = new ArrayList<>();


    @BeforeEach
    void setUpAzureBlobClientService() {
        final StorageApplicationParameters storageApplicationParameters = new StorageApplicationParameters();
        setField(azureBlobClientService, "rotaslStorageConnectionString", ROTASL_STORAGE_CONNECTION_STRING);
        setField(azureBlobClientService, "rotaslInputContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "rotaslArchiveContainerName", AZURE_BLOB_INPUT_CONTAINER_NAME);
        setField(azureBlobClientService, "storageApplicationParameters", storageApplicationParameters);
        maxCreatedOnForCourtScheduleJudiciary = null;
        
        // Override stubs to use updated files for NewRotaFileProcessorIT
        stubGetReferenceDataJudiciaries(UPDATED_JUDICIARIES_FILE);
        stubGetReferenceDataRotaBusinessTypes(UPDATED_ROTA_BUSINESS_TYPES_FILE);
    }

    @AfterEach
    void tearDown() {
        filesToBeDeletedFromOutputContainer.forEach(fileToBeDeleted -> 
                azureBlobClientService.deleteFile(fileToBeDeleted, of(AZURE_BLOB_OUTPUT_CONTAINER_NAME)));
    }

    @Test
    void shouldProcessTwoRotaFilesSequentially() throws IOException, SQLException {
        final int expectedSchedules = 586;
        final int expectedJudiciaries = 1284;
        
        // First run: process with old flow
        logger.info("Processing first rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(ROTA_FILE_PROCESSOR_REQUEST_OLD,
                expectedSchedules, expectedJudiciaries);
        validateRunResults("First run", expectedSchedules, expectedJudiciaries);
        
        // Clean judiciary table before second run
        cleanJudiciaryTable();
        
        // Second run: process with new flow
        logger.info("Processing second rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(ROTA_FILE_PROCESSOR_REQUEST_NEW,
                expectedSchedules, expectedJudiciaries);
        validateRunResults("Second run", expectedSchedules, expectedJudiciaries);
    }

    @Test
    void shouldProcessFullRotaFileWithManualEntries() throws IOException, SQLException {
        final int expectedSchedulesFirstRun = 586;
        final int expectedJudiciariesFirstRun = 1284;
        final int expectedJudiciariesSecondRun = 1295;
        
        // First run: process with old flow
        logger.info("Processing first rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(ROTA_FILE_PROCESSOR_REQUEST_OLD,
                expectedSchedulesFirstRun, expectedJudiciariesFirstRun);
        validateRunResults("First run", expectedSchedulesFirstRun, expectedJudiciariesFirstRun);
        
        // Create manual entries from JSON file
        final int manualEntryCount = createManualCourtScheduleEntries();
        final int expectedTotalSchedules = expectedSchedulesFirstRun + manualEntryCount;
        validateManualEntries(manualEntryCount, expectedTotalSchedules);
        
        // Clean judiciary table before second run
        cleanJudiciaryTable();
        
        // Second run: process with new flow
        logger.info("Processing second rota file: {}", BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        processFullRotaFile(ROTA_FILE_PROCESSOR_REQUEST_NEW,
                expectedTotalSchedules, expectedJudiciariesSecondRun);
        validateRunResults("Second run", expectedTotalSchedules, expectedJudiciariesSecondRun);
    }


    private void processFullRotaFile(final String payloadFileName,
                                     final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) throws IOException, SQLException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String finalMasterRotaFileName = uploadRotaFile();
        insertCourtSchedulerMigrationStatus(List.of(BEDFORD_SHIRE_MAGISTRATES_COURT_OU_CODE));
        
        final String payloadAsJsonString = getPayload(payloadFileName);
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, ROTASL_PROCESS_ROTA_FILES_CONTENT_TYPE, 
                SYSTEM_USER_ID, payloadAsJsonString);

        awaitProcessing(expectedNumberOfSlots, expectedNumberOfJudiciaries);
        logger.info("Master rota file processing took time as seconds: {}", stopwatch.elapsed(SECONDS));

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
        updateMaxCreatedOnForJudiciary();
        
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        assertDefaultStartTimeAndEndTime(courtScheduleEntities);
        filesToBeDeletedFromOutputContainer.add(finalMasterRotaFileName);
    }
    
    private String uploadRotaFile() throws IOException {
        final String generatedUniqueFileId = randomUUID().toString().substring(0, 8);
        final String finalMasterRotaFileName = format("%s_%s.xml", NewRotaFileProcessorIT.BEDFORD_SHIRE_MASTER_FILE_BASE_NAME, generatedUniqueFileId);
        final String resourcePath = format(ROTAFILEPROCESSOR_RESOURCE_PATH, NewRotaFileProcessorIT.BEDFORD_SHIRE_MASTER_FILE_BASE_NAME);
        final InputStream rotaFileInputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);

        try (rotaFileInputStream) {
            if (isNull(rotaFileInputStream)) {
                fail(format("rotaFileInputStream is null for file: %s", resourcePath));
                return null;
            }
            final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
            azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes),
                    (long) rotaFileAsBytes.length, finalMasterRotaFileName, of(AZURE_BLOB_INPUT_CONTAINER_NAME));
            return finalMasterRotaFileName;
        }
    }
    
    private void awaitProcessing(final int expectedNumberOfSlots, final int expectedNumberOfJudiciaries) {
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();
            final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
            return courtScheduleJudiciaryEntities.size() == expectedNumberOfJudiciaries 
                    && courtScheduleEntities.size() == expectedNumberOfSlots;
        });
    }
    
    private void updateMaxCreatedOnForJudiciary() {
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();
        if (isNull(maxCreatedOnForCourtScheduleJudiciary) && !courtScheduleJudiciaryEntities.isEmpty()) {
            final Pair<LocalDateTime, LocalDateTime> maxCreatedUpdatedPairForJudiciary = 
                    databaseReader.getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary();
            final LocalDateTime maxCreatedOn = maxCreatedUpdatedPairForJudiciary.getLeft();
            if (maxCreatedOn != null) {
                maxCreatedOnForCourtScheduleJudiciary = maxCreatedOn;
            }
        }
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

        courtSchedule.setIsDraft(false);

        return courtSchedule;
    }
    
    private void validateManualEntries(final int manualEntryCount, final int expectedTotal) {
        final List<CourtSchedule> courtSchedulesAfterManualEntries = databaseReader.courtSchedules();
        assertEquals(expectedTotal, courtSchedulesAfterManualEntries.size(), 
                format("Should have %d from first run + %d manual entries = %d total",
                        586, manualEntryCount, expectedTotal));
        logger.info("After manual entries: {} court schedules", courtSchedulesAfterManualEntries.size());
    }
    
    private void cleanJudiciaryTable() throws SQLException {
        logger.info("Cleaning court_schedule_judiciary table before second run");
        databaseSeeder.cleanCourtScheduleJudiciaryTable();
        databaseSeeder.cleanMigrationStatusTable();
    }
    
    private void insertCourtSchedulerMigrationStatus(final List<String> ouCodes) throws SQLException {
        for (final String ouCode : ouCodes) {
            final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
            courtSchedulerMigrationStatus.setOuCode(ouCode);
            courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
            courtSchedulerMigrationStatus.setMigrated(false);
            databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
        }
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

