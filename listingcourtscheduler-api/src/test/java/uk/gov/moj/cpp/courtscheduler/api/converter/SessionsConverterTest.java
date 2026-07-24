package uk.gov.moj.cpp.courtscheduler.api.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SessionsConverterTest {

    @InjectMocks
    private SessionsConverter sessionsConverter;

    @Test
    public void shouldConvertJsonObject_ToSessionsParam() {

        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sessions", Json.createArrayBuilder()
                        .add("550e8400-e29b-41d4-a716-446655440000")
                        .add("550e8400-e29b-41d4-a716-446655440001")
                        .add("550e8400-e29b-41d4-a716-446655440002"))
                .build();

        SessionsParam sessionsParam = sessionsConverter.convert(jsonObject.toString());

        assertEquals("550e8400-e29b-41d4-a716-446655440000", sessionsParam.getSessions().get(0));
    }
}