package uk.gov.moj.cpp.courtscheduler.api.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlots;

import java.io.IOException;
import java.util.List;

import static java.lang.String.format;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.createDefaultHearingStartTime;

public class ListHearingSlotConverter implements Converter<String, HearingSlots> {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public HearingSlots convert(final String payload) {

        try {
            final HearingSlots transformed = mapper.readValue(payload, new TypeReference<>() {
            });

            List<Hearing> hearings = transformed.getHearings();

            if (!hearings.isEmpty()) {
                //for each hearing get the courtschedules and check for session start time
                for(Hearing hearing: hearings) {
                    List<CourtSchedule> courtScheduleList = hearing.getCourtSchedules();

                    for (CourtSchedule cs : courtScheduleList) {
                        //check session start time for null and do the necessary business rule

                    }

                }

            }
//
//            transformed.getHearings().forEach(hearing -> {
//                if (StringUtils.isBlank(hearing..getHearingStartTime())) {
//                    final String st = createDefaultHearingStartTime(allocatedSlot.getSession(), allocatedSlot.getSessionDate());
//                    allocatedSlot.setHearingStartTime(st);
//                }
//            });

            return transformed;

        } catch (IOException iox) {
            throw new ConverterException(format("Error while converting list item %s to AllocatedSlots", payload), iox);
        }
    }
}
