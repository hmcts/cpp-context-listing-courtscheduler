package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static java.util.UUID.nameUUIDFromBytes;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.CourtScheduleIdGenerator.getCourtScheduleId;

import java.time.LocalDate;

import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.Test;

class CourtScheduleIdGeneratorTest {

    @Test
    public void shouldGenerateException() {
        String roomId = null;
        LocalDate courtSessionDate = EnhancedRandom.random(LocalDate.class);
        String sessionStr = EnhancedRandom.random(String.class);
        String businessType = EnhancedRandom.random(String.class);

        assertThrows(
                IllegalArgumentException.class,
                () -> getCourtScheduleId(roomId, courtSessionDate, sessionStr, businessType),
                String.format("All of roomId: %s, courtSessionDate: %s, session: %s and business type: %s are mandatory ", roomId, courtSessionDate, sessionStr, businessType));
    }

    @Test
    public void shouldGenerateCourtScheduleId() {
        String roomId = EnhancedRandom.random(String.class);
        LocalDate courtSessionDate = EnhancedRandom.random(LocalDate.class);
        String sessionStr = EnhancedRandom.random(String.class);
        String businessType = EnhancedRandom.random(String.class);
        String courtScheduleId = getCourtScheduleId(roomId, courtSessionDate, sessionStr, businessType);

        assertThat(courtScheduleId, is(expected(roomId, courtSessionDate, sessionStr, businessType)));
    }

    private static String expected(String roomId, LocalDate courtSessionDate, String sessionStr, String businessType) {
        final String courtSessionDt = courtSessionDate.toString();
        final String courtSessionId = String.format("%s/%s/%s/%s", roomId, courtSessionDt, sessionStr, businessType);
        return nameUUIDFromBytes(courtSessionId.getBytes()).toString();
    }

}