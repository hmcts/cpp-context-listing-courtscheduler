package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class PropertiesLoader {

    private static final Properties PROPERTIES = new Properties();

    public static Map<String, String> getProperties(final String propertiesFile) throws IOException {
        try (InputStream inputStream = Thread.currentThread().getContextClassLoader().getResourceAsStream(propertiesFile)) {
            if (nonNull(inputStream)) {
                PROPERTIES.load(inputStream);
            }
        }

        return PROPERTIES.stringPropertyNames()
                .stream()
                .collect(toMap(property -> property, PROPERTIES::getProperty));
    }
}