package uk.gov.moj.cpp.courtscheduler.api.service.mapper;


import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import uk.gov.justice.services.common.converter.jackson.ObjectMapperProducer;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtRoomMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapperProducer().objectMapper();

    @Test
    void shouldConvertToEntity() throws Exception {
        final CourtRoom courtRoomDomain = courtRoomDomain();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom courtRoomEntity = CourtRoomMapper.toEntity(courtRoomDomain);

        assertThat(courtRoomEntity, notNullValue());
    }

    @Test
    void shouldReturnDomainObjAsNullIfEntityIsNull() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom courtRoomEntity = CourtRoomMapper.toEntity(null);
        assertThat(courtRoomEntity, nullValue());
    }

    @Test
    void shouldConvertToDomain() throws Exception {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom courtRoomEntity = courtRoomEntity();

        final CourtRoom courtRoomDomain = CourtRoomMapper.toDomain(courtRoomEntity);

        assertThat(courtRoomDomain, notNullValue());
    }

    @Test
    void shouldReturnEntityAsNullIfDomainObjIsNull() {
        final CourtRoom courtRoomDomain = CourtRoomMapper.toDomain(null);
        assertThat(courtRoomDomain, nullValue());
    }

    private CourtRoom courtRoomDomain() throws JsonProcessingException {
        final String courtRoomDomainJsonString = FileUtil.fileToString("/test-data/single-court-room-domain-data.json");
        return objectMapper.readValue(courtRoomDomainJsonString, CourtRoom.class);
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom courtRoomEntity() throws JsonProcessingException {
        final String courtRoomDomainJsonString = FileUtil.fileToString("/test-data/single-court-room-entity-data.json");
        return objectMapper.readValue(courtRoomDomainJsonString, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtRoom.class);
    }
}
