package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

public record SlotAndScheduleInfo(List<String> existingNonMigratedSlotScheduleIds,
                                  List<String> confirmedSlotIdsToDelete,
                                  Collection<CourtSchedule> slotsToUpdate,
                                  Collection<CourtScheduleJudiciary> newCourtScheduleJudiciaries,
                                  Collection<CourtScheduleJudiciary> courtScheduleJudiciariesForMigratedExistingSlots,
                                  Map<String, List<CourtScheduleJudiciary>> relatedJudiciarySchedules,
                                  Map<String, CourtSchedule> newSlots,
                                  Map<String, Pair<String, String>> schedulesToUpdateMap) {
}
