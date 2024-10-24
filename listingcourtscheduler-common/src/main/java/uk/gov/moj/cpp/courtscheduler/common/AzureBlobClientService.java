package uk.gov.moj.cpp.courtscheduler.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.concurrent.TimeUnit.SECONDS;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;

import java.io.InputStream;
import java.time.Duration;
import java.util.AbstractMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.azure.core.util.Configuration;
import com.azure.core.util.ConfigurationBuilder;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.azure.storage.blob.specialized.BlobLeaseClient;
import com.azure.storage.blob.specialized.BlobLeaseClientBuilder;
import com.google.common.base.Stopwatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AzureBlobClientService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobClientService.class);
    private static final String ERROR_MSG = "Azure %s is not specified. Please add configuration for `%s`";

    @Inject
    @Value(key = "courtscheduler.rotaslStorageConnectionString", defaultValue = "DefaultEndpointsProtocol=https;AccountName=sasteccmscsl;AccountKey=+p3GXQguT4npJqxd6gAPfDgLu0YuJ3n1+hpTQYg1BQn0UL5Ut+bDDE7l2qrRNTt/yW5jNyf5mRUmM11F8dnkpA==;EndpointSuffix=core.windows.net;")
    private String rotaslStorageConnectionString;

    @Inject
    @Value(key ="courtscheduler.rotaslStorageAccountName", defaultValue = "sasteccmscsl")
    private String rotaslStorageAccountName;

    @Inject
    @Value(key = "courtscheduler.rotaslInputContainerName", defaultValue = "schedulelistinginput")
    private String rotaslInputContainerName;

    @Inject
    @Value(key = "courtscheduler.rotaslArchiveContainerName", defaultValue = "schedulelistingoutput")
    private String rotaslArchiveContainerName;

    @Inject
    private StorageApplicationParameters storageApplicationParameters;

    private BlobContainerClient blobContainerClient = null;

    public static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";

    @PostConstruct
    void init() {
        checkNotNull(rotaslInputContainerName,
                format(ERROR_MSG, "input container name", "courtscheduler.rotaslInputContainerName"));
        checkNotNull(rotaslArchiveContainerName,
                format(ERROR_MSG, "archive container name", "courtscheduler.rotaslArchiveContainerName"));
        checkNotNull(rotaslStorageAccountName,
                format(ERROR_MSG, "storage account name", "courtscheduler.rotaslStorageAccountName"));
    }

    public void connect(final String blobContainerName) {
        final Configuration configuration = new ConfigurationBuilder()
                .putProperty(AZURE_CLIENT_ID, storageApplicationParameters.getAzureLocalMiClientId())
                .putProperty(AZURE_TENANT_ID, storageApplicationParameters.getAzureLocalMiTenantId())
                .build();

        final BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .endpoint(format("https://%s.blob.core.windows.net/", rotaslStorageAccountName))
                .credential(new DefaultAzureCredentialBuilder()
                        .tenantId(storageApplicationParameters.getAzureLocalMiTenantId())
                        .managedIdentityClientId(storageApplicationParameters.getAzureLocalMiClientId())
                        .configuration(configuration)
                        .build())
                .buildClient();

        blobContainerClient = blobServiceClient.getBlobContainerClient(blobContainerName);

        LOGGER.info("blobContainerClient : {}", blobContainerClient);
    }

    public Map<String, BlobItem> collectListBlobItems(final String blobFilePrefix) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        LOGGER.info("Connecting to azure blob storage to collect Blob Items from : {} on {}", rotaslInputContainerName, now());
        connect(rotaslInputContainerName);

        final Map<String, BlobItem> downloadedBlobMap = new HashMap<>();
        LOGGER.info("before calling listBlobs: {}", blobFilePrefix);

        final ListBlobsOptions listBlobsOptions = new ListBlobsOptions().setPrefix(blobFilePrefix);

        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(10)).stream().toList()) {
            final String blobName = blobItem.getName();
            downloadedBlobMap.put(blobName, blobItem);
            LOGGER.info("Downloading blob file with name : {} from azure blob storage on {}", blobName, now());
        }
        LOGGER.info("Total time taken to collect Blob Items from {} is : {} : seconds", rotaslInputContainerName, stopwatch.elapsed(SECONDS));
        return downloadedBlobMap;
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
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(10))) {
            final String blobName = blobItem.getName();
            if (blobNameOfFileToBeDeleted.contains(blobName)) {
                releaseLease(blobName);
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
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(10))) {
            final String blobName = blobItem.getName();
            final BlobClient blob = blobContainerClient.getBlobClient(blobName);
            // Try to acquire a lease. If successful, it means the file is available.
            BlobLeaseClient leaseClient = new BlobLeaseClientBuilder()
                    .blobClient(blob)
                    .buildClient();
            leaseClient.acquireLease(-1);
            //blob.releaseLease(AccessCondition.generateLeaseCondition(leaseId));
            return Optional.of(new AbstractMap.SimpleEntry<>(blobName, blobItem));
        }

        return Optional.empty();
    }

    public void releaseLease(String releaseBlobName) {
        final ListBlobsOptions listBlobsOptions = new ListBlobsOptions().setPrefix(releaseBlobName);
        for(BlobItem blobItem : blobContainerClient.listBlobs(listBlobsOptions, Duration.ofMinutes(10))) {
            final String blobName = blobItem.getName();
            if (releaseBlobName.contains(blobName)) {
                final BlobClient blob = blobContainerClient.getBlobClient(blobName);
                // Try to acquire a lease. If successful, it means the file is available.
                BlobLeaseClient leaseClient = new BlobLeaseClientBuilder()
                        .blobClient(blob)
                        .buildClient();
                leaseClient.releaseLease();
                break;
            }
        }
    }
}
