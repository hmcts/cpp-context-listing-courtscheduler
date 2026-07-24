package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;
import uk.gov.moj.cpp.courtscheduler.domain.DateSessionType;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Stream;

import jakarta.inject.Inject;
import jakarta.json.JsonNumber;
import jakarta.json.JsonObject;
import jakarta.json.JsonValue;

@Service
public class SearchAvailableJudiciariesService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MIN_SEARCH_LENGTH = 2;
    private static final String LIMIT = "limit";

    @Inject
    private ReferenceDataService referenceDataService;

    @Inject
    private JudiciaryAvailabilityService judiciaryAvailabilityService;

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public List<Judiciary> search(final JsonObject payload) {
        final SearchInput input = toSearchInput(payload);
        validateScope(input);

        final List<Judiciary> judiciaries = referenceDataService.searchJudiciaries(
                input.search,
                input.judiciaryGroup != null ? input.judiciaryGroup : "",
                input.limit,
                true);

        if (input.ignoreAvailability) {
            return judiciaries;
        }

        final AvailabilityContext ctx = buildAvailabilityContext(input);
        return filterByAvailableIds(judiciaries, ctx);
    }

    private SearchInput toSearchInput(final JsonObject payload) {
        final String search = trimToNull(payload, "search");
        if (search == null || search.length() < MIN_SEARCH_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'search' is required and must be at least " + MIN_SEARCH_LENGTH + " characters.");
        }
        return new SearchInput(
                search,
                trimToNull(payload, "judiciaryGroup"),
                parseLimit(payload),
                payload.containsKey("ignoreAvailability") && payload.getBoolean("ignoreAvailability"),
                trimToNull(payload, "dates"),
                trimToNull(payload, "courtScheduleIds"),
                trimToNull(payload, "courtHouseId")
        );
    }

    private void validateScope(final SearchInput input) {
        if (input.ignoreAvailability) {
            return;
        }
        final boolean hasDates = input.datesCsv != null && !input.datesCsv.isEmpty();
        final boolean hasScheduleIds = input.courtScheduleIdsCsv != null && !input.courtScheduleIdsCsv.isEmpty();
        if (hasDates && hasScheduleIds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provide either 'dates' or 'courtScheduleIds', not both.");
        }
        if (!hasDates && !hasScheduleIds) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "When availability is applied, provide 'dates' or 'courtScheduleIds'.");
        }
    }

    private AvailabilityContext buildAvailabilityContext(final SearchInput input) {
        if (input.datesCsv != null && !input.datesCsv.isEmpty()) {
            if (input.courtHouseIdParam == null || input.courtHouseIdParam.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'courtHouseId' is required when using 'dates' with availability filtering.");
            }
            return buildContextFromDates(input.datesCsv, input.courtHouseIdParam);
        }
        return buildContextFromCourtScheduleIds(input.courtScheduleIdsCsv);
    }

    private List<Judiciary> filterByAvailableIds(final List<Judiciary> judiciaries, final AvailabilityContext ctx) {

        final List<String> candidateIds = judiciaries.stream()
                .map(Judiciary::getId)
                .filter(Objects::nonNull)
                .toList();

        final List<String> availableIds = judiciaryAvailabilityService.findAvailableJudiciaryIdsFromList(
                candidateIds,
                ctx.rangeStart,
                ctx.rangeEnd,
                ctx.courtHouseId,
                ctx.slots,
                ctx.matchSessionType);

        final Set<String> available = Set.copyOf(availableIds);
        return judiciaries.stream().filter(j -> j.getId() != null && available.contains(j.getId())).toList();
    }

    private static String trimToNull(final JsonObject payload, final String key) {
        if (!payload.containsKey(key) || payload.isNull(key)) {
            return null;
        }
        final String s = payload.getString(key, "").trim();
        return s.isEmpty() ? null : s;
    }

    private static int parseLimit(final JsonObject payload) {
        if (!payload.containsKey(LIMIT) || payload.isNull(LIMIT)) {
            return DEFAULT_LIMIT;
        }
        try {
            final JsonValue v = payload.get(LIMIT);
            final int parsed = v.getValueType() == JsonValue.ValueType.NUMBER
                    ? ((JsonNumber) v).intValue()
                    : Integer.parseInt(payload.getString(LIMIT));
            return parsed > 0 ? parsed : DEFAULT_LIMIT;
        } catch (final RuntimeException e) {
            return DEFAULT_LIMIT;
        }
    }

    private AvailabilityContext buildContextFromDates(final String datesCsv, final String courtHouseId) {
        final List<LocalDate> dates = parseDatesCsv(datesCsv);
        if (dates.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'dates' must contain at least one ISO date (yyyy-MM-dd).");
        }
        final LocalDate start = dates.stream().min(LocalDate::compareTo).orElseThrow();
        final LocalDate end = dates.stream().max(LocalDate::compareTo).orElseThrow();
        final List<DateSessionType> slots = dates.stream().map(d -> new DateSessionType(d, null)).toList();
        return new AvailabilityContext(start, end, courtHouseId, slots, false);
    }

    private AvailabilityContext buildContextFromCourtScheduleIds(final String courtScheduleIdsCsv) {
        final List<String> ids = Stream.of(courtScheduleIdsCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Parameter 'courtScheduleIds' must list at least one id.");
        }
        final List<String> uniqueOrder = new ArrayList<>(new LinkedHashSet<>(ids));
        final List<CourtSchedule> schedules = courtScheduleRepository.findByCourtScheduleIds(uniqueOrder);
        if (schedules.size() != uniqueOrder.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "One or more court schedule ids were not found.");
        }
        final Map<String, CourtSchedule> byId = schedules.stream().collect(toMap(CourtSchedule::getCourtScheduleId, s -> s));
        final Set<String> houseIds = schedules.stream().map(CourtSchedule::getCourtHouseId).collect(toSet());
        if (houseIds.size() != 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "All court schedules must belong to the same court house.");
        }
        final String courtHouseId = houseIds.iterator().next();

        final List<DateSessionType> slots = new ArrayList<>();
        for (final String id : uniqueOrder) {
            final CourtSchedule cs = byId.get(id);
            slots.add(new DateSessionType(cs.getSessionDate(), parseCourtSession(cs.getCourtSession())));
        }

        final LocalDate rangeStart = slots.stream().map(DateSessionType::date).min(LocalDate::compareTo).orElseThrow();
        final LocalDate rangeEnd = slots.stream().map(DateSessionType::date).max(LocalDate::compareTo).orElseThrow();
        return new AvailabilityContext(rangeStart, rangeEnd, courtHouseId, slots, true);
    }

    private static List<LocalDate> parseDatesCsv(final String datesCsv) {
        return Arrays.stream(datesCsv.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(LocalDate::parse)
                .toList();
    }

    private static SessionType parseCourtSession(final String courtSession) {
        try {
            return SessionType.valueOf(courtSession.trim().toUpperCase());
        } catch (final IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid court session type: " + courtSession);
        }
    }

    private static final class AvailabilityContext {
        private final LocalDate rangeStart;
        private final LocalDate rangeEnd;
        private final String courtHouseId;
        private final List<DateSessionType> slots;
        private final boolean matchSessionType;

        private AvailabilityContext(
                final LocalDate rangeStart,
                final LocalDate rangeEnd,
                final String courtHouseId,
                final List<DateSessionType> slots,
                final boolean matchSessionType) {
            this.rangeStart = rangeStart;
            this.rangeEnd = rangeEnd;
            this.courtHouseId = courtHouseId;
            this.slots = slots;
            this.matchSessionType = matchSessionType;
        }
    }

    private static final class SearchInput {
        private final String search;
        private final String judiciaryGroup;
        private final int limit;
        private final boolean ignoreAvailability;
        private final String datesCsv;
        private final String courtScheduleIdsCsv;
        private final String courtHouseIdParam;

        private SearchInput(
                final String search,
                final String judiciaryGroup,
                final int limit,
                final boolean ignoreAvailability,
                final String datesCsv,
                final String courtScheduleIdsCsv,
                final String courtHouseIdParam) {
            this.search = search;
            this.judiciaryGroup = judiciaryGroup;
            this.limit = limit;
            this.ignoreAvailability = ignoreAvailability;
            this.datesCsv = datesCsv;
            this.courtScheduleIdsCsv = courtScheduleIdsCsv;
            this.courtHouseIdParam = courtHouseIdParam;
        }
    }
}
