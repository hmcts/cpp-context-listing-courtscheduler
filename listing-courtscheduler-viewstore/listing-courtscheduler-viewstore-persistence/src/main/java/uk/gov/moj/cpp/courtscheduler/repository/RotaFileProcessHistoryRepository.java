package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistoryKey;

import java.sql.Timestamp;
import java.util.List;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = RotaFileProcessHistory.class)
public interface RotaFileProcessHistoryRepository extends EntityRepository<RotaFileProcessHistory, RotaFileProcessHistoryKey> {

    RotaFileProcessHistory findByFileDateGreaterThan(Timestamp fileDate);

    @Query(value = "from RotaFileProcessHistory where id.fileNamePrefix=:filePrefix and id.fileDate > fileDate")
    List<RotaFileProcessHistory> findByFileNamePrefixAndFileDateGreaterThan(@QueryParam("filePrefix") final String filePrefix,
                                                                            @QueryParam("fileDate") final Timestamp fileDate);

    @Query(value = "DELETE RotaFileProcessHistory rf WHERE rf.id.fileNamePrefix = :fileNamePrefix AND rf.id.fileDate <= :fileDate")
    void delete(@QueryParam("fileNamePrefix") String fileNamePrefix, @QueryParam("fileDate") Timestamp fileDate);
}
