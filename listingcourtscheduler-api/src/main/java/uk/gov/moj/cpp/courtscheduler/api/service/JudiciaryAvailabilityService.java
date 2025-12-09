package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;

import uk.gov.moj.cpp.courtscheduler.domain.AddJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.domain.DeleteJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityResponse;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleRequest;
import uk.gov.moj.cpp.courtscheduler.domain.FindJudiciaryAvailabilityRuleResponse;
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

    @Inject
    private JudiciaryAvailabilityRuleRepository repository;
    @Inject
    private ReferenceDataService referenceDataService;
    @Inject
    private EntityManager entityManager;

    public void addJudiciaryAvailabilityRule(final AddJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Adding judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = new JudiciaryAvailabilityRule();
        entity.setId(randomUUID().toString());
        entity.setJudiciaryId(request.getJudiciaryId());
        entity.setCourtHouseId(request.getCourtHouseId());
        entity.setFromDate(request.getStartDate());
        entity.setToDate(request.getEndDate());
        entity.setRecurringType(request.getRecurringType());
        entity.setSessionType(request.getSessionType() != null ? request.getSessionType() : SessionType.AD);

        // Convert domain repeat days to entity repeat days
        final List<uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay> repeatDays = new ArrayList<>();
        if (request.getRepeatDays() != null) {
            for (JudiciaryAvailabilityRuleRepeatDay domainDay : request.getRepeatDays()) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay persistDay = 
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryAvailabilityRuleRepeatDay();
                persistDay.setDayOfWeek(domainDay.getDayOfWeek());
                // Convert null index to 0 (0 means no index in database)
                persistDay.setIndex(domainDay.getIndex() != null ? domainDay.getIndex() : 0);
                repeatDays.add(persistDay);
            }
        }
        entity.setRepeatDays(repeatDays);
        entity.setUnavailabilities(new ArrayList<>());

        // Create unavailability records from the unavailabilities array
        if (request.getUnavailabilities() != null && !request.getUnavailabilities().isEmpty()) {
            for (uk.gov.moj.cpp.courtscheduler.domain.JudiciaryUnavailabilityRequest unavailabilityRequest : request.getUnavailabilities()) {
                final uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability unavailability = 
                        new uk.gov.moj.cpp.courtscheduler.persist.entity.JudiciaryUnavailability();
                unavailability.setId(randomUUID().toString());
                unavailability.setRule(entity);
                unavailability.setFromDate(unavailabilityRequest.getStartDate());
                unavailability.setToDate(unavailabilityRequest.getEndDate());
                unavailability.setReason(unavailabilityRequest.getReason());
                
                // Add to entity - will be persisted via cascade when entity is saved
                entity.getUnavailabilities().add(unavailability);
                LOGGER.info("Created judiciary unavailability with id: {}", unavailability.getId());
            }
        }

        // Save the rule - unavailabilities will be persisted automatically via cascade
        repository.save(entity);
        LOGGER.info("Saved judiciary availability rule with id: {} and {} unavailabilities", 
                entity.getId(), entity.getUnavailabilities().size());
    }

    public void deleteJudiciaryAvailabilityRule(final DeleteJudiciaryAvailabilityRuleRequest request) {
        LOGGER.info("Deleting judiciary availability rule: {}", request);

        final JudiciaryAvailabilityRule entity = repository.findBy(request.getRuleId());
        if (entity == null) {
            LOGGER.warn("Judiciary availability rule with id {} not found", request.getRuleId());
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
        final boolean withJudiciaries = Boolean.TRUE.equals(request.getWithJudiciaries());
        final boolean withSpecialisms = Boolean.TRUE.equals(request.getWithSpecialisms());

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
        if (withJudiciaries && requester != null) {
            // Extract unique judiciary IDs from rules
            final Set<String> judiciaryIds = rules.stream()
                    .map(JudiciaryAvailabilityRule::getJudiciaryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!judiciaryIds.isEmpty()) {
                final List<String> judiciaryIdList = new ArrayList<>(judiciaryIds);
                final List<Judiciary> fetchedJudiciaries = referenceDataService.getJudiciariesByIds(judiciaryIdList, requester);
                judiciaries.addAll(fetchedJudiciaries);
                LOGGER.info("Fetched {} judiciaries for {} unique IDs", fetchedJudiciaries.size(), judiciaryIds.size());
            }
        }
        
        response.setJudiciaries(judiciaries);

        // Always initialize specialisms list (empty if not requested)
        final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism> specialisms = new ArrayList<>();
        
        // Fetch specialisms if requested
        if (withSpecialisms && requester != null) {
            // Extract unique judiciary IDs from rules
            final Set<String> judiciaryIds = rules.stream()
                    .map(JudiciaryAvailabilityRule::getJudiciaryId)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            if (!judiciaryIds.isEmpty()) {
                final List<String> judiciaryIdList = new ArrayList<>(judiciaryIds);
                final List<uk.gov.moj.cpp.courtscheduler.domain.JudiciarySpecialism> fetchedSpecialisms = referenceDataService.getSpecialismsByJudiciaryIds(judiciaryIdList, requester);
                specialisms.addAll(fetchedSpecialisms);
                LOGGER.info("Fetched {} specialisms for {} unique IDs", fetchedSpecialisms.size(), judiciaryIds.size());
            }
        }
        
        response.setSpecialisms(specialisms);
        return response;
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
            return DayOfWeek.valueOf(dayName.name());
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

            if(unavailabilityDates.contains(current)){
                current = current.plusDays(1);
                continue;
            }
            final DayOfWeek dayOfWeek = current.getDayOfWeek();
            // Check if the day name matches (stored as "Monday", "Tuesday", etc.)
            final String dayName = dayOfWeek.name().substring(0, 1) + dayOfWeek.name().substring(1).toLowerCase();
            if (availableDays.contains(dayName)) {
                return true;
            }
            current = current.plusDays(1);
        }

        return false;
    }
}

