package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static java.lang.String.format;

import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ListHearingSlotConverter implements Converter<String, RequestedSlots> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public RequestedSlots convert(final String payload) {

        try {
            return mapper.readValue(payload, new TypeReference<>() {
            });

        } catch (IOException iox) {
            throw new ConverterException(format("Error while converting list item %s to hearings", payload), iox);
        }
    }
}
