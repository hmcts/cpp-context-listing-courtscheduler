package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static org.apache.commons.io.IOUtils.toByteArray;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.test.util.ReflectionTestUtils.setField;

import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import javax.xml.stream.XMLInputFactory;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RotaFileParserTest {

    @InjectMocks
    private RotaFileParser rotaFileParser;

    @Spy
    private XMLInputFactory xmlInputFactory;


    @BeforeEach
    void setUp() {
        setField(rotaFileParser, "propertiesLoader", new PropertiesLoader());
    }

    @Test
    void shouldParseValidRotaXML() throws IOException {
        final String file = "rotafileprocessor/rota_payload.xml";

        final byte[] blobContent = givenBlobContent(file);

        final Map<RotaPayload, Map<String, Map<String, String>>> result = rotaFileParser.parse(file, blobContent);

        assertRotaPeriodDetails(result);

        assertMagistratesDetails(result);

        assertDistrictJudgesDetails(result);

        assertCourtListingsDetails(result);

        assertSchedulesDetails(result);

        assertLocationsDetails(result);
    }

    private void assertLocationsDetails(final Map<RotaPayload, Map<String, Map<String, String>>> result) {
        final Map<String, Map<String, String>> propertyMap = result.get(RotaPayload.LOCATION);

        assertThat(propertyMap.size(), is(1));

        final Map<String, String> locationDetails = propertyMap.values().iterator().next();
        assertThat(locationDetails.get("175"), is("Cheltenham MC"));
        assertThat(locationDetails.get("177"), is("Gloucester County Court"));
    }

    private void assertRotaPeriodDetails(final Map<RotaPayload, Map<String, Map<String, String>>> periodInfo) {
        final Map<String, Map<String, String>> propertyMap = periodInfo.get(RotaPayload.ROTA_PERIOD);

        assertThat(propertyMap.size(), is(1));

        final Map<String, String> rtDetail = propertyMap.values().iterator().next();
        assertThat(rtDetail.get("rotaPeriodStartDate"), is("2019-10-01"));
        assertThat(rtDetail.get("rotaPeriodEndDate"), is("2019-12-31"));
        assertThat(rtDetail.get("justiceAreaId"), is("46"));
        assertThat(rtDetail.get("justiceAreaType"), is("LJA"));
    }

    private void assertMagistratesDetails(final Map<RotaPayload, Map<String, Map<String, String>>> periodInfo) {
        final Map<String, Map<String, String>> propertyMap = periodInfo.get(RotaPayload.MAGISTRATES);

        assertThat(propertyMap.size(), is(120));

        final Map<String, String> magistrate = propertyMap.get("MA21568");
        assertThat(magistrate.get("id"), is("MA21568"));
        assertThat(magistrate.get("magistrateForenames"), is("FarraTS"));
        assertThat(magistrate.get("magistrateTitle"), is("Dr"));
        assertThat(magistrate.get("magistrateSurname"), is("DavyTS"));
    }

    private void assertDistrictJudgesDetails(final Map<RotaPayload, Map<String, Map<String, String>>> periodInfo) {
        final Map<String, Map<String, String>> propertyMap = periodInfo.get(RotaPayload.DISTRICT_JUDGES);

        assertThat(propertyMap.size(), is(1));

        final Map<String, String> districtJudge = propertyMap.get("DJ77");
        assertThat(districtJudge.get("id"), is("DJ77"));
        assertThat(districtJudge.get("judgeForenames"), is("SusyTS&TS"));
        assertThat(districtJudge.get("judgeSurname"), is("TurnerTS"));
    }

    private void assertCourtListingsDetails(final Map<RotaPayload, Map<String, Map<String, String>>> periodInfo) {
        final Map<String, Map<String, String>> propertyMap = periodInfo.get(RotaPayload.COURT_LISTING);

        assertThat(propertyMap.size(), is(472));

        final Map<String, String> courtListing = propertyMap.get("CS2129929");
        assertThat(courtListing.get("venueName"), is("Court 1 & Cheltenham & Glos < \" Twyver House"));
        assertThat(courtListing.get("welshSpeaking"), is("false"));
        assertThat(courtListing.get("linkedSessionId"), is("CS2129928"));
        assertThat(courtListing.get("business"), is("GAP"));
        assertThat(courtListing.get("sessionDate"), is("2019-11-01"));
        assertThat(courtListing.get("session"), is("PM"));
        assertThat(courtListing.get("locationId"), is("175"));
        assertThat(courtListing.get("venueId"), is("17729"));
        assertThat(courtListing.get("id"), is("CS2129929"));
        assertThat(courtListing.get("panel"), is("ADULT"));
    }

    private void assertSchedulesDetails(final Map<RotaPayload, Map<String, Map<String, String>>> periodInfo) {
        final Map<String, Map<String, String>> propertyMap = periodInfo.get(RotaPayload.SCHEDULE);

        assertThat(propertyMap.size(), is(1392));

        final Map<String, String> schedule = propertyMap.get("CS2130184_RIGHT_WINGER");
        assertThat(schedule.get("id"), is("CS2130184_RIGHT_WINGER"));
        assertThat(schedule.get("courtListingProfile"), is("CS2130184"));
        assertThat(schedule.get("isDoubleBooked"), is("false"));
        assertThat(schedule.get("justice"), is("MA15775"));
        assertThat(schedule.get("slot"), is("RIGHT_WINGER"));
    }

    private byte[] givenBlobContent(final String file) throws IOException {
        try (final InputStream inputStream = RotaFileParserTest.class.getClassLoader().getResourceAsStream(file)) {

            return toByteArray(inputStream);
        }
    }
}
