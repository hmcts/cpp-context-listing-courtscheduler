package uk.gov.moj.cpp.courtscheduler.domain;


import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.function.BiPredicate;

@SuppressWarnings({"squid:S00116", "squid:S2201"})
public class MiExtractRange {

    private final String fromDate;
    private final String toDate;

    private LocalDate fromLocalDate;
    private LocalDate toLocalDate;

    private final BiPredicate<LocalDate, LocalDate> IN_30_DAY_RANGE = (from, to) -> {
        final long daysBetween = ChronoUnit.DAYS.between(from, to);
        return daysBetween <= 30;
    };

    private final BiPredicate<LocalDate, LocalDate> DATES_IN_PAST = (from, to) -> {
        final LocalDate today = LocalDate.now();
        return !fromLocalDate.isAfter(today) || !toLocalDate.isAfter(today);
    };

    private final BiPredicate<LocalDate, LocalDate> FROM_NOT_AFTER_TO = (from, to) -> !this.fromLocalDate.isAfter(toLocalDate);

    public MiExtractRange(final String fromDate, final String toDate) {
        this.fromLocalDate = LocalDate.parse(fromDate);
        this.toLocalDate = LocalDate.parse(toDate);
        this.fromDate = fromDate;
        this.toDate = toDate;
    }

    public String getFromDate() {
        return fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public boolean isValid() {
        return IN_30_DAY_RANGE.test(fromLocalDate, toLocalDate)
                && DATES_IN_PAST.test(fromLocalDate, toLocalDate)
                && FROM_NOT_AFTER_TO.test(fromLocalDate, toLocalDate);
    }

    @Override
    public String toString() {
        return "MiExtractRange{" +
                "fromDate='" + fromDate + '\'' +
                ", toDate='" + toDate + '\'' +
                '}';
    }
}