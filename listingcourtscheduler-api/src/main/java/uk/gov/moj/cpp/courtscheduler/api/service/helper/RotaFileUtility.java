package uk.gov.moj.cpp.courtscheduler.api.service.helper;

import static java.util.Objects.isNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for rota file-related operations such as file validation,
 * snapshot file processing, and file type checks.
 */
@ApplicationScoped
public class RotaFileUtility {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileUtility.class);
    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String DUMMY_NAME_PART = "dummysupport";
    private static final long NANOSECONDS_TO_MILLISECONDS = 1_000_000L;
    private static final String EMPTY_STRING = "";
    private static final String SNAPSHOT_FILE_PROCESSING_SKIPPED = "Snapshot file processing skipped";

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    /**
     * Converts nanoseconds to milliseconds.
     *
     * @param nanos the time in nanoseconds
     * @return the time in milliseconds
     */
    public long convertNanosToMillis(final long nanos) {
        return nanos / NANOSECONDS_TO_MILLISECONDS;
    }

    /**
     * Checks if the file is a snapshot file.
     *
     * @param fileName the name of the file
     * @return true if the file is a snapshot file, false otherwise
     */
    public boolean isSnapshotFile(final String fileName) {
        return fileName.contains(SNAPSHOT_NAME_PART);
    }

    /**
     * Checks if the file is a dummy support file.
     *
     * @param fileName the name of the file
     * @return true if the file is a dummy file, false otherwise
     */
    public boolean isDummyFile(final String fileName) {
        return fileName.contains(DUMMY_NAME_PART);
    }

    /**
     * Checks if a newer snapshot file has already been processed.
     *
     * @param fileName the name of the file to check
     * @return true if a newer snapshot file has been processed, false otherwise
     */
    public boolean isNewerSnapshotFileProcessed(final String fileName) {
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        if (isNull(fileDateTime)) {
            logger.warn("Invalid file date/time in fileName: {}", fileName);
            return true;
        }

        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final List<RotaFileProcessHistory> newerFiles = rotaFileProcessHistoryRepository
                .findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, Timestamp.from(fileDateTime.toInstant()));

        if (isNotEmpty(newerFiles)) {
            logger.warn("Newer snapshot file already processed for prefix: {}", fileNamePrefix);
            return true;
        }

        return false;
    }

    /**
     * Processes a snapshot file if needed, generating an execution ID.
     *
     * @param fileName the name of the file
     * @param content the byte content of the file
     * @param rotaFileProcessHistoryService the service for saving file process history
     * @return the execution ID if it's a snapshot file, empty string otherwise
     * @throws IllegalStateException if a newer snapshot file has already been processed
     */
    public String processSnapshotFileIfNeeded(final String fileName,
                                              final byte[] content,
                                              final uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService rotaFileProcessHistoryService) {
        if (!isSnapshotFile(fileName)) {
            return EMPTY_STRING;
        }

        if (isNewerSnapshotFileProcessed(fileName)) {
            logger.warn("Skipping snapshot file - newer version already processed: {}", fileName);
            throw new IllegalStateException(SNAPSHOT_FILE_PROCESSING_SKIPPED);
        }

        logger.info("DD-15703:processSnapshotRotaFile: before rotaFileProcessHistoryRepository.save");
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final String executionId = UUID.randomUUID().toString();
        rotaFileProcessHistoryService.save(fileNamePrefix, fileDateTime, content, executionId);
        logger.info("DD-15703:processSnapshotRotaFile: after rotaFileProcessHistoryRepository.save - executionId: {}", executionId);
        return executionId;
    }
}

