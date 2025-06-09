package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.common.converter.JsonObjectToObjectConverter;
import uk.gov.justice.services.common.converter.ObjectToJsonObjectConverter;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatus;
import uk.gov.moj.cpp.courtscheduler.domain.OrganisationUnitHMIStatusList;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonReader;

import com.google.common.io.Resources;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class OrganisationUnitHMIStatusService {

    @Inject
    private JsonObjectToObjectConverter jsonObjectToObjectConverter;

    @Inject
    private ObjectToJsonObjectConverter objectToJsonObjectConverter;

    private static final Logger LOGGER = LoggerFactory.getLogger(OrganisationUnitHMIStatusService.class);

    private List<OrganisationUnitHMIStatus> getAllOrganisationUnitHMIStatusList() {
        final List<OrganisationUnitHMIStatus> statusList = new ArrayList<>();

        final String payload = getPayload("predefined_response_organisation_status.json");
        final JsonObject jsonObject = getJsonObject(payload);
        final JsonArray jsonArray = jsonObject.getJsonArray("organisationUnitHMIStatus");

        for (int i = 0; i < jsonArray.size(); i++) {
            final JsonObject json = jsonArray.getJsonObject(i);
            final OrganisationUnitHMIStatus organisationUnitHmiStatus = jsonObjectToObjectConverter.convert(json, OrganisationUnitHMIStatus.class);
            statusList.add(organisationUnitHmiStatus);
        }

        return statusList;
    }

    public OrganisationUnitHMIStatusList getAllOrganisationUnitsHMIStatus() {
        final List<OrganisationUnitHMIStatus> statusList = getAllOrganisationUnitHMIStatusList();
        return new OrganisationUnitHMIStatusList(statusList);
    }

    public Optional<OrganisationUnitHMIStatus> getOrganisationUnitHMIStatus(String oucode) {
        final List<OrganisationUnitHMIStatus> statusList = getAllOrganisationUnitHMIStatusList();
        final Optional<OrganisationUnitHMIStatus> orgUnitOpt = statusList.stream()
                .filter(organisationUnitHmiStatus -> oucode.equals(organisationUnitHmiStatus.getOucode()))
                .findAny();
        return orgUnitOpt;
    }

    public static String getPayload(String path) {
        String request = null;
        try {
            request = Resources.toString(
                    Resources.getResource(path),
                    Charset.defaultCharset()
            );
        } catch (IOException e) {
            LOGGER.error("Error consuming file from location {}", path, e);
        }
        return request;
    }

    public static JsonObject getJsonObject(final String json) {
        try (final JsonReader reader = Json.createReader(new StringReader(json))) {
            return reader.readObject();
        }
    }
}
