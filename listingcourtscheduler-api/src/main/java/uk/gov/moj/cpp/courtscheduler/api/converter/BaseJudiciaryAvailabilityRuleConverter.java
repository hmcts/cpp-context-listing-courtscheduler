package uk.gov.moj.cpp.courtscheduler.api.converter;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.BaseJudiciaryAvailabilityRuleWithDetailsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Base converter class for judiciary availability rule requests.
 * Contains common conversion logic for Add and Update converters.
 */
public class BaseJudiciaryAvailabilityRuleConverter {

    private static final Logger LOG = LoggerFactory.getLogger(BaseJudiciaryAvailabilityRuleConverter.class);

    protected static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    protected static final String JUDICIARY_ID = "judiciaryId";
    protected static final String COURT_HOUSE_ID = "courtHouseId";
    protected static final String START_DATE = "startDate";
    protected static final String END_DATE = "endDate";
    protected static final String SESSION_TYPE = "sessionType";
    protected static final String REPEAT_DAYS = "repeatDays";
    protected static final String UNAVAILABILITIES = "unavailabilities";

    /**
     * Populates common base fields from JSON object to request object.
     */
    protected void populateBaseFields(final JsonObject jsonObject, final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        setStringField(jsonObject, JUDICIARY_ID, request::setJudiciaryId);
        setStringField(jsonObject, COURT_HOUSE_ID, request::setCourtHouseId);
        setDateField(jsonObject, START_DATE, request::setStartDate);
        setDateField(jsonObject, END_DATE, request::setEndDate);
    }

    /**
     * Populates detail fields (sessionType, repeatDays, unavailabilities) from JSON object.
     */
    protected void populateDetailFields(final JsonObject jsonObject, final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (hasField(jsonObject, SESSION_TYPE)) {
            request.setSessionType(SessionType.valueOf(jsonObject.getString(SESSION_TYPE)));
        }

        if (hasField(jsonObject, REPEAT_DAYS)) {
            final JsonArray repeatDaysArray = jsonObject.getJsonArray(REPEAT_DAYS);
            request.setRepeatDays(convertRepeatDays(repeatDaysArray));
        }

        if (hasField(jsonObject, UNAVAILABILITIES)) {
            final JsonArray unavailabilitiesArray = jsonObject.getJsonArray(UNAVAILABILITIES);
            request.setUnavailabilities(convertUnavailabilities(unavailabilitiesArray));
        }
    }

    /**
     * Converts JSON array of repeat days to list of AvailabilityDayOfWeek enum values.
     * Each item must be a string enum value: "Monday", "Tuesday", "Wednesday", "Thursday", "Friday".
     */
    protected List<AvailabilityDayOfWeek> convertRepeatDays(final JsonArray repeatDaysArray) {
        final List<AvailabilityDayOfWeek> repeatDays = new ArrayList<>();

        for (final JsonValue jsonValue : repeatDaysArray) {
            if (jsonValue.getValueType() == JsonValue.ValueType.STRING) {
                final String dayOfWeek = jsonValue.toString().replace("\"", "");
                final String titleCase = dayOfWeek.isEmpty()
                    ? dayOfWeek
                    : dayOfWeek.substring(0, 1).toUpperCase(Locale.ROOT) + dayOfWeek.substring(1).toLowerCase(Locale.ROOT);
                try {
                    repeatDays.add(AvailabilityDayOfWeek.fromWireValue(titleCase));
                } catch (IllegalArgumentException e) {
                    // Invalid day name - skip it, but log so a malformed request doesn't
                    // silently drop data without any trace (downstream validation only
                    // sees the resulting, already-filtered, repeatDays list).
                    LOG.warn("Ignoring unrecognised repeatDays value '{}': {}", dayOfWeek, e.getMessage());
                }
            }
        }

        return repeatDays;
    }

    /**
     * Converts JSON array of unavailabilities to list of JudiciaryUnavailabilityRequest.
     */
    @SuppressWarnings("PMD.AvoidInstantiatingObjectsInLoops")
    // A distinct JudiciaryUnavailabilityRequest is required per array element, since each
    // is collected into the returned list - it cannot be created once and reused.
    protected List<JudiciaryUnavailabilityRequest> convertUnavailabilities(final JsonArray unavailabilitiesArray) {
        final List<JudiciaryUnavailabilityRequest> unavailabilities = new ArrayList<>();

        for (final JsonValue jsonValue : unavailabilitiesArray) {
            if (jsonValue.getValueType() == JsonValue.ValueType.OBJECT) {
                final JsonObject unavailabilityObject = (JsonObject) jsonValue;
                final JudiciaryUnavailabilityRequest unavailability = new JudiciaryUnavailabilityRequest();

                setDateField(unavailabilityObject, START_DATE, unavailability::setStartDate);
                setDateField(unavailabilityObject, END_DATE, unavailability::setEndDate);

                if (hasField(unavailabilityObject, "reason")) {
                    final String reasonString = unavailabilityObject.getString("reason");
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
    protected boolean hasField(final JsonObject jsonObject, final String fieldName) {
        return jsonObject.containsKey(fieldName) && !jsonObject.isNull(fieldName);
    }

    /**
     * Helper method to set a string field if it exists and is not null.
     */
    protected void setStringField(final JsonObject jsonObject, final String fieldName, final java.util.function.Consumer<String> setter) {
        if (hasField(jsonObject, fieldName)) {
            setter.accept(jsonObject.getString(fieldName));
        }
    }

    /**
     * Helper method to set a date field if it exists and is not null.
     */
    protected void setDateField(final JsonObject jsonObject, final String fieldName, final java.util.function.Consumer<LocalDate> setter) {
        if (hasField(jsonObject, fieldName)) {
            setter.accept(LocalDate.parse(jsonObject.getString(fieldName), DATE_FORMATTER));
        }
    }
}
