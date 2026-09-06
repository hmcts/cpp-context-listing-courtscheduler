package uk.gov.moj.cpp.courtscheduler.api;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import uk.gov.moj.cpp.courtscheduler.api.converter.AllocatedSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotRequestParamConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.HearingSlotSearchRequestConverter;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListHearingSlotConverter;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsRemoveService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsSearchService;
import uk.gov.moj.cpp.courtscheduler.api.service.SlotsUpdateService;
import uk.gov.moj.cpp.courtscheduler.api.validator.HearingSlotsApiValidator;
import uk.gov.moj.cpp.courtscheduler.common.service.AllocatedListingService;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.HearingSlotSearchRequest;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonObject;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HearingSlotsApiTest {

    @Mock
    private SlotsUpdateService slotsUpdateService;
    @Mock
    private SlotsSearchService slotsSearchService;
    @Mock
    private SlotsRemoveService slotsRemoveService;
    @Mock
    private AllocatedListingService allocatedListingService;
    @Mock
    private HearingSlotsApiValidator hearingSlotsApiValidator;
    @Mock
    private AllocatedSlotConverter allocatedSlotConverter;
    @Mock
    private HearingSlotRequestParamConverter hearingSlotRequestParamConverter;
    @Mock
    private HearingSlotSearchRequestConverter hearingSlotSearchRequestConverter;
    @Mock
    private ListHearingSlotConverter listHearingSlotConverter;

    private HearingSlotsApi hearingSlotsApi;

    @BeforeEach
    void setUp() {
        hearingSlotsApi = new HearingSlotsApi(slotsUpdateService, slotsSearchService, slotsRemoveService,
                allocatedListingService, hearingSlotsApiValidator, allocatedSlotConverter,
                hearingSlotRequestParamConverter, hearingSlotSearchRequestConverter, listHearingSlotConverter,
                new ObjectMapper());

        when(hearingSlotSearchRequestConverter.convert(any())).thenReturn(
                new HearingSlotSearchRequest("hearing-1", "B01LY00", "2025-05-13",
                        null, null, null, 20, false, null));
        when(hearingSlotsApiValidator.searchAndBookRequestValidation(any())).thenReturn(Json.createObjectBuilder().build());
        when(slotsUpdateService.searchAndBook(any())).thenReturn(new HearingSlotSearchAndBookResponse());
    }

    @Test
    void shouldForwardBusinessTypeToSearchRequestConverterWhenProvided() {
        hearingSlotsApi.getSearchListHearingSlots("hearing-1", "B01LY00", "2025-05-13", 20,
                null, null, null, null, "ENF_AUTO");

        final ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(hearingSlotSearchRequestConverter).convert(captor.capture());
        assertThat(captor.getValue().getString("businessType"), is("ENF_AUTO"));
    }

    @Test
    void shouldNotSendBusinessTypeKeyWhenNotProvided() {
        hearingSlotsApi.getSearchListHearingSlots("hearing-1", "B01LY00", "2025-05-13", 20,
                null, null, null, null, null);

        final ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
        verify(hearingSlotSearchRequestConverter).convert(captor.capture());
        assertThat(captor.getValue().containsKey("businessType"), is(false));
    }
}
