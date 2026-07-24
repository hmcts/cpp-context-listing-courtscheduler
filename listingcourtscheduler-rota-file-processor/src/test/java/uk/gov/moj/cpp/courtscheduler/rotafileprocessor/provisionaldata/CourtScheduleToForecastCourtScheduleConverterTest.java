package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static java.time.LocalDate.parse;
import static java.util.UUID.randomUUID;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary;

import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleToForecastCourtScheduleConverterTest {

    @InjectMocks
    private CourtScheduleToForecastCourtScheduleConverter courtScheduleToForecastCourtScheduleConverter;

    private static final String OU_CODE = "CABC90";
    private static final String COURT_HOUSE_NAME = "Liverpool Mags Court";
    private static final String COURT_HOUSE_ID = "0b9417b8-91b4-385d-9e01-069855777c4f";
    private static final String COURT_ROOM_NAME = "Court name1";
    private static final String OPERATIONAL_UNIT = "ANC";
    private static final String BUSINESS_TYPE = "BYS";
    private static final String PANEL = "ADULT";
    private static final String COURT_SESSION = "AM";

    private static final String COURT_ROOM_ID = "001c067d-eaca-4ce5-ad90-a366ef3e4bb6";
    private static final int COURT_ROOM_NUMBER = 1234;
    private static final int MAX_SLOTS = 182;
    private static final int MAX_DURATION = 182;
    private static final int AVAILABLE_SLOTS = 125;
    private static final int AVAILABLE_DURATION = 182;

    @Test
    void shouldConvertToProvisionalCourtSchedule() {
        final List<CourtScheduleJudiciary> judiciaries = new ArrayList<>();
        judiciaries.add(judiciary().withRotaJudiciaryId("123").withPosition("CHAIR").build());
        final CourtSchedule courtScheduleExtracted = new CourtSchedule.CourtScheduleBuilder()
                .withCourtScheduleId("0000fbb0-8579-4f2b-948e-c4e48a48e3f8")
                .withListingProfileId("0000fbb0-8579-4f2b-948e-c4e48a48e3f7")
                .withSessionDate(parse("2020-12-01"))
                .withOuCode(OU_CODE)
                .withCourtRoomId(COURT_ROOM_ID)
                .withCourtRoomNumber(COURT_ROOM_NUMBER)
                .withCourtHouseName(COURT_HOUSE_NAME)
                .withCourtHouseId(COURT_HOUSE_ID)
                .withCourtRoomName(COURT_ROOM_NAME)
                .withOperationalUnit(OPERATIONAL_UNIT)
                .withBusinessType(BUSINESS_TYPE)
                .withPanel(PANEL)
                .withCourtSession(COURT_SESSION)
                .withMaxDuration(MAX_DURATION)
                .withAvailableSlots(AVAILABLE_SLOTS)
                .withAvailableDuration(AVAILABLE_DURATION)
                .withMaxSlots(MAX_SLOTS)
                .withJudiciaries(judiciaries)
                .build();
        final String newCourtScheduleId = randomUUID().toString();

        final LocalDate newSessionDate = LocalDate.of(2020, 12, 01);
        final CourtSchedule courtSchedule = courtScheduleToForecastCourtScheduleConverter
                .convertToProvisionalCourtSchedule(courtScheduleExtracted, newSessionDate, newCourtScheduleId);

        assertThat(courtSchedule.getCourtScheduleId(), is(newCourtScheduleId));
        assertThat(courtSchedule.getListingProfileId(), is(nullValue()));
        assertThat(courtSchedule.getSessionDate().toString(), is(newSessionDate.toString()));

        assertThat(courtSchedule.getOuCode(), is(OU_CODE));
        assertThat(courtSchedule.getCourtRoomId(), is(COURT_ROOM_ID));
        assertThat(courtSchedule.getCourtRoomNumber(), is(COURT_ROOM_NUMBER));
        assertThat(courtSchedule.getCourtHouseName(), is(COURT_HOUSE_NAME));
        assertThat(courtSchedule.getCourtRoomName(), is(COURT_ROOM_NAME));
        assertThat(courtSchedule.getOperationalUnit(), is(OPERATIONAL_UNIT));
        assertThat(courtSchedule.getBusinessType(), is(BUSINESS_TYPE));
        assertThat(courtSchedule.getPanel(), is(PANEL));
        assertThat(courtSchedule.getCourtSession(), is(COURT_SESSION));
        assertThat(courtSchedule.getMaxDuration(), is(MAX_DURATION));
        assertThat(courtSchedule.getAvailableSlots(), is(AVAILABLE_SLOTS));
        assertThat(courtSchedule.getAvailableDuration(), is(AVAILABLE_DURATION));
        assertThat(courtSchedule.getMaxSlots(), is(MAX_SLOTS));
        assertThat(courtSchedule.getJudiciaries(), is(nullValue()));
    }

}
