package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util;

import static java.util.Objects.nonNull;
import static java.util.stream.Collectors.toMap;

import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.exception.RotaFileProcessorException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.springframework.stereotype.Service;

@Service
public class PropertiesLoader {

    private final Properties properties = new Properties();

    public Map<String, String> getXmlProperties(final String propertiesFile) {
        try (final InputStream inputStream = PropertiesLoader.class.getClassLoader().getResourceAsStream(propertiesFile)) {
            if (nonNull(inputStream)) {
                properties.load(inputStream);
            }
        } catch (final IOException e) {
            throw new RotaFileProcessorException(e);
        }

        return properties.stringPropertyNames()
                .stream()
                .collect(toMap(properties::getProperty, v -> v));
    }
}