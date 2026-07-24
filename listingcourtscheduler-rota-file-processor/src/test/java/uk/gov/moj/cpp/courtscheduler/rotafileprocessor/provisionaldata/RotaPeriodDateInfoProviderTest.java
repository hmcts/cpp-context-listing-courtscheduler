package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.io.IOException;
import java.io.InputStream;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaPeriodDateInfoProviderTest {

    @Test
    void shouldReturnDayOfWeek() throws IOException {
        final String file = "rotafileprocessor/rota_payload_sample.xml";
        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final byte[] blobContent = givenBlobContent(file);
        final Map<RotaPayload, Map<String, Map<String, String>>> result = rotaFileParser.parse(file, blobContent);

        final RotaPeriodDateInfoProvider rotaPeriodProcessor = new RotaPeriodDateInfoProvider(result);

        final DayOfWeek startDayOfWeek = rotaPeriodProcessor.getRotaPeriodStartDay();
        assertThat(startDayOfWeek.toString(), is("TUESDAY"));

        final LocalDate rotaPeriodStartDate = rotaPeriodProcessor.getRotaPeriodStartDate();
        assertThat(rotaPeriodStartDate.toString(), is("2019-10-01"));

        final DayOfWeek endDayOfWeek = rotaPeriodProcessor.getRotaPeriodEndDay();
        assertThat(endDayOfWeek.toString(), is("TUESDAY"));

        final LocalDate rotaPeriodEndDate = rotaPeriodProcessor.getRotaPeriodEndDate();
        assertThat(rotaPeriodEndDate.toString(), is("2019-12-31"));

        final long monthsBetweenRotaPeriod = rotaPeriodProcessor.getMonthsBetweenRotaPeriod();
        assertThat(monthsBetweenRotaPeriod, is(3L));

    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaPeriodDateInfoProviderTest.class.getClassLoader().getResourceAsStream(file)) {
            return toByteArray(inputStream);
        }
    }
}
