package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.ROTA_PROCESSING_ERROR;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LINKED_SESSION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_ALL_DAY_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_ALL_DAY_START_TIME;
import static uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog.RotaProcessLogBuilder.rotaProcessLog;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RotaDataEnricher {

    private static final Logger logger = LoggerFactory.getLogger(RotaDataEnricher.class);

    private static final String EXCEPTION_MSG = "Exception while processing CourtListingProfile : %s";

    @Inject
    private MissingReferenceDataMappingLogger missingReferenceDataMappingLogger;

    @Inject
    private CourtScheduleEnricher courtScheduleEnricher;

    @Inject
    private RotaProcessLogService rotaProcessLogService;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    // Deliberate broad safety net: this loop enriches one CourtListingProfile record per
    // iteration; a single malformed/unexpected record must not abort enrichment of the rest of
    // the rota file, so the failure is logged and persisted to the rota process log and the loop
    // continues.
    @SuppressWarnings({"squid:S2221", "PMD.AvoidCatchingGenericException"})
    public Map<String, CourtSchedule> enrichCourtListings(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                          final LocalDate rotaPeriodEndDate,
                                                          final Map<String, Boolean> migratedMap,
                                                          final Boolean migrated,
                                                          final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                                          final String executionId,
                                                          final Map<String, String> missingReferenceDataMappingMap) {
        logger.info("enrichCourtListing - rotaPeriodEndDate: {}", rotaPeriodEndDate);
        final long enrichCourtListingStartTime = System.currentTimeMillis();
        final Map<String, Map<String, String>> courtListings = records.get(COURT_LISTING);
        final Map<String, CourtSchedule> courtSchedules = new HashMap<>();
        for (final Map<String, String> listingProfile : courtListings.values()) {
            try {
                final String linkedSessionId = listingProfile.get(LINKED_SESSION_ID);
                final String strSessionDate = listingProfile.get(SESSION_DATE);
                final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

                if (sessionDate.isBefore(rotaPeriodEndDate) || sessionDate.isEqual(rotaPeriodEndDate)) {
                    final CourtSchedule courtSchedule = courtSchedules.get(linkedSessionId);
                    buildCourtSchedule(listingProfile, courtSchedule, courtSchedules, migratedMap, migrated, missingReferenceDataMappingMap, activeCourtSchedulesByOuCodesWithinRotaPeriod, executionId);
                }
            } catch (final Exception ex) {
                logger.error(format(EXCEPTION_MSG, listingProfile.get("id")), ex);
                rotaProcessLogService.saveRotaProcessLog(
                        rotaProcessLog()
                                .withExecutionId(executionId)
                                .withErrorCode(ROTA_PROCESSING_ERROR.code())
                                .withErrorText(ROTA_PROCESSING_ERROR.template().formatted(ex.getMessage()))
                                .build()
                );
            }
        }
        final long enrichCourtListingEndTime = System.currentTimeMillis();
        logger.info("Time taken to enrich court listings: {} ms", enrichCourtListingEndTime - enrichCourtListingStartTime);
        return courtSchedules;
    }

    private void buildCourtSchedule(final Map<String, String> listingProfile,
                                    final CourtSchedule courtSchedule,
                                    final Map<String, CourtSchedule> courtSchedules,
                                    final Map<String, Boolean> migratedMap,
                                    final Boolean migrated,
                                    final Map<String, String> missingReferenceDataMappingMap,
                                    final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                    final String executionId) {
        final String businessType = listingProfile.get(BUSINESS_TYPE);
        final String strSessionDate = listingProfile.get(SESSION_DATE);
        final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

        final CourtSchedule newCourtSchedule;
        if (isNull(courtSchedule) || !businessType.equals(courtSchedule.getBusinessType())) {
            newCourtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, missingReferenceDataMappingMap, activeCourtSchedulesByOuCodesWithinRotaPeriod, executionId);
            if (migrated.equals(migratedMap.get(newCourtSchedule.getOuCode()))) {
                addCourtSchedule(courtSchedules, newCourtSchedule);
            }
        } else {
            newCourtSchedule = updateExistingCourtSchedule(courtSchedule, activeCourtSchedulesByOuCodesWithinRotaPeriod);
            courtSchedules.put(courtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }

    private void addCourtSchedule(final Map<String, CourtSchedule> courtSchedules, final CourtSchedule newCourtSchedule) {
        if (nonNull(newCourtSchedule.getCourtScheduleId())) {
            courtSchedules.put(newCourtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }


    private CourtSchedule updateExistingCourtSchedule(final CourtSchedule courtSchedule,
                                                      final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinDateRange) {
        final CourtSchedule courtScheduleBuilder = copyOf(courtSchedule);

        courtScheduleBuilder.courtSession(ALL_DAY)
                .sessionStartTime(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME)))
                .sessionEndTime(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_END_TIME)));

        final Optional<CourtSchedule> courtScheduleOptional = activeCourtSchedulesByOuCodesWithinDateRange.stream()
                .filter(activeCourtSchedule -> activeCourtSchedule.getCourtRoomId().equals(courtSchedule.getCourtRoomId())
                        && activeCourtSchedule.getSessionDate().equals(courtSchedule.getSessionDate())
                        && activeCourtSchedule.getBusinessType().equals(courtSchedule.getBusinessType())
                        && ALL_DAY.equals(activeCourtSchedule.getCourtSession()))
                .findAny();
        if(courtScheduleOptional.isPresent() && isNotEmpty(courtScheduleOptional.get().getCourtScheduleId())) {
            courtScheduleBuilder.courtScheduleId(courtScheduleOptional.get().getCourtScheduleId());
            courtScheduleBuilder.createdOn(courtScheduleOptional.get().getCreatedOn());
        } else {
            courtScheduleBuilder.courtScheduleId(randomUUID().toString());
        }

        return courtScheduleBuilder;
    }

    /**
     * Defensive copy: generated OpenAPI models have no copy-constructor, only no-arg + fluent
     * setters, so this replaces the old hand-written CourtScheduleBuilder#withCourtSchedule
     * bulk-copy.
     */
    private static CourtSchedule copyOf(final CourtSchedule source) {
        return new CourtSchedule()
                .courtScheduleId(source.getCourtScheduleId())
                .sessionDate(source.getSessionDate())
                .ouCode(source.getOuCode())
                .courtHouseName(source.getCourtHouseName())
                .courtHouseId(source.getCourtHouseId())
                .courtRoomId(source.getCourtRoomId())
                .courtRoomNumber(source.getCourtRoomNumber())
                .courtRoomName(source.getCourtRoomName())
                .businessType(source.getBusinessType())
                .courtSession(source.getCourtSession())
                .slotBased(source.getSlotBased())
                .maxSlots(source.getMaxSlots())
                .maxDuration(source.getMaxDuration())
                .listingProfileId(source.getListingProfileId())
                .operationalUnit(source.getOperationalUnit())
                .panel(source.getPanel())
                .availableDuration(source.getAvailableDuration())
                .availableSlots(source.getAvailableSlots())
                .judiciaries(source.getJudiciaries())
                .slotStartTimes(source.getSlotStartTimes())
                .active(source.getActive())
                .createdOn(source.getCreatedOn())
                .updatedOn(source.getUpdatedOn())
                .allDaySplit(source.getAllDaySplit())
                .maxDurationForMorning(source.getMaxDurationForMorning())
                .maxDurationForAfternoon(source.getMaxDurationForAfternoon())
                .totalBooked(source.getTotalBooked())
                .sessionStartTime(source.getSessionStartTime())
                .sessionEndTime(source.getSessionEndTime())
                .totalBookedForMorning(source.getTotalBookedForMorning())
                .totalBookedForAfternoon(source.getTotalBookedForAfternoon())
                .availableDurationForMorning(source.getAvailableDurationForMorning())
                .availableDurationForAfternoon(source.getAvailableDurationForAfternoon())
                .overbookingAllowed(source.getOverbookingAllowed())
                .nationalBreakTime(source.getNationalBreakTime())
                .draft(source.getDraft())
                .minHearingTime(source.getMinHearingTime())
                .maxHearingTime(source.getMaxHearingTime())
                .jurisdiction(source.getJurisdiction());
    }
}
