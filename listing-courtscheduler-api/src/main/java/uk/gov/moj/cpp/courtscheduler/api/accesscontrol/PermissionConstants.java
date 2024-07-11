package uk.gov.moj.cpp.courtscheduler.api.accesscontrol;

import static uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission.builder;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.accesscontrol.drools.ExpectedPermission;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class PermissionConstants {

    private static final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    private static final String COURT_SCHEDULE_OBJECT = "CourtSchedule";
    private static final String HEARING_SLOTS_OBJECT = "HearingSlots";
    private static final String PROVISIONAL_BOOKING_OBJECT = "ProvisionalBooking";
    private static final String CREATE_ACTION = "Create";
    private static final String UPDATE_ACTION = "Edit";
    private static final String VIEW_ACTION = "View";
    private static final String DELETE_ACTION = "Delete";
    private static final String EXPORT_ACTION = "Mi-extract";
    private static final String CREATE_HEARING = "Create-hearing";
    private static final String CREATE_PROVISIONAL = "Create-provisional";
    private static final String DELETE_HEARING = "Delete-hearing";
    private static final String VIEW_HEARING = "View-hearing";
    private static final String VIEW_PROVISIONAL = "View-provisional";

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

    public static String deleteCourtSchedulePermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(COURT_SCHEDULE_OBJECT)
                .withAction(DELETE_ACTION)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String updateCourtSchedulePermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(COURT_SCHEDULE_OBJECT)
                .withAction(UPDATE_ACTION)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String updateHearingSlotsPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(HEARING_SLOTS_OBJECT)
                .withAction(CREATE_HEARING)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String getHearingSlotsPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(HEARING_SLOTS_OBJECT)
                .withAction(VIEW_HEARING)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String removeHearingSlotsPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(HEARING_SLOTS_OBJECT)
                .withAction(DELETE_HEARING)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String createProvisionalBookingPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(PROVISIONAL_BOOKING_OBJECT)
                .withAction(CREATE_PROVISIONAL)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String getProvisionalBookingPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(PROVISIONAL_BOOKING_OBJECT)
                .withAction(VIEW_PROVISIONAL)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }

    public static String exportCourtSchedulesPermission() throws JsonProcessingException {
        final ExpectedPermission expectedPermission = builder()
                .withObject(COURT_SCHEDULE_OBJECT)
                .withAction(EXPORT_ACTION)
                .build();

        return objectMapper.writeValueAsString(expectedPermission);
    }
}
