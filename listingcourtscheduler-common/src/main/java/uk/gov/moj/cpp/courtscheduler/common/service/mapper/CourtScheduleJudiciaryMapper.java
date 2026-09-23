package uk.gov.moj.cpp.courtscheduler.common.service.mapper;

import static java.util.Objects.isNull;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.time.ZoneOffset;
import java.util.Date;

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
        entity.setDeputy(domain.getIsDeputy());
        entity.setEmail(domain.getEmailAddress());
        entity.setCourtListingProfileId(domain.getCourtListingProfileId());
        entity.setBenchChairman(domain.getIsBenchChairman());
        entity.setSurname(domain.getSurname());
        entity.setForenames(domain.getForenames());
        entity.setPosition(domain.getPosition());
        entity.setRotaJudiciaryId(domain.getRotaJudiciaryId());
        entity.setTitle(domain.getTitle());
        entity.setPosition(domain.getPosition());
        entity.setCreatedOn(domain.getCreatedOn() == null ? null : Date.from(domain.getCreatedOn().toInstant()));
        entity.setUpdatedOn(domain.getUpdatedOn() == null ? null : Date.from(domain.getUpdatedOn().toInstant()));
        entity.setActive(domain.getActive());
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
                .isBenchChairman(entity.getBenchChairman())
                .rotaJudiciaryId(entity.getRotaJudiciaryId())
                .title(entity.getTitle())
                .isDeputy(entity.getDeputy())
                .emailAddress(entity.getEmail())
                .createdOn(entity.getCreatedOn() == null ? null : entity.getCreatedOn().toInstant().atOffset(ZoneOffset.UTC))
                .updatedOn(entity.getUpdatedOn() == null ? null : entity.getUpdatedOn().toInstant().atOffset(ZoneOffset.UTC))
                .active(entity.getActive());
    }
}
