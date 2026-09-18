package uk.gov.moj.cpp.courtscheduler.api.converter;



import java.time.DayOfWeek;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.json.JsonArray;
import jakarta.json.JsonValue;


public class DayOfWeekConverter {


    private DayOfWeekConverter() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }
    public static Set<DayOfWeek> convert(final String listOfDays) {
        if(listOfDays.trim().isEmpty()) {
            return new HashSet<>();
        }

        final String[] daysOfWeek = listOfDays.split(",");
        final Set<DayOfWeek> dayOfWeekList = new HashSet<>();
        for (final String day : daysOfWeek) {
            dayOfWeekList.add(DayOfWeek.valueOf(day.trim()));
        }
        return dayOfWeekList;
    }

    public static Set<DayOfWeek> convert(final JsonArray listOfDays) {
        final Set<DayOfWeek> dayOfWeekList = new HashSet<>();
        for (final JsonValue day : listOfDays) {
            dayOfWeekList.add(DayOfWeek.valueOf(day.toString().replace("\"", "").toUpperCase()));
        }
        return dayOfWeekList;
    }

    public static String convert(final List<DayOfWeek> daysOfWeekList) {
        final StringBuilder stringBuilder = new StringBuilder();
        for (final DayOfWeek dayOfWeek : daysOfWeekList) {
            stringBuilder.append(dayOfWeek.toString()).append(",");
        }
        return stringBuilder.toString();
    }

}