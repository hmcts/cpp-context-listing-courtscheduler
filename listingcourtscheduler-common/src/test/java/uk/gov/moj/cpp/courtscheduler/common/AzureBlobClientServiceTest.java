package uk.gov.moj.cpp.courtscheduler.common;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.justice.services.test.utils.core.reflection.ReflectionUtil.setField;

import com.azure.core.credential.TokenCredential;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AzureBlobClientServiceTest {

    @Spy
    @InjectMocks
    private AzureBlobClientService service;

    @Mock
    private StorageApplicationParameters storageApplicationParameters;

    @Mock
    private BlobServiceClientBuilder blobServiceClientBuilder;

    @Mock
    private BlobServiceClient blobServiceClient;

    @Mock
    private BlobContainerClient blobContainerClient;

    @BeforeEach
    void setUp() {
        doReturn(blobServiceClientBuilder).when(service).newBlobServiceClientBuilder();
        when(blobServiceClientBuilder.buildClient()).thenReturn(blobServiceClient);
        when(blobServiceClient.getBlobContainerClient(anyString())).thenReturn(blobContainerClient);
    }

    @Test
    void shouldUseStorageEndpointDirectlyWhenEndpointIsSet() {
        when(blobServiceClientBuilder.endpoint(anyString())).thenReturn(blobServiceClientBuilder);
        when(blobServiceClientBuilder.connectionString(anyString())).thenReturn(blobServiceClientBuilder);
        setField(service, "rotaslStorageEndpoint", "http://localhost:10000/devstoreaccount1");
        setField(service, "rotaslStorageConnectionString", "UseDevelopmentStorage=true");
        setField(service, "rotaslInputContainerName", "schedulelistinginput");

        service.connect("schedulelistinginput");

        verify(blobServiceClientBuilder).endpoint("http://localhost:10000/devstoreaccount1");
        verify(blobServiceClientBuilder).connectionString("UseDevelopmentStorage=true");
        verify(blobServiceClientBuilder, never()).credential(any(TokenCredential.class));
    }

    @Test
    void shouldUseConnectionStringOnlyWhenEndpointEmptyAndAccountNameEmpty() {
        when(blobServiceClientBuilder.connectionString(anyString())).thenReturn(blobServiceClientBuilder);
        setField(service, "rotaslStorageEndpoint", "");
        setField(service, "rotaslStorageAccountName", "");
        setField(service, "rotaslStorageConnectionString", "DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=abc123==");
        setField(service, "rotaslInputContainerName", "schedulelistinginput");

        service.connect("schedulelistinginput");

        verify(blobServiceClientBuilder, never()).endpoint(anyString());
        verify(blobServiceClientBuilder).connectionString("DefaultEndpointsProtocol=https;AccountName=myaccount;AccountKey=abc123==");
        verify(blobServiceClientBuilder, never()).credential(any(TokenCredential.class));
    }

    @Test
    void shouldUseManagedIdentityWhenEndpointEmptyAndAccountNameSet() {
        when(blobServiceClientBuilder.endpoint(anyString())).thenReturn(blobServiceClientBuilder);
        when(blobServiceClientBuilder.credential(any(TokenCredential.class))).thenReturn(blobServiceClientBuilder);
        setField(service, "rotaslStorageEndpoint", "");
        setField(service, "rotaslStorageAccountName", "sasteccmscsl");
        setField(service, "rotaslStorageConnectionString", "");
        setField(service, "rotaslInputContainerName", "schedulelistinginput");
        when(storageApplicationParameters.getAzureLocalMiTenantId()).thenReturn("tenant-id");
        when(storageApplicationParameters.getAzureLocalMiClientId()).thenReturn("client-id");

        service.connect("schedulelistinginput");

        verify(blobServiceClientBuilder).endpoint("https://sasteccmscsl.blob.core.windows.net/");
        verify(blobServiceClientBuilder).credential(any(TokenCredential.class));
        verify(blobServiceClientBuilder, never()).connectionString(anyString());
    }
}
