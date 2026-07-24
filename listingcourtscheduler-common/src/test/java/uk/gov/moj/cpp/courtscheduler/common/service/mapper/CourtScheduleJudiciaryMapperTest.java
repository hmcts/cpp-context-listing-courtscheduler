package uk.gov.moj.cpp.courtscheduler.common.service.mapper;


import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;


import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.platform.test.data.utils.FileUtil;

import java.util.Date;
import java.util.UUID;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

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
    void shouldMapNullableRotaProfileAndPositionBetweenDomainAndEntity() {
        final String courtScheduleId = UUID.randomUUID().toString();
        final String judiciaryId = UUID.randomUUID().toString();
        final CourtScheduleJudiciary domain = CourtScheduleJudiciary.judiciary()
                .withCourtScheduleId(courtScheduleId)
                .withJudiciaryId(judiciaryId)
                .withRotaJudiciaryId(null)
                .withCourtListingProfileId(null)
                .withPosition(null)
                .withTitle("Mr")
                .withForenames("Test")
                .withSurname("Judge")
                .withEmailAddress("test.judge@example.com")
                .withJudiciaryType("DJ")
                .withIsBenchChairman(false)
                .withIsDeputy(false)
                .withActive(true)
                .withCreatedOn(new Date())
                .withUpdatedOn(new Date())
                .build();

        final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary entity =
                CourtScheduleJudiciaryMapper.toEntity(domain);
        assertNotNull(entity);

        assertNull(entity.getRotaJudiciaryId());
        assertNull(entity.getCourtListingProfileId());
        assertNull(entity.getPosition());

        final CourtScheduleJudiciary roundTrip = CourtScheduleJudiciaryMapper.toDomain(entity);
        assertNotNull(roundTrip);

        assertNull(roundTrip.getRotaJudiciaryId());
        assertNull(roundTrip.getCourtListingProfileId());
        assertNull(roundTrip.getPosition());
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
