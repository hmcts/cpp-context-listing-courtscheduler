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
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingSlotRequestParamConverterTest {
    private static final String DATE_2025_07_28 = "2025-07-28";
    private static final String DATE_2025_07_30 = "2025-07-30";
    private static final String ADULT_2 = "ADULT";
    private static final String OU1_2 = "OU1";


    @InjectMocks
    private HearingSlotRequestParamConverter hearingSlotRequestParamConverter;

    @Test
    void shouldConvertJsonObjectToRequestParam() {
        final JsonObject jsonObject = toJsonObject();
        final HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertEquals("BA124", hearingSlotRequestParam.ouCode());
        assertEquals("MAGISTRATES", hearingSlotRequestParam.jurisdiction());
    }

    @Test
    void shouldConvertJsonObjectToRequestParam_withEmptyValues() {
        final JsonObject jsonObject = toJsonObject_WithSomeEmptyValues();
        final HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

        assertNotNull(hearingSlotRequestParam);
        assertEquals(StringUtils.EMPTY, hearingSlotRequestParam.ouCode());
    }

    @Test
    void shouldConvertJsonObjectToRequestParam_withFieldsNotPresent() {
        final JsonObject jsonObject = toJsonObject_WithFieldsNotPresent();
        final HearingSlotRequestParam hearingSlotRequestParam = hearingSlotRequestParamConverter.convert(jsonObject);

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

    @Test
    void shouldMapStatusAndJurisdictionFromJson() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .add(RequestParameterConstant.STATUS.getLabel(), "DRAFT")
                .add(RequestParameterConstant.JURISDICTION.getLabel(), "CROWN")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertEquals("DRAFT", param.status());
        assertEquals("CROWN", param.jurisdiction());
    }

    @Test
    void shouldConvertWithAllOptionalFieldsPresent() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .add(RequestParameterConstant.EXACT_HEARING_START_DATETIME.getLabel(), "2025-07-28T10:00:00Z")
                .add(RequestParameterConstant.OU_LEVEL2.getLabel(), "L2")
                .add(RequestParameterConstant.COURT_ROOM.getLabel(), "room-1")
                .add(RequestParameterConstant.COURT_ROOM_NUMBER.getLabel(), "101")
                .add(RequestParameterConstant.BUSINESS_TYPE.getLabel(), "CRIMINAL")
                .add(RequestParameterConstant.COURT_SESSION.getLabel(), "AD")
                .add(RequestParameterConstant.IS_SLOT_BASED.getLabel(), "true")
                .add(RequestParameterConstant.HEARING_START_TIME.getLabel(), "2025-07-28T09:00:00+01:00")
                .add(RequestParameterConstant.SHOW_OVERBOOKING_SLOTS.getLabel(), true)
                .add(RequestParameterConstant.AVAILABLE_DURATION_MINS.getLabel(), "60")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertEquals("2025-07-28T10:00:00Z", param.exactHearingStartDateTime());
        assertEquals("L2", param.oucodeL2Code());
        assertEquals("room-1", param.courtRoomId());
        assertEquals("101", param.courtRoomNumber());
        assertEquals("CRIMINAL", param.businessType());
        assertEquals("AD", param.courtSession());
        assertTrue(param.isSlotBased());
        assertEquals("2025-07-28T09:00:00+01:00", param.hearingStartTime());
        assertTrue(param.showOverbookedSlots());
        assertEquals("60", param.duration());
    }

    @Test
    void shouldConvertJurisdictionNullWhenAbsent() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertNull(param.jurisdiction());
    }

    @Test
    void shouldConvertShowOverbookingSlotsFalseWhenAbsent() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertFalse(param.showOverbookedSlots());
    }

    @Test
    void shouldMapAvailableDurationMinsToDuration() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .add(RequestParameterConstant.AVAILABLE_DURATION_MINS.getLabel(), "3600")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertEquals("3600", param.duration());
    }

    @Test
    void shouldReturnNullDurationWhenAvailableDurationMinsAbsent() {
        final JsonObject json = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .build();

        final HearingSlotRequestParam param = hearingSlotRequestParamConverter.convert(json);

        assertNotNull(param);
        assertNull(param.duration());
    }

    @Test
    void shouldConvertIsSlotBasedFromStringTrueOrFalse() {
        final JsonObject jsonTrue = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .add(RequestParameterConstant.IS_SLOT_BASED.getLabel(), "true")
                .build();
        final HearingSlotRequestParam paramTrue = hearingSlotRequestParamConverter.convert(jsonTrue);
        assertTrue(paramTrue.isSlotBased());

        final JsonObject jsonFalse = Json.createObjectBuilder()
                .add(RequestParameterConstant.PANEL.getLabel(), ADULT_2)
                .add(RequestParameterConstant.SESSION_START_DATE.getLabel(), DATE_2025_07_28)
                .add(RequestParameterConstant.SESSION_END_DATE.getLabel(), DATE_2025_07_30)
                .add(RequestParameterConstant.OU_CODE.getLabel(), OU1_2)
                .add(RequestParameterConstant.PAGE_SIZE.getLabel(), "10")
                .add(RequestParameterConstant.PAGE_NUMBER.getLabel(), "1")
                .add(RequestParameterConstant.IS_SLOT_BASED.getLabel(), "false")
                .build();
        final HearingSlotRequestParam paramFalse = hearingSlotRequestParamConverter.convert(jsonFalse);
        assertFalse(paramFalse.isSlotBased());
    }

    private JsonObject toJsonObject() {
        final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.get.hearing.slots.json"));
    }

    private JsonObject toJsonObject_WithSomeEmptyValues() {
        final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.empty.values.get.hearing.slots.json"));
    }

    private JsonObject toJsonObject_WithFieldsNotPresent() {
        final StringToJsonObjectConverter stringToJsonObjectConverter = new StringToJsonObjectConverter();
        return stringToJsonObjectConverter.convert(fileToString("/test-data/courtscheduler.no.fields.get.hearing.slots.json"));
    }
}