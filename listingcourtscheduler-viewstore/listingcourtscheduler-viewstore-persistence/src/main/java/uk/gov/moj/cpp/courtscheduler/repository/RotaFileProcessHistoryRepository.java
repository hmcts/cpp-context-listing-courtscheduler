package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;

import java.sql.Timestamp;
import java.util.List;

import org.apache.deltaspike.data.api.EntityRepository;
import org.apache.deltaspike.data.api.Modifying;
import org.apache.deltaspike.data.api.Query;
import org.apache.deltaspike.data.api.QueryParam;
import org.apache.deltaspike.data.api.Repository;

@Repository(forEntity = RotaFileProcessHistory.class)
public interface RotaFileProcessHistoryRepository extends EntityRepository<RotaFileProcessHistory, Integer> {

    RotaFileProcessHistory findByFileDateGreaterThan(Timestamp fileDate);

    @Query(value = "from RotaFileProcessHistory where fileNamePrefix=:filePrefix and fileDate > :fileDate")
    List<RotaFileProcessHistory> findByFileNamePrefixAndFileDateGreaterThan(@QueryParam("filePrefix") final String filePrefix,
                                                                            @QueryParam("fileDate") final Timestamp fileDate);

    @Modifying
    @Query(value = "DELETE RotaFileProcessHistory rf WHERE rf.fileNamePrefix = :fileNamePrefix AND rf.fileDate <= :fileDate")
    void deleteByFileNamePrefixAndFileDate(@QueryParam("fileNamePrefix") String fileNamePrefix, @QueryParam("fileDate") Timestamp fileDate);
}
