package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.SESSION_ALLOCATION_ERR_MSG;
import static uk.gov.moj.cpp.platform.test.utils.reflection.ReflectionUtil.setField;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.api.service.SessionsService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleMatcherInfo;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleEnricherTest {

    @InjectMocks
    private CourtScheduleEnricher courtScheduleEnricher;

    @Mock
    private ReferenceDataMapperService referenceDataMapperService;

    @Mock
    private SessionsService sessionsService;

    @Mock
    private CourtSession courtSession;

    @Mock
    private Requester requester;

    private Map<String, String> missingReferenceDataMappingMap;

    @BeforeEach
    public void setUp() {
        missingReferenceDataMappingMap = new HashMap();
        setField(courtScheduleEnricher, "missingReferenceDataMappingMap", missingReferenceDataMappingMap);
    }

    @Test
    void shouldBuildNewCourtSchedule() {
        final CourtRoom courtRoom = createCourtRoom();

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation("241546", 1234, "BAUOS05", 8, 60, "TBL", "PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), any(Map.class), eq(requester))).thenReturn(of(courtRoom));
        when(sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(anyString(), any(LocalDate.class), anyString(), anyString())).thenReturn(null);
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(eq(requester), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(courtRoomSessionAllocation));

        final Map<String, String> listingProfile = new HashMap();
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), requester);
        assertThat(courtSchedule.getListingProfileId(), is("CS2129874"));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2019, 10, 01)));
        assertThat(courtSchedule.getPanel(), is("ADULT"));
        assertThat(courtSchedule.getBusinessType(), is("DVB"));
        assertThat(courtSchedule.getCourtSession(), is("AM"));
        assertThat(courtSchedule.getOuCode(), is(courtRoom.getOucode()));
        assertThat(courtSchedule.getCourtHouseId(), is(courtRoom.getOucodeUUID()));
        assertThat(courtSchedule.getOperationalUnit(), is(courtRoom.getOucodeL2Code()));
        assertThat(courtSchedule.getCourtHouseName(), is(courtRoom.getOucodeL3Name()));
        assertThat(courtSchedule.getCourtRoomId(), is(courtRoom.getCourtroomId()));
        assertThat(courtSchedule.getCourtRoomNumber(), is(courtRoom.getCppCourtRoomId()));
        assertThat(courtSchedule.getCourtRoomName(), is(courtRoom.getCourtroomName()));
        assertThat(courtSchedule.getMaxSlots(), is(courtRoomSessionAllocation.getMaxSlot()));
        assertThat(courtSchedule.getAvailableSlots(), is(courtRoomSessionAllocation.getMaxSlot()));
        assertThat(courtSchedule.getMaxDuration(), is(courtRoomSessionAllocation.getMaxDurationMins()));
        assertThat(courtSchedule.getAvailableDuration(), is(courtRoomSessionAllocation.getMaxDurationMins()));
        assertThat(missingReferenceDataMappingMap.size(), is(0));
    }

    @Test
    void shouldBuildCourtScheduleForExistingCourtSchedule() {
        final String courtScheduleId = randomUUID().toString();
        final CourtRoom courtRoom = createCourtRoom();

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation("241546", 1234, "BAUOS05", 8, 60, "TBL", "PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), any(Map.class), eq(requester))).thenReturn(of(courtRoom));
        when(sessionsService.findByCourtRoomIdAndSessionDateAndBusinessTypeAndCourtSession(anyString(), any(LocalDate.class), anyString(), anyString())).thenReturn(new CourtScheduleMatcherInfo(courtScheduleId, Calendar.getInstance().getTime()));
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(eq(requester), anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(courtRoomSessionAllocation));

        final Map<String, String> listingProfile = new HashMap();
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), requester);
        assertThat(courtSchedule.getCourtScheduleId(), is(courtScheduleId));
        assertThat(courtSchedule.getListingProfileId(), is("CS2129874"));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2019, 10, 01)));
        assertThat(courtSchedule.getPanel(), is("ADULT"));
        assertThat(courtSchedule.getBusinessType(), is("DVB"));
        assertThat(courtSchedule.getCourtSession(), is("AM"));
        assertThat(courtSchedule.getOuCode(), is(courtRoom.getOucode()));
        assertThat(courtSchedule.getCourtHouseId(), is(courtRoom.getOucodeUUID()));
        assertThat(courtSchedule.getOperationalUnit(), is(courtRoom.getOucodeL2Code()));
        assertThat(courtSchedule.getCourtHouseName(), is(courtRoom.getOucodeL3Name()));
        assertThat(courtSchedule.getCourtRoomId(), is(courtRoom.getCourtroomId()));
        assertThat(courtSchedule.getCourtRoomNumber(), is(courtRoom.getCppCourtRoomId()));
        assertThat(courtSchedule.getCourtRoomName(), is(courtRoom.getCourtroomName()));
        assertThat(courtSchedule.getMaxSlots(), is(courtRoomSessionAllocation.getMaxSlot()));
        assertThat(courtSchedule.getAvailableSlots(), is(courtRoomSessionAllocation.getMaxSlot()));
        assertThat(courtSchedule.getMaxDuration(), is(courtRoomSessionAllocation.getMaxDurationMins()));
        assertThat(courtSchedule.getAvailableDuration(), is(courtRoomSessionAllocation.getMaxDurationMins()));
        assertThat(missingReferenceDataMappingMap.size(), is(0));
    }

    @Test
    void shouldBuildNewCourtScheduleWithCourtRoomDetailsNotPresentLogMessages() {
        final Map<String, String> listingProfile = new HashMap();
        final String businessType = "DVB";
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", businessType);
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), requester);
        final String msgKey = " Location id : 175\n Venue name  : Court 1 Cheltenham\n Venue id  : 17729";
        final String actual = missingReferenceDataMappingMap.get(msgKey);
        assertThat(actual, is("COURT_DETAIL_NOT_FOUND"));
    }

    @Test
    void shouldBuildNewCourtScheduleWithSessionAllocationDetailsNotPresentLogMessages() {
        final String businessType = "DVB";
        final CourtRoom courtRoom = createCourtRoom();
        when(referenceDataMapperService.findByVenue(any(Venue.class), any(HashMap.class), eq(requester))).thenReturn(of(courtRoom));

        final Map<String, String> listingProfile = new HashMap();
        listingProfile.put("id", "CS2129874");
        final String sessionDate = "2019-10-01";
        final LocalDate sessionDateLD = LocalDate.of(2019, 10, 01);

        listingProfile.put("sessionDate", sessionDate);
        final String SESSION_AM = "AM";
        listingProfile.put("session", SESSION_AM);
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDateLD, requester);
        assertThat(courtSchedule.getListingProfileId(), is("CS2129874"));
        assertThat(courtSchedule.getSessionDate(), is(LocalDate.of(2019, 10, 01)));
        assertThat(courtSchedule.getPanel(), is("ADULT"));
        assertThat(courtSchedule.getBusinessType(), is("DVB"));
        assertThat(courtSchedule.getCourtSession(), is(SESSION_AM));
        assertThat(courtSchedule.getOuCode(), is(courtRoom.getOucode()));
        assertThat(courtSchedule.getCourtHouseId(), is(courtRoom.getOucodeUUID()));
        assertThat(courtSchedule.getOperationalUnit(), is(courtRoom.getOucodeL2Code()));
        assertThat(courtSchedule.getCourtHouseName(), is(courtRoom.getOucodeL3Name()));
        assertThat(courtSchedule.getCourtRoomId(), is(courtRoom.getCourtroomId()));
        assertThat(courtSchedule.getCourtRoomNumber(), is(courtRoom.getCppCourtRoomId()));
        assertThat(courtSchedule.getCourtRoomName(), is(courtRoom.getCourtroomName()));

        final String msgKey = format(SESSION_ALLOCATION_ERR_MSG,
                courtRoom.getOucode(),
                courtRoom.getCppCourtRoomId(),
                businessType,
                courtSession.getCourtSession(sessionDateLD, SESSION_AM));
        assertThat(missingReferenceDataMappingMap.get(msgKey), is("SESSION_ALLOCATION_NOT_FOUND"));
    }

    private CourtRoom createCourtRoom() {
        final int rotaLocationId = 175;
        final String rotaVenueName = "Liverpool Street Court";
        final Integer rotaVenueId = 17111;
        final int courtRoomNumber = 123;

        final String ouCode = "BAUOS05";

        return CourtRoom.CourtRoomBuilder.aCourtRoom()
                .withRotaLocationId(rotaLocationId)
                .withRotaVenueName(rotaVenueName)
                .withRotaVenueId(rotaVenueId)
                .withCourtRoomId(randomUUID().toString())
                .withCppCourtRoomId(courtRoomNumber)
                .withOucode(ouCode)
                .withOucodeL3Name("Liverpool Street Court")
                .withOucodeL2Code("London")
                .withCourtRoomName("Court room 1")
                .withOucodeUUID(randomUUID().toString())
                .build();
    }
}
