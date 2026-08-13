package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.Integer.parseInt;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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
    static final int FULL_DAY_DURATION_MINS = 360;
    private static final String CROWN = "CROWN";
    private static final String UNPAGINATED_PAGE_SIZE = "10000";
    // SPRDT-1276: a CROWN search for more than a full day can only ever be satisfied by whole,
    // duration-based days, so the caller's session filters are not a preference to honour — they
    // are noise. AM/PM sessions and slot-based sessions are structurally incapable of holding a
    // multi-day hearing, and letting them through is what put AM sessions in the 720-minute
    // response. Both values are forced, whatever the caller sent (including nothing at all,
    // which previously meant "no court_session predicate" rather than "AD only").
    static final String MULTIDAY_COURT_SESSION = "AD";
    static final Boolean MULTIDAY_IS_SLOT_BASED = Boolean.FALSE;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public JsonObject search(HearingSlotRequestParam hearingSlotRequestParam) {

        final Pair<Integer, List<CourtSchedule>> courtSchedules;

        if (isMultidayCrownSearch(hearingSlotRequestParam)) {
            courtSchedules = getMultidayCourtSchedules(hearingSlotRequestParam);
        } else {
            courtSchedules = getCourtSchedules(hearingSlotRequestParam);
        }

        final long resultsCount = courtSchedules.getKey();
        int pageSize = parseInt(hearingSlotRequestParam.pageSize());
        if (pageSize <= 0) {
            pageSize = 1;
        }
        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

        final List<CourtSchedule> hearingSlots = courtSchedules.getValue();
        CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(hearingSlots);

        return Json.createObjectBuilder()
                .add(RequestParameterConstant.RESULTS.getLabel(), resultsCount)
                .add(RequestParameterConstant.PAGE_COUNT.getLabel(), toPageCount(resultsCount, pageSize))
                .add(RequestParameterConstant.HEARING_SLOTS.getLabel(),
                        listToJsonArrayConverter.convert(hearingSlots))
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
        final List<CourtSchedule> filteredCourtSchedules = deduplicateSchedules(overbookingFilteredSchedules);

        final long endFiltering = System.nanoTime();
        LOGGER.info("PRF: Time taken for filtering : {}", (endFiltering - startFiltering) / 1000000);
        return Pair.of(courtSchedules.getKey(), filteredCourtSchedules);
    }

    boolean isMultidayCrownSearch(HearingSlotRequestParam param) {
        if (!CROWN.equalsIgnoreCase(param.jurisdiction())) {
            return false;
        }
        Optional<Integer> durationOpt = parseDurationToOptional(param.duration());
        return durationOpt.isPresent() && durationOpt.get() > FULL_DAY_DURATION_MINS;
    }

    Pair<Integer, List<CourtSchedule>> getMultidayCourtSchedules(HearingSlotRequestParam requestParam) {
        final int duration = parseInt(requestParam.duration());
        final int daysNeeded = duration / FULL_DAY_DURATION_MINS;

        LOGGER.info("Multiday CROWN search: duration={}, daysNeeded={}, courtSession forced {}->{}, isSlotBased forced {}->{}",
                duration, daysNeeded, requestParam.courtSession(), MULTIDAY_COURT_SESSION,
                requestParam.isSlotBased(), MULTIDAY_IS_SLOT_BASED);

        // Extend end date to account for weekends within the multiday window.
        // +1 extra day ensures sessions on the last look-ahead day are included: the DB query uses
        // session_start (TIMESTAMP) BETWEEN :sessionStart AND :sessionEnd where LocalDate binds as
        // midnight, so a session on extendedEndDate at 09:00 would be excluded without the extra day.
        final LocalDate originalEndDate = LocalDate.parse(requestParam.sessionEndDate());
        final int weekendBuffer = 2 * ((daysNeeded / 5) + 1);
        final String extendedEndDate = originalEndDate.plusDays(daysNeeded + weekendBuffer).toString();

        // Fetch all schedules in extended date range (unpaginated) to check consecutive availability.
        // courtSession/isSlotBased are overridden here rather than passed through — see the
        // MULTIDAY_* constants.
        final HearingSlotRequestParam unpaginatedRequest = new HearingSlotRequestParam(
                requestParam.panel(), requestParam.sessionStartDate(), extendedEndDate,
                requestParam.exactHearingStartDateTime(), requestParam.oucodeL2Code(), requestParam.ouCode(),
                UNPAGINATED_PAGE_SIZE, "1",
                requestParam.courtRoomId(), requestParam.courtRoomNumber(), requestParam.businessType(),
                MULTIDAY_COURT_SESSION, MULTIDAY_IS_SLOT_BASED, requestParam.hearingStartTime(),
                requestParam.showOverbookedSlots(),
                requestParam.duration(), requestParam.status(), requestParam.jurisdiction());

        // Fix #4 (perf analysis): use the slim discovery + re-hydrate path instead of pulling
        // every aggregated row in the extended window. The repository runs a thin SELECT to
        // identify rooms with daysNeeded consecutive business-day sessions, then re-hydrates
        // only those rows via getCourtSchedulesByIdList (which performs the expensive
        // allocated_listings aggregation). For oucodeL2Code-style requests this typically drops
        // the heavy query's input from thousands of rows to tens.
        final List<CourtSchedule> candidates = courtScheduleRepository
                .getMultidayHearingSlotCandidates(unpaginatedRequest, daysNeeded);

        // Apply overbooking filter with per-day duration (360) instead of full multiday duration
        final List<CourtSchedule> filtered = overbookingFilter(candidates,
                requestParam.showOverbookedSlots(), String.valueOf(FULL_DAY_DURATION_MINS));

        final List<CourtSchedule> dedupSchedules = deduplicateSchedules(filtered);

        // Apply multiday consecutive availability filter
        final List<CourtSchedule> multidayResults = filterForMultidayAvailability(dedupSchedules, daysNeeded,
                requestParam.showOverbookedSlots());

        // Remove results that start after the original end date — look-ahead days verify
        // consecutive availability but should not appear in the response
        multidayResults.removeIf(cs -> cs.getSessionDate().isAfter(originalEndDate));

        // Paginate in memory
        final int pageSize = Math.max(1, parseInt(requestParam.pageSize()));
        final int pageNumber = Math.max(1, parseInt(requestParam.pageNumber()));
        final int fromIndex = Math.min((pageNumber - 1) * pageSize, multidayResults.size());
        final int toIndex = Math.min(fromIndex + pageSize, multidayResults.size());

        final List<CourtSchedule> paginatedResults = new ArrayList<>(multidayResults.subList(fromIndex, toIndex));

        LOGGER.info("Multiday search: total valid start dates={}, returning page {} (pageSize={})",
                multidayResults.size(), pageNumber, pageSize);

        return Pair.of(multidayResults.size(), paginatedResults);
    }

    List<CourtSchedule> filterForMultidayAvailability(List<CourtSchedule> schedules, int daysNeeded,
                                                      boolean showOverbookedSlots) {
        // Group by courtRoomId + businessType + ouCode - consecutive days must share all three
        final Map<String, List<CourtSchedule>> byCourtRoom = schedules.stream()
                .filter(cs -> cs.getSessionDate() != null && cs.getCourtRoomId() != null)
                .collect(Collectors.groupingBy(cs -> buildGroupingKey(cs)));

        final List<CourtSchedule> validStartDates = new ArrayList<>();

        for (final List<CourtSchedule> roomSchedules : byCourtRoom.values()) {
            roomSchedules.sort(Comparator.comparing(CourtSchedule::getSessionDate));

            final Map<LocalDate, CourtSchedule> dateMap = new LinkedHashMap<>();
            for (final CourtSchedule cs : roomSchedules) {
                dateMap.merge(cs.getSessionDate(), cs, SlotsSearchService::preferNonOverbooking);
            }

            for (final CourtSchedule cs : roomSchedules) {
                if (dateMap.get(cs.getSessionDate()) != cs) {
                    continue;
                }
                if (isValidMultidayStart(cs.getSessionDate(), dateMap, daysNeeded, showOverbookedSlots)) {
                    validStartDates.add(cs);
                }
            }
        }

        validStartDates.sort(Comparator.comparing(CourtSchedule::getSessionDate)
                .thenComparing(CourtSchedule::getCourtHouseName, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CourtSchedule::getCourtRoomNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(CourtSchedule::getCourtRoomName, Comparator.nullsLast(Comparator.naturalOrder())));
        return validStartDates;
    }

    boolean isValidMultidayStart(LocalDate startDate, Map<LocalDate, CourtSchedule> dateMap, int daysNeeded,
                                  boolean showOverbookedSlots) {
        LocalDate currentDate = startDate;
        for (int day = 0; day < daysNeeded; day++) {
            if (day > 0) {
                currentDate = SessionAvailability.getNextBusinessDay(currentDate);
            }
            final CourtSchedule daySchedule = dateMap.get(currentDate);
            if (daySchedule == null) {
                return false;
            }
            // When showOverbookedSlots is true, include sessions regardless of capacity.
            if (!showOverbookedSlots
                    && !daySchedule.isOverbookingAllowed()
                    && SessionAvailability.getEffectiveAvailableDuration(daySchedule) < FULL_DAY_DURATION_MINS) {
                return false;
            }
        }
        return true;
    }

    private String buildGroupingKey(CourtSchedule cs) {
        return cs.getCourtRoomId() + "|" + cs.getBusinessType() + "|" + cs.getOuCode();
    }

    private static CourtSchedule preferNonOverbooking(CourtSchedule existing, CourtSchedule incoming) {
        return existing.isOverbookingAllowed() ? incoming : existing;
    }

    private List<CourtSchedule> deduplicateSchedules(List<CourtSchedule> schedules) {
        final List<CourtSchedule> result = new ArrayList<>();
        for (final CourtSchedule courtSchedule : schedules) {
            final Optional<CourtSchedule> foundCourtSchedule = result
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
                result.add(courtSchedule);
            }
        }
        return result;
    }

    private List<CourtSchedule> overbookingFilter(List<CourtSchedule> courtSchedules,
                                                  boolean showoverbookedSlots, String duration) {
        final List<CourtSchedule> overbookingFilteredSchedules = new ArrayList<>();
        Optional<Integer> durationOpt = parseDurationToOptional(duration);
        int durationInt = durationOpt.orElse(2); //changed to 2 from 0 default
        for (final CourtSchedule courtSchedule : courtSchedules) {
            if (courtSchedule.isOverbookingAllowed() || showoverbookedSlots || hasAvailableCapacity(courtSchedule, durationInt)) {
                overbookingFilteredSchedules.add(courtSchedule);
            }
        }
        return overbookingFilteredSchedules;
    }

    private boolean hasAvailableCapacity(CourtSchedule courtSchedule, int durationInt) {
        if (courtSchedule.isSlotBased()) {
            return courtSchedule.getTotalBooked() < courtSchedule.getMaxSlots();
        }
        return SessionAvailability.getEffectiveAvailableDuration(courtSchedule) >= durationInt;
    }

    private static Optional<Integer> parseDurationToOptional(final String duration) {
        return duration == null || duration.isEmpty() ? Optional.empty() : Optional.of(Integer.valueOf(duration));
    }

    private long toPageCount(final long totalCount, final Integer pageSize) {
        return (long) Math.ceil((double) totalCount / (double) pageSize);
    }
}