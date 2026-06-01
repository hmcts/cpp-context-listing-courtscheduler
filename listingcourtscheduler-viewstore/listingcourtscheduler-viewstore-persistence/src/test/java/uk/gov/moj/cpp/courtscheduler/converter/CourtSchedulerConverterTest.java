package uk.gov.moj.cpp.courtscheduler.converter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedListingEachBooked;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.List;

import io.github.benas.randombeans.api.EnhancedRandom;
import org.junit.jupiter.api.Test;

class CourtSchedulerConverterTest {

    @Test
    void shouldConvert() {
        CourtSchedule courtScheduleEnt = EnhancedRandom.random(CourtSchedule.class);
        courtScheduleEnt.setSupportAdSplit(null);

        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule converted = CourtSchedulerConverter.convert(courtScheduleEnt);

        assertEquals(converted.getListingProfileId(), courtScheduleEnt.getListingProfileId());
        assertEquals(converted.getOuCode(), courtScheduleEnt.getOuCode());
        assertEquals(converted.getCourtRoomNumber(), courtScheduleEnt.getCourtRoomNumber());
        assertEquals(converted.getOperationalUnit(), courtScheduleEnt.getOperationalUnit());
        assertEquals(converted.getCourtScheduleId(), courtScheduleEnt.getCourtScheduleId());
        assertEquals(converted.getAvailableDuration(), courtScheduleEnt.getAvailableDuration());
        assertEquals(converted.getMaxDuration(), courtScheduleEnt.getMaxDuration());
        assertEquals(converted.getAvailableSlots(), courtScheduleEnt.getAvailableSlots());
        assertEquals(converted.getMaxSlots(), courtScheduleEnt.getMaxSlots());
        assertEquals(converted.getBusinessType(), courtScheduleEnt.getBusinessType());
        assertEquals(converted.getCourtHouseId(), courtScheduleEnt.getCourtHouseId());
        assertEquals(converted.getCourtHouseName(), courtScheduleEnt.getCourtHouseName());
        assertEquals(converted.getPanel(), courtScheduleEnt.getPanel());
        assertEquals(converted.getCreatedOn(), courtScheduleEnt.getCreatedOn());
        assertEquals(converted.getUpdatedOn(), courtScheduleEnt.getUpdatedOn());
        assertEquals(converted.getSessionStartTime(), courtScheduleEnt.getSessionStartTime());
        assertEquals(converted.getSessionEndTime(), courtScheduleEnt.getSessionEndTime());
        assertFalse(converted.isAllDaySplit());

    }

    @Test
    void shouldConvertWithAllocatedListingBooked() {
        CourtSchedule courtScheduleEnt = EnhancedRandom.random(CourtSchedule.class);
        courtScheduleEnt.setSupportAdSplit(null);

        final List<AllocatedListingEachBooked> allocatedListingEachBookedList = List.of(new AllocatedListingEachBooked(
                courtScheduleEnt.getCourtScheduleId(), courtScheduleEnt.getAvailableDuration(), Calendar.getInstance().getTime()
        ));
        final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule converted = CourtSchedulerConverter.convert(courtScheduleEnt, allocatedListingEachBookedList);

        assertEquals(converted.getListingProfileId(), courtScheduleEnt.getListingProfileId());
        assertEquals(converted.getOuCode(), courtScheduleEnt.getOuCode());
        assertEquals(converted.getCourtRoomNumber(), courtScheduleEnt.getCourtRoomNumber());
        assertEquals(converted.getOperationalUnit(), courtScheduleEnt.getOperationalUnit());
        assertEquals(converted.getCourtScheduleId(), courtScheduleEnt.getCourtScheduleId());
        assertEquals(converted.getAvailableDuration(), courtScheduleEnt.getAvailableDuration());
        assertEquals(converted.getMaxDuration(), courtScheduleEnt.getMaxDuration());
        assertEquals(converted.getAvailableSlots(), courtScheduleEnt.getAvailableSlots());
        assertEquals(converted.getMaxSlots(), courtScheduleEnt.getMaxSlots());
        assertEquals(converted.getBusinessType(), courtScheduleEnt.getBusinessType());
        assertEquals(converted.getCourtHouseId(), courtScheduleEnt.getCourtHouseId());
        assertEquals(converted.getCourtHouseName(), courtScheduleEnt.getCourtHouseName());
        assertEquals(converted.getPanel(), courtScheduleEnt.getPanel());
        assertEquals(converted.getCreatedOn(), courtScheduleEnt.getCreatedOn());
        assertEquals(converted.getUpdatedOn(), courtScheduleEnt.getUpdatedOn());
        assertFalse(converted.isAllDaySplit());
    }

    @Test
    void shouldConvertToMi() {
        CourtSchedule courtScheduleEnt = EnhancedRandom.random(CourtSchedule.class);

        final uk.gov.moj.cpp.courtscheduler.domain.mi.CourtSchedule converted = CourtSchedulerConverter.convertToMi(courtScheduleEnt);

        assertEquals(converted.getListingProfileId(), courtScheduleEnt.getListingProfileId());
        assertEquals(converted.getOuCode(), courtScheduleEnt.getOuCode());
        assertEquals(converted.getCourtRoomNumber(), courtScheduleEnt.getCourtRoomNumber());
        assertEquals(converted.getOperationalUnit(), courtScheduleEnt.getOperationalUnit());
        assertEquals(converted.getCourtScheduleId(), courtScheduleEnt.getCourtScheduleId());
        assertEquals(converted.getAvailableDuration(), courtScheduleEnt.getAvailableDuration());
        assertEquals(converted.getMaxDuration(), courtScheduleEnt.getMaxDuration());
        assertEquals(converted.getAvailableSlots(), courtScheduleEnt.getAvailableSlots());
        assertEquals(converted.getMaxSlots(), courtScheduleEnt.getMaxSlots());
        assertEquals(converted.getBusinessType(), courtScheduleEnt.getBusinessType());
        assertEquals(converted.getCourtHouseId(), courtScheduleEnt.getCourtHouseId());
        assertEquals(converted.getCourtHouseName(), courtScheduleEnt.getCourtHouseName());
        assertEquals(converted.getPanel(), courtScheduleEnt.getPanel());
        final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'");
        assertEquals(converted.getCreatedOn(), simpleDateFormat.format(courtScheduleEnt.getCreatedOn()));
        assertEquals(converted.getUpdatedOn(), simpleDateFormat.format(courtScheduleEnt.getUpdatedOn()));
    }


}
