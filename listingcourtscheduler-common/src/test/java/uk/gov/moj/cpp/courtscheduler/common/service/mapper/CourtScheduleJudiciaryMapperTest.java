package uk.gov.moj.cpp.courtscheduler.common.service.mapper;


import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleJudiciaryMapperTest {

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @Test
    void shouldConvertToEntity() throws Exception {
        final CourtScheduleJudiciary courtScheduleJudiciaryDomain = courtScheduleJudiciaryDomain();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity = CourtScheduleJudiciaryMapper.toEntity(courtScheduleJudiciaryDomain);

        assertThat(courtScheduleJudiciaryEntity, notNullValue());
    }

    @Test
    void shouldReturnDomainObjAsNullIfEntityIsNull() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity = CourtScheduleJudiciaryMapper.toEntity(null);
        assertThat(courtScheduleJudiciaryEntity, nullValue());
    }

    @Test
    void shouldConvertToDomain() throws Exception {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity = courtScheduleJudiciaryEntity();

        final CourtScheduleJudiciary courtScheduleJudiciaryDomain = CourtScheduleJudiciaryMapper.toDomain(courtScheduleJudiciaryEntity);

        assertThat(courtScheduleJudiciaryDomain, notNullValue());
    }

    @Test
    void shouldReturnEntityAsNullIfDomainObjIsNull() {
        final CourtScheduleJudiciary courtScheduleJudiciaryDomain = CourtScheduleJudiciaryMapper.toDomain(null);
        assertThat(courtScheduleJudiciaryDomain, nullValue());
    }

    private CourtScheduleJudiciary courtScheduleJudiciaryDomain() throws JsonProcessingException {
        final String courtScheduleJudiciaryDomainJsonString = FileUtil.fileToString("/test-data/single-court-schedule-judiciary-domain-data.json");
        return objectMapper.readValue(courtScheduleJudiciaryDomainJsonString, CourtScheduleJudiciary.class);
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary courtScheduleJudiciaryEntity() throws JsonProcessingException {
        final String courtScheduleJudiciaryEntityJsonString = FileUtil.fileToString("/test-data/single-court-schedule-judiciary-entity-data.json");
        return objectMapper.readValue(courtScheduleJudiciaryEntityJsonString, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary.class);
    }
}
