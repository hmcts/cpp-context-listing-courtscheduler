package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Integer.parseInt;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class SlotsSearchService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SlotsSearchService.class.getName());

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public JsonObject search(HearingSlotRequestParam hearingSlotRequestParam) {

        final Pair<Integer, List<CourtSchedule>> courtSchedules = getCourtSchedules(hearingSlotRequestParam);
        final long resultsCount = courtSchedules.getKey();
        int pageSize = parseInt(hearingSlotRequestParam.pageSize());
        if (pageSize <= 0) {
            pageSize = 1;
        }
        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        return Json.createObjectBuilder()
                .add(RequestParameterConstant.RESULTS.getLabel(), resultsCount)
                .add(RequestParameterConstant.PAGE_COUNT.getLabel(), toPageCount(resultsCount, pageSize))
                .add(RequestParameterConstant.HEARING_SLOTS.getLabel(),
                        listToJsonArrayConverter.convert(courtSchedules.getValue()))
                .build();
    }

    public Pair<Integer, List<CourtSchedule>> getCourtSchedules(HearingSlotRequestParam hearingSlotRequestParam) {
        final   long startCourtScheduleQuery = System.nanoTime();
        final Pair<Integer, List<CourtSchedule>> courtSchedules = courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam);
        final long endCourtScheduleQuery = System.nanoTime();
        LOGGER.info("PRF: Time taken for validation : {}", (endCourtScheduleQuery - startCourtScheduleQuery) / 1000000);

        final long startFiltering = System.nanoTime();
        List<CourtSchedule> overbookingFilteredSchedules = overbookingFilter(courtSchedules.getValue(),
                hearingSlotRequestParam.showOverbookedSlots(), hearingSlotRequestParam.duration());
        final List<CourtSchedule> filteredCourtSchedules = new ArrayList<>();
        for (final CourtSchedule courtSchedule : overbookingFilteredSchedules) {
            final Optional<CourtSchedule> foundCourtSchedule = filteredCourtSchedules
                    .stream()
                    .filter(addedCourtSchedule -> addedCourtSchedule.getCourtScheduleId()
                            .equals(courtSchedule.getCourtScheduleId()))
                    .findAny();
            LOGGER.info("getCourtSchedules foundCourtSchedule: {}", foundCourtSchedule);
            if (foundCourtSchedule.isPresent()) {
                LOGGER.info("getCourtSchedules foundCourtSchedule.isPresent()");
                foundCourtSchedule.get().getJudiciaries().addAll(courtSchedule.getJudiciaries());
                LOGGER.info("getCourtSchedules foundCourtSchedule after adding Judiciaries : {}", foundCourtSchedule);
            } else {
                courtSchedule.setMinHearingTime(null);
                courtSchedule.setMaxHearingTime(null);
                filteredCourtSchedules.add(courtSchedule);
            }
        }
        final long endFiltering = System.nanoTime();
        LOGGER.info("PRF: Time taken for filtering : {}", (endFiltering - startFiltering) / 1000000);
        return Pair.of(courtSchedules.getKey(), filteredCourtSchedules);
    }

    private List<CourtSchedule> overbookingFilter(List<CourtSchedule> courtSchedules,
                                                  boolean showoverbookedSlots, String duration) {
        final List<CourtSchedule> overbookingFilteredSchedules = new ArrayList<>();
        Optional<Integer> durationOpt = parseDurationToOptional(duration);
        int durationInt = durationOpt.orElse(2); //changed to 2 from 0 default
        for (final CourtSchedule courtSchedule : courtSchedules) {

            if(courtSchedule.isOverbookingAllowed() || showoverbookedSlots) {
                overbookingFilteredSchedules.add(courtSchedule);
            } else {
                if (courtSchedule.isSlotBased() && courtSchedule.getTotalBooked() < courtSchedule.getMaxSlots()) {
                    overbookingFilteredSchedules.add(courtSchedule);
                } else if (courtSchedule.isAllDaySplit() && ((courtSchedule.getMaxDurationForMorning() + courtSchedule.getMaxDurationForAfternoon())
                            - (courtSchedule.getTotalBookedForMorning() + courtSchedule.getTotalBookedForAfternoon()) >= durationInt)
                        || (((courtSchedule.getMaxDuration() - courtSchedule.getTotalBooked()) >= durationInt)
                        && !courtSchedule.isSlotBased() && !courtSchedule.isAllDaySplit())) //extra checks to avoid zero duration sessions
                    overbookingFilteredSchedules.add(courtSchedule);
            }
        }
        return overbookingFilteredSchedules;
    }

    private static Optional<Integer> parseDurationToOptional(final String duration) {
        return duration == null || duration.isEmpty() ? Optional.empty() : Optional.of(Integer.valueOf(duration));
    }

    private long toPageCount(final long totalCount, final Integer pageSize) {
        return (long) Math.ceil((double) totalCount / (double) pageSize);
    }
}