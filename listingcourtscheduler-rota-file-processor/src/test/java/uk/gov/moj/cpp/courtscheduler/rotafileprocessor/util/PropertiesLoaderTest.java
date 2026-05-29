package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.exception.RotaFileProcessorException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PropertiesLoaderTest {

    @InjectMocks
    private PropertiesLoader propertiesLoader;

    @Test
    void shouldLoadProperties() {
        final Map<String, String> propertiesMap = propertiesLoader.getXmlProperties("rotaXml.properties");
        assertFalse(propertiesMap.isEmpty());
    }

    @Test
    void shouldNotLoadPropertiesIfPropertiesFileNotExists() {
        final Map<String, String> propertiesMap = propertiesLoader.getXmlProperties("not-exist-rota-xml.properties");
        assertTrue(propertiesMap.isEmpty());
    }

    @Test
    void shouldThrowRotaFileProcessorExceptionIfPropertiesLoadFailed() throws IOException {
        final Properties properties = spy(Properties.class);
        setField(propertiesLoader, "properties", properties);
        doThrow(IOException.class).when(properties).load(any(InputStream.class));

        assertThrows(RotaFileProcessorException.class, () -> propertiesLoader.getXmlProperties("rotaXml.properties"));

    }
}
