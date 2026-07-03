package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import jakarta.json.JsonObject;

import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;

@Service
public class FindJudiciaryAvailabilityRuleConverter implements Converter<JsonObject, FindJudiciaryAvailabilityRuleRequest> {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ISO_DATE;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int DEFAULT_PAGE_NUMBER = 1;
    private static final boolean DEFAULT_WITH_JUDICIARY = true;
    public static final String COURT_CENTRE_ID = "courtCentreId";
    public static final String JUDICIARY_ID = "judiciaryId";
    public static final String PAGE_SIZE = "pageSize";
    public static final String PAGE_NUMBER = "pageNumber";
    public static final String WITH_JUDICIARY = "withJudiciary";

    @Override
    public FindJudiciaryAvailabilityRuleRequest convert(final JsonObject jsonObject) {
        final FindJudiciaryAvailabilityRuleRequest request = new FindJudiciaryAvailabilityRuleRequest();

        request.setStartDate(LocalDate.parse(jsonObject.getString("startDate"), FindJudiciaryAvailabilityRuleConverter.DATE_FORMATTER));
        request.setEndDate(LocalDate.parse(jsonObject.getString("endDate"), FindJudiciaryAvailabilityRuleConverter.DATE_FORMATTER));

        if (jsonObject.containsKey(COURT_CENTRE_ID) && !jsonObject.isNull(COURT_CENTRE_ID)) {
            request.setCourtHouseId(jsonObject.getString(COURT_CENTRE_ID));
        }

        if (jsonObject.containsKey(JUDICIARY_ID) && !jsonObject.isNull(JUDICIARY_ID)) {
            request.setJudiciaryId(jsonObject.getString(JUDICIARY_ID));
        }

        // Handle pagination with defaults
        if (jsonObject.containsKey(PAGE_SIZE) && !jsonObject.isNull(PAGE_SIZE)) {
            request.setPageSize(jsonObject.getInt(PAGE_SIZE));
        } else {
            request.setPageSize(DEFAULT_PAGE_SIZE);
        }

        if (jsonObject.containsKey(PAGE_NUMBER) && !jsonObject.isNull(PAGE_NUMBER)) {
            request.setPageNumber(jsonObject.getInt(PAGE_NUMBER));
        } else {
            request.setPageNumber(DEFAULT_PAGE_NUMBER);
        }

        if (jsonObject.containsKey(WITH_JUDICIARY) && !jsonObject.isNull(WITH_JUDICIARY)) {
            request.setWithJudiciary(jsonObject.getBoolean(WITH_JUDICIARY));
        } else {
            request.setWithJudiciary(DEFAULT_WITH_JUDICIARY);
        }

        return request;
    }
}

