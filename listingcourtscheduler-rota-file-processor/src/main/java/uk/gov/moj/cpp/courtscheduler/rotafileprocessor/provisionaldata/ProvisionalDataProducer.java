package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.util.Collections.sort;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;

import uk.gov.moj.cpp.courtscheduler.common.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
public class ProvisionalDataProducer {

    @Inject
    private SessionsService sessionsService;

    @Inject
    private CourtScheduleToForecastCourtScheduleConverter courtScheduleToForecastCourtScheduleConverter;


    public List<CourtSchedule> produceProvisionalData(final LocalDate startDate, final LocalDate endDate, final int cyclesToPopulate, final List<String> ouCodes, final ProvisionalSessionDateProvider provisionalSessionDateProvider) {
        final List<CourtSchedule> courtSchedules = sessionsService.getExtractedCourtSchedulesForGhostRota(ouCodes, startDate, endDate);
        final List<CourtSchedule> provisionalCourtList = new ArrayList();

        for (int cycleNo = 0; cycleNo < cyclesToPopulate; cycleNo++) {
            for (final CourtSchedule courtSchedule : courtSchedules) {
                final LocalDate newSessionDate = provisionalSessionDateProvider.provisionalDate(cycleNo, courtSchedule.getSessionDate());
                if (nonNull(newSessionDate)) {
                    final String provisionalCourtScheduleId = randomUUID().toString();
                    final CourtSchedule provisionalCourtSchedule = courtScheduleToForecastCourtScheduleConverter
                            .convertToProvisionalCourtSchedule(courtSchedule, newSessionDate, provisionalCourtScheduleId);
                    provisionalCourtList.add(provisionalCourtSchedule);
                }
            }
        }

        sort(provisionalCourtList, comparator());
        return provisionalCourtList;
    }

    @SuppressWarnings("squid:S1604")
    private Comparator comparator() {
        return (Comparator<CourtSchedule>) (o1, o2) -> {
            if (o1.getSessionDate().isAfter(o2.getSessionDate())) {
                return 1;
            } else if (o1.getSessionDate().isEqual(o2.getSessionDate())) {
                return 0;
            }
            return -1;
        };
    }
}
