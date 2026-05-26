package uk.gov.moj.cpp.courtscheduler.api.converter;

import static jakarta.json.Json.createObjectBuilder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static uk.gov.moj.cpp.platform.test.data.utils.FileUtil.fileToString;

import uk.gov.moj.cpp.courtscheduler.common.converter.StringToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;

import jakarta.json.JsonObject;

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

    // Production path: HearingSlotsApi.getHearingSlots receives showOverbookedSlots
    // as Boolean and stores it in the qp map via .toString() before round-tripping
    // through toJsonObject(...). Parsson then exposes it as JsonString("true"),
    // not JsonValue.TRUE — getBoolean() ClassCastException → HTTP 500. The other
    // boolean field in this converter (isSlotBased) reads via getString()+valueOf
    // so it survives; showOverbookedSlots was missed.
    @Test
    void shouldAcceptShowOverbookedSlotsAsJsonStringTrue() {
        final JsonObject jsonObject = createObjectBuilder()
                .add("showOverbookedSlots", "true")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(param);
        assertTrue(param.showOverbookedSlots());
    }

    @Test
    void shouldAcceptShowOverbookedSlotsAsJsonStringFalse() {
        final JsonObject jsonObject = createObjectBuilder()
                .add("showOverbookedSlots", "false")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(param);
        assertFalse(param.showOverbookedSlots());
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