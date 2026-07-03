package uk.gov.moj.cpp.courtscheduler.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.Optional.empty;
import static java.util.concurrent.TimeUnit.SECONDS;

import org.springframework.beans.factory.annotation.Value;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;

import java.io.InputStream;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import com.azure.core.util.Configuration;
import com.azure.core.util.ConfigurationBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.BlobStorageException;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobLeaseClient;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import com.google.common.base.Stopwatch;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class AzureBlobClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobClientService.class);
    private static final String ERROR_MSG = "Azure %s is not specified. Please add configuration for `%s`";

    @Value("${courtscheduler.rotaslStorageConnectionString:}")
    private String rotaslStorageConnectionString;

    @Value("${courtscheduler.rotaslStorageAccountName:}")
    private String rotaslStorageAccountName;

    @Value("${courtscheduler.rotaslInputContainerName:schedulelistinginput}")
    private String rotaslInputContainerName;

    @Value("${courtscheduler.rotaslArchiveContainerName:schedulelistingoutput}")
    private String rotaslArchiveContainerName;

    @Inject
    private StorageApplicationParameters storageApplicationParameters;

    private BlobContainerClient blobContainerClient = null;

    public static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";
    public static final Duration TIMEOUT_DURATION_FOR_BLOB_STORAGE = Duration.ofMinutes(10);

    @PostConstruct
    void init() {
        checkNotNull(rotaslInputContainerName,
                format(ERROR_MSG, "input container name", "courtscheduler.rotaslInputContainerName"));
        checkNotNull(rotaslArchiveContainerName,
                format(ERROR_MSG, "archive container name", "courtscheduler.rotaslArchiveContainerName"));
    }

    public void connect(final String blobContainerName) {
        final BlobServiceClient blobServiceClient = createBlobServiceClient();

        blobContainerClient = blobServiceClient.getBlobContainerClient(blobContainerName);
        blobContainerClient.createIfNotExists();
        LOGGER.info("blobContainerClient : {}", blobContainerClient);
    }

    public BlobContent downloadFiles(final BlobItem blobItem) {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            LOGGER.info("Connecting to azure blob storage to download files from : {} on {}", rotaslInputContainerName, now());
            connect(rotaslInputContainerName);
            final String blobName = blobItem.getName();
            byte[] blobByteArray = blobContainerClient.getBlobClient(blobName).downloadContent().toBytes();

            LOGGER.info("Total time taken for all the blobs to be downloaded from {} is : {} : seconds", rotaslInputContainerName, stopwatch.elapsed(SECONDS));

            return new BlobContent(blobByteArray);
    }

    public void deleteFile(final String blobNameOfFileToBeDeleted, final Optional<String> containerNameOptional) {
        final String containerName = containerNameOptional.orElseGet(() -> rotaslInputContainerName);
        final Stopwatch stopwatch = Stopwatch.createStarted();
        LOGGER.info("Connecting to azure blob storage to delete files from the container {} on {}", containerName, now());
        connect(containerName);

        final ListBlobsOptions listBlobsOptions = new ListBlobsOptions().setPrefix(blobNameOfFileToBeDeleted);
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, TIMEOUT_DURATION_FOR_BLOB_STORAGE)) {
            final String blobName = blobItem.getName();
            if (blobNameOfFileToBeDeleted.contains(blobName)) {
                blobContainerClient.getBlobClient(blobName).delete();
                LOGGER.info("Deleted blob file successfully with name {} from azure blob storage container {} on {}", blobName, containerName, now());
                LOGGER.info("Total time taken to delete files from azure blob storage container {} is : {} : seconds", containerName, stopwatch.elapsed(SECONDS));
                break;
            }
        }
    }

    /**
     * Upload a file to Azure blob storage
     *
     * @param file                File to upload
     * @param fileSize            Size of file to upload
     * @param destinationFileName file name
     * @return void
     * @throws AzureBlobClientException
     */
    public void uploadProcessedFile(final InputStream file, final Long fileSize, final String destinationFileName, final Optional<String> containerNameOptional) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        final String containerName = containerNameOptional.orElseGet(() -> rotaslArchiveContainerName);
        LOGGER.info("Connecting to azure blob storage to upload files into {} on {}", containerName, now());
        connect(containerName);
        LOGGER.info("Uploading {} file to azure blob storage on {}", destinationFileName, now());
        blobContainerClient.getBlobClient(destinationFileName).upload(file, fileSize, true);
        LOGGER.info("Total time taken for file upload to azure blob storage {} is : {} : seconds", containerName, stopwatch.elapsed(SECONDS));
    }

    public Optional<Map.Entry<String, BlobItem>> findAvailableFile(final String blobFilePrefix) {
        connect(rotaslInputContainerName);

        final ListBlobsOptions listBlobsOptions = new ListBlobsOptions().setPrefix(blobFilePrefix);
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, TIMEOUT_DURATION_FOR_BLOB_STORAGE)) {
            final String blobName = blobItem.getName();
            if(!blobName.contains("failed")) {
                final BlobClient blob = blobContainerClient.getBlobClient(blobName);
                // Try to acquire a lease. If successful, it means the file is available.
                BlobLeaseClient leaseClient = new BlobLeaseClientBuilder()
                        .blobClient(blob)
                        .buildClient();
                try {
                    LOGGER.info(blobName + " Acquiring lease");
                    String leaseId = leaseClient.acquireLease(-1);
                    return Optional.of(new AbstractMap.SimpleEntry<>(leaseId, blobItem));
                } catch (BlobStorageException storageException) {
                    LOGGER.info(blobName + " blob is already acquired lease");
                }
            }
        }

        return Optional.empty();
    }

    public void releaseLease(String releaseBlobName, final String leaseId, boolean failed) {
        connect(rotaslInputContainerName);
        final ListBlobsOptions listBlobsOptions = new ListBlobsOptions().setPrefix(releaseBlobName);
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, TIMEOUT_DURATION_FOR_BLOB_STORAGE)) {
            final String blobName = blobItem.getName();
            if (releaseBlobName.contains(blobName)) {
                LOGGER.info(blobName + " Releasing lease");
                final BlobClient blobClient = blobContainerClient.getBlobClient(blobName);
                // Try to acquire a lease. If successful, it means the file is available.
                BlobLeaseClient leaseClient = new BlobLeaseClientBuilder()
                        .blobClient(blobClient)
                        .leaseId(leaseId)
                        .buildClient();
                leaseClient.releaseLease();
                if(failed) {
                    String newBlobName = blobName+"_failed";
                    final BlobClient newBlobclient = blobContainerClient.getBlobClient(newBlobName);
                    newBlobclient.copyFromUrl(blobClient.getBlobUrl());
                    deleteFile(blobName, empty());
                }
                break;
            }
        }
    }

    private BlobServiceClient createBlobServiceClient() {
        if (StringUtils.isEmpty(rotaslStorageAccountName)) {
            return new BlobServiceClientBuilder()
                    .connectionString(rotaslStorageConnectionString)
                    .buildClient();
        }

        final Configuration configuration = new ConfigurationBuilder()
                .putProperty(AZURE_CLIENT_ID, storageApplicationParameters.getAzureLocalMiClientId())
                .putProperty(AZURE_TENANT_ID, storageApplicationParameters.getAzureLocalMiTenantId())
                .build();

        return new BlobServiceClientBuilder()
                .endpoint(format("https://%s.blob.core.windows.net/", rotaslStorageAccountName))
                .credential(new DefaultAzureCredentialBuilder()
                        .tenantId(storageApplicationParameters.getAzureLocalMiTenantId())
                        .managedIdentityClientId(storageApplicationParameters.getAzureLocalMiClientId())
                        .configuration(configuration)
                        .build())
                .buildClient();
    }
}
