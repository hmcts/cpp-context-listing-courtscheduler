package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class CourtSession {

    public String getCourtSession(final LocalDate sessionDate, final String session) {
        return sessionDate.getDayOfWeek()
                .getDisplayName(TextStyle.SHORT, Locale.UK)
                .toUpperCase() + session;
    }
}
