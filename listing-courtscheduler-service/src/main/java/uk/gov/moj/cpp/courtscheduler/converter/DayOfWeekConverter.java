package uk.gov.moj.cpp.courtscheduler.converter;

import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Session;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

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
        for (String day : daysOfWeek) {
            dayOfWeekList.add(DayOfWeek.valueOf(day.trim()));
        }
        return dayOfWeekList;
    }

    public static Set<DayOfWeek> convert(final JsonArray listOfDays) {
        final Set<DayOfWeek> dayOfWeekList = new HashSet<>();
        for (JsonValue day : listOfDays) {
            dayOfWeekList.add(DayOfWeek.valueOf(day.toString().replace("\"", "").toUpperCase()));
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