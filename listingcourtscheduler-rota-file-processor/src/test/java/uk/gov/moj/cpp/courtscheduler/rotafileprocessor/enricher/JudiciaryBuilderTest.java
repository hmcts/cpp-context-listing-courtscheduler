package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.util.UUID.randomUUID;
import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.RotaFileParser;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JudiciaryBuilderTest {

    @InjectMocks
    private JudiciaryBuilder judiciaryBuilder;

    @InjectMocks
    private RotaFileParser rotaFileParser;

    @Test
    void shouldBuildJudiciary() throws IOException {
        final String courtScheduleId = randomUUID().toString();
        final String file = "rotafileprocessor/rota_payload.xml";

        final byte[] blobContent = givenBlobContent(file);

        final RotaFileParser rotaFileParser = new RotaFileParser();
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
        final Map<RotaPayload, Map<String, Map<String, String>>> result = rotaFileParser.parse(file, blobContent);
        final Map<String, Map<String, String>> propertyMap = result.get(RotaPayload.SCHEDULE);


        final CourtScheduleJudiciary courtScheduleJudiciary = judiciaryBuilder.build(propertyMap.values().iterator().next(), courtScheduleId);

        assertThat(courtScheduleJudiciary, notNullValue());
        assertEquals("MA15775", courtScheduleJudiciary.getRotaJudiciaryId());
        assertEquals("CS2130184", courtScheduleJudiciary.getCourtListingProfileId());
        assertEquals(courtScheduleId, courtScheduleJudiciary.getCourtScheduleId());
        assertEquals("RIGHT_WINGER", courtScheduleJudiciary.getPosition());
        assertEquals(false, courtScheduleJudiciary.getBenchChairman());
        assertEquals(true, courtScheduleJudiciary.getDeputy());
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = JudiciaryBuilderTest.class.getClassLoader().getResourceAsStream(file)) {

            return toByteArray(inputStream);
        }
    }
}
