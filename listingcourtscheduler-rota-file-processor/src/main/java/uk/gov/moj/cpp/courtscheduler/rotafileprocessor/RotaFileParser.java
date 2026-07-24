package uk.gov.moj.cpp.courtscheduler.rotafileprocessor;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;
import static java.util.UUID.randomUUID;
import static javax.xml.stream.XMLInputFactory.IS_COALESCING;
import static javax.xml.stream.XMLInputFactory.newInstance;
import static javax.xml.stream.XMLStreamConstants.CHARACTERS;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;
import static org.apache.commons.lang3.StringUtils.removeEnd;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.COURT_LISTING;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.DISTRICT_JUDGES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.LOCATION;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.ROTA_PERIOD;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload.SCHEDULE;

import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.exception.RotaFileProcessorException;
import uk.gov.moj.cpp.courtscheduler.rotafileprocessor.util.PropertiesLoader;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.io.ByteArrayInputStream;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.Attribute;
import javax.xml.stream.events.Characters;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class RotaFileParser {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileParser.class);

    private static final String TAG_SEPARATOR = ".";
    private static final String TAG_ID = "id";
    private static final String TAG_ID_REF = "idref";
    private static final String TAG_MAGISTRATE = "magistrate";
    private static final String TAG_DISTRICT_JUDGE = "districtJudge";
    private static final String TAG_VENUE = "venue";
    private static final String TAG_LOCATION = "location";
    private static final String TAG_SCHEDULE = "schedule";
    private static final String TAG_COURT_LISTING = "courtListingProfiles.courtListingProfile";
    private static final String TAG_JUSTICE = "justice";
    private static final String TAG_SCH_LISTING = "schedule.courtListingProfile";
    private static final String TAG_VENUE_ID = "venue.venueId";
    private static final String TAG_CL_VENUE_ID = "venueId";
    private static final String TAG_VENUE_NAME = "venueName";
    private static final String TAG_CL_LOCATION_ID = "locationId";
    private static final String TAG_LOCATION_ID = "location.locationId";
    private static final String TAG_LOCATION_NAME = "location.name";
    private static final String TAG_ROTA_PERIOD = "rotaPeriod";

    private static final Set<String> attributeTags = new TreeSet<>(asList(TAG_MAGISTRATE, TAG_DISTRICT_JUDGE, TAG_VENUE, TAG_SCHEDULE));
    private Map<String, String> requiredElements = new ConcurrentHashMap<>();

    @Inject
    private PropertiesLoader propertiesLoader;

    public Map<RotaPayload, Map<String, Map<String, String>>> parse(final String file, final byte[] content) {
        if (requiredElements.isEmpty()) {
            requiredElements = propertiesLoader.getXmlProperties("rotaXml.properties");
        }

        final Map<String, String> locations = new HashMap<>();
        final Map<String, String> venues = new HashMap<>();
        final Map<String, Map<String, String>> magistrates = new HashMap<>();
        final Map<String, Map<String, String>> districtJudges = new HashMap<>();
        final Map<String, Map<String, String>> courtListingProfiles = new TreeMap<>();
        final Map<String, Map<String, String>> schedules = new HashMap<>();
        final Map<String, Map<String, String>> locationsMap = new HashMap<>();
        final EnumMap<RotaPayload, Map<String, Map<String, String>>> result = new EnumMap<>(RotaPayload.class);

        Map<String, String> record = new HashMap<>();
        String xpath = StringUtils.EMPTY;
        boolean root = true;

        try {
            final XMLInputFactory xmlInputFactory = newInstance();
            xmlInputFactory.setProperty(IS_COALESCING, true);
            final XMLEventReader eventReader = xmlInputFactory.createXMLEventReader(new ByteArrayInputStream(content));

            while (eventReader.hasNext()) {
                final XMLEvent event = eventReader.nextEvent();

                switch (event.getEventType()) {

                    case START_ELEMENT: {
                        final StartElement startElement = event.asStartElement();
                        final String qName = startElement.getName().getLocalPart();

                        if (root) {
                            xpath = qName;
                            root = false;
                        } else {
                            xpath += TAG_SEPARATOR + qName;
                        }

                        if (isAttributesRequired(qName, xpath)) {
                            record = new HashMap<>(getAttributes(startElement));
                        } else if (qName.equals(TAG_JUSTICE) || xpath.endsWith(TAG_SCH_LISTING)) {
                            record.put(qName, getIdRefValue(startElement));
                        }

                        break;
                    }

                    case CHARACTERS: {
                        populateElementValue(event, record, xpath);

                        break;
                    }

                    case END_ELEMENT: {
                        final String qName = event.asEndElement().getName().getLocalPart();

                        if (xpath.endsWith(TAG_COURT_LISTING)) {
                            populateLocation(locations, record);
                            populateVenueName(venues, record);
                            courtListingProfiles.put(record.get(TAG_ID), record);
                        } else {

                            if (qName.equals(TAG_MAGISTRATE)) {
                                magistrates.put(record.get(TAG_ID), record);
                            } else if (qName.equals(TAG_DISTRICT_JUDGE)) {
                                districtJudges.put(record.get(TAG_ID), record);
                            } else if (qName.equals(TAG_VENUE)) {
                                venues.put(record.get(TAG_VENUE_ID), record.get(TAG_VENUE_NAME));
                            } else if (qName.equals(TAG_SCHEDULE)) {
                                schedules.put(record.get(TAG_ID), record);
                            } else if (qName.equals(TAG_ROTA_PERIOD)) {
                                result.put(ROTA_PERIOD, singletonMap(randomUUID().toString(), record));
                            } else if (qName.equals(TAG_LOCATION)) {
                                locations.put(record.get(TAG_LOCATION_ID), record.get(TAG_LOCATION_NAME));
                                locationsMap.put(TAG_CL_LOCATION_ID, locations);
                            }
                        }

                        xpath = removeEnd(xpath, TAG_SEPARATOR + qName);

                        break;
                    }
                }
            }

            result.put(RotaPayload.MAGISTRATES, magistrates);
            result.put(DISTRICT_JUDGES, districtJudges);
            result.put(COURT_LISTING, courtListingProfiles);
            result.put(SCHEDULE, schedules);
            result.put(LOCATION, locationsMap);

        } catch (final XMLStreamException e) {
            final String message = format("Parsing failed for the blob %s, please see the stack trace for more detail", file);

            logger.error(message, e);

            throw new RotaFileProcessorException(message, e);
        }

        return result;
    }

    private boolean isAttributesRequired(final String qName, final String xpath) {
        return attributeTags.contains(qName) || xpath.endsWith(TAG_COURT_LISTING);
    }

    private void populateVenueName(final Map<String, String> venues, Map<String, String> courtListing) {
        final String venueId = courtListing.get(TAG_CL_VENUE_ID);
        final String venueName = venues.get(venueId);

        courtListing.put(TAG_VENUE_NAME, venueName);
    }

    private void populateLocation(final Map<String, String> locations, Map<String, String> courtListing) {
        final String locationId = courtListing.get(TAG_CL_LOCATION_ID);

        //final
        courtListing.put(TAG_CL_LOCATION_ID, locationId);
    }

    private Map<String, String> getAttributes(final StartElement startElement) {
        final Map<String, String> attrMap = new HashMap<>();
        final Iterator<Attribute> attrs = startElement.getAttributes();

        while (attrs.hasNext()) {
            final Attribute attr = attrs.next();
            attrMap.put(attr.getName().getLocalPart(), attr.getValue());
        }

        return attrMap;
    }

    private String getIdRefValue(final StartElement element) {
        return element.getAttributeByName(new QName(TAG_ID_REF)).getValue();
    }

    private void populateElementValue(final XMLEvent event, final Map<String, String> record, final String xpath) {
        final String property = requiredElements.get(xpath);
        if (property != null) {
            final Characters characters = event.asCharacters();
            record.put(property, characters.getData());
        }
    }
}
