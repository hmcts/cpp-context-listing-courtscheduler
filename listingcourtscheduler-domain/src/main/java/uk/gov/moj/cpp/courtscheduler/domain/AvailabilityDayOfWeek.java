package uk.gov.moj.cpp.courtscheduler.domain;

/**
 * Days of the week a judiciary availability rule can repeat on.
 *
 * <p>The constant names follow the standard Java enum naming convention (upper snake case), but the
 * external wire/DB representation is the historical title-case form ("Monday", "Tuesday", ...) used
 * by the JSON API contract and already-persisted rows. {@link #getWireValue()} and
 * {@link #fromWireValue(String)} translate between the two so neither the API contract nor stored
 * data needs to change.</p>
 */
public enum AvailabilityDayOfWeek {
    MONDAY("Monday"),
    TUESDAY("Tuesday"),
    WEDNESDAY("Wednesday"),
    THURSDAY("Thursday"),
    FRIDAY("Friday");

    private final String wireValue;

    AvailabilityDayOfWeek(final String wireValue) {
        this.wireValue = wireValue;
    }

    public String getWireValue() {
        return wireValue;
    }

    public static AvailabilityDayOfWeek fromWireValue(final String value) {
        for (final AvailabilityDayOfWeek day : values()) {
            if (day.wireValue.equals(value)) {
                return day;
            }
        }
        throw new IllegalArgumentException("Unknown day of week: " + value);
    }
}
