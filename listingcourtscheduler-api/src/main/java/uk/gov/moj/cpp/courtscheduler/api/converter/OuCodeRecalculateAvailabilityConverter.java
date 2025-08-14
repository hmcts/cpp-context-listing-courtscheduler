package uk.gov.moj.cpp.courtscheduler.api.converter;

import static java.lang.String.format;

import uk.gov.moj.cpp.courtscheduler.domain.OuCodeRecalculateAvailabilityRequest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class OuCodeRecalculateAvailabilityConverter implements Converter<String, OuCodeRecalculateAvailabilityRequest> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public OuCodeRecalculateAvailabilityRequest convert(final String payload) {
        try {
            return mapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException iox) {
            throw new ConverterException(format("Error while converting list item %s to OuCodeRecalculateAvailabilityRequest", payload), iox);
        }
    }
}
