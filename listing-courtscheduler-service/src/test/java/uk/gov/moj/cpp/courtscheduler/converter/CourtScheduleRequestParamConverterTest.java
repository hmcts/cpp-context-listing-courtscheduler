package uk.gov.moj.cpp.courtscheduler.converter;

import org.junit.jupiter.api.Test;
import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;

import javax.json.JsonObject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

class CourtScheduleRequestParamConverterTest {

    CourtScheduleRequestParamConverter courtScheduleRequestParamConverter = new CourtScheduleRequestParamConverter();

    @Test
    public void shouldConvertJsonObjectToRequestParam() {
        JsonObject jsonObject = toJsonObject();
        CourtScheduleRequestParam courtScheduleRequestParam = courtScheduleRequestParamConverter.convert(jsonObject);

        assertNotNull(courtScheduleRequestParam);
    }

    private JsonObject toJsonObject() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.court.schedules.json"));
    }
}