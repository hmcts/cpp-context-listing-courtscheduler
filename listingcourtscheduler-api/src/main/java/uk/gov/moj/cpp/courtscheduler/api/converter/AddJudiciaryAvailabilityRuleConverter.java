package uk.gov.moj.cpp.courtscheduler.api.converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

public class AddJudiciaryAvailabilityRuleConverter implements Converter<JsonObject, AddJudiciaryAvailabilityRuleRequest> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    public static final String RECURRING_TYPE = "recurringType";
    public static final String REPEAT_DAYS = "repeatDays";
    public static final String INDEX = "index";
    public static final String SESSION_TYPE = "sessionType";

    @Override
    public AddJudiciaryAvailabilityRuleRequest convert(final JsonObject jsonObject) {
        final AddJudiciaryAvailabilityRuleRequest request = new AddJudiciaryAvailabilityRuleRequest();

        request.setJudiciaryId(jsonObject.getString("judiciaryId"));
        request.setCourtHouseId(jsonObject.getString("courtHouseId"));
        request.setStartDate(LocalDate.parse(jsonObject.getString("startDate"), AddJudiciaryAvailabilityRuleConverter.DATE_FORMATTER));
        request.setEndDate(LocalDate.parse(jsonObject.getString("endDate"), AddJudiciaryAvailabilityRuleConverter.DATE_FORMATTER));

        if (jsonObject.containsKey(RECURRING_TYPE) && !jsonObject.isNull(RECURRING_TYPE)) {
            request.setRecurringType(RecurringType.valueOf(jsonObject.getString(RECURRING_TYPE)));
        }

        if (jsonObject.containsKey(SESSION_TYPE) && !jsonObject.isNull(SESSION_TYPE)) {
            request.setSessionType(SessionType.valueOf(jsonObject.getString(SESSION_TYPE)));
        }

        // Convert repeatDays - support both array of strings and array of objects with index
        if (jsonObject.containsKey(REPEAT_DAYS) && !jsonObject.isNull(REPEAT_DAYS)) {
            final JsonArray repeatDaysArray = jsonObject.getJsonArray(REPEAT_DAYS);
            final List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = this.convertRepeatDays(repeatDaysArray);
            request.setRepeatDays(repeatDays);
        }

        // Convert unavailabilities array
        if (jsonObject.containsKey("unavailabilities") && !jsonObject.isNull("unavailabilities")) {
            final JsonArray unavailabilitiesArray = jsonObject.getJsonArray("unavailabilities");
            final List<JudiciaryUnavailabilityRequest> unavailabilities = this.convertUnavailabilities(unavailabilitiesArray);
            request.setUnavailabilities(unavailabilities);
        }

        return request;
    }

    private List<JudiciaryAvailabilityRuleRepeatDay> convertRepeatDays(final JsonArray repeatDaysArray) {
        final List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();

        for (JsonValue jsonValue : repeatDaysArray) {
            if (jsonValue.getValueType() == javax.json.JsonValue.ValueType.STRING) {
                // Simple string format: "Monday"
                final String dayOfWeek = jsonValue.toString().replace("\"", "");
                repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.valueOf(dayOfWeek.toUpperCase()), null));
            } else if (jsonValue.getValueType() == javax.json.JsonValue.ValueType.OBJECT) {
                // Object format with optional index: {"day": "Tuesday", "index": 2}
                final JsonObject dayObject = (JsonObject) jsonValue;
                final String dayOfWeek = dayObject.getString("day");
                Integer index = null;
                if (dayObject.containsKey(INDEX) && !dayObject.isNull(INDEX)) {
                    index = dayObject.getInt(INDEX);
                }
                repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.valueOf(dayOfWeek.toUpperCase()), index));
            }
        }

        return repeatDays;
    }

    private List<JudiciaryUnavailabilityRequest> convertUnavailabilities(final JsonArray unavailabilitiesArray) {
        final List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();

        for (JsonValue jsonValue : unavailabilitiesArray) {
            if (jsonValue.getValueType() == javax.json.JsonValue.ValueType.OBJECT) {
                final JsonObject unavailabilityObject = (JsonObject) jsonValue;
                final JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();
                
                if (unavailabilityObject.containsKey("startDate") && !unavailabilityObject.isNull("startDate")) {
                    unavailability.setStartDate(LocalDate.parse(unavailabilityObject.getString("startDate"), DATE_FORMATTER));
                }
                
                if (unavailabilityObject.containsKey("endDate") && !unavailabilityObject.isNull("endDate")) {
                    unavailability.setEndDate(LocalDate.parse(unavailabilityObject.getString("endDate"), DATE_FORMATTER));
                }
                
                if (unavailabilityObject.containsKey("reason") && !unavailabilityObject.isNull("reason")) {
                    final String reasonString = unavailabilityObject.getString("reason");
                    unavailability.setReason(UnavailabilityReason.valueOf(reasonString));
                }
                
                unavailabilities.add(unavailability);
            }
        }

        return unavailabilities;
    }
}

