package uk.gov.moj.cpp.courtscheduler.integration.utils;

import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;

/**
 * JUnit 5 extension that boots an Azurite Testcontainer for the annotated test class.
 * Mirrors hmcts/service-hmcts-springboot-demo/azure-azureite-storage but without the
 * Spring {@code ApplicationContextInitializer} half — this codebase uses CDI, not Spring.
 * Tests wire their {@code AzureBlobClientService} via the static accessors below.
 */
public class AzuriteContainerInitialise implements BeforeAllCallback, AfterAllCallback {

    private static final String AZURITE_IMAGE = "mcr.microsoft.com/azure-storage/azurite:3.35.0";
    private static final int BLOB_PORT = 10000;
    private static final String ACCOUNT_NAME = "devstoreaccount1";

    /**
     * Fixed host-side port so the Wildfly container can reach this container
     * via {@code host.docker.internal:10000} regardless of which Docker engine or
     * compose network it's on. Requires port 10000 to be free on the host — the
     * docker-compose {@code cpp-azurite} service (which also binds 10000) must
     * stay gated behind its {@code azurite} profile.
     */
    private static final int FIXED_HOST_PORT = 10000;

    /**
     * Azurite's well-known, publicly documented development account key — NOT a secret.
     * Hardcoded into every Azurite release: https://github.com/Azure/Azurite#default-storage-account
     * Overridable via the AZURITE_ACCOUNT_KEY env var so secret-scanners in CI can be silenced
     * without changing source.
     */
    private static final String ACCOUNT_KEY = System.getenv().getOrDefault(
            "AZURITE_ACCOUNT_KEY",
            "Eby8vdM02xNOcqFlqUwJPLlmEtlCDXJ1OUzFT50uSRZ6IFsuFq2UVErCz4I6tq/K1SZFPTOtr/KBHBeksoGMGw==");

    @SuppressWarnings("resource")
    private static final GenericContainer<?> AZURITE = new GenericContainer<>(AZURITE_IMAGE)
            .withCreateContainerCmdModifier(cmd -> {
                cmd.getHostConfig().withPortBindings(
                        new com.github.dockerjava.api.model.PortBinding(
                                com.github.dockerjava.api.model.Ports.Binding.bindPort(FIXED_HOST_PORT),
                                new com.github.dockerjava.api.model.ExposedPort(BLOB_PORT)));
            })
            .withExposedPorts(BLOB_PORT)
            .withCommand("azurite-blob --blobHost 0.0.0.0 --skipApiVersionCheck")
            .waitingFor(Wait.forLogMessage(".*Blob service successfully listens.*", 1));

    @Override
    public void beforeAll(final ExtensionContext context) {
        if (!AZURITE.isRunning()) {
            AZURITE.start();
            preCreateContainer("schedulelistinginput");
            preCreateContainer("schedulelistingoutput");
        }
    }

    @Override
    public void afterAll(final ExtensionContext context) {
        // Container is static and shared; leave running so parallel/subsequent test classes
        // can reuse it within the same JVM. Ryuk will clean up on JVM exit.
    }

    public static String getBlobEndpoint() {
        // Test JVM on the host reaches Azurite via the fixed host port.
        return "http://" + AZURITE.getHost() + ":" + FIXED_HOST_PORT + "/" + ACCOUNT_NAME;
    }

    public static String getConnectionString() {
        return "DefaultEndpointsProtocol=http;"
                + "AccountName=" + ACCOUNT_NAME + ";"
                + "AccountKey=" + ACCOUNT_KEY + ";"
                + "BlobEndpoint=" + getBlobEndpoint() + ";";
    }

    private static void preCreateContainer(final String containerName) {
        final BlobServiceClient client = new BlobServiceClientBuilder()
                .endpoint(getBlobEndpoint())
                .connectionString(getConnectionString())
                .buildClient();
        client.getBlobContainerClient(containerName).createIfNotExists();
    }
}
