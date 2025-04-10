package uk.gov.moj.cpp.courtscheduler.api.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotWrapper;

import java.io.IOException;

import static java.lang.String.format;

public class ListHearingSlotConverter implements Converter<String, HearingSlotWrapper> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public HearingSlotWrapper convert(final String payload) {

        try {
            final HearingSlotWrapper transformed = mapper.readValue(payload, new TypeReference<>() {
            });

            return transformed;

        } catch (IOException iox) {
            throw new ConverterException(format("Error while converting list item %s to hearings", payload), iox);
        }
    }
}
