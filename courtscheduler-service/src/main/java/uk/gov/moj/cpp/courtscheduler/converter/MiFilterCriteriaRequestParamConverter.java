package uk.gov.moj.cpp.courtscheduler.converter;

import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import javax.json.JsonObject;

public class MiFilterCriteriaRequestParamConverter implements Converter<JsonObject, MiFilterCriteria> {
    @Override
    public MiFilterCriteria convert(final JsonObject jsonObject) {
        final String fromDate = jsonObject.getString(RequestParameterConstant.FROM_DATE.getLabel());
        final String toDate = jsonObject.getString(RequestParameterConstant.TO_DATE.getLabel());
        return new MiFilterCriteria(fromDate, toDate);
    }
}
