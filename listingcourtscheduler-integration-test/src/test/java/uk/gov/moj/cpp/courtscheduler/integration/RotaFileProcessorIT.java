package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.Optional.of;
import static java.util.concurrent.TimeUnit.SECONDS;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
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
import java.util.List;

import javax.ws.rs.core.Response;

import com.google.common.base.Stopwatch;
import org.apache.commons.io.IOUtils;
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

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
    }

    @BeforeEach
    public void setUpAzureBlobClientService() {
        setField(azureBlobClientService, "rotaslStorageConnectionString", rotaslStorageConnectionString);
    }

    @Test
    void shouldProcessFullRotaFile() throws IOException, SQLException {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");

        final String fileBlobName = "lja_bedfordshire_rota_20240402T180039Z.xml";

        azureBlobClientService.deleteFile(fileBlobName, of(azureBlobOutputContainerName));
        final InputStream rotaFileInputStream = FileUtil.class.getClassLoader().getResourceAsStream(format("rotafileprocessor/%s", fileBlobName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, fileBlobName, of(azureBlobInputContainerName));
        insertCourtSchedulerMigrationStatus();

        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, null);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final byte[] downloadedBlobFromOutputContainer = azureBlobClientService.downloadFile(fileBlobName, azureBlobOutputContainerName);
            return nonNull(downloadedBlobFromOutputContainer) && downloadedBlobFromOutputContainer.length > 0;
        });

        logger.info("rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));

        // then after do validation against database to see if we have expected data for this rota file
        final List<CourtSchedule> courtScheduleEntities = databaseReader.courtSchedules();
        final List<CourtScheduleJudiciary> courtScheduleJudiciaryEntities = databaseReader.courtScheduleJudiciaries();

        assertEquals(623, courtScheduleEntities.size());
        assertEquals(34, courtScheduleJudiciaryEntities.size());
        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    @Test
    void shouldProcessSnapshotRotaFile() throws SQLException, IOException {
        shouldProcessFullRotaFile();

        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String snapshotFileName = "lja_bedfodshire_snapshot_20240403T180039Z.xml";
        azureBlobClientService.deleteFile(snapshotFileName, of(azureBlobOutputContainerName));
        final InputStream rotaFileInputStream = FileUtil.class.getClassLoader().getResourceAsStream(format("rotafileprocessor/%s", snapshotFileName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, snapshotFileName, of(azureBlobInputContainerName));

        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, null);

        // await until this file uploaded into archive container
        await().timeout(DEFAULT_POLL_TIMEOUT_FOR_ROTA_FILE_PROCESS_IN_SEC, SECONDS).until(() -> {
            final byte[] downloadedBlobFromOutputContainer = azureBlobClientService.downloadFile(snapshotFileName, azureBlobOutputContainerName);
            return nonNull(downloadedBlobFromOutputContainer) && downloadedBlobFromOutputContainer.length > 0;
        });

        logger.info("rota file processing took time as seconds : {}", stopwatch.elapsed(SECONDS));
    }

    private void insertCourtSchedulerMigrationStatus() throws SQLException {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("B40IM00");
        courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
        courtSchedulerMigrationStatus.setMigrated(false);
        databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
    }

}
