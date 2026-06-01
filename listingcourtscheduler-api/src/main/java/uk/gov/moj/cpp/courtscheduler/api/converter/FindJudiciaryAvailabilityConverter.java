package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;

@Service
public class FindJudiciaryAvailabilityConverter implements Converter<JsonObject, FindJudiciaryAvailabilityRequest> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String JUDICIARY_ID = "judiciaryId";

    @Override
    public FindJudiciaryAvailabilityRequest convert(final JsonObject jsonObject) {
        final FindJudiciaryAvailabilityRequest request = new FindJudiciaryAvailabilityRequest();

        request.setStartDate(LocalDate.parse(jsonObject.getString("startDate"), FindJudiciaryAvailabilityConverter.DATE_FORMATTER));
        request.setEndDate(LocalDate.parse(jsonObject.getString("endDate"), FindJudiciaryAvailabilityConverter.DATE_FORMATTER));

        if (jsonObject.containsKey(COURT_CENTRE_ID) && !jsonObject.isNull(COURT_CENTRE_ID)) {
            request.setCourtHouseId(jsonObject.getString(COURT_CENTRE_ID));
        }

        if (jsonObject.containsKey(JUDICIARY_ID) && !jsonObject.isNull(JUDICIARY_ID)) {
            request.setJudiciaryId(jsonObject.getString(JUDICIARY_ID));
        }

        return request;
    }
}

