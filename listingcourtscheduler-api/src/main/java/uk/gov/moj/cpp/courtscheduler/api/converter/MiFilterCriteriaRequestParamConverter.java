package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import uk.gov.moj.cpp.courtscheduler.openapi.model.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;

import java.time.LocalDate;

import jakarta.json.JsonObject;

@Service
public class MiFilterCriteriaRequestParamConverter implements Converter<JsonObject, MiFilterCriteria> {
    @Override
    public MiFilterCriteria convert(final JsonObject jsonObject) {
        final String fromDate = jsonObject.getString(RequestParameterConstant.FROM_DATE.getLabel());
        final String toDate = jsonObject.getString(RequestParameterConstant.TO_DATE.getLabel());
        return new MiFilterCriteria()
                .fromDate(LocalDate.parse(fromDate))
                .toDate(LocalDate.parse(toDate));
    }
}
