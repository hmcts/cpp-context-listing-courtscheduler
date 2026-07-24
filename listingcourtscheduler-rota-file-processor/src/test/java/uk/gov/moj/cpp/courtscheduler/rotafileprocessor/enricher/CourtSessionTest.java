package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtSessionTest {

    @Test
    void shouldGetCourtSession() {
        final LocalDate sessionDate = LocalDate.of(2024,10,17);
        final CourtSession session = new CourtSession();
        final String result = session.getCourtSession(sessionDate, "AM");
        assertThat(result,is("THUAM"));
    }

}
