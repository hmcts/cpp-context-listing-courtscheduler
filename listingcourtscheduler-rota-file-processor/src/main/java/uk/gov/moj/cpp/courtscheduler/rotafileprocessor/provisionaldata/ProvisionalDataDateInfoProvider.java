package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.temporal.ChronoUnit.DAYS;

import java.time.DayOfWeek;
import java.time.LocalDate;

public class ProvisionalDataDateInfoProvider {
    private final LocalDate provisionalDataStartDate;
    private final DayOfWeek provisionalDataStartDay;
    private final LocalDate provisionalDataEndDate;
    private final DayOfWeek provisionalDataEndDay;
    private final int provisionalDataDaysCountToPopulate;
    private final int cyclesToPopulate;
    private final int provisionalDaysInCycleRemaining;

    public ProvisionalDataDateInfoProvider(final LocalDate rotaPeriodEndDate, final LocalDate masterRotaPeriodCutOffDate, final long noOfMonthsToPopulate, final int rotaFileCycleLength) {
        provisionalDataStartDate = masterRotaPeriodCutOffDate;
        provisionalDataStartDay = provisionalDataStartDate.getDayOfWeek();
        provisionalDataEndDate = rotaPeriodEndDate.plusMonths(noOfMonthsToPopulate);
        provisionalDataEndDay = provisionalDataEndDate.getDayOfWeek();
        provisionalDataDaysCountToPopulate = (int) (DAYS.between(provisionalDataStartDate, provisionalDataEndDate))+1;
        cyclesToPopulate = cyclesToPopulate(rotaFileCycleLength);
        provisionalDaysInCycleRemaining =  provisionalDataDaysCountToPopulate % rotaFileCycleLength;
    }

    private int cyclesToPopulate(final int rotaFileCycleLength) {
        final int cyclesDiv = provisionalDataDaysCountToPopulate / rotaFileCycleLength;
        final int cyclesMod = provisionalDataDaysCountToPopulate % rotaFileCycleLength;

        if (cyclesMod == 0){
            return cyclesDiv;
        }
        return cyclesDiv +1 ;
    }

    public LocalDate getProvisionalDataStartDate() {
        return provisionalDataStartDate;
    }

    public DayOfWeek getProvisionalDataStartDay() {
        return provisionalDataStartDay;
    }

    public LocalDate getProvisionalDataEndDate() {
        return provisionalDataEndDate;
    }

    public DayOfWeek getProvisionalDataEndDay() {
        return provisionalDataEndDay;
    }

    public int getProvisionalDataDaysCountToPopulate() {
        return provisionalDataDaysCountToPopulate;
    }

    public int getCyclesToPopulate() {
        return cyclesToPopulate;
    }

    public int getProvisionalDaysInCycleRemaining() {
        return provisionalDaysInCycleRemaining;
    }


    @Override
    public String toString() {
        return "RotaPeriodProcessor{" +
                "\n provisionalDataStartDate=" + provisionalDataStartDate +
                "\n provisionalDataStartDay=" + provisionalDataStartDay +
                "\n provisionalDataEndDate=" + provisionalDataEndDate +
                "\n provisionalDataEndDay=" + provisionalDataEndDay +
                "\n provisionalDataDaysCountToPopulate=" + provisionalDataDaysCountToPopulate +
                "\n cyclesToPopulate=" + cyclesToPopulate +
                "\n provisionalDaysInCycleRemaining=" + provisionalDaysInCycleRemaining +
                "\n}";
    }
}
