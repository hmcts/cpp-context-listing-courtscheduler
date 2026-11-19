package uk.gov.moj.cpp.courtscheduler.repository;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;

import java.time.Instant;
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

    RotaFileProcessHistory findByFileDateGreaterThan(Instant fileDate);

    @Query("from RotaFileProcessHistory where fileNamePrefix=:filePrefix and fileDate > :fileDate")
    List<RotaFileProcessHistory> findByFileNamePrefixAndFileDateGreaterThan(@Param("filePrefix") String filePrefix,
                                                                            @Param("fileDate") Instant fileDate);

    @Modifying
    @Query("DELETE RotaFileProcessHistory rf WHERE rf.fileNamePrefix = :fileNamePrefix AND rf.fileDate <= :fileDate")
    void deleteByFileNamePrefixAndFileDate(@Param("fileNamePrefix") String fileNamePrefix, @Param("fileDate") Instant fileDate);
}
