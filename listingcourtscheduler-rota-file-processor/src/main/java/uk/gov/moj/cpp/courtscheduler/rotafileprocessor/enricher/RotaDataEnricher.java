package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;
import static java.util.UUID.randomUUID;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder.courtSchedule;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.LINKED_SESSION_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_ALL_DAY_END_TIME;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.DEFAULT_ALL_DAY_START_TIME;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class RotaDataEnricher {

    private static final Logger logger = LoggerFactory.getLogger(RotaDataEnricher.class);

    private static final String EXCEPTION_MSG = "Exception while processing CourtListingProfile : %s";

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    @Inject
    private MissingReferenceDataMappingLogger missingReferenceDataMappingLogger;

    @Inject
    private CourtSession courtSession;

    @Inject
    private CourtScheduleEnricher courtScheduleEnricher;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @SuppressWarnings("squid:S2221")
    public Map<String, CourtSchedule> enrichCourtListings(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                          final LocalDate rotaPeriodEndDate,
                                                          final Map<String, Boolean> migratedMap,
                                                          final Boolean migrated,
                                                          final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                                          final Requester requester) {
        logger.info("enrichCourtListing - rotaPeriodEndDate: {}", rotaPeriodEndDate);
        long enrichCourtListingStartTime = System.currentTimeMillis();
        final Map<String, Map<String, String>> courtListings = records.get(COURT_LISTING);
        final Map<String, CourtSchedule> courtSchedules = new HashMap<>();
        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        for (final Map<String, String> listingProfile : courtListings.values()) {
            try {
                final String linkedSessionId = listingProfile.get(LINKED_SESSION_ID);
                final String strSessionDate = listingProfile.get(SESSION_DATE);
                final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

                if (sessionDate.isBefore(rotaPeriodEndDate) || sessionDate.isEqual(rotaPeriodEndDate)) {
                    final CourtSchedule courtSchedule = courtSchedules.get(linkedSessionId);
                    buildCourtSchedule(listingProfile, courtSchedule, courtSchedules, migratedMap, migrated, missingReferenceDataMappingMap, activeCourtSchedulesByOuCodesWithinRotaPeriod, requester);
                }
            } catch (final Exception ex) {
                logger.error(format(EXCEPTION_MSG, listingProfile.get("id")), ex);
            }
        }
        final long enrichCourtListingEndTime = System.currentTimeMillis();
        logger.info("Time taken to enrich court listings: {} ms", enrichCourtListingEndTime - enrichCourtListingStartTime);
        if (!missingReferenceDataMappingMap.isEmpty()) {
            missingReferenceDataMappingLogger.logCourtDetailsMessage(missingReferenceDataMappingMap);
        }
        return courtSchedules;
    }

    private void buildCourtSchedule(final Map<String, String> listingProfile,
                                    final CourtSchedule courtSchedule,
                                    final Map<String, CourtSchedule> courtSchedules,
                                    final Map<String, Boolean> migratedMap,
                                    final Boolean migrated,
                                    final Map<String, String> missingReferenceDataMappingMap,
                                    final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinRotaPeriod,
                                    final Requester requester) {
        final String businessType = listingProfile.get(BUSINESS_TYPE);
        final String strSessionDate = listingProfile.get(SESSION_DATE);
        final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

        CourtSchedule newCourtSchedule;
        if (isNull(courtSchedule) || !businessType.equals(courtSchedule.getBusinessType())) {
            newCourtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, missingReferenceDataMappingMap, activeCourtSchedulesByOuCodesWithinRotaPeriod, requester);
            if (migrated.equals(migratedMap.get(newCourtSchedule.getOuCode()))) {
                addCourtSchedule(courtSchedules, newCourtSchedule);
            }
        } else {
            newCourtSchedule = updateExistingCourtSchedule(courtSchedule, listingProfile.get(SESSION), activeCourtSchedulesByOuCodesWithinRotaPeriod, requester);
            courtSchedules.put(courtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }

    private void addCourtSchedule(Map<String, CourtSchedule> courtSchedules, CourtSchedule newCourtSchedule) {
        if (nonNull(newCourtSchedule.getCourtScheduleId())) {
            courtSchedules.put(newCourtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }


    private CourtSchedule updateExistingCourtSchedule(final CourtSchedule courtSchedule,
                                                      final String sessionStr,
                                                      final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinDateRange,
                                                      final Requester requester) {

        final String listingSession = courtSession.getCourtSession(courtSchedule.getSessionDate(), sessionStr);
        final Optional<CourtRoomSessionAllocation> sessionAllocation  = referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(requester, courtSchedule.getOuCode(), courtSchedule.getCourtRoomNumber(), listingSession, courtSchedule.getBusinessType());
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = courtSchedule().withCourtSchedule(courtSchedule);
        courtScheduleBuilder.withCourtSession(ALL_DAY)
                .withSessionStartTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_START_TIME))
                .withSessionEndTime(DateUtils.combineDateAndTime(courtSchedule.getSessionDate(), DEFAULT_ALL_DAY_END_TIME));

        final Optional<CourtSchedule> courtScheduleOptional = activeCourtSchedulesByOuCodesWithinDateRange.stream()
                .filter(activeCourtSchedule -> activeCourtSchedule.getCourtRoomId().equals(courtSchedule.getCourtRoomId())
                        && activeCourtSchedule.getSessionDate().equals(courtSchedule.getSessionDate())
                        && activeCourtSchedule.getBusinessType().equals(courtSchedule.getBusinessType())
                        && activeCourtSchedule.getCourtSession().equals(ALL_DAY))
                .findAny();
        if(courtScheduleOptional.isPresent() && isNotEmpty(courtScheduleOptional.get().getCourtScheduleId())) {
            courtScheduleBuilder.withCourtScheduleId(courtScheduleOptional.get().getCourtScheduleId());
            courtScheduleBuilder.withCreatedOn(courtScheduleOptional.get().getCreatedOn());
        } else {
            courtScheduleBuilder.withCourtScheduleId(randomUUID().toString());
        }

        if (sessionAllocation.isPresent()) {
            final CourtRoomSessionAllocation allocation = sessionAllocation.get();

            final int allocationMaxSlot = defaultIfNull(allocation.getMaxSlot(), 0);
            courtScheduleBuilder.withMaxSlots(defaultIfNull(courtSchedule.getMaxSlots(), 0) + allocationMaxSlot);
            courtScheduleBuilder.withAvailableSlots(defaultIfNull(courtSchedule.getAvailableSlots(), 0) + allocationMaxSlot);

            final int allocationMaxDurationMins = defaultIfNull(allocation.getMaxDurationMins(), 0);
            courtScheduleBuilder.withMaxDuration(defaultIfNull(courtSchedule.getMaxDuration(), 0) + allocationMaxDurationMins);
            courtScheduleBuilder.withAvailableDuration(defaultIfNull(courtSchedule.getAvailableDuration(), 0) + allocationMaxDurationMins);

            logger.info(format(SESSION_ALLOCATION_MAX_SLOT_UPDATE_MSG, allocation.getOucode(), allocation.getCourtRoomId(),courtScheduleBuilder.getSessionDate(), allocation.getCourtSession(),
                    allocation.getRotaBusinessTypeCode(), courtScheduleBuilder.getMaxSlots(), courtScheduleBuilder.getMaxDuration()));
        }
        return courtScheduleBuilder.build();
    }
}
