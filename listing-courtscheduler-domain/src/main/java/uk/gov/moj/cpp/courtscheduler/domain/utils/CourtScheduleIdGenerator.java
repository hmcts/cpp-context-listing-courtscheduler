package uk.gov.moj.cpp.courtscheduler.domain.utils;


import static java.util.UUID.nameUUIDFromBytes;

import java.time.LocalDate;

public class CourtScheduleIdGenerator {

    private CourtScheduleIdGenerator() {}

    public static String getCourtScheduleId(final String roomId,
                                            final LocalDate courtSessionDate,
                                            final String sessionStr,
                                            final String businessType) {

        if (null == roomId || null == courtSessionDate || null == sessionStr || null == businessType) {
            final String msg = String.format("All of roomId: %s, courtSessionDate: %s, session: %s and business type: %s are mandatory ", roomId, courtSessionDate, sessionStr, businessType);
            throw new IllegalArgumentException(msg);
        }
        final String courtSessionDt = courtSessionDate.toString();

        final String courtSessionId = String.format("%s/%s/%s/%s", roomId, courtSessionDt, sessionStr, businessType);
        return nameUUIDFromBytes(courtSessionId.getBytes()).toString();
    }
}