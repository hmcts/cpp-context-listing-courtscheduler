package uk.gov.moj.cpp.courtscheduler.integration;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static javax.ws.rs.core.Response.Status.ACCEPTED;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.fail;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.setupUserAsSystemUser;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceCourtRooms;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataCourtRoomSessionAllocations;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataJudiciaries;
import static uk.gov.moj.cpp.courtscheduler.integration.utils.StubUtil.stubGetReferenceDataRotaBusinessTypes;

import uk.gov.moj.cpp.courtscheduler.common.AzureBlobClientService;
import uk.gov.moj.cpp.courtscheduler.integration.utils.FileUtil;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import javax.ws.rs.core.Response;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RotaFileProcessorIT extends AbstractIT {

    private static final String ROTASL_FILE_PROCESSOR_URL = "/rotasl/process-rota-files";

    private AzureBlobClientService azureBlobClientService = new AzureBlobClientService();

    private final String azureBlobInputContainerName = "schedulelistinginput";
    private static final String rotaslStorageConnectionString = "DefaultEndpointsProtocol=https;AccountName=sadevcommonscsl;AccountKey=HMx/mhSuq/1Gbf7R/d+WmuP8X9w3eqvYS3Sg9rhvch0KLO5Qr+rcS70emQKRLLJptS5GzcBiOdQe+AStaKyOig==;EndpointSuffix=core.windows.net;";

    @BeforeAll
    static void setupSystemUser() {
        setupUserAsSystemUser(USER_ID.toString());
    }

    @BeforeEach
    public void setUpAzureBlobClientService() {
        setField(azureBlobClientService, "rotaslStorageConnectionString", rotaslStorageConnectionString);
    }

    @Test
    void shouldProcessRotaFiles() throws IOException, SQLException {
        stubGetReferenceDataRotaBusinessTypes("referencedata.rota-business-types-duration-based.json");
        stubGetReferenceCourtRooms("referencedata.rota-courtrooms.json");
        stubGetReferenceDataCourtRoomSessionAllocations("referencedata.rota-courtroom-sessionallocations.json");
        stubGetReferenceDataJudiciaries("referencedata.judiciaries.json");

        final String fileBlobName = "lja_bedfordshire_rota_20240402T180039Z.xml";
        final InputStream rotaFileInputStream = FileUtil.class.getClassLoader().getResourceAsStream(format("rotafileprocessor/%s", fileBlobName));

        if (isNull(rotaFileInputStream)) {
            fail("rotaFileInputStream is null");
            return;
        }
        final byte[] rotaFileAsBytes = IOUtils.toByteArray(rotaFileInputStream);
        // upload the rota file first
        azureBlobClientService.uploadProcessedFile(new ByteArrayInputStream(rotaFileAsBytes), (long) rotaFileAsBytes.length, fileBlobName, azureBlobInputContainerName);
        insertCourtSchedulerMigrationStatus();

        // then call rota file processor api
        final Response response = postCommand(ROTASL_FILE_PROCESSOR_URL, "application/vnd.courtscheduler.rotasl.process_rota_files+json", USER_ID, null);

        // await until this file uploaded into archive container

        // then after do validation against database to see if we have expected data for this rota file

        assertThat(response.getStatus(), is(ACCEPTED.getStatusCode()));
    }

    private void insertCourtSchedulerMigrationStatus() throws SQLException {
        final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = new CourtSchedulerMigrationStatus();
        courtSchedulerMigrationStatus.setOuCode("B40IM00");
        courtSchedulerMigrationStatus.setCourtCentreId("000f36bc-f33a-42ea-8a6c-8103636c5341");
        courtSchedulerMigrationStatus.setMigrated(false);
        databaseSeeder.insertCourtScheduleMigrationStatus(courtSchedulerMigrationStatus);
    }

}
