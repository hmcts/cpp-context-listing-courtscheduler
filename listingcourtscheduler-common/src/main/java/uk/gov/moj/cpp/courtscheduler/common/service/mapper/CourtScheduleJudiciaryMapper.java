package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static java.util.Objects.isNull;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

public class CourtScheduleJudiciaryMapper {

    // Private constructor to prevent instantiation
    private CourtScheduleJudiciaryMapper() {
        throw new UnsupportedOperationException("This is a utility class and cannot be instantiated");
    }

    public static CourtScheduleJudiciary toEntity(uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary domain) {
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
        entity.setCreatedOn(domain.getCreatedOn());
        entity.setUpdatedOn(domain.getUpdatedOn());
        entity.setActive(domain.isActive());
        return entity;
    }

    public static uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary toDomain(CourtScheduleJudiciary entity) {
        if (isNull(entity)) {
            return null;
        }

        return uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.judiciary()
                .withJudiciaryId(entity.getId().getJudiciaryId())
                .withCourtScheduleId(entity.getId().getCourtScheduleId())
                .withJudiciaryType(entity.getJudiciaryType())
                .withSurname(entity.getSurname())
                .withForenames(entity.getForenames())
                .withPosition(entity.getPosition())
                .withCourtListingProfileId(entity.getCourtListingProfileId())
                .withIsBenchChairman(entity.getBenchChairman())
                .withRotaJudiciaryId(entity.getRotaJudiciaryId())
                .withTitle(entity.getTitle())
                .withIsDeputy(entity.getDeputy())
                .withEmailAddress(entity.getEmail())
                .withCreatedOn(entity.getCreatedOn())
                .withUpdatedOn(entity.getUpdatedOn())
                .withActive(entity.getActive())
                .build();
    }
}
