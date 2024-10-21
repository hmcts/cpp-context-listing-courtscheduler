package uk.gov.moj.cpp.courtscheduler.common;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.String.format;
import static java.time.LocalDate.now;
import static java.util.concurrent.TimeUnit.SECONDS;

import uk.gov.justice.services.common.configuration.Value;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureAPIMInvocationException;
import uk.gov.moj.cpp.courtscheduler.common.exception.AzureBlobClientException;
import uk.gov.moj.cpp.courtscheduler.common.service.data.BlobContent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.StreamSupport;

import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import com.azure.core.credential.AccessToken;
import com.azure.core.credential.TokenRequestContext;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.Configuration;
import com.azure.identity.ClientSecretCredential;
import com.azure.identity.ClientSecretCredentialBuilder;
import com.azure.identity.ManagedIdentityCredential;
import com.azure.identity.ManagedIdentityCredentialBuilder;
import com.azure.storage.blob.BlobClientBuilder;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import com.azure.storage.blob.models.BlobItem;
import com.azure.storage.blob.models.ListBlobsOptions;
import com.google.common.base.Stopwatch;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsToken;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.CloudBlobClient;
import com.microsoft.azure.storage.blob.CloudBlobContainer;
import com.microsoft.azure.storage.blob.CloudBlockBlob;
import com.microsoft.azure.storage.blob.ListBlobItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

@ApplicationScoped
public class AzureBlobClientService {

    private static final String AZURE_SERVICE_HTTP_ERROR = "Error returned from azure service. Http code: %d and error code: %s";
    private static final String CONNECTION_URI_PARSE_ERROR = "Connection URI parse error";
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

    private CloudBlobContainer container = null;

    private BlobContainerClient blobContainerClient = null;

    public static final String AZURE_CLIENT_ID = "AZURE_CLIENT_ID";
    public static final String AZURE_TENANT_ID = "AZURE_TENANT_ID";
    public static final String BEARER_TOKEN = "Bearer %s";

    @PostConstruct
    void init() {
        checkNotNull(rotaslInputContainerName,
                format(ERROR_MSG, "container name", "courtscheduler.rotaslInputContainerName"));
        checkNotNull(rotaslArchiveContainerName,
                format(ERROR_MSG, "container name", "courtscheduler.rotaslArchiveContainerName"));
    }

    public void connect(final String blobContainerName) {
        try {
            final String manageIdentityToken = getTokenFromLocalClientSecretCredentials();
            LOGGER.info("manageIdentityToken : {} - rotaslStorageAccountName: {}", manageIdentityToken, rotaslStorageAccountName);
//            BlobServiceClient blobServiceClient = new BlobServiceClientBuilder().credential(
//                    new ManagedIdentityCredentialBuilder()
//                            .clientId(storageApplicationParameters.getAzureLocalMiClientId())
//                            .build())
//                    .buildClient();
//            final BlobContainerClient blobContainerClient = blobServiceClient.getBlobContainerClient(blobContainerName);
            final StorageCredentialsToken credentialsToken = new StorageCredentialsToken(rotaslStorageAccountName, format(BEARER_TOKEN, manageIdentityToken));
            LOGGER.info("credentialsToken : {}", credentialsToken);
            final CloudStorageAccount storageAccount = new CloudStorageAccount(credentialsToken, true);
//            final CloudBlobClient blobClient = new CloudBlobClient(new URI(format("https://%s.blob.core.windows.net/", rotaslStorageAccountName)), credentialsToken);
            final CloudBlobClient blobClient = storageAccount.createCloudBlobClient();
            container = blobClient.getContainerReference(blobContainerName);

            final Configuration configuration = new Configuration();
            configuration.put(AZURE_CLIENT_ID, storageApplicationParameters.getAzureLocalMiClientId());
            configuration.put(AZURE_TENANT_ID, storageApplicationParameters.getAzureLocalMiTenantId());

            final ManagedIdentityCredential managedIdentityCredential = getManagedIdentityCredential();
            blobContainerClient = new BlobContainerClientBuilder()
                    .endpoint(format("https://%s.blob.core.windows.net/", rotaslStorageAccountName))
                    .configuration(configuration)
                    .credential(managedIdentityCredential)
                    .connectionString(rotaslStorageConnectionString)
                                    .containerName(blobContainerName)
                                            .buildClient();

            LOGGER.info("container : {}", container);
        }
        catch (URISyntaxException ex) {
            throw new AzureBlobClientException(CONNECTION_URI_PARSE_ERROR, ex);
        }
        catch (final StorageException ex) {
            throw new AzureBlobClientException(format(
                    AZURE_SERVICE_HTTP_ERROR,
                    ex.getHttpStatusCode(), ex.getErrorCode()), ex);
        }

    }

