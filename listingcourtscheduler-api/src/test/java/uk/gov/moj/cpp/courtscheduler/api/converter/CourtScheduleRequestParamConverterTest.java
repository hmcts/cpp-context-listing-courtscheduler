package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class CourtScheduleRequestParamConverterTest {

    private CourtScheduleRequestParamConverter courtScheduleRequestParamConverter = new CourtScheduleRequestParamConverter();
    
    @Test
    void shouldConvertJsonObjectToRequestParam() {
        final JsonObject jsonObject = toJsonObject();
        final CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(jsonObject);

        assertNotNull(courtScheduleRequestParam);
    }

    private JsonObject toJsonObject() {
        final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.court.schedules.json"));
    }
}