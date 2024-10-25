package uk.gov.moj.cpp.courtscheduler.integration.utils;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

public class PropertiesLoader {

    private static final Properties properties = new Properties();

    public static Map<String, String> getProperties(final String propertiesFile) throws IOException {
        final InputStream inputStream = PropertiesLoader.class.getClassLoader().getResourceAsStream(propertiesFile);
        if (nonNull(inputStream)) {
            properties.load(inputStream);
        }

        return properties.stringPropertyNames()
                .stream()
                .collect(toMap(property -> property, properties::getProperty));
    }
}