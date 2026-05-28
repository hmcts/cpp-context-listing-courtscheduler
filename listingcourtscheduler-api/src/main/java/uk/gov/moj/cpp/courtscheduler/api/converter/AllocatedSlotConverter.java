package uk.gov.moj.cpp.courtscheduler.api.converter;

import org.springframework.stereotype.Service;

import static java.lang.String.format;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.createDefaultHearingStartTime;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlots;

import java.io.IOException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;

@Service
public class AllocatedSlotConverter implements Converter<String, AllocatedSlots> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public AllocatedSlots convert(final String payload) {

        try {
            final AllocatedSlots transformed = mapper.readValue(payload, new TypeReference<>() {
            });

            transformed.getHearingSlots().forEach(allocatedSlot -> {
                if (StringUtils.isBlank(allocatedSlot.getHearingStartTime())) {
                    final String st = createDefaultHearingStartTime(allocatedSlot.getSession(), allocatedSlot.getSessionDate());
                    allocatedSlot.setHearingStartTime(st);
                }
            });

            return transformed;

        } catch (IOException iox) {
            throw new ConverterException(format("Error while converting list item %s to AllocatedSlots", payload), iox);
        }
    }
}
