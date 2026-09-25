package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toOffsetDateTime;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

public class CourtScheduleJudiciaryMapper {

    // Private constructor to prevent instantiation
    private CourtScheduleJudiciaryMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static CourtScheduleJudiciary toEntity(uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary domain) {
        if (domain == null) {
            return null;
        }

        final CourtScheduleJudiciary entity = new CourtScheduleJudiciary();
        entity.setId(new CourtScheduleJudiciaryKey(domain.getCourtScheduleId(), domain.getJudiciaryId()));
        entity.setJudiciaryType(domain.getJudiciaryType());
        entity.setDeputy(domain.getDeputy());
        entity.setEmail(domain.getEmailAddress());
        entity.setCourtListingProfileId(domain.getCourtListingProfileId());
        entity.setBenchChairman(domain.getBenchChairman());
        entity.setSurname(domain.getSurname());
        entity.setForenames(domain.getForenames());
        entity.setPosition(domain.getPosition());
        entity.setRotaJudiciaryId(domain.getRotaJudiciaryId());
        entity.setTitle(domain.getTitle());
        entity.setPosition(domain.getPosition());
        entity.setCreatedOn(domain.getCreatedOn() != null ? java.util.Date.from(domain.getCreatedOn().toInstant()) : null);
        entity.setUpdatedOn(domain.getUpdatedOn() != null ? java.util.Date.from(domain.getUpdatedOn().toInstant()) : null);
        entity.setActive(Boolean.TRUE.equals(domain.getActive()));
        return entity;
    }

    public static uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary toDomain(CourtScheduleJudiciary entity) {
        if (isNull(entity)) {
            return null;
        }

        return new uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary()
                .judiciaryId(entity.getId().getJudiciaryId())
                .courtScheduleId(entity.getId().getCourtScheduleId())
                .judiciaryType(entity.getJudiciaryType())
                .surname(entity.getSurname())
                .forenames(entity.getForenames())
                .position(entity.getPosition())
                .courtListingProfileId(entity.getCourtListingProfileId())
                .benchChairman(entity.getBenchChairman())
                .rotaJudiciaryId(entity.getRotaJudiciaryId())
                .title(entity.getTitle())
                .deputy(entity.getDeputy())
                .emailAddress(entity.getEmail())
                .createdOn(toOffsetDateTime(entity.getCreatedOn()))
                .updatedOn(toOffsetDateTime(entity.getUpdatedOn()))
                .active(entity.getActive());
    }
}
