package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.lang3.ObjectUtils.defaultIfNull;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.SESSION_ALLOCATION_ERR_MSG;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.SESSION_ALLOCATION_NOT_FOUND;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.ALL_DAY;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.BUSINESS_TYPE;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.LINKED_SESSION_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.SESSION;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.SESSION_DATE;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule.CourtScheduleBuilder.courtSchedule;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.repository.CourtRoomRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtRoomSessionAllocationRepository;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
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
    private CourtRoomRepository courtRoomRepository;

    @Inject
    private SessionsService sessionsService;

    @Inject
    private CourtRoomSessionAllocationRepository courtRoomSessionAllocationRepository;

    @Inject
    private MissingReferenceDataMappingLogger missingReferenceDataMappingLogger;

    @Inject
    private CourtSession courtSession;

    @Inject
    private CourtScheduleEnricher courtScheduleEnricher;

    private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    @SuppressWarnings("squid:S2221")
    public Map<String, CourtSchedule> enrichCourtListings(final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                          final LocalDate masterRotaFileCutOffDate,
                                                          final Requester requester) {
        final Map<String, Map<String, String>> courtListings = records.get(COURT_LISTING);
        final Map<String, CourtSchedule> courtSchedules = new HashMap<>();
        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        for (final Map<String, String> listingProfile : courtListings.values()) {
            try {
                final String linkedSessionId = listingProfile.get(LINKED_SESSION_ID);
                final String strSessionDate = listingProfile.get(SESSION_DATE);
                final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

                if (sessionDate.isBefore(masterRotaFileCutOffDate)) {
                    final CourtSchedule courtSchedule = courtSchedules.get(linkedSessionId);
                    buildCourtSchedule(listingProfile, courtSchedule, courtSchedules, missingReferenceDataMappingMap, requester);
                }
            } catch (Exception ex) {
                logger.error(format(EXCEPTION_MSG, listingProfile.get("id")), ex);
            }
        }
        if (!missingReferenceDataMappingMap.isEmpty()) {
            missingReferenceDataMappingLogger.logMissingMessage(missingReferenceDataMappingMap);
        }
        return courtSchedules;
    }

    private void buildCourtSchedule(final Map<String, String> listingProfile,
                                    final CourtSchedule courtSchedule,
                                    final Map<String, CourtSchedule> courtSchedules,
                                    final Map<String, String> missingReferenceDataMappingMap,
                                    final Requester requester) {
        final String businessType = listingProfile.get(BUSINESS_TYPE);
        final String strSessionDate = listingProfile.get(SESSION_DATE);
        final LocalDate sessionDate = LocalDate.parse(strSessionDate, formatter);

        CourtSchedule newCourtSchedule;
        if (courtSchedule == null || !businessType.equals(courtSchedule.getBusinessType())) {
            newCourtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, requester);
            addCourtSchedule(courtSchedules, newCourtSchedule);
        } else {
            newCourtSchedule = updateExistingCourtSchedule(courtSchedule, listingProfile.get(SESSION), missingReferenceDataMappingMap);
            courtSchedules.put(courtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }

    private void addCourtSchedule(Map<String, CourtSchedule> courtSchedules, CourtSchedule newCourtSchedule) {
        if (newCourtSchedule.getCourtScheduleId() != null) {
            courtSchedules.put(newCourtSchedule.getListingProfileId(), newCourtSchedule);
        }
    }


    private CourtSchedule updateExistingCourtSchedule(final CourtSchedule courtSchedule,
                                                      final String sessionStr,
                                                      final Map<String, String> missingReferenceDataMappingMap) {

        final String listingSession = courtSession.getCourtSession(courtSchedule.getSessionDate(), sessionStr);
        final CourtRoomSessionAllocation courtRoomSessionAllocation = courtRoomSessionAllocationRepository.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(courtSchedule.getOuCode(), courtSchedule.getCourtRoomNumber(), listingSession, courtSchedule.getBusinessType());
        final Optional<CourtRoomSessionAllocation> sessionAllocation = nonNull(courtRoomSessionAllocation) ? of(courtRoomSessionAllocation) : empty();
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = courtSchedule().withCourtSchedule(courtSchedule);
        courtScheduleBuilder.withCourtSession(ALL_DAY);
        final String courtScheduleId = sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(courtSchedule.getCourtRoomId(), courtSchedule.getSessionDate(), courtSchedule.getBusinessType(), ALL_DAY);
        courtScheduleBuilder.withCourtScheduleId(courtScheduleId);

        if (sessionAllocation.isPresent()) {
            final CourtRoomSessionAllocation allocation = sessionAllocation.get();

            final int allocationMaxSlot = defaultIfNull(allocation.getMaxSlot(), 0);
            courtScheduleBuilder.withMaxSlots(defaultIfNull(courtSchedule.getMaxSlots(), 0) + allocationMaxSlot);
            courtScheduleBuilder.withAvailableSlots(defaultIfNull(courtSchedule.getAvailableSlots(), 0) + allocationMaxSlot);

            final int allocationMaxDurationMins = defaultIfNull(allocation.getMaxDurationMins(), 0);
            courtScheduleBuilder.withMaxDuration(defaultIfNull(courtSchedule.getMaxDuration(), 0) + allocationMaxDurationMins);
            courtScheduleBuilder.withAvailableDuration(defaultIfNull(courtSchedule.getAvailableDuration(), 0) + allocationMaxDurationMins);

        } else {
            if (nonNull(courtSchedule.getOuCode())) {
                missingReferenceDataMappingMap.putIfAbsent(format(SESSION_ALLOCATION_ERR_MSG,
                        courtSchedule.getOuCode(), courtSchedule.getCourtRoomId(), courtSchedule.getBusinessType(),
                        courtSession.getCourtSession(courtSchedule.getSessionDate(), sessionStr)), SESSION_ALLOCATION_NOT_FOUND);
            }
        }
        return courtScheduleBuilder.build();
    }

    public CourtRoomSessionAllocationRepository courtRoomSessionAllocationRepository(){
       return courtRoomSessionAllocationRepository;
    }
}
