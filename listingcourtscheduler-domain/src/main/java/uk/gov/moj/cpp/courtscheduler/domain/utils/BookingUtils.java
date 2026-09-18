package uk.gov.moj.cpp.courtscheduler.domain.utils;

import java.util.concurrent.atomic.AtomicInteger;

public class BookingUtils {

    public static void updateTotalBooked(final int duration, final AtomicInteger totalBookedForMorning, final AtomicInteger totalBookedForAfternoon, final int defaultDuration) {
        if (defaultDuration <= duration) {
            final int overflownToAfternoon = duration - defaultDuration;
            totalBookedForAfternoon.set(overflownToAfternoon + totalBookedForAfternoon.get());
            totalBookedForMorning.set(totalBookedForMorning.get() + defaultDuration);
        } else {
            totalBookedForMorning.set(totalBookedForMorning.get() + duration);
        }
    }
}