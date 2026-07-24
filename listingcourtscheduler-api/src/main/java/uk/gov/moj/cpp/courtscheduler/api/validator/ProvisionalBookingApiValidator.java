package uk.gov.moj.cpp.courtscheduler.api.validator;

import org.springframework.stereotype.Service;

import static java.util.Optional.ofNullable;
import static jakarta.json.Json.createObjectBuilder;
import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.BOOKING_IDS;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_DATA_MISSING;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.PAYLOAD_CANNOT_EMPTY;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.PAYLOAD_NOT_CORRECT;

import uk.gov.moj.cpp.courtscheduler.api.converter.ConverterException;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import java.util.List;
import java.util.Optional;

import jakarta.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@Service
public class ProvisionalBookingApiValidator {
    private static final Logger LOGGER = LoggerFactory.getLogger(ProvisionalBookingApiValidator.class.getName());

    public JsonObject createProvisionalBookingValidation(ProvisionalBookingSlots provisionalBookingSlots) {

        if (provisionalBookingSlots != null
                && provisionalBookingSlots.getProvisionalSlots() != null
                && !provisionalBookingSlots.getProvisionalSlots().isEmpty()) {
            LOGGER.info("Processing provisionalBooking with payload : {}", provisionalBookingSlots);
            try {
                if (isPostPayloadValid(provisionalBookingSlots.getProvisionalSlots())) {
                    LOGGER.info("Payload Validation Passed");
                    return EMPTY_JSON_OBJECT;
                }
            } catch (ConverterException converterException) {
                LOGGER.error("provisionalSlot payload is incorrect : {}", converterException.getMessage());
                return getMessage(PAYLOAD_NOT_CORRECT);
            }
            LOGGER.info("Mandatory data missing on Provisional Booking Payload : {}", provisionalBookingSlots);
            return getMessage(MANDATORY_DATA_MISSING);
        } else {
            return getMessage(PAYLOAD_CANNOT_EMPTY);
        }
    }

    public JsonObject getProvisionalBookingValidation(final String bookingIds) {
        LOGGER.info("Validating BookingIds : {}", bookingIds);

        final Optional<String> optionalBookingIds = ofNullable(bookingIds);

        if (optionalBookingIds.isEmpty()) {
            return getMessage(BOOKING_IDS);
        }
        return EMPTY_JSON_OBJECT;
    }

    private boolean isPostPayloadValid(final List<ProvisionalSlot> slots) {
        return isNotEmpty(slots) && slots.stream()
                .noneMatch(slot -> slot.getCourtScheduleId() == null);
    }

    private JsonObject getMessage(final String value) {
        return buildErrorResponse(MANDATORY_SEARCH_CRITERIA + value + CANNOT_BE_NULL);
    }

    private JsonObject buildErrorResponse(String errorMessage) {
        return createObjectBuilder()
                .add(ERROR_MESSAGE, errorMessage)
                .build();
    }
}
