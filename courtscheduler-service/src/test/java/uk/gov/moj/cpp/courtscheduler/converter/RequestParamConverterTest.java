package uk.gov.moj.cpp.courtscheduler.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;

import javax.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RequestParamConverterTest {

    @InjectMocks
    RequestParamConverter requestParamConverter;

    @Test
    public void shouldConvertJsonObjectToRequestParam() {
        JsonObject jsonObject = toJsonObject();
        HearingSlotRequestParam hearingSlotRequestParam = requestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertEquals("BA124", hearingSlotRequestParam.ouCode());
    }

    private JsonObject toJsonObject() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.hearing.slots.json"));
    }
}