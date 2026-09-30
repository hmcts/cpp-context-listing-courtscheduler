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
    private static final String DATE_2024_12_03 = "2024-12-03";
    private static final String COURT_CENTRE_ID = "courtCentreId";


    private CourtScheduleApiValidator courtScheduleApiValidator = new CourtScheduleApiValidator();

    @Test
    void shouldValidateSuccessfully() {
        final JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createRequestParam());
        assertEquals(EMPTY_JSON_OBJECT, response);
    }

    @Test
    void shouldValidateAndReturnError() {

        final JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createInvalidRequestParam());

        assertEquals(MANDATORY_SEARCH_CRITERIA + RequestParameterConstant.START_DATE.getLabel() + CANNOT_BE_NULL, response.getString("errorMessage"));
    }

    @Test
    void shouldReturnErrorWhenStartDateAfterEndDate() {
        final CourtScheduleRequestParam params = new CourtScheduleRequestParam(
                COURT_CENTRE_ID,
                "courtRoomId",
                "businessType",
                "2024-12-05",
                DATE_2024_12_03,
                null,
                "10",
                "1");

        final JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(params);

        assertEquals("Start date must be on or before end date", response.getString("errorMessage"));
    }

    @Test
    void shouldReturnSuccessWhenOptionalFieldsMissing() {

        final JsonObject response = courtScheduleApiValidator.getCourtSchedulesValidation(createRequestWithOptionalFieldsOnly());

        assertEquals(EMPTY_JSON_OBJECT, response);
    }

    private CourtScheduleRequestParam createRequestParam() {
        final String courtCentreId = COURT_CENTRE_ID;
        final String courtRoomId = "courtRoomId";
        final String businessType = "businessType";
        final String sessionStartDate = "2024-12-01";
        final String sessionEndDate = DATE_2024_12_03;
        final String pageSize = "10";
        final String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, sessionStartDate, sessionEndDate, null, pageSize, pageNumber);
    }

    private CourtScheduleRequestParam createInvalidRequestParam() {
        final String courtCentreId = COURT_CENTRE_ID;
        final String courtRoomId = "courtRoomId";
        final String businessType = "businessType";
        final String sessionEndDate = DATE_2024_12_03;
        final String pageSize = "10";
        final String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, courtRoomId, businessType, null, sessionEndDate, null, pageSize, pageNumber);
    }

    private CourtScheduleRequestParam createRequestWithOptionalFieldsOnly() {
        final String courtCentreId = COURT_CENTRE_ID;
        final String sessionStartDate = "2024-12-01";
        final String sessionEndDate = DATE_2024_12_03;
        final String pageSize = "10";
        final String pageNumber = "1";
        return new CourtScheduleRequestParam(courtCentreId, null, null, sessionStartDate, sessionEndDate, null, pageSize, pageNumber);
    }


}