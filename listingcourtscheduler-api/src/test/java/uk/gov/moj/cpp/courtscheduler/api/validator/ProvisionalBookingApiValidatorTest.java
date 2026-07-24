package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.PAYLOAD_CANNOT_EMPTY;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalBookingApiValidatorTest {

    @InjectMocks
    ProvisionalBookingApiValidator provisionalBookingApiValidator;

    @Test
    void shouldValidateSuccessfully() {
        ProvisionalBookingSlots provisionalBookingSlots = new ProvisionalBookingSlots();
        List<ProvisionalSlot> provisionalSlotList = new ArrayList<>();
        ProvisionalSlot provisionalSlot = new ProvisionalSlot(UUID.randomUUID().toString());
        provisionalSlotList.add(provisionalSlot);
        provisionalBookingSlots.setProvisionalSlots(provisionalSlotList);

        JsonObject response = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        assertEquals(EMPTY_JSON_OBJECT, response);
    }

    @Test
    void shouldValidateAndReturnError() {
        ProvisionalBookingSlots provisionalBookingSlots = new ProvisionalBookingSlots();
        List<ProvisionalSlot> provisionalSlotList = new ArrayList<>();
        provisionalBookingSlots.setProvisionalSlots(provisionalSlotList);

        JsonObject response = provisionalBookingApiValidator.createProvisionalBookingValidation(provisionalBookingSlots);

        assertEquals(MANDATORY_SEARCH_CRITERIA + PAYLOAD_CANNOT_EMPTY + CANNOT_BE_NULL, response.getString("errorMessage"));
    }
}