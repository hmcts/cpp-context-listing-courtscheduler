package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.util.UUID.randomUUID;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.empty;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtRoom;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReferenceDataCacheCourtCentreRoomsTest {

    private static final String COURT_CENTRE_ID = randomUUID().toString();
    private static final String OTHER_COURT_CENTRE_ID = randomUUID().toString();
    private static final String ROOM_A = randomUUID().toString();
    private static final String ROOM_B = randomUUID().toString();

    @Mock
    private ReferenceDataService referenceDataService;
    @InjectMocks
    private ReferenceDataCache referenceDataCache;

    @Test
    void shouldReturnOnlyTheCourtCentresRoomsInReferenceDataOrder() {
        when(referenceDataService.getCpCourtRooms()).thenReturn(allCourtRooms());

        final List<CourtRoom> rooms = referenceDataCache.getCpCourtRoomsByCourtCentreId(COURT_CENTRE_ID);

        assertThat(rooms.stream().map(CourtRoom::getId).toList(), contains(ROOM_B, ROOM_A));
    }

    @Test
    void shouldReturnEmptyForANullCourtCentre() {
        assertThat(referenceDataCache.getCpCourtRoomsByCourtCentreId(null), empty());
    }

    private static List<CourtRoom> allCourtRooms() {
        return List.of(
                room(ROOM_B, COURT_CENTRE_ID),
                room(randomUUID().toString(), OTHER_COURT_CENTRE_ID),
                room(ROOM_A, COURT_CENTRE_ID));
    }

    private static CourtRoom room(final String id, final String courtCentreId) {
        return new CourtRoom().id(id).oucodeUUID(courtCentreId);
    }
}
