package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.justice.services.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;

import javax.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingSlotRequestParamConverterTest {

    @InjectMocks
    HearingSlotRequestParamConverter hearingSlotRequestParamConverter;

    @Test
    public void shouldConvertJsonObjectToRequestParam() {
        JsonObject jsonObject = toJsonObject();
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertEquals("BA124", hearingSlotRequestParam.ouCode());
    }

    @Test
    public void shouldConvertJsonObjectToRequestParam_withEmptyValues() {
        JsonObject jsonObject = toJsonObject_WithSomeEmptyValues();
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertEquals(StringUtils.EMPTY, hearingSlotRequestParam.ouCode());
    }

    @Test
    public void shouldConvertJsonObjectToRequestParam_withFieldsNotPresent() {
        JsonObject jsonObject = toJsonObject_WithFieldsNotPresent();
        HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertNull(hearingSlotRequestParam.ouCode());
    }

    private JsonObject toJsonObject() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.hearing.slots.json"));
    }

    private JsonObject toJsonObject_WithSomeEmptyValues() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.empty.values.get.hearing.slots.json"));
    }

    private JsonObject toJsonObject_WithFieldsNotPresent() {
        StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.no.fields.get.hearing.slots.json"));
    }
}