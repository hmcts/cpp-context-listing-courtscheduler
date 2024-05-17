package uk.gov.moj.cpp.courtscheduler.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;

import java.util.List;

import static java.lang.String.format;

public class ProvisionalSlotConverter implements Converter<String, List<ProvisionalSlot>> {

    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public List<ProvisionalSlot> convert(final String payload) {
        try {
            return mapper.readValue(payload, new TypeReference<>() {
            });
        } catch (JsonProcessingException iox) {
            throw new ConverterException(format("Error while converting list item %s to List<ProvisionalSlot>", payload), iox);
        }
    }
}
