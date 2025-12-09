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
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.CREATE_SESSIONS_DUPLICATE_COURTROOMS_FOUND;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_COURT_ROOM_SESSION_ALLOCATIONS_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_JUDICIARIES_NAME;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.getPayload;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.mockBusinessType;
import static uk.gov.moj.cpp.courtscheduler.common.helper.SessionsHelper.mockCourtRooms;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.test.utils.framework.api.JsonObjectConvertersFactory;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import javax.json.Json;
import javax.json.JsonObject;

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
    private Requester requester;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @InjectMocks
    private ReferenceDataService referenceDataService;

    @Spy
    private JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectConvertersFactory().jsonObjectToObjectConverter();

    @Test
    void shouldReturnBusinessTypeWhenTypeCodeIsProvided() {

        final JsonObject responsePayload = mockBusinessType("DVLA");

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final Optional<BusinessType> businessType = referenceDataService.getRotaBusinessTypeByCode("DVLA", requester);
        assertThat(businessType, Matchers.notNullValue());
    }

    @Test
    void shouldReturnCourtRoomWhenCourtRoomIdIsProvided() {
        final String courtRoomId = randomUUID().toString();
        final JsonObject responsePayload = mockCourtRooms(courtRoomId);
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final Optional<CourtRoom> courtRoom = referenceDataService.getRotaCourtRoomByCourtRoomId(courtRoomId, requester);
        assertThat(courtRoom, Matchers.notNullValue());
    }

    @Test
    void shouldRequestPublicHolidays() {
        final JsonObject responsePayload = Json.createObjectBuilder().build();
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME)
                .build(), responsePayload);
        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final List<LocalDate> publicholidays = referenceDataService.getPublicHolidays("DIV1", LocalDate.of(2024, 1, 1), LocalDate.of(2021, 12, 31), requester);
        assertThat(publicholidays, Matchers.empty());
    }

    @Test
    void shouldGetRotaCourtRoomByVenue() {
        final JsonObject courtRoomJson = getPayload("/test-data/referencedata.get.rota.courtrooms.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME)
                .build(), courtRoomJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final Optional<CourtRoom> courtRoom = referenceDataService.getRotaCourtRoomByVenue(new Venue(77, 0, "Court 9"), new HashMap<>(), requester);
        assertThat(courtRoom, Matchers.notNullValue());
    }


    @Test
    void shouldGetJudiciariesMap() {
        final JsonObject judiciariesJson = getPayload("/test-data/referencedata-judiciaries.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_JUDICIARIES_NAME)
                .build(), judiciariesJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final List<Judiciary> judiciaries = referenceDataService.getJudiciariesMap(requester);
        assertTrue(isNotEmpty(judiciaries));
    }

    @Test
    void shouldGetCourtRoomSessionAllocationsMap() {
        final JsonObject courtRoomSessionAllocationsJson = getPayload("/test-data/referencedata-court-room-session-allocations.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_COURT_ROOM_SESSION_ALLOCATIONS_NAME)
                .build(), courtRoomSessionAllocationsJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);
        final List<CourtRoomSessionAllocation> courtRoomSessionAllocations = referenceDataService.getCourtRoomSessionAllocationsMap(requester);
        assertTrue(isNotEmpty(courtRoomSessionAllocations));
    }

    @Test
    void shouldGetRotaBusinessTypes() {
        final JsonObject businessTypesJson = getPayload("/test-data/referencedata.get.businesstypes.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), businessTypesJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final List<BusinessType> businessTypes = referenceDataService.getRotaBusinessTypes(requester);
        assertTrue(isNotEmpty(businessTypes));
    }

    @Test
    void shouldGetRotaBusinessTypesMap() {
        final JsonObject businessTypesJson = getPayload("/test-data/referencedata.get.businesstypes.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME)
                .build(), businessTypesJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final Map<String, BusinessType> businessTypes = referenceDataService.getRotaBusinessTypesMap(requester);
        assertFalse(businessTypes.isEmpty());
    }

    @Test
    void shouldGetCourtRoomsMap() {
        final JsonObject courtRoomJson = getPayload("/test-data/referencedata.get.rota.courtrooms.json");
        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName(REFERENCEDATA_QUERY_ROTA_COURT_ROOM_NAME)
                .build(), courtRoomJson);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final Map<UUID, CourtRoom> courtRoomsMap = referenceDataService.getCourtRoomsMap(requester);
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

    @Test
    void shouldGetSpecialismsByJudiciaryIds() {
        final String judiciaryId1 = randomUUID().toString();
        final String judiciaryId2 = randomUUID().toString();

        // Create response payload with new structure: { "judiciarySpecialisms": [{ "judiciaryId": "...", "specialisms": [...] }, ...] }
        // The specialisms are enum strings in an array
        final JsonObject responsePayload = Json.createObjectBuilder()
                .add("judiciarySpecialisms", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judiciaryId", judiciaryId1)
                                .add("specialisms", Json.createArrayBuilder()
                                        .add("MURDER")
                                        .add("ATTEMPTED_MURDER")))
                        .add(Json.createObjectBuilder()
                                .add("judiciaryId", judiciaryId2)
                                .add("specialisms", Json.createArrayBuilder()
                                        .add("SEXUAL_OFFENCE"))))
                .build();

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName("referencedata.query.judiciary-specialisms")
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final List<JudiciarySpecialism> specialisms = referenceDataService.getSpecialismsByJudiciaryIds(
                java.util.Arrays.asList(judiciaryId1, judiciaryId2), requester);

        assertTrue(isNotEmpty(specialisms));
        assertThat(specialisms.size(), Matchers.is(2)); // 1 for judiciary1, 1 for judiciary2
        assertThat(specialisms.get(0).getJudiciaryId(), Matchers.is(judiciaryId1));
        assertThat(specialisms.get(0).getSpecialisms().size(), Matchers.is(2));
        assertThat(specialisms.get(0).getSpecialisms(), Matchers.hasItems(
                uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.MURDER,
                uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.ATTEMPTED_MURDER));
        assertThat(specialisms.get(1).getJudiciaryId(), Matchers.is(judiciaryId2));
        assertThat(specialisms.get(1).getSpecialisms().size(), Matchers.is(1));
        assertThat(specialisms.get(1).getSpecialisms(), Matchers.hasItem(
                uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialismType.SEXUAL_OFFENCE));
    }

    @Test
    void shouldReturnEmptyListWhenJudiciaryIdsIsEmpty() {
        final List<JudiciarySpecialism> specialisms = referenceDataService.getSpecialismsByJudiciaryIds(
                java.util.Collections.emptyList(), requester);

        assertThat(specialisms, Matchers.empty());
    }

    @Test
    void shouldReturnEmptyListWhenJudiciaryIdsIsNull() {
        final List<JudiciarySpecialism> specialisms = referenceDataService.getSpecialismsByJudiciaryIds(
                null, requester);

        assertThat(specialisms, Matchers.empty());
    }

    @Test
    void shouldGetSpecialismsFromArrayWhenJudiciaryIdNotInRequest() {
        final String judiciaryId = randomUUID().toString();

        // Create response payload with the requested judiciaryId
        final JsonObject responsePayload = Json.createObjectBuilder()
                .add("judiciarySpecialisms", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("judiciaryId", judiciaryId)
                                .add("specialisms", Json.createArrayBuilder()
                                        .add("MURDER")
                                        .add("TERRORISM"))))
                .build();

        final Envelope<Object> envelope = Envelope.envelopeFrom(Envelope.metadataBuilder()
                .withId(randomUUID())
                .withName("referencedata.query.judiciary-specialisms")
                .build(), responsePayload);

        when(requester.requestAsAdmin(any(), any())).thenReturn(envelope);

        final List<JudiciarySpecialism> specialisms = referenceDataService.getSpecialismsByJudiciaryIds(
                java.util.Arrays.asList(judiciaryId), requester);

        // The reference data service should return the requested judiciaryId
        assertThat(specialisms.size(), Matchers.is(1));
        assertThat(specialisms.get(0).getJudiciaryId(), Matchers.is(judiciaryId));
        assertThat(specialisms.get(0).getSpecialisms().size(), Matchers.is(2));
    }
}