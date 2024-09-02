package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

public class ProvisionalDataExtractDateInfoProvider {

    private final LocalDate provisionalDataExtractStartDate;
    private final DayOfWeek provisionalDataExtractStartDay;
    private final LocalDate provisionalDataExtractEndDate;
    private final DayOfWeek provisionalDataExtractEndDay;
    private final int provisionalDataExtractCountToPopulate;

    public ProvisionalDataExtractDateInfoProvider(final LocalDate rotaPeriodStartDate,
                                                  final DayOfWeek provisionalDataStartDay,
                                                  final int rotaFileCycleLength) {

        provisionalDataExtractStartDate = rotaPeriodStartDate.with(TemporalAdjusters.firstInMonth(provisionalDataStartDay));
        provisionalDataExtractStartDay = provisionalDataExtractStartDate.getDayOfWeek();
        provisionalDataExtractEndDate = provisionalDataExtractStartDate.plusDays(rotaFileCycleLength-1L);
        provisionalDataExtractEndDay = provisionalDataExtractEndDate.getDayOfWeek();
        provisionalDataExtractCountToPopulate = (int) DAYS.between(provisionalDataExtractStartDate, provisionalDataExtractEndDate)+1;
    }

    public LocalDate getProvisionalDataExtractStartDate() {
        return provisionalDataExtractStartDate;
    }

    public DayOfWeek getProvisionalDataExtractStartDay() {
        return provisionalDataExtractStartDay;
    }

    public LocalDate getProvisionalDataExtractEndDate() {
        return provisionalDataExtractEndDate;
    }

    public DayOfWeek getProvisionalDataExtractEndDay() {
        return provisionalDataExtractEndDay;
    }

    public int getProvisionalDataExtractDaysCountToPopulate() {
        return provisionalDataExtractCountToPopulate;
    }

    @Override
    public String toString() {
        return "RotaPeriodProcessor{" +
                "\n provisionalDataExtractStartDate=" + provisionalDataExtractStartDate +
                "\n provisionalDataExtractStartDay=" + provisionalDataExtractStartDay +
                "\n provisionalDataExtractEndDate=" + provisionalDataExtractEndDate +
                "\n provisionalDataExtractEndDay=" + provisionalDataExtractEndDay +
                "\n provisionalDataExtractDaysCountToPopulate=" + provisionalDataExtractCountToPopulate +
                "\n}";
    }
}
