package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.util;

import static java.util.stream.Collectors.toMap;

import uk.gov.moj.cpp.courtscheduler.api.exception.RotaFileProcessorException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class PropertiesLoader {

    private PropertiesLoader() {
    }

    public static Map<String, String> getXmlProperties(final String propertiesFile) {
        final Properties props = new Properties();

        try (final InputStream input = PropertiesLoader.class.getClassLoader().getResourceAsStream(propertiesFile)) {
            props.load(input);
        } catch (final IOException e) {
            throw new RotaFileProcessorException(e);
        }

        return props.stringPropertyNames()
                .stream()
                .collect(toMap(props::getProperty, v -> v));
    }
}