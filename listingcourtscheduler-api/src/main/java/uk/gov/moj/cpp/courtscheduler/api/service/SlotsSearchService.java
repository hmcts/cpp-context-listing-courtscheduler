package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Integer.parseInt;

import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonObject;

import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
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
        final   long startcourtschedulequery = System.nanoTime();
        final Pair<Integer, List<CourtSchedule>> courtSchedules = courtScheduleRepository.getCourtSchedules(hearingSlotRequestParam);
        final long endcourtschedulequery = System.nanoTime();
        LOGGER.info("PRF: Time taken for validation : {}", (endcourtschedulequery - startcourtschedulequery) / 1000000);

        final long startFiltering = System.nanoTime();
        final List<CourtSchedule> filteredCourtSchedules = new ArrayList<>();
        for (final CourtSchedule courtSchedule : courtSchedules.getValue()) {
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
                filteredCourtSchedules.add(courtSchedule);
            }
        }
        final long endFiltering = System.nanoTime();
        LOGGER.info("PRF: Time taken for filtering : {}", (endFiltering - startFiltering) / 1000000);
        return Pair.of(courtSchedules.getKey(), filteredCourtSchedules);
    }

    private long toPageCount(final long totalCount, final Integer pageSize) {
        return (long) Math.ceil((double) totalCount / (double) pageSize);
    }
}