package uk.gov.moj.cpp.courtscheduler.api.converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.BaseJudiciaryAvailabilityRuleWithDetailsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

/**
 * Base converter class for judiciary availability rule requests.
 * Contains common conversion logic for Add and Update converters.
 */
public abstract class BaseJudiciaryAvailabilityRuleConverter {

    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    protected static final String JUDICIARY_ID = "judiciaryId";
    protected static final String COURT_HOUSE_ID = "courtHouseId";
    protected static final String START_DATE = "startDate";
    protected static final String END_DATE = "endDate";
    protected static final String RECURRING_TYPE = "recurringType";
    protected static final String SESSION_TYPE = "sessionType";
    protected static final String REPEAT_DAYS = "repeatDays";
    protected static final String UNAVAILABILITIES = "unavailabilities";
    protected static final String INDEX = "index";
    protected static final String DAY = "day";

    /**
     * Populates common base fields from JSON object to request object.
     */
    protected void populateBaseFields(JsonObject jsonObject, BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        setStringField(jsonObject, JUDICIARY_ID, request::setJudiciaryId);
        setStringField(jsonObject, COURT_HOUSE_ID, request::setCourtHouseId);
        setDateField(jsonObject, START_DATE, request::setStartDate);
        setDateField(jsonObject, END_DATE, request::setEndDate);
    }

    /**
     * Populates detail fields (recurringType, sessionType, repeatDays, unavailabilities) from JSON object.
     */
    protected void populateDetailFields(JsonObject jsonObject, BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (hasField(jsonObject, RECURRING_TYPE)) {
            request.setRecurringType(RecurringType.valueOf(jsonObject.getString(RECURRING_TYPE)));
        }

        if (hasField(jsonObject, SESSION_TYPE)) {
            request.setSessionType(SessionType.valueOf(jsonObject.getString(SESSION_TYPE)));
        }

        if (hasField(jsonObject, REPEAT_DAYS)) {
            JsonArray repeatDaysArray = jsonObject.getJsonArray(REPEAT_DAYS);
            request.setRepeatDays(convertRepeatDays(repeatDaysArray));
        }

        if (hasField(jsonObject, UNAVAILABILITIES)) {
            JsonArray unavailabilitiesArray = jsonObject.getJsonArray(UNAVAILABILITIES);
            request.setUnavailabilities(convertUnavailabilities(unavailabilitiesArray));
        }
    }

    /**
     * Converts JSON array of repeat days to list of JudiciaryAvailabilityRuleRepeatDay.
     * Supports both string format ("Monday") and object format ({"day": "Tuesday", "index": 2}).
     */
    protected List<JudiciaryAvailabilityRuleRepeatDay> convertRepeatDays(JsonArray repeatDaysArray) {
        List<JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();

        for (JsonValue jsonValue : repeatDaysArray) {
            if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                String dayOfWeek = jsonValue.toString().replace("\"", "");
                repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(
                        AvailabilityDayOfWeek.valueOf(dayOfWeek.toUpperCase()), null));
            } else if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject dayObject = (JsonObject) jsonValue;
                String dayOfWeek = dayObject.getString(DAY);
                Integer index = null;
                if (hasField(dayObject, INDEX)) {
                    index = dayObject.getInt(INDEX);
                }
                repeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(
                        AvailabilityDayOfWeek.valueOf(dayOfWeek.toUpperCase()), index));
            }
        }

        return repeatDays;
    }

    /**
     * Converts JSON array of unavailabilities to list of JudiciaryUnavailabilityRequest.
     */
    protected List<JudiciaryUnavailabilityRequest> convertUnavailabilities(JsonArray unavailabilitiesArray) {
        List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();

        for (JsonValue jsonValue : unavailabilitiesArray) {
            if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
                JsonObject unavailabilityObject = (JsonObject) jsonValue;
                JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();

                setDateField(unavailabilityObject, START_DATE, unavailability::setStartDate);
                setDateField(unavailabilityObject, END_DATE, unavailability::setEndDate);

                if (hasField(unavailabilityObject, "reason")) {
                    String reasonString = unavailabilityObject.getString("reason");
                    unavailability.setReason(UnavailabilityReason.valueOf(reasonString));
                }

                unavailabilities.add(unavailability);
            }
        }

        return unavailabilities;
    }

    /**
     * Helper method to check if a field exists and is not null.
     */
    protected boolean hasField(JsonObject jsonObject, String fieldName) {
        return jsonObject.containsKey(fieldName) && !jsonObject.isNull(fieldName);
    }

    /**
     * Helper method to set a string field if it exists and is not null.
     */
    protected void setStringField(JsonObject jsonObject, String fieldName, java.util.function.Consumer<String> setter) {
        if (hasField(jsonObject, fieldName)) {
            setter.accept(jsonObject.getString(fieldName));
        }
    }

    /**
     * Helper method to set a date field if it exists and is not null.
     */
    protected void setDateField(JsonObject jsonObject, String fieldName, java.util.function.Consumer<LocalDate> setter) {
        if (hasField(jsonObject, fieldName)) {
            setter.accept(LocalDate.parse(jsonObject.getString(fieldName), DATE_FORMATTER));
        }
    }
}
