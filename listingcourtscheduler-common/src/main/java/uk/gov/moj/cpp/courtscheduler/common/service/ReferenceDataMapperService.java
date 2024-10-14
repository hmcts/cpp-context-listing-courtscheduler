package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.lang.String.format;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static org.apache.commons.collections.CollectionUtils.isEmpty;
import static org.apache.commons.collections.ListUtils.synchronizedList;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;

import uk.gov.justice.services.core.requester.Requester;
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
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

    public Optional<Judiciary> findByEmail(final Requester requester, final String email) {
        this.judiciaries = isEmpty(judiciaries) ? referenceDataCache.getJudiciaries(requester) : judiciaries;

        final Optional<Judiciary> judiciaryOptional = getJudiciaryByEmail(email);
        logger.debug("judiciary found for email {} with judiciary : {}", email, judiciaryOptional.orElse(null));
        return judiciaryOptional;
    }

    public Optional<CourtRoomSessionAllocation> findByOuCodeAndRoomIdAndListingSessionAndBusinessType(final Requester requester,
                                                                                                      final String ouCode,
                                                                                                      final Integer roomId,
                                                                                                      final String listingSession,
                                                                                                      final String businessType) {
        courtRoomSessionAllocations = isEmpty(courtRoomSessionAllocations) ? referenceDataCache.getCourtRoomSessionAllocations(requester) : courtRoomSessionAllocations;
        return getAllocationByOuCodeAndRoomIAndSessionAndBusinessType(ouCode, roomId, listingSession, businessType);
    }

    public Optional<CourtRoom> findByVenue(final Venue venue, final Map<String, String> exceptionMessages, final Requester requester) {
        courtRooms = isEmpty(courtRooms) ? referenceDataCache.getCourtRooms(requester) : courtRooms;
        return findCourtRoomByVenue(venue, exceptionMessages);
    }

    public Map<UUID, CourtRoom> getCourtRoomsMap(final Requester requester) {
        courtRooms = isEmpty(courtRooms) ? referenceDataCache.getCourtRooms(requester) : courtRooms;
        return courtRooms.stream().filter(courtRoom -> nonNull(courtRoom.getCourtroomId()))
                .collect(Collectors.toMap(courtRoom -> UUID.fromString(courtRoom.getCourtroomId()), Function.identity()));
    }

    public Map<String, BusinessType> getBusinessTypeMap(final Requester requester) {
        if (businessTypeMap.isEmpty()) {
            loadBusinessTypeMap(requester);
        }
        return businessTypeMap;
    }

    public void loadBusinessTypeMap(final Requester requester) {
        businessTypeMap = referenceDataCache.getRotaBusinessTypes(requester)
                .stream()
                .collect(Collectors.toMap(BusinessType::getTypeCode, Function.identity()));
    }

    public void clearReferenceDataInMemory() {
        if (nonNull(courtRooms)) courtRooms = null;
        if (nonNull(courtRoomSessionAllocations)) courtRoomSessionAllocations = null;
        if (nonNull(judiciaries)) judiciaries = null;
        if (nonNull(businessTypeMap)) businessTypeMap.clear();
    }

    public void loadJudiciaries(final Requester requester) {
        judiciaries = referenceDataCache.getJudiciaries(requester);
    }

    public void loadCourtRoomSessionAllocations(final Requester requester) {
        courtRoomSessionAllocations = referenceDataCache.getCourtRoomSessionAllocations(requester);
    }

    public void loadCourtRooms(final Requester requester) {
        courtRooms = referenceDataCache.getCourtRooms(requester);
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
                        && (equalsIgnoreCase(courtRoom.getRotaVenueName(), venue.getVenueName()) || courtRoom.getRotaVenueId().equals(venue.getVenueId())))
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
