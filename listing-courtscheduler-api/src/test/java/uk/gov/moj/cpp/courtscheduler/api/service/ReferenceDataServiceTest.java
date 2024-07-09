package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.REFERENCEDATA_QUERY_PUBLIC_HOLIDAYS_NAME;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.REFERENCEDATA_QUERY_ROTA_BUSINESS_TYPES_NAME;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.mockBusinessType;
import static uk.gov.moj.cpp.courtscheduler.api.helper.SessionsHelper.mockCourtRooms;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.justice.services.messaging.Envelope;
import uk.gov.justice.services.test.utils.framework.api.JsonObjectConvertersFactory;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import javax.json.Json;
import javax.json.JsonObject;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataServiceTest {

    @Mock
    private Requester requester;


    @InjectMocks
    private ReferenceDataService referenceDataService;
    @Spy
    private JsonObjectToObjectConverter jsonToObjectConverter = new JsonObjectConvertersFactory().jsonObjectToObjectConverter();


    @BeforeEach
    void setUp() {
       /* when(referenceDataService.getRotaBusinessTypeByCode(BUSINESS_TYPE_CODE, requester)).thenReturn(Optional.of(new BusinessType()));
        when(referenceDataService.getRotaCourtRoomByCourtRoomId(COURT_ROOM_ID, requester)).thenReturn(Optional.of(new CourtRoom()));*/
    }


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


}