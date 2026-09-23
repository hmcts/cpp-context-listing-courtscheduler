package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.util.Collections.emptyList;
import static java.util.Optional.empty;
import static java.util.Optional.of;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum.AM;

import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.openapi.model.Venue;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private CourtSession courtSession;

    @Test
    void shouldBuildNewCourtSchedule() {
        final CourtRoom courtRoom = createCourtRoom();

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation().id("241546").courtRoomId(1234).oucode("BAUOS05").maxSlot(8).maxDurationMins(60).rotaBusinessTypeCode("TBL").courtSession("PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(courtRoomSessionAllocation));

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), emptyList(), randomUUID().toString());
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
    }

    @Test
    void shouldBuildCourtScheduleForExistingCourtSchedule() {
        final String courtScheduleId = randomUUID().toString();
        final CourtRoom courtRoom = createCourtRoom();

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation().id("241546").courtRoomId(1234).oucode(courtRoom.getOucode()).maxSlot(8).maxDurationMins(60).rotaBusinessTypeCode("TBL").courtSession("PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(courtRoomSessionAllocation));

        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), List.of(new CourtSchedule()
                .courtScheduleId(courtScheduleId).ouCode(courtRoom.getOucode())
                .courtRoomId(courtRoom.getCourtroomId())
                .courtSession(AM.name())
                .sessionDate(LocalDate.of(2019, 10, 1))
                .businessType("DVB")
                .createdOn(Calendar.getInstance().getTime().toInstant().atOffset(java.time.ZoneOffset.UTC))), randomUUID().toString());
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
    }

    @Test
    void shouldBuildNewCourtScheduleWithCourtRoomDetailsNotPresentLogMessages() {
        final Map<String, String> listingProfile = new HashMap<>();
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
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", "AM");
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(empty());

        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        final String executionId = randomUUID().toString();

        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), executionId);
        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), executionId);

        assertThat(missingReferenceDataMappingMap.size(), is(1));
    }

    @Test
    void shouldApplyRefdataSessionStartAndEndTimesForAmSession() {
        final CourtRoom courtRoom = createCourtRoom();

        // Refdata-supplied times override the hardcoded morning defaults (10:00 / 13:00)
        final CourtRoomSessionAllocation allocation = new CourtRoomSessionAllocation()
                .id("241546")
                .courtRoomId(1234)
                .oucode("BAUOS05")
                .maxSlot(8)
                .maxDurationMins(60)
                .rotaBusinessTypeCode("TBL")
                .courtSession("WEDAM")
                .sessionStartTime("09:30")
                .sessionEndTime("12:45")
                ;
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(allocation));

        final Map<String, String> listingProfile = listingProfile("AM");
        final LocalDate sessionDate = LocalDate.of(2019, 10, 1);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, new HashMap<>(), emptyList(), randomUUID().toString());

        assertThat(courtSchedule.getSessionStartTime(), is(DateUtils.combineDateAndTime(sessionDate, "09:30").toInstant().atOffset(java.time.ZoneOffset.UTC)));
        assertThat(courtSchedule.getSessionEndTime(), is(DateUtils.combineDateAndTime(sessionDate, "12:45").toInstant().atOffset(java.time.ZoneOffset.UTC)));
    }

    @Test
    void shouldFallBackToDefaultMorningTimesWhenAllocationHasNoTimes() {
        final CourtRoom courtRoom = createCourtRoom();

        // Allocation present but no start/end times configured -> defaults must apply (10:00 / 13:00 for AM)
        final CourtRoomSessionAllocation allocation = new CourtRoomSessionAllocation().id("241546").courtRoomId(1234).oucode("BAUOS05").maxSlot(8).maxDurationMins(60).rotaBusinessTypeCode("TBL").courtSession("WEDAM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(of(allocation));

        final Map<String, String> listingProfile = listingProfile("AM");
        final LocalDate sessionDate = LocalDate.of(2019, 10, 1);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, new HashMap<>(), emptyList(), randomUUID().toString());

        assertThat(courtSchedule.getSessionStartTime(), is(DateUtils.combineDateAndTime(sessionDate, "10:00").toInstant().atOffset(java.time.ZoneOffset.UTC)));
        assertThat(courtSchedule.getSessionEndTime(), is(DateUtils.combineDateAndTime(sessionDate, "13:00").toInstant().atOffset(java.time.ZoneOffset.UTC)));
    }

    @Test
    void shouldFallBackToDefaultAfternoonTimesWhenAllocationAbsent() {
        final CourtRoom courtRoom = createCourtRoom();

        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDPM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap())).thenReturn(of(courtRoom));
        // No CourtRoomSessionAllocation configured for this room/session
        when(referenceDataMapperService.findByOuCodeAndRoomIdAndListingSessionAndBusinessType(anyString(), anyInt(), anyString(), anyString()))
                .thenReturn(empty());

        final Map<String, String> listingProfile = listingProfile("PM");
        final LocalDate sessionDate = LocalDate.of(2019, 10, 1);

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, sessionDate, new HashMap<>(), emptyList(), randomUUID().toString());

        assertThat(courtSchedule.getSessionStartTime(), is(DateUtils.combineDateAndTime(sessionDate, "14:00").toInstant().atOffset(java.time.ZoneOffset.UTC)));
        assertThat(courtSchedule.getSessionEndTime(), is(DateUtils.combineDateAndTime(sessionDate, "17:00").toInstant().atOffset(java.time.ZoneOffset.UTC)));
    }

    private Map<String, String> listingProfile(final String session) {
        final Map<String, String> listingProfile = new HashMap<>();
        listingProfile.put("id", "CS2129874");
        listingProfile.put("sessionDate", "2019-10-01");
        listingProfile.put("session", session);
        listingProfile.put("panel", "ADULT");
        listingProfile.put("business", "DVB");
        listingProfile.put("venueName", "Court 1 Cheltenham");
        listingProfile.put("venueId", "17729");
        listingProfile.put("locationId", "175");
        listingProfile.put("welshSpeaking", "false");
        return listingProfile;
    }

    private CourtRoom createCourtRoom() {
        final int rotaLocationId = 175;
        final String rotaVenueName = "Liverpool Street Court";
        final Integer rotaVenueId = 17111;
        final int courtRoomNumber = 123;

        final String ouCode = "BAUOS05";

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
                .oucodeUUID(randomUUID().toString());
    }
}
