package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.LocalDate.parse;
import static java.time.format.DateTimeFormatter.ofPattern;
import static java.time.temporal.ChronoUnit.MONTHS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.ROTA_PERIOD;

import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class RotaPeriodDateInfoProvider {
    private static final DateTimeFormatter formatter = ofPattern("yyyy-MM-dd");

    private final LocalDate rotaPeriodStartDate;
    private final DayOfWeek rotaPeriodStartDay;
    private final LocalDate rotaPeriodEndDate;
    private final DayOfWeek rotaPeriodEndDay;
    private final long monthsBetweenRotaPeriod;

    public RotaPeriodDateInfoProvider(final Map<RotaPayload, Map<String, Map<String, String>>> records) {

        final Map<String, Map<String, String>> rotaPeriod = records.get(ROTA_PERIOD);
        final Map<String, String> rotaDetail = rotaPeriod.values().iterator().next();
        final String rotaPeriodStartDateStr = rotaDetail.get("rotaPeriodStartDate");
        final String rotaPeriodEndDateStr = rotaDetail.get("rotaPeriodEndDate");

        rotaPeriodStartDate = parse(rotaPeriodStartDateStr, formatter);
        rotaPeriodEndDate = parse(rotaPeriodEndDateStr, formatter);
        rotaPeriodStartDay = rotaPeriodStartDate.getDayOfWeek();
        rotaPeriodEndDay = rotaPeriodEndDate.getDayOfWeek();
        monthsBetweenRotaPeriod = MONTHS.between(rotaPeriodStartDate, rotaPeriodEndDate) + 1;

    }

    public DayOfWeek getRotaPeriodEndDay() {
        return rotaPeriodEndDay;
    }

    public DayOfWeek getRotaPeriodStartDay() {
        return rotaPeriodStartDay;
    }

    public LocalDate getRotaPeriodStartDate() {
        return rotaPeriodStartDate;
    }

    public LocalDate getRotaPeriodEndDate() {
        return rotaPeriodEndDate;
    }

    public long getMonthsBetweenRotaPeriod() {
        return monthsBetweenRotaPeriod;
    }

    @Override
    public String toString() {
        return "RotaPeriodProcessor{" +
                "\n rotaPeriodStartDate=" + rotaPeriodStartDate +
                "\n rotaPeriodStartDay=" + rotaPeriodStartDay +
                "\n rotaPeriodEndDate=" + rotaPeriodEndDate +
                "\n rotaPeriodEndDay=" + rotaPeriodEndDay +
                "\n monthsBetweenRotaPeriod=" + monthsBetweenRotaPeriod +
                "\n}";
    }
}
