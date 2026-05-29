package uk.gov.moj.cpp.courtscheduler.api.validator;

import static jakarta.json.JsonValue.EMPTY_JSON_OBJECT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.CANNOT_BE_NULL;
import static uk.gov.moj.cpp.courtscheduler.api.ApiConstants.MANDATORY_SEARCH_CRITERIA;

import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import jakarta.json.JsonObject;

import org.junit.jupiter.api.Test;

class CourtScheduleApiValidatorTest {

    CourtScheduleApiValidator courtScheduleApiValidator = new CourtScheduleApiValidator();

    @Test
    void shouldValidateSuccessfully() {
        JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createRequestParam());
        assertEquals(EMPTY_JSON_OBJECT, response);
    }

    @Test
    void shouldValidateAndReturnError() {

        JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createInvalidRequestParam());

        assertEquals(MANDATORY_SEARCH_CRITERIA + RequestParameterConstant.START_DATE.getLabel() + CANNOT_BE_NULL, response.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenStartDateAfterEndDate() {
        CourtScheduleRequestParam params = new CourtScheduleRequestParam(
                "courtCentreId",
                "courtRoomId",
                "businessType",
                "2024-12-05",
                "2024-12-03",
                null,
                "10",
                "1");

        JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(params);

        assertEquals("Start date must be on or before end date", response.getString("errorMessage"));
    }

    @Test
    void shouldReturnSuccessWhenOptionalFieldsMissing() {

        JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createRequestWithOptionalFieldsOnly());

        assertEquals(EMPTY_JSON_OBJECT, response);
    }

    private CourtScheduleRequestParam createRequestParam() {
        String courtCentreId = "courtCentreId";
        String courtRoomId = "courtRoomId";
        String businessType = "businessType";
        String sessionStartDate = "2024-12-01";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, null, pageSize, pageNumber);
    }

    private CourtScheduleRequestParam createInvalidRequestParam() {
        String courtCentreId = "courtCentreId";
        String courtRoomId = "courtRoomId";
        String businessType = "businessType";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, null, sessionEndDate, null, pageSize, pageNumber);
    }

    private CourtScheduleRequestParam createRequestWithOptionalFieldsOnly() {
        String courtCentreId = "courtCentreId";
        String sessionStartDate = "2024-12-01";
        String sessionEndDate = "2024-12-03";
        String pageSize = "10";
        String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, null, null, sessionStartDate, sessionEndDate, null, pageSize, pageNumber);
    }


}