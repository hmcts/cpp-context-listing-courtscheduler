package uk.gov.moj.cpp.courtscheduler.repository;

import org.apache.deltaspike.data.api.AbstractEntityRepository;
import org.apache.deltaspike.data.api.Repository;
import uk.gov.moj.cpp.courtscheduler.domain.MiFilterCriteria;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.util.Date;
import java.util.List;

@Repository(forEntity = CourtScheduleJudiciary.class)
public abstract class CourtScheduleJudiciaryRepository extends AbstractEntityRepository<CourtScheduleJudiciary, CourtScheduleJudiciaryKey> {
    abstract CourtScheduleJudiciary findByEmail(String email);

    abstract List<CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(Date fromDate, Date toDate);

    public List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> findByUpdatedOnGreaterThanAndUpdatedOnLessThan(MiFilterCriteria miFilterCriteria) {
        List<CourtScheduleJudiciary> courtScheduleJudiciaries = findByUpdatedOnGreaterThanAndUpdatedOnLessThan(
                DateUtils.getDate(miFilterCriteria.getFromLocalDate()),
                DateUtils.getDate(miFilterCriteria.getToLocalDate()));
        return courtScheduleJudiciaries.stream().map(courtScheduleJudiciaryEntity -> new uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.Builder()
                .withCourtScheduleId(courtScheduleJudiciaryEntity.getId().getCourtScheduleId())
                .withJudiciaryId(courtScheduleJudiciaryEntity.getId().getJudiciaryId())
                .withPosition(courtScheduleJudiciaryEntity.getPosition())
                .withForenames(courtScheduleJudiciaryEntity.getForenames())
                .withEmailAddress(courtScheduleJudiciaryEntity.getEmail())
                .build()).toList();

    }

}
