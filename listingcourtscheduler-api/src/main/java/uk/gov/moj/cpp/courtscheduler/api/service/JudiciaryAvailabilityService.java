package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerAddJudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerDeleteJudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.domain.DateSessionType;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerUpdateJudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerFindJudiciaryAvailability;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerFindJudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtschedulerGetJudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryUnavailability;
import uk.gov.moj.cpp.courtscheduler.domain.UnavailabilityReason;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;

import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.ADDING_UNAVAILABILITY_WOULD_AFFECT_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.CANNOT_DELETE_ITINERARY_IN_USE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.CHANGING_END_DATE_FROM_TO_WOULD_AFFECT;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.DATE_RANGE_MUST_BE_3_YEARS_OR_LESS;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.END_DATE_MUST_BE_IN_FUTURE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.JUDICIARY_ALREADY_ASSIGNED_DURING_DATES;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.NEW_END_DATE_MUST_BE_IN_FUTURE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.NEW_START_DATE_MUST_BE_IN_FUTURE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.RULE_ID_REQUIRED;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.RULE_ID_REQUIRED_FOR_UPDATE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.RULE_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.START_DATE_MUST_BE_IN_FUTURE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.UNAVAILABILITY_DATES_CANNOT_OVERLAP;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.UNAVAILABILITY_END_DATE_MUST_BE_BETWEEN;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.UNAVAILABILITY_START_DATE_MUST_BE_BETWEEN;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class JudiciaryAvailabilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityService.class.getName());
    public static final String JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND = "Judiciary availability rule with id {} not found";

    @Inject
    private JudiciaryAvailabilityRuleRepository repository;
    @Inject
    private ReferenceDataService referenceDataService;
    @Inject
    private EntityManager entityManager;
    @Inject
    private uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    public void addJudiciaryAvailabilityRule(final CourtschedulerAddJudiciaryAvailabilityRule request) {
        LOGGER.info("Adding judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = new JudiciaryAvailabilityRule();
        entity.setId(randomUUID().toString());

        populateEntityFields(entity, request.getJudiciaryId(), request.getCourtHouseId(), request.getStartDate(), request.getEndDate(), request.getSessionType());
        entity.setRepeatDays(convertRepeatDaysToEntity(request.getRepeatDays()));
        entity.setUnavailabilities(convertUnavailabilitiesToEntity(request.getUnavailabilities(), entity));

        repository.save(entity);
        LOGGER.info("Saved judiciary availability rule with id: {} and {} unavailabilities",
                entity.getId(), entity.getUnavailabilities().size());
    }

    public void updateJudiciaryAvailabilityRule(final CourtschedulerUpdateJudiciaryAvailabilityRule request) {
        LOGGER.info("Updating judiciary availability rule: {}", request);

        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            throw new IllegalArgumentException("Rule ID is required for update");
        }

        final JudiciaryAvailabilityRule entity = repository.findById(request.getRuleId()).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        populateEntityFields(entity, request.getJudiciaryId(), request.getCourtHouseId(), request.getStartDate(), request.getEndDate(), request.getSessionType());

        // Update repeat days (required field - always initialized)
        entity.getRepeatDays().clear();
        entity.getRepeatDays().addAll(convertRepeatDaysToEntity(request.getRepeatDays()));

        // Update unavailabilities (optional field - clear existing to handle orphan removal properly)
        if (entity.getUnavailabilities() == null) {
            entity.setUnavailabilities(new ArrayList<>());
        } else {
            entity.getUnavailabilities().clear();
        }
        entity.getUnavailabilities().addAll(convertUnavailabilitiesToEntity(request.getUnavailabilities(), entity));

        repository.save(entity);
        LOGGER.info("Updated judiciary availability rule with id: {} and {} unavailabilities",
                entity.getId(), entity.getUnavailabilities().size());
    }

    public void deleteJudiciaryAvailabilityRule(final CourtschedulerDeleteJudiciaryAvailabilityRule request) {
        LOGGER.info("Deleting judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = repository.findById(request.getRuleId()).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        repository.remove(entity);
        LOGGER.info("Deleted judiciary availability rule with id: {}", request.getRuleId());
    }

    public CourtschedulerFindJudiciaryAvailability findJudiciaryAvailability(final LocalDate startDate, final LocalDate endDate,
                                                                             final String courtHouseId, final String judiciaryId) {
        LOGGER.info("Finding judiciary availability for: startDate={}, endDate={}, courtHouseId={}, judiciaryId={}",
                startDate, endDate, courtHouseId, judiciaryId);

        // Find all rules that overlap with the query date range
        final List<JudiciaryAvailabilityRule> rules = repository.findRulesByDateRange(
                startDate,
                endDate,
                courtHouseId,
                judiciaryId
        );

        LOGGER.info("Found {} rules overlapping with date range", rules.size());

        // Group rules by judiciaryId
        final Map<String, List<JudiciaryAvailabilityRule>> rulesByJudiciary = rules.stream()
                .collect(Collectors.groupingBy(JudiciaryAvailabilityRule::getJudiciaryId));

        // Compile rules for each judiciary
        final List<String> availableJudiciaries = new ArrayList<>();
        for (Map.Entry<String, List<JudiciaryAvailabilityRule>> entry : rulesByJudiciary.entrySet()) {
            final String eachJudiciaryId = entry.getKey();
            final List<JudiciaryAvailabilityRule> judiciaryRules = entry.getValue();

            final Set<LocalDate> unavailabilityDates = new HashSet<>();
            // Compile available days for this judiciary
            final Set<String> compiledAvailableDays = compileAvailableDays(judiciaryRules, startDate, endDate, unavailabilityDates);

            // Check if any date in the query range matches the compiled days
            if (hasMatchingDates(startDate, endDate, compiledAvailableDays, unavailabilityDates)) {
                availableJudiciaries.add(eachJudiciaryId);
            }
        }

        LOGGER.info("Found {} available judiciaries", availableJudiciaries.size());
        return new CourtschedulerFindJudiciaryAvailability().availableJudiciaries(availableJudiciaries);
    }

    /**
     * Whether a stored rule session type covers a required listing session. AD covers AM, PM, and AD.
     */
    public static boolean coversSessionType(final SessionType ruleSession, final SessionType requiredSession) {
        Objects.requireNonNull(requiredSession, "requiredSession");
        final SessionType effective = ruleSession != null ? ruleSession : SessionType.AD;
        if (effective == SessionType.AD) {
            return true;
        }
        return effective == requiredSession;
    }

    /**
     * From refdata search candidates, returns IDs that are available for every required slot.
     * Judiciaries with no overlapping rules in range are treated as available.
     */
    public List<String> findAvailableJudiciaryIdsFromList(
            final List<String> candidateJudiciaryIds,
            final LocalDate rangeStart,
            final LocalDate rangeEnd,
            final String courtHouseId,
            final List<DateSessionType> requiredSlots,
            final boolean matchSessionType) {

        if (candidateJudiciaryIds == null || candidateJudiciaryIds.isEmpty()) {
            return List.of();
        }

        final List<JudiciaryAvailabilityRule> rules = repository.findRulesByDateRangeAndJudiciaryIds(
                rangeStart,
                rangeEnd,
                courtHouseId,
                candidateJudiciaryIds);

        final Map<String, List<JudiciaryAvailabilityRule>> byJudiciary = rules.stream()
                .collect(Collectors.groupingBy(JudiciaryAvailabilityRule::getJudiciaryId));

        final List<String> available = new ArrayList<>();
        for (final String judiciaryId : candidateJudiciaryIds) {
            final List<JudiciaryAvailabilityRule> mine = byJudiciary.getOrDefault(judiciaryId, List.of());
            if (mine.isEmpty() || isAvailableForAllSchedules(mine, requiredSlots, matchSessionType)) {
                available.add(judiciaryId);
            }
        }
        return available;
    }

    private boolean isAvailableForAllSchedules(
            final List<JudiciaryAvailabilityRule> rulesForOneJudiciary,
            final List<DateSessionType> requiredSlots,
            final boolean matchSessionType) {
        for (final DateSessionType slot : requiredSlots) {
            if (!judiciaryAvailableForSlot(rulesForOneJudiciary, slot, matchSessionType)) {
                return false;
            }
        }
        return true;
    }

    private boolean judiciaryAvailableForSlot(
            final List<JudiciaryAvailabilityRule> judiciaryRules,
            final DateSessionType slot,
            final boolean matchSessionType) {

        final LocalDate d = slot.date();
        final List<JudiciaryAvailabilityRule> rulesOnDate = judiciaryRules.stream()
                .filter(r -> !r.getFromDate().isAfter(d) && !r.getToDate().isBefore(d))
                .toList();

        if (rulesOnDate.isEmpty()) {
            return true;
        }

        final List<JudiciaryAvailabilityRule> applicable;
        if (matchSessionType && slot.sessionType() != null) {
            applicable = rulesOnDate.stream()
                    .filter(r -> coversSessionType(r.getSessionType(), slot.sessionType()))
                    .toList();
            if (applicable.isEmpty()) {
                return false;
            }
        } else {
            applicable = rulesOnDate;
        }

        final Set<LocalDate> unavailabilityDates = new HashSet<>();
        final Set<String> compiled = compileAvailableDays(applicable, d, d, unavailabilityDates);
        return hasMatchingDates(d, d, compiled, unavailabilityDates);
    }

    public CourtschedulerFindJudiciaryAvailabilityRule findJudiciaryAvailabilityRules(final LocalDate startDate, final LocalDate endDate,
                                                                                       final String courtHouseId, final String judiciaryId,
                                                                                       final Integer pageSizeParam, final Integer pageNumberParam,
                                                                                       final Boolean withJudiciaryParam) {
        LOGGER.info("Finding judiciary availability rules for: startDate={}, endDate={}, courtHouseId={}, judiciaryId={}",
                startDate, endDate, courtHouseId, judiciaryId);

        // Get default pagination values if not provided
        final int pageSize = pageSizeParam != null ? pageSizeParam : 20;
        final int pageNumber = pageNumberParam != null ? pageNumberParam : 1;
        final boolean withJudiciary = Boolean.TRUE.equals(withJudiciaryParam);

        // Find rules with pagination
        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = repository.findRulesByDateRangeWithPagination(
                startDate,
                endDate,
                courtHouseId,
                judiciaryId,
                pageSize,
                pageNumber
        );

        final int totalCount = result.getKey();
        final List<JudiciaryAvailabilityRule> rules = result.getValue();

        LOGGER.info("Found {} rules (total: {}) for page {} with page size {}", rules.size(), totalCount, pageNumber, pageSize);

        // Convert entities to response objects
        final List<JudiciaryAvailabilityRuleResponse> ruleResponses = rules.stream()
                .map(this::convertToResponse)
                .toList();

        final CourtschedulerFindJudiciaryAvailabilityRule response = new CourtschedulerFindJudiciaryAvailabilityRule()
                .rules(ruleResponses).totalCount(totalCount).pageNumber(pageNumber).pageSize(pageSize);

        // Always initialize judiciaries list (empty if not requested)
        final List<uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary> judiciaries = new ArrayList<>();

        // Fetch judiciaries if requested
        if (withJudiciary) {
            final List<String> judiciaryIdList = extractUniqueJudiciaryIds(rules);
            if (!judiciaryIdList.isEmpty()) {
                final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesWithSpecialismByIds(judiciaryIdList);
                judiciaries.addAll(fetchedJudiciaries);
                LOGGER.info("Fetched {} judiciaries for {} unique IDs", fetchedJudiciaries.size(), judiciaryIdList.size());
            }
        }

        response.setJudiciaries(judiciaries);

        return response;
    }

    public CourtschedulerGetJudiciaryAvailabilityRule getJudiciaryAvailabilityRule(final String ruleId, final Boolean withJudiciaryParam) {
        LOGGER.info("Getting judiciary availability rule for ruleId: {}", ruleId);

        if (ruleId == null || ruleId.isEmpty()) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        final JudiciaryAvailabilityRule entity = repository.findById(ruleId).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, ruleId);
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        final JudiciaryAvailabilityRuleResponse ruleResponse = convertToResponse(entity);

        // Fetch judiciary if requested
        final boolean withJudiciary = Boolean.TRUE.equals(withJudiciaryParam);
        uk.gov.moj.cpp.courtscheduler.openapi.model.Judiciary judiciary = null;
        if (withJudiciary && entity.getJudiciaryId() != null) {
            final List<String> judiciaryIdList = List.of(entity.getJudiciaryId());
            final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesWithSpecialismByIds(judiciaryIdList);
            if (!fetchedJudiciaries.isEmpty()) {
                judiciary = fetchedJudiciaries.get(0);
                LOGGER.info("Fetched judiciary for ruleId {}", ruleId);
            }
        }

        return new CourtschedulerGetJudiciaryAvailabilityRule().rule(ruleResponse).judiciary(judiciary);
    }


    private JudiciaryAvailabilityRuleResponse convertToResponse(final JudiciaryAvailabilityRule entity) {
        final JudiciaryAvailabilityRuleResponse response = new JudiciaryAvailabilityRuleResponse();
        response.setId(entity.getId());
        response.setJudiciaryId(entity.getJudiciaryId());
        response.setCourtHouseId(entity.getCourtHouseId());
        response.setStartDate(entity.getFromDate());
        response.setEndDate(entity.getToDate());
        response.setSessionType(entity.getSessionType() == null ? null : entity.getSessionType().name());

        // Convert entity repeat days (enum) to wire repeat days (String)
        if (entity.getRepeatDays() != null && !entity.getRepeatDays().isEmpty()) {
            final List<String> repeatDays = new ArrayList<>();
            for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay entityDay : entity.getRepeatDays()) {
                repeatDays.add(entityDay.getDayOfWeek().name());
            }
            response.setRepeatDays(repeatDays);
        }

        // Convert entity unavailabilities to wire unavailabilities
        if (entity.getUnavailabilities() != null && !entity.getUnavailabilities().isEmpty()) {
            final List<JudiciaryUnavailability> unavailabilities = new ArrayList<>();
            for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability entityUnavailability : entity.getUnavailabilities()) {
                unavailabilities.add(new JudiciaryUnavailability()
                        .startDate(entityUnavailability.getFromDate())
                        .endDate(entityUnavailability.getToDate())
                        .reason(entityUnavailability.getReason() == null ? null : entityUnavailability.getReason().name()));
            }
            response.setUnavailabilities(unavailabilities);
        }

        return response;
    }

    /**
     * Compile available days by:
     * 1. Starting with an empty set
     * 2. Adding days from "Available" rules
     * 3. Removing days from "Unavailable" rules
     * 4. Eliminating duplications
     */
    private Set<String> compileAvailableDays(
            final List<JudiciaryAvailabilityRule> rules,
            final LocalDate queryStartDate,
            final LocalDate queryEndDate, final Set<LocalDate> unavailabilityDays) {

        final Set<String> availableDays = new HashSet<>();

        for (JudiciaryAvailabilityRule rule : rules) {
            // Always add days from the rule (rule defines availability)
            final Set<String> ruleDays = extractDaysFromRule(rule, queryStartDate, queryEndDate);
            availableDays.addAll(ruleDays);

            // Remove days that are marked as unavailable
            if (rule.getUnavailabilities() != null && !rule.getUnavailabilities().isEmpty()) {
                final Set<LocalDate> unavailableDays = extractDaysFromUnavailabilities(
                        rule.getUnavailabilities(), queryStartDate, queryEndDate);
                unavailabilityDays.addAll(unavailableDays);
            }
        }

        return availableDays;
    }

    /**
     * Extract days from a rule, considering recurring patterns and indices.
     */
    private Set<String> extractDaysFromRule(
            final JudiciaryAvailabilityRule rule,
            final LocalDate queryStartDate,
            final LocalDate queryEndDate) {

        final Set<String> days = new HashSet<>();

        // Determine the effective date range (intersection of rule dates and query dates)
        final LocalDate effectiveStart = rule.getFromDate().isAfter(queryStartDate) ? rule.getFromDate() : queryStartDate;
        final LocalDate effectiveEnd = rule.getToDate().isBefore(queryEndDate) ? rule.getToDate() : queryEndDate;

        if (rule.getRepeatDays() == null || rule.getRepeatDays().isEmpty()) {
            return days;
        }

        for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay repeatDay : rule.getRepeatDays()) {
            final AvailabilityDayOfWeek dayName = repeatDay.getDayOfWeek();
            // Add all matching days in the date range (recurringType removed, always weekly behavior)
            addDaysForDateRange(days, dayName, effectiveStart, effectiveEnd);
        }

        return days;
    }

    /**
     * Extract days from unavailabilities (all days within each unavailability date range).
     */
    private Set<LocalDate> extractDaysFromUnavailabilities(
            final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability> unavailabilities,
            final LocalDate queryStartDate,
            final LocalDate queryEndDate) {

        final Set<LocalDate> days = new HashSet<>();

        for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability : unavailabilities) {
            // Determine the effective date range (intersection of unavailability dates and query dates)
            final LocalDate effectiveStart = unavailability.getFromDate().isAfter(queryStartDate) 
                    ? unavailability.getFromDate() 
                    : queryStartDate;
            final LocalDate effectiveEnd = unavailability.getToDate().isBefore(queryEndDate) 
                    ? unavailability.getToDate() 
                    : queryEndDate;

            // If there's no overlap, skip this unavailability
            if (effectiveStart.isAfter(effectiveEnd)) {
                continue;
            }

            // Add all days within the unavailability date range
            LocalDate current = effectiveStart;
            while (!current.isAfter(effectiveEnd)) {
                days.add(current);
                current = current.plusDays(1);
            }
        }

        return days;
    }

    /**
     * Add all occurrences of a day of week within the date range.
     */
    private void addDaysForDateRange(final Set<String> days, final AvailabilityDayOfWeek dayName, final LocalDate start, final LocalDate end) {
        final DayOfWeek targetDayOfWeek = convertDayNameToDayOfWeek(dayName);
        if (targetDayOfWeek == null) {
            return;
        }

        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (current.getDayOfWeek() == targetDayOfWeek) {
                days.add(targetDayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK));
            }
            current = current.plusDays(1);
        }
    }


    /**
     * Convert day name (e.g., "Monday") to DayOfWeek enum.
     */
    private DayOfWeek convertDayNameToDayOfWeek(final AvailabilityDayOfWeek dayName) {
        if (dayName == null) {
            return null;
        }
        try {
            // Convert title case enum name (e.g., "Monday") to uppercase for java.time.DayOfWeek (e.g., "MONDAY")
            return DayOfWeek.valueOf(dayName.name().toUpperCase());
        } catch (IllegalArgumentException e) {
            LOGGER.warn("Invalid day name: {}", dayName);
            return null;
        }
    }

    /**
     * Check if any date in the query range has a day of week that matches the compiled available days.
     */
    private boolean hasMatchingDates(final LocalDate start, final LocalDate end, final Set<String> availableDays, final Set<LocalDate> unavailabilityDates) {
        if (availableDays.isEmpty()) {
            return false;
        }

        LocalDate current = start;
        while (!current.isAfter(end)) {
            if (unavailabilityDates.contains(current)) {
                current = current.plusDays(1);
                continue;
            }
            
            final DayOfWeek dayOfWeek = current.getDayOfWeek();
            final String dayName = dayOfWeek.getDisplayName(TextStyle.FULL, Locale.UK);
            if (availableDays.contains(dayName)) {
                return true;
            }
            current = current.plusDays(1);
        }

        return false;
    }

    /**
     * Populates entity fields from request (judiciaryId, courtHouseId, startDate, endDate, sessionType).
     * These fields are required as per contract.
     */
    private void populateEntityFields(final JudiciaryAvailabilityRule entity,
                                     final String judiciaryId, final String courtHouseId,
                                     final LocalDate startDate, final LocalDate endDate,
                                     final String sessionType) {
        entity.setJudiciaryId(judiciaryId);
        entity.setCourtHouseId(courtHouseId);
        entity.setFromDate(startDate);
        entity.setToDate(endDate);
        entity.setSessionType(sessionType != null ? SessionType.valueOf(sessionType) : SessionType.AD);
    }

    /**
     * Converts wire repeat days (String) to entity repeat days.
     * repeatDays is required as per contract.
     */
    private List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> convertRepeatDaysToEntity(
            final List<String> wireRepeatDays) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> entityRepeatDays = new ArrayList<>();
        if (wireRepeatDays != null) {
            for (String dayOfWeek : wireRepeatDays) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay persistDay =
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay(AvailabilityDayOfWeek.valueOf(dayOfWeek));
                entityRepeatDays.add(persistDay);
            }
        }
        return entityRepeatDays;
    }

    /**
     * Converts wire unavailabilities to entity unavailabilities.
     */
    private List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability> convertUnavailabilitiesToEntity(
            final List<JudiciaryUnavailability> wireUnavailabilities,
            final JudiciaryAvailabilityRule entity) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability> entityUnavailabilities = new ArrayList<>();
        if (wireUnavailabilities != null && !wireUnavailabilities.isEmpty()) {
            for (JudiciaryUnavailability unavailabilityRequest : wireUnavailabilities) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability =
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
                unavailability.setId(randomUUID().toString());
                unavailability.setRule(entity);
                unavailability.setFromDate(unavailabilityRequest.getStartDate());
                unavailability.setToDate(unavailabilityRequest.getEndDate());
                unavailability.setReason(unavailabilityRequest.getReason() == null ? null : UnavailabilityReason.valueOf(unavailabilityRequest.getReason()));

                entityUnavailabilities.add(unavailability);
                LOGGER.debug("Created judiciary unavailability with id: {}", unavailability.getId());
            }
        }
        return entityUnavailabilities;
    }

    /**
     * Extracts unique judiciary IDs from rules.
     */
    private List<String> extractUniqueJudiciaryIds(final List<JudiciaryAvailabilityRule> rules) {
        final Set<String> judiciaryIds = rules.stream()
                .map(JudiciaryAvailabilityRule::getJudiciaryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return new ArrayList<>(judiciaryIds);
    }

    /**
     * Validates an add judiciary availability rule request.
     * Returns the first validation error message encountered, or null if validation passed.
     */
    public String validateAddJudiciaryAvailabilityRule(final CourtschedulerAddJudiciaryAvailabilityRule request) {
        String error = validateDateRangeMaxThreeYears(request.getStartDate(), request.getEndDate());
        if (error != null) {
            return error;
        }

        error = validateFutureDatesForCreation(request.getStartDate(), request.getEndDate());
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityDateRanges(request.getStartDate(), request.getEndDate(), request.getUnavailabilities());
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityOverlaps(request.getUnavailabilities());
        if (error != null) {
            return error;
        }

        error = validateOverlappingRules(request.getJudiciaryId(), request.getStartDate(), request.getEndDate(), null);
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityAffectsAssignedSessions(request.getJudiciaryId(), request.getUnavailabilities());

        return error;
    }

    private String validateDateRangeMaxThreeYears(final LocalDate startDate, final LocalDate endDate) {
        if (startDate != null && endDate != null) {
            final long yearsBetween = java.time.temporal.ChronoUnit.YEARS.between(startDate, endDate);
            if (yearsBetween > 3) {
                return DATE_RANGE_MUST_BE_3_YEARS_OR_LESS;
            }
        }
        return null;
    }

    private String validateFutureDatesForCreation(final LocalDate startDate, final LocalDate endDate) {
        final LocalDate today = LocalDate.now();
        if (startDate != null && startDate.isBefore(today)) {
            return START_DATE_MUST_BE_IN_FUTURE;
        }
        if (endDate != null && endDate.isBefore(today)) {
            return END_DATE_MUST_BE_IN_FUTURE;
        }
        return null;
    }

    private String validateUnavailabilityDateRanges(final LocalDate startDate, final LocalDate endDate,
                                                     final List<JudiciaryUnavailability> unavailabilities) {
        if (unavailabilities == null || unavailabilities.isEmpty()) {
            return null;
        }

        for (int i = 0; i < unavailabilities.size(); i++) {
            final JudiciaryUnavailability unavailability = unavailabilities.get(i);
            if (unavailability.getStartDate() != null && unavailability.getEndDate() != null) {
                if (startDate != null && unavailability.getStartDate().isBefore(startDate)) {
                    return String.format(UNAVAILABILITY_START_DATE_MUST_BE_BETWEEN, i + 1, startDate, endDate);
                }
                if (endDate != null && unavailability.getEndDate().isAfter(endDate)) {
                    return String.format(UNAVAILABILITY_END_DATE_MUST_BE_BETWEEN, i + 1, startDate, endDate);
                }
            }
        }
        return null;
    }

    private String validateUnavailabilityOverlaps(final List<JudiciaryUnavailability> unavailabilities) {
        if (unavailabilities == null || unavailabilities.isEmpty()) {
            return null;
        }

        for (int i = 0; i < unavailabilities.size(); i++) {
            for (int j = i + 1; j < unavailabilities.size(); j++) {
                final JudiciaryUnavailability u1 = unavailabilities.get(i);
                final JudiciaryUnavailability u2 = unavailabilities.get(j);
                if (doDateRangesOverlap(u1.getStartDate(), u1.getEndDate(), u2.getStartDate(), u2.getEndDate())) {
                    return UNAVAILABILITY_DATES_CANNOT_OVERLAP;
                }
            }
        }
        return null;
    }

    private String validateOverlappingRules(final String judiciaryId, final LocalDate startDate, final LocalDate endDate, final String excludeRuleId) {
        if (judiciaryId == null || startDate == null || endDate == null) {
            return null;
        }

        final List<JudiciaryAvailabilityRule> overlappingRules = repository.findRulesByDateRange(
                startDate,
                endDate,
                null, // courtHouseId - check all court houses
                judiciaryId
        );

        final List<JudiciaryAvailabilityRule> rulesToCheck = excludeRuleId != null
                ? overlappingRules.stream()
                        .filter(rule -> !rule.getId().equals(excludeRuleId))
                        .toList()
                : overlappingRules;

        if (!rulesToCheck.isEmpty()) {
            return JUDICIARY_ALREADY_ASSIGNED_DURING_DATES;
        }

        return null;
    }

    private String validateUnavailabilityAffectsAssignedSessions(final String judiciaryId, final List<JudiciaryUnavailability> unavailabilities) {
        if (judiciaryId == null) {
            return null;
        }

        if (unavailabilities == null || unavailabilities.isEmpty()) {
            return null;
        }

        for (final JudiciaryUnavailability unavailability : unavailabilities) {
            if (unavailability.getStartDate() != null && unavailability.getEndDate() != null) {
                final List<String> affectedSessions = courtScheduleJudiciaryRepository
                        .findCourtScheduleIdsByJudiciaryAndDateRange(
                                judiciaryId,
                                unavailability.getStartDate(),
                                unavailability.getEndDate()
                        );
                if (!affectedSessions.isEmpty()) {
                    return String.format(ADDING_UNAVAILABILITY_WOULD_AFFECT_SESSIONS,
                            unavailability.getStartDate(), unavailability.getEndDate(), affectedSessions.size());
                }
            }
        }
        return null;
    }

    /**
     * Validates an update judiciary availability rule request.
     * Returns the first validation error message encountered, or null if validation passed.
     */
    public String validateUpdateJudiciaryAvailabilityRule(final CourtschedulerUpdateJudiciaryAvailabilityRule request) {
        String error = validateRuleIdForUpdate(request);
        if (error != null) {
            return error;
        }

        final JudiciaryAvailabilityRule existingRule = repository.findById(request.getRuleId()).orElse(null);
        error = validateExistingRule(existingRule);
        if (error != null) {
            return error;
        }

        error = validateDateRangeMaxThreeYears(request.getStartDate(), request.getEndDate());
        if (error != null) {
            return error;
        }

        error = validateChangedDatesForUpdate(request, existingRule);
        if (error != null) {
            return error;
        }

        error = validateDateRangeChangesAffectAssignedSessions(request, existingRule);
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityDateRanges(request.getStartDate(), request.getEndDate(), request.getUnavailabilities());
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityOverlaps(request.getUnavailabilities());
        if (error != null) {
            return error;
        }

        error = validateOverlappingRules(request.getJudiciaryId(), request.getStartDate(), request.getEndDate(), request.getRuleId());
        if (error != null) {
            return error;
        }

        error = validateUnavailabilityAffectsAssignedSessions(request.getJudiciaryId(), request.getUnavailabilities());

        return error;
    }

    private String validateRuleIdForUpdate(final CourtschedulerUpdateJudiciaryAvailabilityRule request) {
        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            return RULE_ID_REQUIRED_FOR_UPDATE;
        }
        return null;
    }

    private String validateExistingRule(final JudiciaryAvailabilityRule existingRule) {
        if (existingRule == null) {
            return String.format(RULE_NOT_FOUND);
        }
        return null;
    }

    private String validateChangedDatesForUpdate(final CourtschedulerUpdateJudiciaryAvailabilityRule request,
                                               final JudiciaryAvailabilityRule existingRule) {
        if (existingRule == null) {
            return String.format(RULE_NOT_FOUND);
        }
        final LocalDate today = LocalDate.now();
        final boolean startDateChanged = !Objects.equals(existingRule.getFromDate(), request.getStartDate());
        final boolean endDateChanged = !Objects.equals(existingRule.getToDate(), request.getEndDate());

        if (startDateChanged && request.getStartDate() != null && request.getStartDate().isBefore(today)) {
            return NEW_START_DATE_MUST_BE_IN_FUTURE;
        }
        if (endDateChanged && request.getEndDate() != null && request.getEndDate().isBefore(today)) {
            return NEW_END_DATE_MUST_BE_IN_FUTURE;
        }
        return null;
    }

    private String validateDateRangeChangesAffectAssignedSessions(final CourtschedulerUpdateJudiciaryAvailabilityRule request,
                                                                 final JudiciaryAvailabilityRule existingRule) {
        if (request.getJudiciaryId() == null) {
            return null;
        }

        final boolean startDateChanged = !Objects.equals(existingRule.getFromDate(), request.getStartDate());
        final boolean endDateChanged = !Objects.equals(existingRule.getToDate(), request.getEndDate());

        if (!startDateChanged && !endDateChanged) {
            return null;
        }

        final LocalDate oldStart = existingRule.getFromDate();
        final LocalDate oldEnd = existingRule.getToDate();
        final LocalDate newStart = request.getStartDate();
        final LocalDate newEnd = request.getEndDate();

        if (startDateChanged && newStart != null && newStart.isAfter(oldStart)) {
            String error = validateStartDateChangeAffectsSessions(request.getJudiciaryId(), oldStart, newStart);
            if (error != null) {
                return error;
            }
        }

        if (endDateChanged && newEnd != null && newEnd.isBefore(oldEnd)) {
            String error = validateEndDateChangeAffectsSessions(request.getJudiciaryId(), oldEnd, newEnd);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private String validateStartDateChangeAffectsSessions(final String judiciaryId,
                                                        final LocalDate oldStart,
                                                        final LocalDate newStart) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        judiciaryId,
                        oldStart,
                        newStart.minusDays(1)
                );
        if (!affectedSessions.isEmpty()) {
            return "Changing the start date affects " + affectedSessions.size() +
                    " sessions already assigned between " + oldStart + " and " + newStart +
                    ". Review these sessions before you continue.";
        }
        return null;
    }

    private String validateEndDateChangeAffectsSessions(final String judiciaryId,
                                                      final LocalDate oldEnd,
                                                      final LocalDate newEnd) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        judiciaryId,
                        newEnd.plusDays(1),
                        oldEnd
                );
        if (!affectedSessions.isEmpty()) {
            return String.format(CHANGING_END_DATE_FROM_TO_WOULD_AFFECT, oldEnd, newEnd, affectedSessions.size());
        }
        return null;
    }


    /**
     * Checks if two date ranges overlap.
     */
    private boolean doDateRangesOverlap(final LocalDate start1, final LocalDate end1, 
                                       final LocalDate start2, final LocalDate end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return false;
        }
        // Two ranges overlap if: start1 <= end2 AND start2 <= end1
        return !start1.isAfter(end2) && !start2.isAfter(end1);
    }

    /**
     * Validates if a judiciary availability rule can be deleted.
     * Checks if the rule is already applied/assigned to a session.
     * Returns an error message if the rule is applied to sessions, null otherwise.
     */
    public String validateDeleteJudiciaryAvailabilityRule(final CourtschedulerDeleteJudiciaryAvailabilityRule request) {
        LOGGER.info("Validating delete for judiciary availability rule: {}", request);

        if (request == null || request.getRuleId() == null || request.getRuleId().isEmpty()) {
            return RULE_ID_REQUIRED;
        }

        final JudiciaryAvailabilityRule rule = repository.findById(request.getRuleId()).orElse(null);
        if (rule == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            return String.format(RULE_NOT_FOUND);
        }

        // Find court schedules (sessions) that match the rule's criteria
        final String sessionType = rule.getSessionType() != null ? rule.getSessionType().name() : "AD";
        final List<Object[]> matchingSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryDateRangeAndSessionType(
                        rule.getJudiciaryId(),
                        rule.getFromDate(),
                        rule.getToDate(),
                        sessionType
                );

        if (matchingSessions.isEmpty()) {
            LOGGER.info("No matching sessions found for rule {}", request.getRuleId());
            return null;
        } else {
            return CANNOT_DELETE_ITINERARY_IN_USE;
        }
    }
}

