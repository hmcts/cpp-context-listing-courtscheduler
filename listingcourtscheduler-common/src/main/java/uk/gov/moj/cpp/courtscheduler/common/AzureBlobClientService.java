package uk.gov.moj.cpp.courtscheduler.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.concurrent.TimeUnit.SECONDS;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.security.InvalidKeyException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.google.common.base.Stopwatch;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class AzureBlobClientService {

    private static final String AZURE_SERVICE_HTTP_ERROR = "Error returned from azure service. Http code: %d and error code: %s";
    private static final String CONNECTION_URI_PARSE_ERROR = "Connection URI parse error";
    private static final Logger LOGGER = LoggerFactory.getLogger(AzureBlobClientService.class);
    private static final String ERROR_MSG = "Azure %s is not specified. Please add configuration for `%s`";

    @Inject
    @Value(key = "courtscheduler.rotaslStorageConnectionString", defaultValue = "DefaultEndpointsProtocol=https;AccountName=sadevcommonscsl;AccountKey=HMx/mhSuq/1Gbf7R/d+WmuP8X9w3eqvYS3Sg9rhvch0KLO5Qr+rcS70emQKRLLJptS5GzcBiOdQe+AStaKyOig==;EndpointSuffix=core.windows.net;")
    private String rotaslStorageConnectionString;

    @Inject
    @Value(key = "courtscheduler.rotaslInputContainerName", defaultValue = "schedulelistinginput")
    private String rotaslInputContainerName;

    @Inject
    @Value(key = "courtscheduler.rotaslArchiveContainerName", defaultValue = "schedulelistingoutput")
    private String rotaslArchiveContainerName;

    private CloudBlobContainer container = null;

    @PostConstruct
    void init() {
        checkNotNull(rotaslStorageConnectionString,
                format(ERROR_MSG, "connection string",
                        "courtscheduler.rotaslStorageConnectionString"));
        checkNotNull(rotaslInputContainerName,
                format(ERROR_MSG, "container name", "courtscheduler.rotaslInputContainerName"));
        checkNotNull(rotaslArchiveContainerName,
                format(ERROR_MSG, "container name", "courtscheduler.rotaslArchiveContainerName"));
    }

    public void connect(final String blobContainerName) {
        try {
            final CloudStorageAccount storageAccount = CloudStorageAccount.parse(rotaslStorageConnectionString);
            final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            container = blobClient.getContainerReference(blobContainerName);
        } catch (InvalidKeyException ex) {
            throw new AzureBlobClientException("Invalid connection string", ex);
        } catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
        } catch (final StorageException ex) {
            throw new AzureBlobClientException(format(
                    AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        }

    }

    public Map<String, byte[]> downloadFiles() {
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            LOGGER.info("Connecting to azure blob storage to download files from : {} on {}", rotaslInputContainerName, now());
            connect(rotaslInputContainerName);

            final Map<String, byte[]> downloadedBlobMap = new HashMap<>();
            for(ListBlobItem blobItem : container.listBlobs()) {
                final String blobName = getBlobName(blobItem.getUri().getPath(), rotaslInputContainerName);
                final CloudBlockBlob blob = container.getBlockBlobReference(blobName);
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                blob.download(outputStream);

                downloadedBlobMap.put(blobName, outputStream.toByteArray());

                LOGGER.info("Downloading blob file with name : {} from azure blob storage on {}", blobName, now());
            }

            LOGGER.info("Total time taken for all the blobs to be downloaded from {} is : {} : seconds", rotaslInputContainerName, stopwatch.elapsed(SECONDS));

            return downloadedBlobMap;
        } catch (StorageException ex) {
            throw new AzureBlobClientException(format(AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        } catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
        }
    }

    public byte[] downloadFile(final String blobName, final String containerName) {
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            LOGGER.info("Connecting to azure blob storage to downloading file with name {} from : {} on {}", blobName, containerName, now());
            connect(containerName);

            final Optional<ListBlobItem> optionalBlobItem = StreamSupport.stream(container.listBlobs().spliterator(), false)
                    .filter(blobItem -> blobName.equals(getBlobName(blobItem.getUri().getPath(), containerName)))
                    .findAny();

            if (optionalBlobItem.isPresent()) {
                final CloudBlockBlob blob = container.getBlockBlobReference(blobName);
                final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                blob.download(outputStream);

                LOGGER.info("Total time taken for the blob with name {} to be downloaded from {} is : {} : seconds", blobName, containerName, stopwatch.elapsed(SECONDS));
                return outputStream.toByteArray();
            }
        } catch (StorageException ex) {
            throw new AzureBlobClientException(format(AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        } catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
        }

        return null;
    }

    public void deleteFile(final String blobNameOfFileToBeDeleted, final Optional<String> containerNameOptional) {
        try {
            final String containerName = containerNameOptional.orElseGet(() -> rotaslInputContainerName);
            final Stopwatch stopwatch = Stopwatch.createStarted();
            LOGGER.info("Connecting to azure blob storage to delete files from the container {} on {}", containerName, now());
            connect(containerName);

            for(ListBlobItem blobItem : container.listBlobs(blobNameOfFileToBeDeleted)) {
                final String blobName = getBlobName(blobItem.getUri().getPath(), containerName);
                if (blobNameOfFileToBeDeleted.contains(blobName)) {
                    final CloudBlockBlob blob = container.getBlockBlobReference(blobName);
                    blob.delete();

                    LOGGER.info("Deleted blob file successfully with name {} from azure blob storage container {} on {}", blobName, containerName, now());
                    LOGGER.info("Total time taken to delete files from azure blob storage container {} is : {} : seconds", containerName, stopwatch.elapsed(SECONDS));
                    break;
                }
            }
        } catch (StorageException ex) {
            throw new AzureBlobClientException(format(AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        } catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
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
    public void uploadProcessedFile(final InputStream file, final Long fileSize, final String destinationFileName, final String containerName) {

        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            LOGGER.info("Connecting to azure blob storage to upload files into {} on {}", containerName, now());
            connect(containerName);
            final CloudBlockBlob fileBlob = container.getBlockBlobReference(destinationFileName);
            LOGGER.info("Uploading {} file to azure blob storage on {}", destinationFileName, now());
            fileBlob.upload(file, fileSize);
            LOGGER.info("Total time taken for file upload to azure blob storage {} is : {} : seconds", containerName, stopwatch.elapsed(SECONDS));

        } catch (StorageException ex) {
            throw new AzureBlobClientException(format(AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        } catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
        } catch (IOException ex) {
            throw new AzureBlobClientException("Error while uploading file to azure blob storage", ex);
        }
    }

    private String getBlobName(final String blobFilePath, final String containerName) {
        final int index = blobFilePath.lastIndexOf(containerName);
        if (index == -1) {
            throw new AzureBlobClientException(
                    format("Azure S&L blob storage file path and container name doesn't match, filePath: %s, containerName: %s",
                            blobFilePath, containerName));
        }
        return blobFilePath.substring(index + containerName.length() + 1);
    }
}
