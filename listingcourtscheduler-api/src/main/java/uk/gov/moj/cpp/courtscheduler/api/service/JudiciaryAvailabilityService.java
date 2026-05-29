package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.BaseJudiciaryAvailabilityRuleWithDetailsRequest;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.GetJudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
// (removed) Requester replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;

import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.ADDING_UNAVAILABILITY_WOULD_AFFECT_SESSIONS;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.CANNOT_DELETE_ITINERARY_IN_USE;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.CHANGING_END_DATE_FROM_TO_WOULD_AFFECT;
import static uk.gov.moj.cpp.courtscheduler.api.JudiciaryAvailabilityValidationMessages.CHANGING_START_DATE_AFFECTS_SESSIONS;
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

    public void addJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Adding judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = new JudiciaryAvailabilityRule();
        entity.setId(randomUUID().toString());
        
        populateEntityFields(entity, request);
        entity.setRepeatDays(convertRepeatDaysToEntity(request.getRepeatDays()));
        entity.setUnavailabilities(convertUnavailabilitiesToEntity(request.getUnavailabilities(), entity));

        repository.save(entity);
        LOGGER.info("Saved judiciary availability rule with id: {} and {} unavailabilities", 
                entity.getId(), entity.getUnavailabilities().size());
    }

    public void updateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Updating judiciary availability rule: {}", request);

        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            throw new IllegalArgumentException("Rule ID is required for update");
        }

        final JudiciaryAvailabilityRule entity = repository.findById(request.getRuleId()).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        populateEntityFields(entity, request);
        
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

    public void deleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Deleting judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = repository.findById(request.getRuleId()).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        repository.remove(entity);
        LOGGER.info("Deleted judiciary availability rule with id: {}", request.getRuleId());
    }

    public FindJudiciaryAvailabilityResponse findJudiciaryAvailability(final FindJudiciaryAvailabilityRequest request) {
        LOGGER.info("Finding judiciary availability for: {}", request);

        // Find all rules that overlap with the query date range
        final List<JudiciaryAvailabilityRule> rules = repository.findRulesByDateRange(
                request.getStartDate(),
                request.getEndDate(),
                request.getCourtHouseId(),
                request.getJudiciaryId()
        );

        LOGGER.info("Found {} rules overlapping with date range", rules.size());

        // Group rules by judiciaryId
        final Map<String, List<JudiciaryAvailabilityRule>> rulesByJudiciary = rules.stream()
                .collect(Collectors.groupingBy(JudiciaryAvailabilityRule::getJudiciaryId));

        // Compile rules for each judiciary
        final List<String> availableJudiciaries = new ArrayList<>();
        for (Map.Entry<String, List<JudiciaryAvailabilityRule>> entry : rulesByJudiciary.entrySet()) {
            final String judiciaryId = entry.getKey();
            final List<JudiciaryAvailabilityRule> judiciaryRules = entry.getValue();

            final Set<LocalDate> unavailabilityDates = new HashSet<>();
            // Compile available days for this judiciary
            final Set<String> compiledAvailableDays = compileAvailableDays(judiciaryRules, request.getStartDate(), request.getEndDate(), unavailabilityDates);

            // Check if any date in the query range matches the compiled days
            if (hasMatchingDates(request.getStartDate(), request.getEndDate(), compiledAvailableDays, unavailabilityDates)) {
                availableJudiciaries.add(judiciaryId);
            }
        }

        LOGGER.info("Found {} available judiciaries", availableJudiciaries.size());
        return new FindJudiciaryAvailabilityResponse(availableJudiciaries);
    }

    public FindJudiciaryAvailabilityRuleResponse findJudiciaryAvailabilityRules(final FindJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Finding judiciary availability rules for: {}", request);

        // Get default pagination values if not provided
        final int pageSize = request.getPageSize() != null ? request.getPageSize() : 20;
        final int pageNumber = request.getPageNumber() != null ? request.getPageNumber() : 1;
        final boolean withJudiciary = Boolean.TRUE.equals(request.getWithJudiciary());

        // Find rules with pagination
        final java.util.Map.Entry<Integer, List<JudiciaryAvailabilityRule>> result = repository.findRulesByDateRangeWithPagination(
                request.getStartDate(),
                request.getEndDate(),
                request.getCourtHouseId(),
                request.getJudiciaryId(),
                pageSize,
                pageNumber
        );

        final int totalCount = result.getKey();
        final List<JudiciaryAvailabilityRule> rules = result.getValue();

        LOGGER.info("Found {} rules (total: {}) for page {} with page size {}", rules.size(), totalCount, pageNumber, pageSize);

        // Convert entities to domain response objects
        final List<JudiciaryAvailabilityRuleResponse> ruleResponses = rules.stream()
                .map(this::convertToResponse)
                .toList();

        final FindJudiciaryAvailabilityRuleResponse response = new FindJudiciaryAvailabilityRuleResponse(ruleResponses, totalCount, pageNumber, pageSize);

        // Always initialize judiciaries list (empty if not requested)
        final List<Judiciary> judiciaries = new ArrayList<>();
        
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

    public GetJudiciaryAvailabilityRuleResponse getJudiciaryAvailabilityRule(final GetJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Getting judiciary availability rule for ruleId: {}", request.getRuleId());

        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        final JudiciaryAvailabilityRule entity = repository.findById(request.getRuleId()).orElse(null);
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException(RULE_NOT_FOUND);
        }

        final JudiciaryAvailabilityRuleResponse ruleResponse = convertToResponse(entity);

        // Fetch judiciary if requested
        final boolean withJudiciary = Boolean.TRUE.equals(request.getWithJudiciary());
        Judiciary judiciary = null;
        if (withJudiciary && entity.getJudiciaryId() != null) {
            final List<String> judiciaryIdList = List.of(entity.getJudiciaryId());
            final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesWithSpecialismByIds(judiciaryIdList);
            if (!fetchedJudiciaries.isEmpty()) {
                judiciary = fetchedJudiciaries.get(0);
                LOGGER.info("Fetched judiciary for ruleId {}", request.getRuleId());
            }
        }

        return new GetJudiciaryAvailabilityRuleResponse(ruleResponse, judiciary);
    }

    private JudiciaryAvailabilityRuleResponse convertToResponse(final JudiciaryAvailabilityRule entity) {
        final JudiciaryAvailabilityRuleResponse response = new JudiciaryAvailabilityRuleResponse();
        response.setId(entity.getId());
        response.setJudiciaryId(entity.getJudiciaryId());
        response.setCourtHouseId(entity.getCourtHouseId());
        response.setStartDate(entity.getFromDate());
        response.setEndDate(entity.getToDate());
        response.setSessionType(entity.getSessionType());

        // Convert entity repeat days to domain repeat days (enum)
        if (entity.getRepeatDays() != null && !entity.getRepeatDays().isEmpty()) {
            final List<AvailabilityDayOfWeek> domainRepeatDays = new ArrayList<>();
            for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay entityDay : entity.getRepeatDays()) {
                domainRepeatDays.add(entityDay.getDayOfWeek());
            }
            response.setRepeatDays(domainRepeatDays);
        }

        // Convert entity unavailabilities to domain unavailabilities
        if (entity.getUnavailabilities() != null && !entity.getUnavailabilities().isEmpty()) {
            final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse> domainUnavailabilities = new ArrayList<>();
            for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability entityUnavailability : entity.getUnavailabilities()) {
                domainUnavailabilities.add(new uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityResponse(
                        entityUnavailability.getFromDate(),
                        entityUnavailability.getToDate(),
                        entityUnavailability.getReason()
                ));
            }
            response.setUnavailabilities(domainUnavailabilities);
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
                                     final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        entity.setJudiciaryId(request.getJudiciaryId());
        entity.setCourtHouseId(request.getCourtHouseId());
        entity.setFromDate(request.getStartDate());
        entity.setToDate(request.getEndDate());
        entity.setSessionType(request.getSessionType() != null ? request.getSessionType() : SessionType.AD);
    }

    /**
     * Converts domain repeat days (enum) to entity repeat days.
     * repeatDays is required as per contract.
     */
    private List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> convertRepeatDaysToEntity(
            final List<AvailabilityDayOfWeek> domainRepeatDays) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> entityRepeatDays = new ArrayList<>();
        if (domainRepeatDays != null) {
            for (AvailabilityDayOfWeek dayOfWeek : domainRepeatDays) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay persistDay = 
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay(dayOfWeek);
                entityRepeatDays.add(persistDay);
            }
        }
        return entityRepeatDays;
    }

    /**
     * Converts domain unavailabilities to entity unavailabilities.
     */
    private List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability> convertUnavailabilitiesToEntity(
            final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest> domainUnavailabilities,
            final JudiciaryAvailabilityRule entity) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability> entityUnavailabilities = new ArrayList<>();
        if (domainUnavailabilities != null && !domainUnavailabilities.isEmpty()) {
            for (uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest unavailabilityRequest : domainUnavailabilities) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability = 
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
                unavailability.setId(randomUUID().toString());
                unavailability.setRule(entity);
                unavailability.setFromDate(unavailabilityRequest.getStartDate());
                unavailability.setToDate(unavailabilityRequest.getEndDate());
                unavailability.setReason(unavailabilityRequest.getReason());
                
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
    public String validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        String error = validateDateRangeMaxThreeYears(request);
        if (error != null) {
            return error;
        }
        
        error = validateFutureDatesForCreation(request);
        if (error != null) {
            return error;
        }
        
        error = validateUnavailabilityDateRanges(request);
        if (error != null) {
            return error;
        }
        
        error = validateUnavailabilityOverlaps(request);
        if (error != null) {
            return error;
        }
        
        error = validateOverlappingRules(request, null);
        if (error != null) {
            return error;
        }
        
        error = validateUnavailabilityAffectsAssignedSessions(request);
        
        return error;
    }

    private String validateDateRangeMaxThreeYears(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (request.getStartDate() != null && request.getEndDate() != null) {
            final long yearsBetween = java.time.temporal.ChronoUnit.YEARS.between(request.getStartDate(), request.getEndDate());
            if (yearsBetween > 3) {
                return DATE_RANGE_MUST_BE_3_YEARS_OR_LESS;
            }
        }
        return null;
    }

    private String validateFutureDatesForCreation(final AddJudiciaryAvailabilityRuleRequest request) {
        final LocalDate today = LocalDate.now();
        if (request.getStartDate() != null && request.getStartDate().isBefore(today)) {
            return START_DATE_MUST_BE_IN_FUTURE;
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(today)) {
            return END_DATE_MUST_BE_IN_FUTURE;
        }
        return null;
    }

    private String validateUnavailabilityDateRanges(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return null;
        }
        
        for (int i = 0; i < request.getUnavailabilities().size(); i++) {
            final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest unavailability = 
                    request.getUnavailabilities().get(i);
            if (unavailability.getStartDate() != null && unavailability.getEndDate() != null) {
                if (request.getStartDate() != null && unavailability.getStartDate().isBefore(request.getStartDate())) {
                    return String.format(UNAVAILABILITY_START_DATE_MUST_BE_BETWEEN, i + 1, request.getStartDate(), request.getEndDate());
                }
                if (request.getEndDate() != null && unavailability.getEndDate().isAfter(request.getEndDate())) {
                    return String.format(UNAVAILABILITY_END_DATE_MUST_BE_BETWEEN, i + 1, request.getStartDate(), request.getEndDate());
                }
            }
        }
        return null;
    }

    private String validateUnavailabilityOverlaps(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return null;
        }
        
        for (int i = 0; i < request.getUnavailabilities().size(); i++) {
            for (int j = i + 1; j < request.getUnavailabilities().size(); j++) {
                final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest u1 = 
                        request.getUnavailabilities().get(i);
                final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest u2 = 
                        request.getUnavailabilities().get(j);
                if (doDateRangesOverlap(u1.getStartDate(), u1.getEndDate(), u2.getStartDate(), u2.getEndDate())) {
                    return UNAVAILABILITY_DATES_CANNOT_OVERLAP;
                }
            }
        }
        return null;
    }

    private String validateOverlappingRules(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final String excludeRuleId) {
        if (request.getJudiciaryId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return null;
        }
        
        final List<JudiciaryAvailabilityRule> overlappingRules = repository.findRulesByDateRange(
                request.getStartDate(),
                request.getEndDate(),
                null, // courtHouseId - check all court houses
                request.getJudiciaryId()
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

    private String validateUnavailabilityAffectsAssignedSessions(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        if (request.getJudiciaryId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return null;
        }
        
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return null;
        }
        
        for (final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest unavailability : 
                request.getUnavailabilities()) {
            if (unavailability.getStartDate() != null && unavailability.getEndDate() != null) {
                final List<String> affectedSessions = courtScheduleJudiciaryRepository
                        .findCourtScheduleIdsByJudiciaryAndDateRange(
                                request.getJudiciaryId(),
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
    public String validateUpdateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        String error = validateRuleIdForUpdate(request);
        if (error != null) {
            return error;
        }
        
        final JudiciaryAvailabilityRule existingRule = repository.findById(request.getRuleId()).orElse(null);
        error = validateExistingRule(request, existingRule);
        if (error != null) {
            return error;
        }
        
        error = validateDateRangeMaxThreeYears(request);
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
        
        error = validateUnavailabilityDateRanges(request);
        if (error != null) {
            return error;
        }
        
        error = validateUnavailabilityOverlaps(request);
        if (error != null) {
            return error;
        }
        
        error = validateOverlappingRules(request, request.getRuleId());
        if (error != null) {
            return error;
        }
        
        error = validateUnavailabilityAffectsAssignedSessions(request);
        
        return error;
    }

    private String validateRuleIdForUpdate(final UpdateJudiciaryAvailabilityRuleRequest request) {
        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            return RULE_ID_REQUIRED_FOR_UPDATE;
        }
        return null;
    }

    private String validateExistingRule(final UpdateJudiciaryAvailabilityRuleRequest request, 
                                         final JudiciaryAvailabilityRule existingRule) {
        if (existingRule == null) {
            return String.format(RULE_NOT_FOUND);
        }
        return null;
    }

    private String validateChangedDatesForUpdate(final UpdateJudiciaryAvailabilityRuleRequest request,
                                               final JudiciaryAvailabilityRule existingRule) {
        final LocalDate today = LocalDate.now();
        final boolean startDateChanged = !existingRule.getFromDate().equals(request.getStartDate());
        final boolean endDateChanged = !existingRule.getToDate().equals(request.getEndDate());
        
        if (startDateChanged && request.getStartDate() != null && request.getStartDate().isBefore(today)) {
            return NEW_START_DATE_MUST_BE_IN_FUTURE;
        }
        if (endDateChanged && request.getEndDate() != null && request.getEndDate().isBefore(today)) {
            return NEW_END_DATE_MUST_BE_IN_FUTURE;
        }
        return null;
    }

    private String validateDateRangeChangesAffectAssignedSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                                 final JudiciaryAvailabilityRule existingRule) {
        if (request.getJudiciaryId() == null) {
            return null;
        }
        
        final boolean startDateChanged = !existingRule.getFromDate().equals(request.getStartDate());
        final boolean endDateChanged = !existingRule.getToDate().equals(request.getEndDate());
        
        if (!startDateChanged && !endDateChanged) {
            return null;
        }
        
        final LocalDate oldStart = existingRule.getFromDate();
        final LocalDate oldEnd = existingRule.getToDate();
        final LocalDate newStart = request.getStartDate();
        final LocalDate newEnd = request.getEndDate();
        
        if (startDateChanged && newStart != null && newStart.isAfter(oldStart)) {
            String error = validateStartDateChangeAffectsSessions(request, oldStart, newStart);
            if (error != null) {
                return error;
            }
        }
        
        if (endDateChanged && newEnd != null && newEnd.isBefore(oldEnd)) {
            String error = validateEndDateChangeAffectsSessions(request, oldEnd, newEnd);
            if (error != null) {
                return error;
            }
        }
        return null;
    }

    private String validateStartDateChangeAffectsSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                        final LocalDate oldStart,
                                                        final LocalDate newStart) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        request.getJudiciaryId(),
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

    private String validateEndDateChangeAffectsSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                      final LocalDate oldEnd,
                                                      final LocalDate newEnd) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        request.getJudiciaryId(),
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
    public String validateDeleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
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

