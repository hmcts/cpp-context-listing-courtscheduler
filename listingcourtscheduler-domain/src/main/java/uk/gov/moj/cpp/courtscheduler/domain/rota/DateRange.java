package uk.gov.moj.cpp.courtscheduler.domain.rota;

import java.time.LocalDate;

public class DateRange {
    private final LocalDate start;
    private final LocalDate end;

    public DateRange(LocalDate start, LocalDate end) {
        this.start = start;
        this.end = end;
    }

    public LocalDate getStart() {
        return this.start;
    }
    public LocalDate getEnd() {
        return this.end;
    }
}
