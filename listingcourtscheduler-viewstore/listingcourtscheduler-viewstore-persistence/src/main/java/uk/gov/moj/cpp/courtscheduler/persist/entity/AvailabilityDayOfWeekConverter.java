package uk.gov.moj.cpp.courtscheduler.persist.entity;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link AvailabilityDayOfWeek} to/from the title-case column value ("Monday", "Tuesday", ...)
 * already stored in {@code day_of_week}, independently of the Java enum constant names.
 */
@Converter
public class AvailabilityDayOfWeekConverter implements AttributeConverter<AvailabilityDayOfWeek, String> {

    @Override
    public String convertToDatabaseColumn(final AvailabilityDayOfWeek attribute) {
        return attribute == null ? null : attribute.getWireValue();
    }

    @Override
    public AvailabilityDayOfWeek convertToEntityAttribute(final String dbData) {
        return dbData == null ? null : AvailabilityDayOfWeek.fromWireValue(dbData);
    }
}
