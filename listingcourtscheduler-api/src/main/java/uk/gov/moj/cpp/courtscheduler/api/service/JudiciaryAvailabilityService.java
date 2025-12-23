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
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleRepeatDay;
import uk.gov.moj.cpp.courtscheduler.domain.JudiciaryAvailabilityRuleResponse;
import uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRule;
import uk.gov.moj.cpp.courtscheduler.domain.RecurringType;
import uk.gov.moj.cpp.courtscheduler.domain.SessionType;
import uk.gov.moj.cpp.courtscheduler.repository.JudiciaryAvailabilityRuleRepository;
import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataService;

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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.persistence.EntityManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class JudiciaryAvailabilityService {

    private static final Logger LOGGER = LoggerFactory.getLogger(JudiciaryAvailabilityService.class.getName());
    public static final String JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND = "Judiciary availability rule with id {} not found";
    private static final String UNAVAILABILITY_PREFIX = "Unavailability ";
    private static final String WOULD_AFFECT = " would affect ";

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

        final JudiciaryAvailabilityRule entity = repository.findBy(request.getRuleId());
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException("Judiciary availability rule with id " + request.getRuleId() + " not found");
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

        final JudiciaryAvailabilityRule entity = repository.findBy(request.getRuleId());
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException("Judiciary availability rule with id " + request.getRuleId() + " not found");
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

    public FindJudiciaryAvailabilityRuleResponse findJudiciaryAvailabilityRules(final FindJudiciaryAvailabilityRuleRequest request, final Requester requester) {
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
        if (withJudiciary && requester != null) {
            final List<String> judiciaryIdList = extractUniqueJudiciaryIds(rules);
            if (!judiciaryIdList.isEmpty()) {
                final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesWithSpecialismByIds(judiciaryIdList, requester);
                judiciaries.addAll(fetchedJudiciaries);
                LOGGER.info("Fetched {} judiciaries for {} unique IDs", fetchedJudiciaries.size(), judiciaryIdList.size());
            }
        }
        
        response.setJudiciaries(judiciaries);

        return response;
    }

    public GetJudiciaryAvailabilityRuleResponse getJudiciaryAvailabilityRule(final GetJudiciaryAvailabilityRuleRequest request, final Requester requester) {
        LOGGER.info("Getting judiciary availability rule for ruleId: {}", request.getRuleId());

        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            throw new IllegalArgumentException("Rule ID is required");
        }

        final JudiciaryAvailabilityRule entity = repository.findBy(request.getRuleId());
        if (entity == null) {
            LOGGER.warn(JUDICIARY_AVAILABILITY_RULE_WITH_ID_NOT_FOUND, request.getRuleId());
            throw new IllegalArgumentException("Judiciary availability rule with id " + request.getRuleId() + " not found");
        }

        final JudiciaryAvailabilityRuleResponse ruleResponse = convertToResponse(entity);

        // Fetch judiciary if requested
        final boolean withJudiciary = Boolean.TRUE.equals(request.getWithJudiciary());
        Judiciary judiciary = null;
        if (withJudiciary && requester != null && entity.getJudiciaryId() != null) {
            final List<String> judiciaryIdList = List.of(entity.getJudiciaryId());
            final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesWithSpecialismByIds(judiciaryIdList, requester);
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
        response.setRecurringType(entity.getRecurringType());
        response.setSessionType(entity.getSessionType());

        // Convert entity repeat days to domain repeat days
        if (entity.getRepeatDays() != null && !entity.getRepeatDays().isEmpty()) {
            final List<JudiciaryAvailabilityRuleRepeatDay> domainRepeatDays = new ArrayList<>();
            for (uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay entityDay : entity.getRepeatDays()) {
                // Convert 0 index to null (0 means no index in database)
                final Integer index = entityDay.getIndex() != null && entityDay.getIndex() > 0 ? entityDay.getIndex() : null;
                domainRepeatDays.add(new JudiciaryAvailabilityRuleRepeatDay(entityDay.getDayOfWeek(), index));
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
            final Integer index = repeatDay.getIndex();
            // 0 means no index was provided
            final boolean hasIndex = index != null && index > 0;

            if (rule.getRecurringType() == null) {
                // No recurring type - add all matching days in the date range
                addDaysForDateRange(days, dayName, effectiveStart, effectiveEnd);
            } else if (RecurringType.MONTHLY.equals(rule.getRecurringType()) && hasIndex) {
                // Monthly recurring with index (e.g., 2nd Tuesday, 3rd Wednesday)
                addDaysForMonthlyRecurring(days, dayName, index, effectiveStart, effectiveEnd);
            } else if (RecurringType.WEEKLY.equals(rule.getRecurringType())) {
                // Weekly recurring - add all matching days in the date range
                addDaysForDateRange(days, dayName, effectiveStart, effectiveEnd);
            } else {
                // Default: add all matching days
                addDaysForDateRange(days, dayName, effectiveStart, effectiveEnd);
            }
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
     * Add days for monthly recurring pattern with index (e.g., 2nd Tuesday of each month).
     */
    private void addDaysForMonthlyRecurring(
            final Set<String> days,
            final AvailabilityDayOfWeek dayName,
            final Integer index,
            final LocalDate start,
            final LocalDate end) {

        final DayOfWeek targetDayOfWeek = convertDayNameToDayOfWeek(dayName);
        if (targetDayOfWeek == null || index == null) {
            return;
        }

        LocalDate current = start;
        while (!current.isAfter(end)) {
            // Check if current date is the Nth occurrence of the target day in its month
            if (isNthOccurrenceOfDayInMonth(current, targetDayOfWeek, index)) {
                days.add(targetDayOfWeek.getDisplayName(TextStyle.FULL,Locale.UK));
            }
            current = current.plusDays(1);
        }
    }

    /**
     * Check if a date is the Nth occurrence of a day of week in its month.
     */
    private boolean isNthOccurrenceOfDayInMonth(final LocalDate date, final DayOfWeek dayOfWeek, final int n) {
        if (date.getDayOfWeek() != dayOfWeek) {
            return false;
        }

        // Count occurrences of this day in the month up to this date
        int occurrenceCount = 0;
        LocalDate firstOfMonth = date.withDayOfMonth(1);
        LocalDate current = firstOfMonth;

        while (!current.isAfter(date)) {
            if (current.getDayOfWeek() == dayOfWeek) {
                occurrenceCount++;
            }
            current = current.plusDays(1);
        }

        return occurrenceCount == n;
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
     * Populates entity fields from request (judiciaryId, courtHouseId, startDate, endDate, recurringType, sessionType).
     * These fields are required as per contract.
     */
    private void populateEntityFields(final JudiciaryAvailabilityRule entity, 
                                     final BaseJudiciaryAvailabilityRuleWithDetailsRequest request) {
        entity.setJudiciaryId(request.getJudiciaryId());
        entity.setCourtHouseId(request.getCourtHouseId());
        entity.setFromDate(request.getStartDate());
        entity.setToDate(request.getEndDate());
        entity.setRecurringType(request.getRecurringType());
        entity.setSessionType(request.getSessionType() != null ? request.getSessionType() : SessionType.AD);
    }

    /**
     * Converts domain repeat days to entity repeat days.
     * repeatDays is required as per contract.
     */
    private List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> convertRepeatDaysToEntity(
            final List<JudiciaryAvailabilityRuleRepeatDay> domainRepeatDays) {
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> entityRepeatDays = new ArrayList<>();
        if (domainRepeatDays != null) {
            for (JudiciaryAvailabilityRuleRepeatDay domainDay : domainRepeatDays) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay persistDay = 
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
                persistDay.setDayOfWeek(domainDay.getDayOfWeek());
                // Convert null index to 0 (0 means no index in database)
                persistDay.setIndex(domainDay.getIndex() != null ? domainDay.getIndex() : 0);
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
     * Returns a list of validation error messages. Empty list means validation passed.
     */
    public List<String> validateAddJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        final List<String> errors = new ArrayList<>();
        
        validateDateRangeMaxThreeYears(request, errors);
        validateFutureDatesForCreation(request, errors);
        validateUnavailabilityDateRanges(request, errors);
        validateUnavailabilityOverlaps(request, errors);
        validateOverlappingRules(request, null, errors);
        validateUnavailabilityAffectsAssignedSessions(request, errors);
        
        return errors;
    }

    private void validateDateRangeMaxThreeYears(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final List<String> errors) {
        if (request.getStartDate() != null && request.getEndDate() != null) {
            final long yearsBetween = java.time.temporal.ChronoUnit.YEARS.between(request.getStartDate(), request.getEndDate());
            if (yearsBetween > 3) {
                errors.add("Date range cannot exceed 3 years");
            }
        }
    }

    private void validateFutureDatesForCreation(final AddJudiciaryAvailabilityRuleRequest request, final List<String> errors) {
        final LocalDate today = LocalDate.now();
        if (request.getStartDate() != null && request.getStartDate().isBefore(today)) {
            errors.add("Start date must be in the future during creation");
        }
        if (request.getEndDate() != null && request.getEndDate().isBefore(today)) {
            errors.add("End date must be in the future during creation");
        }
    }

    private void validateUnavailabilityDateRanges(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final List<String> errors) {
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return;
        }
        
        for (int i = 0; i < request.getUnavailabilities().size(); i++) {
            final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest unavailability = 
                    request.getUnavailabilities().get(i);
            if (unavailability.getStartDate() != null && unavailability.getEndDate() != null) {
                if (request.getStartDate() != null && unavailability.getStartDate().isBefore(request.getStartDate())) {
                    errors.add(UNAVAILABILITY_PREFIX + (i + 1) + " start date must be within availability date range");
                }
                if (request.getEndDate() != null && unavailability.getEndDate().isAfter(request.getEndDate())) {
                    errors.add(UNAVAILABILITY_PREFIX + (i + 1) + " end date must be within availability date range");
                }
            }
        }
    }

    private void validateUnavailabilityOverlaps(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final List<String> errors) {
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return;
        }
        
        for (int i = 0; i < request.getUnavailabilities().size(); i++) {
            for (int j = i + 1; j < request.getUnavailabilities().size(); j++) {
                final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest u1 = 
                        request.getUnavailabilities().get(i);
                final uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest u2 = 
                        request.getUnavailabilities().get(j);
                if (doDateRangesOverlap(u1.getStartDate(), u1.getEndDate(), u2.getStartDate(), u2.getEndDate())) {
                    errors.add("Unavailabilities cannot overlap");
                    return;
                }
            }
        }
    }

    private void validateOverlappingRules(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final String excludeRuleId, final List<String> errors) {
        if (request.getJudiciaryId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return;
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
        
        for (final JudiciaryAvailabilityRule existingRule : rulesToCheck) {
            if (hasOverlappingRepeatPattern(request, existingRule)) {
                errors.add("A judiciary can only be available in one place at a time. An overlapping rule exists for the same date range and repeat pattern");
                return;
            }
        }
    }

    private void validateUnavailabilityAffectsAssignedSessions(final BaseJudiciaryAvailabilityRuleWithDetailsRequest request, final List<String> errors) {
        if (request.getJudiciaryId() == null || request.getStartDate() == null || request.getEndDate() == null) {
            return;
        }
        
        if (request.getUnavailabilities() == null || request.getUnavailabilities().isEmpty()) {
            return;
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
                    errors.add("Adding unavailability from " + unavailability.getStartDate() + 
                            " to " + unavailability.getEndDate() + 
                            WOULD_AFFECT + affectedSessions.size() + 
                            " already assigned session(s). Please review the assigned sessions before proceeding.");
                    return;
                }
            }
        }
    }

    /**
     * Validates an update judiciary availability rule request.
     * Returns a list of validation error messages. Empty list means validation passed.
     */
    public List<String> validateUpdateJudiciaryAvailabilityRule(final UpdateJudiciaryAvailabilityRuleRequest request) {
        final List<String> errors = new ArrayList<>();
        
        if (validateRuleIdForUpdate(request, errors)) {
            return errors;
        }
        
        final JudiciaryAvailabilityRule existingRule = repository.findBy(request.getRuleId());
        if (validateExistingRule(request, existingRule, errors)) {
            return errors;
        }
        
        validateDateRangeMaxThreeYears(request, errors);
        validateChangedDatesForUpdate(request, existingRule, errors);
        validateDateRangeChangesAffectAssignedSessions(request, existingRule, errors);
        validateUnavailabilityDateRanges(request, errors);
        validateUnavailabilityOverlaps(request, errors);
        validateOverlappingRules(request, request.getRuleId(), errors);
        validateUnavailabilityAffectsAssignedSessions(request, errors);
        
        return errors;
    }

    private boolean validateRuleIdForUpdate(final UpdateJudiciaryAvailabilityRuleRequest request, final List<String> errors) {
        if (request.getRuleId() == null || request.getRuleId().isEmpty()) {
            errors.add("Rule ID is required for update");
            return true;
        }
        return false;
    }

    private boolean validateExistingRule(final UpdateJudiciaryAvailabilityRuleRequest request, 
                                         final JudiciaryAvailabilityRule existingRule, 
                                         final List<String> errors) {
        if (existingRule == null) {
            errors.add("Judiciary availability rule with id " + request.getRuleId() + " not found");
            return true;
        }
        return false;
    }

    private void validateChangedDatesForUpdate(final UpdateJudiciaryAvailabilityRuleRequest request,
                                               final JudiciaryAvailabilityRule existingRule,
                                               final List<String> errors) {
        final LocalDate today = LocalDate.now();
        final boolean startDateChanged = !existingRule.getFromDate().equals(request.getStartDate());
        final boolean endDateChanged = !existingRule.getToDate().equals(request.getEndDate());
        
        if (startDateChanged && request.getStartDate() != null && request.getStartDate().isBefore(today)) {
            errors.add("If start date is changed, it must be in the future");
        }
        if (endDateChanged && request.getEndDate() != null && request.getEndDate().isBefore(today)) {
            errors.add("If end date is changed, it must be in the future");
        }
    }

    private void validateDateRangeChangesAffectAssignedSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                                 final JudiciaryAvailabilityRule existingRule,
                                                                 final List<String> errors) {
        if (request.getJudiciaryId() == null) {
            return;
        }
        
        final boolean startDateChanged = !existingRule.getFromDate().equals(request.getStartDate());
        final boolean endDateChanged = !existingRule.getToDate().equals(request.getEndDate());
        
        if (!startDateChanged && !endDateChanged) {
            return;
        }
        
        final LocalDate oldStart = existingRule.getFromDate();
        final LocalDate oldEnd = existingRule.getToDate();
        final LocalDate newStart = request.getStartDate();
        final LocalDate newEnd = request.getEndDate();
        
        if (startDateChanged && newStart != null && newStart.isAfter(oldStart)) {
            validateStartDateChangeAffectsSessions(request, oldStart, newStart, errors);
        }
        
        if (endDateChanged && newEnd != null && newEnd.isBefore(oldEnd)) {
            validateEndDateChangeAffectsSessions(request, oldEnd, newEnd, errors);
        }
    }

    private void validateStartDateChangeAffectsSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                        final LocalDate oldStart,
                                                        final LocalDate newStart,
                                                        final List<String> errors) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        request.getJudiciaryId(),
                        oldStart,
                        newStart.minusDays(1)
                );
        if (!affectedSessions.isEmpty()) {
            errors.add("Changing start date from " + oldStart + " to " + newStart + 
                    WOULD_AFFECT + affectedSessions.size() + 
                    " already assigned session(s) in the removed date range. Please review the assigned sessions before proceeding.");
        }
    }

    private void validateEndDateChangeAffectsSessions(final UpdateJudiciaryAvailabilityRuleRequest request,
                                                      final LocalDate oldEnd,
                                                      final LocalDate newEnd,
                                                      final List<String> errors) {
        final List<String> affectedSessions = courtScheduleJudiciaryRepository
                .findCourtScheduleIdsByJudiciaryAndDateRange(
                        request.getJudiciaryId(),
                        newEnd.plusDays(1),
                        oldEnd
                );
        if (!affectedSessions.isEmpty()) {
            errors.add("Changing end date from " + oldEnd + " to " + newEnd + 
                    WOULD_AFFECT + affectedSessions.size() + 
                    " already assigned session(s) in the removed date range. Please review the assigned sessions before proceeding.");
        }
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
     * Checks if a request has an overlapping repeat pattern with an existing rule.
     * This checks if the repeat days and recurring type would cause conflicts.
     */
    private boolean hasOverlappingRepeatPattern(
            final BaseJudiciaryAvailabilityRuleWithDetailsRequest request,
            final JudiciaryAvailabilityRule existingRule) {
        
        // If both have the same recurring type
        if (request.getRecurringType() != null && existingRule.getRecurringType() != null) {
            if (!request.getRecurringType().equals(existingRule.getRecurringType())) {
                return false; // Different recurring types don't conflict
            }
        } else if (request.getRecurringType() != existingRule.getRecurringType()) {
            return false; // One has recurring type, the other doesn't
        }
        
        // Check if they have overlapping repeat days
        if (request.getRepeatDays() != null && !request.getRepeatDays().isEmpty() &&
            existingRule.getRepeatDays() != null && !existingRule.getRepeatDays().isEmpty()) {
            
            // Extract day names from request
            final Set<AvailabilityDayOfWeek> requestDays = request.getRepeatDays().stream()
                    .map(JudiciaryAvailabilityRuleRepeatDay::getDayOfWeek)
                    .collect(Collectors.toSet());
            
            // Extract day names from existing rule
            final Set<AvailabilityDayOfWeek> existingDays = existingRule.getRepeatDays().stream()
                    .map(uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay::getDayOfWeek)
                    .collect(Collectors.toSet());
            
            // Check if there's any overlap in days
            final Set<AvailabilityDayOfWeek> intersection = new HashSet<>(requestDays);
            intersection.retainAll(existingDays);
            
            // If there's overlap in days and same recurring type, they conflict
            return !intersection.isEmpty();
        }
        
        // If one has repeat days and the other doesn't, they might still conflict
        // For simplicity, we consider any overlap in date range with same judiciary as a conflict
        return true;
    }
}

