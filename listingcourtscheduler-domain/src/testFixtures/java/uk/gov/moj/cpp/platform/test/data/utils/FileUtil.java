package uk.gov.moj.cpp.platform.test.data.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;

/**
 * Shared test-resource helper, replacing the per-module duplicates that had drifted
 * apart over time (formerly under {@code …common.utils}, {@code …api.utils},
 * {@code …rotafileprocessor.utils}, {@code …integration.utils}). Also stands in for
 * the legacy Justice Services {@code FileUtil} library that wasn't migrated.
 *
 * <p>API surface kept narrow to what callers actually use today:</p>
 * <ul>
 *   <li>{@link #fileToString(String)} — read a classpath resource as a UTF-8 string.</li>
 *   <li>{@link #getPayload(String)} — alias of {@code fileToString} preserving the
 *       legacy method name; callers passed in paths under {@code src/test/resources}.</li>
 *   <li>{@link #payloadToObject(String)} — parse a JSON string as a {@link JsonObject}.</li>
 * </ul>
 *
 * <p>All read failures throw — silently returning {@code null} (as some legacy copies
 * did) hides misconfigured test resources behind opaque NPEs at the call site.</p>
 */
public final class FileUtil {

    private FileUtil() {
    }

    public static String fileToString(final String classpathResource) {
        final String path = classpathResource.startsWith("/") ? classpathResource.substring(1) : classpathResource;
        try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalArgumentException("Test resource not found on classpath: " + classpathResource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read test resource: " + classpathResource, e);
        }
    }

    /** Legacy-named alias of {@link #fileToString(String)} — kept for the test imports we already had. */
    public static String getPayload(final String classpathResource) {
        return fileToString(classpathResource);
    }

    public static JsonObject payloadToObject(final String payload) {
        // ByteArrayInputStream holds no OS resource; JsonReader.close() is a courtesy here.
        try (JsonReader reader = Json.createReader(new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8)))) {
            return reader.readObject();
        }
    }
}
