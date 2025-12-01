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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.moj.cpp.courtscheduler.domain.SessionTimeEnum.AM;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.common.service.RotaProcessLogService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.Venue;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaProcessLog;

import java.time.LocalDate;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

    @Mock
    private Requester requester;

    @Mock
    private RotaProcessLogService rotaProcessLogService;

    @Test
    void shouldBuildNewCourtSchedule() {
        final CourtRoom courtRoom = createCourtRoom();

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation("241546", 1234, "BAUOS05", 8, 60, "TBL", "PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), any(Map.class), eq(requester))).thenReturn(of(courtRoom));
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

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), emptyList(), requester, randomUUID().toString());
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

        final CourtRoomSessionAllocation courtRoomSessionAllocation = new CourtRoomSessionAllocation("241546", 1234, courtRoom.getOucode(), 8, 60, "TBL", "PM");
        when(courtSession.getCourtSession(any(), anyString())).thenReturn("WEDAM");
        when(referenceDataMapperService.findByVenue(any(Venue.class), any(Map.class), eq(requester))).thenReturn(of(courtRoom));
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

        final CourtSchedule courtSchedule = courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), new HashMap<>(), List.of(CourtSchedule.CourtScheduleBuilder.courtSchedule()
                .withCourtScheduleId(courtScheduleId).withOuCode(courtRoom.getOucode())
                .withCourtRoomId(courtRoom.getCourtroomId())
                .withCourtSession(AM.name())
                .withSessionDate(LocalDate.of(2019, 10, 1))
                .withBusinessType("DVB")
                .withCreatedOn(Calendar.getInstance().getTime()).build()), requester, randomUUID().toString());
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

        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        final String executionId = randomUUID().toString();
        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), requester, executionId);
        final String msgKey = "No matching venue found by either venueId or venueName or LocationId: 175 - Court 1 Cheltenham - 17729";
        final String actual = missingReferenceDataMappingMap.get(msgKey);
        assertThat(actual, is("REF_DATA_VENUE_NOT_FOUND"));

        // verify persisted rota_process_log entry
        final ArgumentCaptor<RotaProcessLog> logCaptor = ArgumentCaptor.forClass(RotaProcessLog.class);
        verify(rotaProcessLogService).saveRotaProcessLog(logCaptor.capture());
        final RotaProcessLog saved = logCaptor.getValue();
        assertThat(saved.getExecutionId(), is(executionId));
        assertThat(saved.getErrorCode(), is("REF_DATA_VENUE_NOT_FOUND"));
        assertThat(saved.getErrorText(), is(msgKey));
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

        when(referenceDataMapperService.findByVenue(any(Venue.class), anyMap(), eq(requester))).thenReturn(empty());

        final Map<String, String> missingReferenceDataMappingMap = new HashMap<>();
        final String executionId = randomUUID().toString();

        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), requester, executionId);
        courtScheduleEnricher.build(listingProfile, LocalDate.of(2019, 10, 1), missingReferenceDataMappingMap, emptyList(), requester, executionId);

        verify(rotaProcessLogService, times(1)).saveRotaProcessLog(any(RotaProcessLog.class));
        assertThat(missingReferenceDataMappingMap.size(), is(1));
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
