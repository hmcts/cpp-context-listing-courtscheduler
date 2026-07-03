package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;

import java.sql.Timestamp;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Migrated from DeltaSpike Data {@code EntityRepository<RotaFileProcessHistory, String>}
 * to Spring Data JPA. Annotation names match the DeltaSpike API (Modifying/Query) so
 * the rest of the code is unaffected; only {@code @QueryParam} → {@code @Param} (Spring).
 */
@Repository
public interface RotaFileProcessHistoryRepository extends JpaRepository<RotaFileProcessHistory, String> {

    RotaFileProcessHistory findByFileDateGreaterThan(Timestamp fileDate);

    @Query(value = "from RotaFileProcessHistory where fileNamePrefix=:filePrefix and fileDate > :fileDate")
    List<RotaFileProcessHistory> findByFileNamePrefixAndFileDateGreaterThan(@Param("filePrefix") final String filePrefix,
                                                                            @Param("fileDate") final Timestamp fileDate);

    @Modifying
    @Query(value = "DELETE RotaFileProcessHistory rf WHERE rf.fileNamePrefix = :fileNamePrefix AND rf.fileDate <= :fileDate")
    void deleteByFileNamePrefixAndFileDate(@Param("fileNamePrefix") String fileNamePrefix, @Param("fileDate") Timestamp fileDate);
}
