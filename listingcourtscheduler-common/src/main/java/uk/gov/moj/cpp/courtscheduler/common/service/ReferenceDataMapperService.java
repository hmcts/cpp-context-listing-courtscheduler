package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.ListUtils.synchronizedList;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static uk.gov.moj.cpp.courtscheduler.common.utils.VenueNameComparator.matches;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ReferenceDataMapperService {

    private static Logger logger = LoggerFactory.getLogger(ReferenceDataMapperService.class);

    @Inject
    private ReferenceDataCache referenceDataCache;

    private List<Judiciary> judiciaries = synchronizedList(new ArrayList<>());

    private List<CourtRoomSessionAllocation> courtRoomSessionAllocations = synchronizedList(new ArrayList<CourtRoomSessionAllocation>());

    private List<CourtRoom> courtRooms = synchronizedList(new ArrayList<>());

    private Map<String, BusinessType> businessTypeMap = new ConcurrentHashMap<>();

    private static final String COURT_DETAIL_NOT_FOUND = "COURT_DETAIL_NOT_FOUND";
    private static final String COURT_ROOM_FETCHED_BY_VENUE_NAME = "CourtRoom fetched by VenueName: %s%n,can't find by VenueId:%s%n";
    private static final String MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME = "Multiple courtrooms found by VenueName : %s%n , but VenueId: %s%n selected by created_on";

    public Optional<Judiciary> findByEmail(final String email) {
        this.judiciaries = isEmpty(judiciaries) ? referenceDataCache.getJudiciaries() : judiciaries;

        final Optional<Judiciary> judiciaryOptional = getJudiciaryByEmail(email);
        logger.debug("judiciary found for email {} with judiciary : {}", email, judiciaryOptional.orElse(null));
        return judiciaryOptional;
    }

    public Optional<Judiciary> findById(final String judiciaryId) {
        this.judiciaries = isEmpty(judiciaries) ? referenceDataCache.getJudiciaries() : judiciaries;
        if (isEmpty(judiciaries)) {
            return empty();
        }
        final Optional<Judiciary> judiciaryOptional = judiciaries.stream()
                .filter(judiciary -> equalsIgnoreCase(judiciaryId, judiciary.getId()))
                .findFirst();
        logger.debug("judiciary found for id {} with judiciary : {}", judiciaryId, judiciaryOptional.orElse(null));
        return judiciaryOptional;
    }

    public Optional<CourtRoomSessionAllocation> findByOuCodeAndRoomIdAndListingSessionAndBusinessType(final String ouCode,
                                                                                                      final Integer roomId,
                                                                                                      final String listingSession,
                                                                                                      final String businessType) {
        courtRoomSessionAllocations = isEmpty(courtRoomSessionAllocations) ? referenceDataCache.getCourtRoomSessionAllocations() : courtRoomSessionAllocations;
        return getAllocationByOuCodeAndRoomIAndSessionAndBusinessType(ouCode, roomId, listingSession, businessType);
    }

    public Optional<CourtRoom> findByVenue(final Venue venue, final Map<String, String> exceptionMessages) {
        courtRooms = isEmpty(courtRooms) ? referenceDataCache.getCourtRooms() : courtRooms;
        return findCourtRoomByVenue(venue, exceptionMessages);
    }

    public Map<UUID, CourtRoom> getCourtRoomsMap() {
        courtRooms = isEmpty(courtRooms) ? referenceDataCache.getCourtRooms() : courtRooms;
        return courtRooms.stream().filter(courtRoom -> nonNull(courtRoom.getCourtroomId()))
                .collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), Function.identity()));
    }

    public Map<String, BusinessType> getBusinessTypeMap() {
        if (businessTypeMap.isEmpty()) {
            loadBusinessTypeMap();
        }
        return businessTypeMap;
    }

    public void loadBusinessTypeMap() {
        businessTypeMap = referenceDataCache.getRotaBusinessTypes()
                .stream()
                .collect(Collectors.toMap(
                        BusinessType::getTypeCode,
                        Function.identity(),
                        (existing, replacement) -> {
                            logger.warn("Duplicate business type code found: {}. Keeping the first occurrence.", existing.getTypeCode());
                            return existing;
                        }
                ));
    }

    public void clearReferenceDataInMemory() {
        if (nonNull(courtRooms)) courtRooms = null;
        if (nonNull(courtRoomSessionAllocations)) courtRoomSessionAllocations = null;
        if (nonNull(judiciaries)) judiciaries = null;
        if (nonNull(businessTypeMap)) businessTypeMap.clear();
    }

    public void loadJudiciaries() {
        judiciaries = referenceDataCache.getJudiciaries();
    }

    public void loadCourtRoomSessionAllocations() {
        courtRoomSessionAllocations = referenceDataCache.getCourtRoomSessionAllocations();
    }

    public void loadCourtRooms() {
        courtRooms = referenceDataCache.getCourtRooms();
    }

    private Optional<CourtRoomSessionAllocation> getAllocationByOuCodeAndRoomIAndSessionAndBusinessType(final String ouCode, final Integer roomId, final String listingSession, final String businessType) {
        return courtRoomSessionAllocations
                .stream()
                .filter(courtRoomSessionAllocation -> ouCode.equals(courtRoomSessionAllocation.getOucode()) &&
                        roomId.equals(courtRoomSessionAllocation.getCourtRoomId()) &&
                        listingSession.equals(courtRoomSessionAllocation.getCourtSession()) &&
                        businessType.equals(courtRoomSessionAllocation.getRotaBusinessTypeCode()))
                .findAny();
    }

    private Optional<Judiciary> getJudiciaryByEmail(final String email) {
        return judiciaries
                .stream()
                .filter(judiciary -> equalsIgnoreCase(email, judiciary.getEmailAddress()))
                .findFirst();
    }

    private Optional<CourtRoom> findCourtRoomByVenue(final Venue venue, final Map<String, String> exceptionMessages) {
        final List<CourtRoom> courtRoomsByLocationAndVenueNameOrVenueId = courtRooms
                .stream()
                .filter(courtRoom -> courtRoom.getRotaLocationId().equals(venue.getLocationId())
                        && matches(courtRoom.getRotaVenueName(), venue.getVenueName()))
                .toList();

        final Optional<CourtRoom> courtRoomOptional = courtRoomsByLocationAndVenueNameOrVenueId.stream().filter(courtRoom -> courtRoom.getRotaVenueId().equals(venue.getVenueId())).findAny();
        if (courtRoomOptional.isPresent()) {
            return courtRoomOptional;
        } else {
            if (courtRoomsByLocationAndVenueNameOrVenueId.size() > 1) {
                exceptionMessages.put(format(MULTIPLE_COURTROOMS_FOUND_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            } else if (courtRoomsByLocationAndVenueNameOrVenueId.size() == 1) {
                exceptionMessages.put(format(COURT_ROOM_FETCHED_BY_VENUE_NAME, venue.getVenueName(), venue.getVenueId()), COURT_DETAIL_NOT_FOUND);
            }
        }

        return isEmpty(courtRoomsByLocationAndVenueNameOrVenueId) ? empty() : of(courtRoomsByLocationAndVenueNameOrVenueId.get(0));
    }
}
