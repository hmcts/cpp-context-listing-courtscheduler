package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static java.lang.String.format;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class ProvisionalSlotConverter implements Converter<String, ProvisionalBookingSlots> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public ProvisionalBookingSlots convert(final String payload) {
        try {
            return mapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException iox) {
            throw new ConverterException(format("Error while converting list item %s to List<ProvisionalSlot>", payload), iox);
        }
    }
}
