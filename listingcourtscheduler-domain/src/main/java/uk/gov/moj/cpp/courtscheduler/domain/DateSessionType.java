package uk.gov.moj.cpp.courtscheduler.domain;

import java.time.LocalDate;
import java.util.Objects;

public record DateSessionType(LocalDate date, SessionType sessionType) {

    public DateSessionType {
        Objects.requireNonNull(date, "date");
    }
}
