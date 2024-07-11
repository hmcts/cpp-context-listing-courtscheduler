package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;

class CourtScheduleRequestParamConverterTest {

    CourtScheduleRequestParamConverter courtScheduleRequestParamConverter = new CourtScheduleRequestParamConverter();
    
    @Test
    void shouldConvertJsonObjectToRequestParam() {
        JsonObject jsonObject = toJsonObject();
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(jsonObject);

        assertNotNull(courtScheduleRequestParam);
    }

    private JsonObject toJsonObject() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.court.schedules.json"));
    }
}