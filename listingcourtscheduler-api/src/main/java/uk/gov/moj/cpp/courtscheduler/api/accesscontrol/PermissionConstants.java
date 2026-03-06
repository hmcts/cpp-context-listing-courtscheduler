package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission.builder;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@JsonPropertyOrder({"object","action","key","keyWithOutSource"})
public final class PermissionConstants {

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final String COURT_SCHEDULE_OBJECT = "CourtSchedule";
    private static final String CREATE_ACTION = "Create";
    private static final String VIEW_ACTION = "View";

    private PermissionConstants() {
    }

    public static String createCourtSchedulePermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(COURT_SCHEDULE_OBJECT)
                .withAction(CREATE_ACTION)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String getCourtSchedulePermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(COURT_SCHEDULE_OBJECT)
                .withAction(VIEW_ACTION)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

}
