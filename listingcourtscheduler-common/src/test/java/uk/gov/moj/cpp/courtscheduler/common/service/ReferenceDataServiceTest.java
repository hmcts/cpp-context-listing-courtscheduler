package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_OU_COURT_ROOMS_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_COURT_ROOM_SESSION_ALLOCATIONS_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_JUDICIARIES_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.getPayload;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.mockBusinessType;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.mockCourtRooms;

import uk.gov.moj.cpp.courtscheduler.common.converter.JsonObjectToObjectConverter;

import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Mock
    private uk.gov.moj.cpp.courtscheduler.common.service.CommonPlatformQueryClient commonPlatformQueryClient;

    @InjectMocks
    private ReferenceDataService referenceDataService;

    @Spy
    private JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectToObjectConverter(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules());

    @Test
    void shouldReturnBusinessTypeWhenTypeCodeIsProvided() {

        final JsonObject responsePayload = getPayload("/test-data/referencedata.get.businesstypes.json");

        final JsonObject envelope = responsePayload;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);

        final Optional<BusinessType> businessType = referenceDataService.getRotaBusinessTypeByCode("APP");
        assertThat(businessType, Matchers.notNullValue());
        assertEquals("APP", businessType.get().getTypeCode());

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<java.util.Map> envelopeCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(commonPlatformQueryClient).getReferenceData(any(), any(), envelopeCaptor.capture());
        java.util.Map<String, Object> payload = envelopeCaptor.getValue();
        assertEquals("ALL", String.valueOf(payload.get("jurisdiction")));
        assertFalse(payload.containsKey("typeCode"));
    }

    @Test
    void shouldReturnCourtRoomWhenCourtRoomIdIsProvided() {
        final String courtRoomId = randomUUID().toString();
        final JsonObject responsePayload = mockCourtRooms(courtRoomId);
        final JsonObject envelope = responsePayload;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);
        final Optional<CourtRoom> courtRoom = referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId);
        assertThat(courtRoom, Matchers.notNullValue());
    }

    @Test
    void shouldRequestPublicHolidays() {
        final JsonObject responsePayload = Json.createObjectBuilder().build();
        final JsonObject envelope = responsePayload;
        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);
        final List<LocalDate> publicholidays = referenceDataService.getPublicHolidays("DIV1", LocalDate.of(2024, 1, 1), LocalDate.of(2021, 12, 31));
        assertThat(publicholidays, Matchers.empty());
    }

    @Test
    void shouldGetRotaCourtRoomByVenue() {
        final JsonObject courtRoomJson = getPayload("/test-data/referencedata.get.rota.courtrooms.json");
        final JsonObject envelope = courtRoomJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);
        final Optional<CourtRoom> courtRoom = referenceDataService.getRotaCourtRoomByVenue(new Venue(77, 0, "Court 9"), new HashMap<>());
        assertThat(courtRoom, Matchers.notNullValue());
    }


    @Test
    void shouldGetJudiciariesMap() {
        final JsonObject judiciariesJson = getPayload("/test-data/referencedata-judiciaries.json");
        final JsonObject envelope = judiciariesJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);
        final List<Judiciary> judiciaries = referenceDataService.getJudiciariesMap();
        assertTrue(isNotEmpty(judiciaries));
        // Verify requestedName is populated
        assertThat(judiciaries.get(0).getRequestedName(), Matchers.is("HER HONOUR JUDGE K WANT QC, HONORARY RECORDER OF WALES"));
        if (judiciaries.size() > 1) {
            assertThat(judiciaries.get(1).getRequestedName(), Matchers.is("HER HONOUR JUDGE N SHANT QC, HONORARY RECORDER OF DERBY"));
        }
    }

    @Test
    void shouldGetCourtRoomSessionAllocationsMap() {
        final JsonObject courtRoomSessionAllocationsJson = getPayload("/test-data/referencedata-court-room-session-allocations.json");
        final JsonObject envelope = courtRoomSessionAllocationsJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);
        final List<CourtRoomSessionAllocation> courtRoomSessionAllocations = referenceDataService.getCourtRoomSessionAllocationsMap();
        assertTrue(isNotEmpty(courtRoomSessionAllocations));
    }

    @Test
    void shouldGetRotaBusinessTypes() {
        final JsonObject businessTypesJson = getPayload("/test-data/referencedata.get.businesstypes.json");
        final JsonObject envelope = businessTypesJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);

        final List<BusinessType> businessTypes = referenceDataService.getRotaBusinessTypes();
        assertTrue(isNotEmpty(businessTypes));

        @SuppressWarnings({"rawtypes", "unchecked"})
        ArgumentCaptor<java.util.Map> envelopeCaptor = ArgumentCaptor.forClass(java.util.Map.class);
        verify(commonPlatformQueryClient).getReferenceData(any(), any(), envelopeCaptor.capture());
        java.util.Map<String, Object> payload = envelopeCaptor.getValue();
        assertEquals("ALL", String.valueOf(payload.get("jurisdiction")));

        // Verify mapping of jurisdiction
        Optional<BusinessType> appType = businessTypes.stream().filter(b -> "APP".equals(b.getTypeCode())).findFirst();
        assertTrue(appType.isPresent());
        assertEquals(MAGISTRATES.getJurisdiction(), appType.get().getJurisdiction());
    }

    @Test
    void shouldGetCpCourtRooms() {
        final JsonObject courtRoomsJson = getPayload("/test-data/referencedata.get.ou-courtrooms.json");
        final JsonObject envelope = courtRoomsJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);

        final List<CourtRoom> courtRooms = referenceDataService.getCpCourtRooms();
        assertTrue(isNotEmpty(courtRooms));
        assertEquals(1, courtRooms.size());
        CourtRoom courtRoom = courtRooms.get(0);
        assertEquals("8e912353-3b5d-36c3-953e-ad3b94b19de3", courtRoom.getId());
        assertEquals(121, courtRoom.getCppCourtRoomId());
        assertEquals("121", courtRoom.getCourtroomId());
        assertEquals("Courtroom 01", courtRoom.getCourtroomName());
        assertEquals(39, courtRoom.getRotaVenueId());
        assertEquals("BEXLEY MAGISTRATES' COURT", courtRoom.getRotaVenueName());
        assertEquals("B01BH00", courtRoom.getOucode());
    }

    @Test
    void shouldGetRotaBusinessTypesMap() {
        final JsonObject businessTypesJson = getPayload("/test-data/referencedata.get.businesstypes.json");
        final JsonObject envelope = businessTypesJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);

        final Map<String, BusinessType> businessTypes = referenceDataService.getRotaBusinessTypesMap();
        assertFalse(businessTypes.isEmpty());
    }

    @Test
    void shouldGetCourtRoomsMap() {
        final JsonObject courtRoomJson = getPayload("/test-data/referencedata.get.rota.courtrooms.json");
        final JsonObject envelope = courtRoomJson;

        when(commonPlatformQueryClient.getReferenceData(any(), any(), any())).thenReturn(envelope);

        final Map<UUID, CourtRoom> courtRoomsMap = referenceDataService.getCourtRoomsMap();
        assertFalse(courtRoomsMap.isEmpty());

        ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService, atLeastOnce()).saveRotaProcessLog(logCaptor.capture());
        RotaProcessLog saved = logCaptor.getValue();

        // Code matches
        assertEquals(
                CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND.code(),
                saved.getErrorCode()
        );
    }

}