package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.BusinessType;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistoryKey;

import java.sql.Timestamp;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = RotaFileProcessHistory.class)
public interface RotaFileProcessHistoryRepository extends EntityRepository<RotaFileProcessHistory, RotaFileProcessHistoryKey> {
    BusinessType findByFileDateGreaterThan(Timestamp fileDate);
}
