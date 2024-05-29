package uk.gov.moj.cpp.courtscheduler.api.validator;

import static javax.json.Json.createObjectBuilder;
import static javax.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.ERROR_MESSAGE;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_DATA_MISSING;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.PAYLOAD_CANNOT_EMPTY;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.PAYLOAD_NOT_CORRECT;

import uk.gov.moj.cpp.courtscheduler.converter.ConverterException;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import java.util.List;

import javax.json.JsonObject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


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

    private boolean isPostPayloadValid(final List<ProvisionalSlot> slots) {
        return isNotEmpty(slots) && slots.stream()
                .noneMatch(slot -> isBlank(slot.getCourtScheduleId()));
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
