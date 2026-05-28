package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourtScheduleMapperTest {

    private final ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @Test
    void shouldConvertToEntity() throws Exception {
        final CourtSchedule courtScheduleDomain = courtScheduleDomain();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity = CourtScheduleMapper.toEntity(courtScheduleDomain);

        assertThat(courtScheduleEntity, notNullValue());
    }

    @Test
    void shouldReturnEntityAsNullIfDomainObjIsNull() {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity = CourtScheduleMapper.toEntity(null);
        assertThat(courtScheduleEntity, nullValue());
    }

    @Test
    void shouldReturnDomainObjAsNullIfEntityIsNull() {
        final CourtSchedule courtScheduleDomain = CourtScheduleMapper.toDomain(null);
        assertThat(courtScheduleDomain, nullValue());
    }

    @Test
    void shouldConvertToDomain() throws Exception {
        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity = courtScheduleEntity();

        final CourtSchedule courtScheduleDomain = CourtScheduleMapper.toDomain(courtScheduleEntity);

        assertThat(courtScheduleDomain, notNullValue());
    }

    private CourtSchedule courtScheduleDomain() throws JsonProcessingException {
        final String courtScheduleDomainJsonString = FileUtil.fileToString("/test-data/single-court-schedule-domain-data.json");
        return objectMapper.readValue(courtScheduleDomainJsonString, CourtSchedule.class);
    }

    private uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule courtScheduleEntity() throws JsonProcessingException {
        final String courtScheduleJudiciaryEntityJsonString = FileUtil.fileToString("/test-data/single-court-schedule-entity-data.json");
        return objectMapper.readValue(courtScheduleJudiciaryEntityJsonString, uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule.class);
    }
}
