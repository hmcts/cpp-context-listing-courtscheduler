package uk.gov.moj.cpp.courtscheduler.integration.utils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * One source of truth for the Azurite (Azure Storage emulator) connection
 * string used by the integration-test stack.
 *
 * <p>The raw value lives in {@code docker/.env} (auto-loaded by docker-compose
 * for the compose-network containers) and is copied onto the test classpath
 * by the IT module's {@code processTestResources} task so this helper can
 * read it with {@link ClassLoader#getResourceAsStream(String)}.</p>
 *
 * <p>The value in {@code .env} uses {@code azurite} as the host (the form
 * the app container needs to reach the Azurite service inside the compose
 * network). Tests running on the host JVM call {@link #connectionString()},
 * which replaces {@code azurite} with {@code localhost} — the only
 * occurrence is in the {@code BlobEndpoint} URL.</p>
 *
 * <p>The Microsoft-published account key embedded in that string is the
 * publicly documented Azurite emulator key; it cannot authenticate against
 * real Azure Storage. Marked {@code # gitleaks:allow} on the {@code .env}
 * line so the HMCTS secret scanner treats it as an intentional fixture.</p>
 */
public final class AzuriteFixture {

    private static final String ENV_KEY = "CSCHED_ROTASLSTORAGECONNECTIONSTRING";

    private static final String RAW_CONTAINER_FORM = loadFromEnvOnClasspath();

    private AzuriteFixture() {
    }

    /** Connection string for tests running on the host JVM. */
    public static String connectionString() {
        return RAW_CONTAINER_FORM.replace("azurite", "localhost");
    }

    private static String loadFromEnvOnClasspath() {
        try (InputStream in = AzuriteFixture.class.getResourceAsStream("/.env")) {
            if (in == null) {
                throw new IllegalStateException(
                        "/.env not found on the test classpath — check build.gradle's processTestResources from('docker/.env')");
            }
            final Properties p = new Properties();
            p.load(in);
            String value = p.getProperty(ENV_KEY);
            if (value == null || value.isBlank()) {
                throw new IllegalStateException(ENV_KEY + " missing from /.env on the test classpath");
            }
            // Strip any trailing ` # …` inline comment. docker-compose v2 strips
            // these from .env values; java.util.Properties does not, so we do it
            // here. The connection string we care about has no legitimate ` #`
            // sequence in its content.
            final int commentStart = value.indexOf(" #");
            if (commentStart >= 0) {
                value = value.substring(0, commentStart).stripTrailing();
            }
            return value;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read /.env from the test classpath", e);
        }
    }
}
