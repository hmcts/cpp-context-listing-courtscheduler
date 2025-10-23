package uk.gov.moj.cpp.courtscheduler.domain;

import org.apache.commons.lang3.tuple.Pair;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public record SlotProcessingContext(int sessionStartHour, int sessionEndHour, int sessionEndMinute,
                                    AtomicInteger nextMinutePart,
                                    List<Pair<Timestamp, Integer>> courtScheduleAllocatedPair,
                                    LocalDateTime sessionStartDateTime, LocalDateTime sessionEndDateTime,
                                    boolean slotBased, int nationalBreakTimeStartHour, String courtSession) {

}
