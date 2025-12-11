package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.nio.charset.Charset.defaultCharset;
import static javax.json.Json.createReader;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import javax.json.JsonObject;
import javax.json.JsonReader;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(FileUtil.class);

    public static String getPayload(final String path) {
        String request = null;
        try (final InputStream inputStream = FileUtil.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(inputStream, notNullValue());
            request = IOUtils.toString(inputStream, defaultCharset());
        } catch (final Exception e) {
            LOGGER.error("Error consuming file from location {}", path, e);
            fail("Error consuming file from location " + path);
        }
        return request;
    }

    public static JsonObject payloadToObject(final String payload) throws IOException {
        //InputStream inputStream = new ByteArrayInputStream(payload.getBytes(StandardCharsets.UTF_8));
        try (final InputStream inputStream = new ByteArrayInputStream(payload.getBytes())) {
            final JsonReader jsonReader = createReader(inputStream);
            return jsonReader.readObject();
        }
    }
}
