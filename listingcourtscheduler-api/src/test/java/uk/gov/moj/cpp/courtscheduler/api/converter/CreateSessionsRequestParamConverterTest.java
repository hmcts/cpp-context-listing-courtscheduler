package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;

import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
@ExtendWith(MockitoExtension.class)
class CreateSessionsRequestParamConverterTest {

    private final CreateSessionsRequestParamConverter converter = new CreateSessionsRequestParamConverter();

    @Test
    public void shouldConvertJsonObjectToCreateSessionRequestParam() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sessions", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("courtCentreId", randomUUID().toString())
                                .add("courtRoomId", randomUUID().toString())
                                .add("sessionType", "type1")
                                .add("businessType", "business1")
                                .add("duration", 2)
                                .add("panel", "panel1")
                                .add("repeatDays", Json.createArrayBuilder().add("MONDAY").add("TUESDAY"))
                                .add("jurisdiction", "jurisdiction1")
                        ))
                .add("repeatPattern", Json.createObjectBuilder()
                        .add("frequency", "EVERY_WEEK")
                        .add("repeatFor", 2)
                        .add("startDate", "2022-01-01")
                        .add("endDate", "2022-12-31"))
                .build();

        CreateSessionRequestParam result = converter.convert(jsonObject);

        assertThat(result.getSessionList().size(), is(1));
        assertThat(result.getRepeatPattern().getFrequency(), is(RepeatFrequency.EVERY_WEEK));
    }

    @Test
    public void shouldHandleEmptyRepeatDaysArray() {
        JsonObject jsonObject = Json.createObjectBuilder()
                .add("sessions", Json.createArrayBuilder()
                        .add(Json.createObjectBuilder()
                                .add("courtCentreId", "centre1")
                                .add("courtRoom", "room1")
                                .add("sessionType", "type1")
                                .add("businessType", "business1")
                                .add("duration", 2)
                                .add("panel", "panel1")
                                .add("repeatDays", Json.createArrayBuilder())))
                .add("repeatPattern", Json.createObjectBuilder()
                        .add("repeatFrequency", "EVERY_WEEK")
                        .add("repeatFor", 2)
                        .add("startDate", "2022-01-01")
                        .add("endDate", "2022-12-31"))
                .build();

        assertThrows(IllegalArgumentException.class, () -> converter.convert(jsonObject));
    }
}