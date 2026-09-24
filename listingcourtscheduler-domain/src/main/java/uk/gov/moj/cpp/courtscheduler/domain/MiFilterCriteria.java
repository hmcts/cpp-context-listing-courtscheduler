package uk.gov.moj.cpp.courtscheduler.domain;


import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.function.BiPredicate;

@SuppressWarnings({"squid:S00116", "squid:S2201"})
public class MiFilterCriteria {

    private final String fromDate;
    private final String toDate;

    private LocalDate fromLocalDate;
    private LocalDate toLocalDate;

    private static final BiPredicate<LocalDate, LocalDate> IN_30_DAY_RANGE = (from, to) -> {
        final long daysBetween = ChronoUnit.DAYS.between(from, to);
        return daysBetween <= 30;
    };

    private static final BiPredicate<LocalDate, LocalDate> DATES_IN_PAST = (from, to) -> {
        final LocalDate today = LocalDate.now();
        return !from.isAfter(today) || !to.isAfter(today);
    };

    private static final BiPredicate<LocalDate, LocalDate> FROM_NOT_AFTER_TO = (from, to) -> !from.isAfter(to);

    public MiFilterCriteria(final String fromDate, final String toDate) {
        this.fromLocalDate = LocalDate.parse(fromDate);
        this.toLocalDate = LocalDate.parse(toDate);
        this.fromDate = fromDate;
        this.toDate = toDate;
    }
    public MiFilterCriteria(final LocalDate fromDate, final LocalDate toDate) {
        this.fromLocalDate = fromDate;
        this.toLocalDate = toDate;
        this.fromDate = fromDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        this.toDate = toDate.format(DateTimeFormatter.BASIC_ISO_DATE);
    }

    public String getFromDate() {
        return fromDate;
    }

    public String getToDate() {
        return toDate;
    }

    public LocalDate getFromLocalDate() {
        return fromLocalDate;
    }

    public LocalDate getToLocalDate() {
        return toLocalDate;
    }

    public boolean isValid() {
        return IN_30_DAY_RANGE.test(fromLocalDate, toLocalDate)
                && DATES_IN_PAST.test(fromLocalDate, toLocalDate)
                && FROM_NOT_AFTER_TO.test(fromLocalDate, toLocalDate);
    }

    @Override
    public String toString() {
        return "MiExtractRange{" +
                ", fromDate='" + fromDate + '\'' +
                ", toDate='" + toDate + '\'' +
                '}';

    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MiFilterCriteria that = (MiFilterCriteria) o;
        return Objects.equals(fromDate, that.fromDate) && Objects.equals(toDate, that.toDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fromDate, toDate);
    }
}