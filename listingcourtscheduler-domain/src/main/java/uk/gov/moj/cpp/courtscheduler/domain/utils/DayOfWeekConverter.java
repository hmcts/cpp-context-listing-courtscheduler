package uk.gov.moj.cpp.courtscheduler.domain.utils;

import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class DayOfWeekConverter {

    private DayOfWeekConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static Set<DayOfWeek> convert(final String listOfDays) {
        if (listOfDays.trim().isEmpty()) {
            return new HashSet<>();
        }

        final String[] daysOfWeek = listOfDays.split(",");
        final Set<DayOfWeek> dayOfWeekList = new HashSet<>();
        for (String day : daysOfWeek) {
            dayOfWeekList.add(DayOfWeek.valueOf(day.trim().toUpperCase(Locale.ROOT)));
        }
        return dayOfWeekList;
    }

    public static String convert(List<DayOfWeek> daysOfWeekList) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (DayOfWeek dayOfWeek : daysOfWeekList) {
            stringBuilder.append(dayOfWeek.toString()).append(",");
        }
        return stringBuilder.toString();
    }

}