    public Map<String, BlobItem> collectListBlobItems(final String blobFilePrefix) {
        final Stopwatch stopwatch = Stopwatch.createStarted();
        LOGGER.info("Connecting to azure blob storage to collect Blob Items from : {} on {}", rotaslInputContainerName, now());
        connect(rotaslInputContainerName);

        final Map<String, BlobItem> downloadedBlobMap = new HashMap<>();
        LOGGER.info("before calling listBlobs: {}", blobFilePrefix);

        for(BlobItem blobItem : blobContainerClient.listBlobs().stream().toList()) {
            final String blobName = blobItem.getName();
            downloadedBlobMap.put(blobName, blobItem);
            LOGGER.info("Downloading blob file with name : {} from azure blob storage on {}", blobName, now());
        }
        LOGGER.info("Total time taken to collect Blob Items from {} is : {} : seconds", rotaslInputContainerName, stopwatch.elapsed(SECONDS));
        return downloadedBlobMap;
    }

    public BlobContent downloadFiles(final BlobItem blobItem) {
        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            BlobContent blobContent = new BlobContent();
            LOGGER.info("Connecting to azure blob storage to download files from : {} on {}", rotaslInputContainerName, now());
            connect(rotaslInputContainerName);
            final String blobName = blobItem.getName();
            final CloudBlockBlob blob = container.getBlockBlobReference(blobName);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            String leaseId = blob.acquireLease(50, null);
            blob.download(outputStream);

            LOGGER.info("Total time taken for all the blobs to be downloaded from {} is : {} : seconds", rotaslInputContainerName, stopwatch.elapsed(SECONDS));

            byte[] blobByteArray = outputStream.toByteArray();
            blobContent.setLeaseId(leaseId);
            blobContent.setBlob(blob);
            blobContent.setBlobByteArray(blobByteArray);
            return blobContent;
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

            final Optional<ListBlobItem> optionalBlobItem = StreamSupport.stream(container.listBlobs(blobName).spliterator(), false)
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
    public void uploadProcessedFile(final InputStream file, final Long fileSize, final String destinationFileName, final Optional<String> containerNameOptional) {

        try {
            final Stopwatch stopwatch = Stopwatch.createStarted();
            final String containerName = containerNameOptional.orElseGet(() -> rotaslArchiveContainerName);
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

    public String getTokenFromLocalClientSecretCredentials() {
        String accessToken = null;
        final Configuration configuration = new Configuration();
        configuration.put(AZURE_CLIENT_ID, storageApplicationParameters.getAzureLocalMiClientId());
        configuration.put(AZURE_TENANT_ID, storageApplicationParameters.getAzureLocalMiTenantId());

        try {
            final ManagedIdentityCredential managedIdentityCredential = new ManagedIdentityCredentialBuilder()
                    .configuration(configuration)
                    .build();
            final TokenRequestContext context = getTokenRequestContext();
            final Mono<String> accessTokenMono = managedIdentityCredential.getToken(context)
                    .map(AccessToken::getToken);
            accessToken = accessTokenMono.block();
        } catch (final AzureAPIMInvocationException azureAPIMInvocationException) {
            LOGGER.error("Failed to acquire Local Access token", azureAPIMInvocationException);
        }
        return accessToken;
    }

    private ManagedIdentityCredential getManagedIdentityCredential() {
        return new ManagedIdentityCredentialBuilder()
                .clientId(storageApplicationParameters.getAzureLocalMiClientId())
                .build();
    }

    private TokenRequestContext getTokenRequestContext() {
        return new TokenRequestContext()
                .addScopes(storageApplicationParameters.getAzureLocalScope());
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
