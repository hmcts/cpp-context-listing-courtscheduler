package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum.AM;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Venue;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleEnricherTest {
    private static final String VALUE_175 = "175";
    private static final String VALUE_17729 = "17729";
    private static final String DATE_2019_10_01 = "2019-10-01";
    private static final String VALUE_241546 = "241546";
    private static final String ADULT_2 = "ADULT";
    private static final String BAUOS05_2 = "BAUOS05";
    private static final String CS2129874_2 = "CS2129874";
    private static final String COURT_1_CHELTENHAM = "Court 1 Cheltenham";
    private static final String DVB_2 = "DVB";
    private static final String TBL_2 = "TBL";
    private static final String WEDAM_2 = "WEDAM";
    private static final String BUSINESS = "business";
    private static final String FALSE = "false";
    private static final String LOCATION_ID = "locationId";
    private static final String PANEL = "panel";
    private static final String SESSION = "session";
    private static final String SESSION_DATE = "sessionDate";
    private static final String VENUE_ID = "venueId";
    private static final String VENUE_NAME = "venueName";
    private static final String WELSH_SPEAKING = "welshSpeaking";


    @InjectMocks
    private CourtScheduleEnricher courtScheduleEnricher;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Test
    void shouldBuildNewCourtSchedule() {
        final CourtRoom courtRoom = createCourtRoom();

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", CS2129874_2);
        listingProfile.put(SESSION_DATE, DATE_2019_10_01);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(PANEL, ADULT_2);
        listingProfile.put(BUSINESS, DVB_2);
        listingProfile.put(VENUE_NAME, COURT_1_CHELTENHAM);
        listingProfile.put(VENUE_ID, VALUE_17729);
        listingProfile.put(LOCATION_ID, VALUE_175);
        listingProfile.put(WELSH_SPEAKING, FALSE);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), emptyList(), randomUUID().toString());
        assertThat(courtSchedule.getListingProfileId(), is(CS2129874_2));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2019, 10, 01)));
        assertThat(courtSchedule.getPanel(), is(ADULT_2));
        assertThat(courtSchedule.getBusinessType(), is(DVB_2));
        assertThat(courtSchedule.getCourtSession(), is("AM"));
        assertThat(courtSchedule.getOuCode(), is(courtRoom.getOucode()));
        assertThat(courtSchedule.getCourtHouseId(), is(courtRoom.getOucodeUUID()));
        assertThat(courtSchedule.getOperationalUnit(), is(courtRoom.getOucodeL2Code()));
        assertThat(courtSchedule.getCourtHouseName(), is(courtRoom.getOucodeL3Name()));
        assertThat(courtSchedule.getCourtRoomId(), is(courtRoom.getCourtroomId()));
        assertThat(courtSchedule.getCourtRoomNumber(), is(courtRoom.getCppCourtRoomId()));
        assertThat(courtSchedule.getCourtRoomName(), is(courtRoom.getCourtroomName()));
        assertThat(courtSchedule.getMaxSlots(), is(0));
        assertThat(courtSchedule.getAvailableSlots(), is(0));
        assertThat(courtSchedule.getMaxDuration(), is(0));
        assertThat(courtSchedule.getAvailableDuration(), is(0));
    }

    @Test
    void shouldBuildCourtScheduleForExistingCourtSchedule() {
        final String courtScheduleId = randomUUID().toString();
        final CourtRoom courtRoom = createCourtRoom();

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", CS2129874_2);
        listingProfile.put(SESSION_DATE, DATE_2019_10_01);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(PANEL, ADULT_2);
        listingProfile.put(BUSINESS, DVB_2);
        listingProfile.put(VENUE_NAME, COURT_1_CHELTENHAM);
        listingProfile.put(VENUE_ID, VALUE_17729);
        listingProfile.put(LOCATION_ID, VALUE_175);
        listingProfile.put(WELSH_SPEAKING, FALSE);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), List.of(new CourtSchedule()
                .courtScheduleId(courtScheduleId).ouCode(courtRoom.getOucode())
                .courtRoomId(courtRoom.getCourtroomId())
                .courtSession(AM.name())
                .sessionDate(LocalDate.of(2019, 10, 1))
                .businessType("DVB")
                .createdOn(uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime(Instant.now()))), randomUUID().toString());
        assertThat(courtSchedule.getCourtScheduleId(), is(courtScheduleId));
        assertThat(courtSchedule.getListingProfileId(), is(CS2129874_2));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2019, 10, 01)));
        assertThat(courtSchedule.getPanel(), is(ADULT_2));
        assertThat(courtSchedule.getBusinessType(), is(DVB_2));
        assertThat(courtSchedule.getCourtSession(), is("AM"));
        assertThat(courtSchedule.getOuCode(), is(courtRoom.getOucode()));
        assertThat(courtSchedule.getCourtHouseId(), is(courtRoom.getOucodeUUID()));
        assertThat(courtSchedule.getOperationalUnit(), is(courtRoom.getOucodeL2Code()));
        assertThat(courtSchedule.getCourtHouseName(), is(courtRoom.getOucodeL3Name()));
        assertThat(courtSchedule.getCourtRoomId(), is(courtRoom.getCourtroomId()));
        assertThat(courtSchedule.getCourtRoomNumber(), is(courtRoom.getCppCourtRoomId()));
        assertThat(courtSchedule.getCourtRoomName(), is(courtRoom.getCourtroomName()));
        assertThat(courtSchedule.getMaxSlots(), is(0));
        assertThat(courtSchedule.getAvailableSlots(), is(0));
        assertThat(courtSchedule.getMaxDuration(), is(0));
        assertThat(courtSchedule.getAvailableDuration(), is(0));
    }

    @Test
    void shouldBuildNewCourtScheduleWithCourtRoomDetailsNotPresentLogMessages() {
        final Map<String, String> listingProfile = new HashMap<>();
        final String businessType = DVB_2;
        listingProfile.put("id", CS2129874_2);
        listingProfile.put(SESSION_DATE, DATE_2019_10_01);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(PANEL, ADULT_2);
        listingProfile.put(BUSINESS, businessType);
        listingProfile.put(VENUE_NAME, COURT_1_CHELTENHAM);
        listingProfile.put(VENUE_ID, VALUE_17729);
        listingProfile.put(LOCATION_ID, VALUE_175);
        listingProfile.put(WELSH_SPEAKING, FALSE);

        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        final String executionId = randomUUID().toString();
        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), executionId);
        final String msgKey = "175 - Court 1 Cheltenham - 17729";
        final String actual = missingReferenceDataMappingMap.get(msgKey);
        assertThat(actual, is("REF_DATA_VENUE_NOT_FOUND"));
    }

    @Test
    void shouldNotPersistDuplicateRotaProcessLogsForSameMissingVenue() {
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", CS2129874_2);
        listingProfile.put(SESSION_DATE, DATE_2019_10_01);
        listingProfile.put(SESSION, "AM");
        listingProfile.put(PANEL, ADULT_2);
        listingProfile.put(BUSINESS, DVB_2);
        listingProfile.put(VENUE_NAME, COURT_1_CHELTENHAM);
        listingProfile.put(VENUE_ID, VALUE_17729);
        listingProfile.put(LOCATION_ID, VALUE_175);
        listingProfile.put(WELSH_SPEAKING, FALSE);

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(empty());

        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        final String executionId = randomUUID().toString();

        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), executionId);
        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), executionId);

        assertThat(missingReferenceDataMappingMap.size(), is(1));
    }

    @Test
    void shouldUseDefaultMorningTimesForAmSession() {
        final CourtRoom courtRoom = createCourtRoom();

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));

        final Map<String, String> listingProfile = listingProfile("AM");
        final LocalDate sessionDate = LocalDate.of(2019, 10, 1);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, new HashMap<>(), emptyList(), randomUUID().toString());

        assertThat(courtSchedule.getSessionStartTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "10:00"))));
        assertThat(courtSchedule.getSessionEndTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "13:00"))));
    }

    @Test
    void shouldUseDefaultAfternoonTimesForPmSession() {
        final CourtRoom courtRoom = createCourtRoom();

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));

        final Map<String, String> listingProfile = listingProfile("PM");
        final LocalDate sessionDate = LocalDate.of(2019, 10, 1);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, new HashMap<>(), emptyList(), randomUUID().toString());

        assertThat(courtSchedule.getSessionStartTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "14:00"))));
        assertThat(courtSchedule.getSessionEndTime(), is(DateUtils.toOffsetDateTime(DateUtils.combineDateAndTime(sessionDate, "17:00"))));
    }

    private Map<String, String> listingProfile(final String session) {
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", CS2129874_2);
        listingProfile.put(SESSION_DATE, DATE_2019_10_01);
        listingProfile.put(SESSION, session);
        listingProfile.put(PANEL, ADULT_2);
        listingProfile.put(BUSINESS, DVB_2);
        listingProfile.put(VENUE_NAME, COURT_1_CHELTENHAM);
        listingProfile.put(VENUE_ID, VALUE_17729);
        listingProfile.put(LOCATION_ID, VALUE_175);
        listingProfile.put(WELSH_SPEAKING, FALSE);
        return listingProfile;
    }

    private CourtRoom createCourtRoom() {
        final int rotaLocationId = 175;
        final String rotaVenueName = "Liverpool Street Court";
        final Integer rotaVenueId = 17_111;
        final int courtRoomNumber = 123;

        final String ouCode = BAUOS05_2;

        return new CourtRoom()
                .rotaLocationId(rotaLocationId)
                .rotaVenueName(rotaVenueName)
                .rotaVenueId(rotaVenueId)
                .courtroomId(randomUUID().toString())
                .cppCourtRoomId(courtRoomNumber)
                .oucode(ouCode)
                .oucodeL3Name("Liverpool Street Court")
                .oucodeL2Code("London")
                .courtroomName("Court room 1")
                .oucodeUUID(randomUUID().toString())
                ;
    }
}
