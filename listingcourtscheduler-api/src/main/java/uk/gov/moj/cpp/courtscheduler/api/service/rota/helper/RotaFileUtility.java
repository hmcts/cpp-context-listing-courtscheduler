package uk.gov.moj.cpp.courtscheduler.api.service.rota.helper;

import static java.util.Objects.isNull;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJAFileNamePrefix;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJAFileTimeStampAsOffsetDateTime;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.FileUtil.getLJAFileTimeStampAsString;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class for rota file-related operations such as file validation,
 * snapshot file processing, and file type checks.
 */
@Service
public class RotaFileUtility {

    private static final Logger logger = LoggerFactory.getLogger(RotaFileUtility.class);
    
    private static final String DUMMY_NAME_PART = "dummysupport";
    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final long NANOSECONDS_TO_MILLISECONDS = 1_000_000L;
    private static final String LOG_PREFIX_DD_15703 = "DD-15703:processSnapshotRotaFile: ";

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
     * Checks if the file is a dummy support file.
     *
     * @param fileName the name of the file
     * @return true if the file is a dummy file, false otherwise
     */
    public boolean isDummyFile(final String fileName) {
        return fileName.contains(DUMMY_NAME_PART);
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
     * Checks if a newer snapshot file has already been processed.
     * Returns true if the file date/time cannot be extracted (fail-safe behavior).
     *
     * @param fileName the name of the file to check
     * @return true if a newer snapshot file has been processed or if file date cannot be extracted, false otherwise
     */
    public boolean isNewerSnapshotFileProcessed(final String fileName) {
        final OffsetDateTime fileDateTime = getLJASnapshotFileTimeStampAsOffsetDateTime(fileName);
        if (isNull(fileDateTime)) {
            logger.warn("Invalid file date/time in fileName: {} - treating as newer file processed", fileName);
            return true;
        }

        return checkForNewerFiles(fileName, fileDateTime);
    }

    /**
     * Checks the repository for newer files with the same prefix.
     *
     * @param fileName     the name of the file
     * @param fileDateTime the date/time of the file
     * @return true if newer files exist, false otherwise
     */
    private boolean checkForNewerFiles(final String fileName, final OffsetDateTime fileDateTime) {
        final String fileNamePrefix = getLJASnapshotFileNamePrefix(fileName);
        final Timestamp fileTimestamp = Timestamp.from(fileDateTime.toInstant());
        final List<RotaFileProcessHistory> newerFiles = rotaFileProcessHistoryRepository
                .findByFileNamePrefixAndFileDateGreaterThan(fileNamePrefix, fileTimestamp);

        if (isNotEmpty(newerFiles)) {
            logger.warn("Newer snapshot file already processed for prefix: {} - found {} newer file(s)",
                    fileNamePrefix, newerFiles.size());
            return true;
        }

        return false;
    }

    /**
     * Creates and saves a file process history record, generating an execution ID.
     * Returns null if the file timestamp cannot be extracted from the filename.
     *
     * @param fileName                      the name of the file
     * @param content                       the byte content of the file
     * @param rotaFileProcessHistoryService the service for saving file process history
     * @return the RotaFileProcessHistory record that was saved, or null if file timestamp cannot be extracted
     */
    public RotaFileProcessHistory createAndSaveFileProcessHistory(final String fileName,
                                                                  final byte[] content,
                                                                  final uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService rotaFileProcessHistoryService) {
        logger.info("{}before rotaFileProcessHistoryRepository.save", LOG_PREFIX_DD_15703);
        
        // Check if timestamp actually exists in the filename (not generated)
        final String timeStampAsString = getLJAFileTimeStampAsString(fileName);
        if (isNull(timeStampAsString)) {
            logger.warn("Cannot create file process history - invalid file date/time in fileName: {}", fileName);
            return null;
        }
        
        final OffsetDateTime fileDateTime = getLJAFileTimeStampAsOffsetDateTime(fileName);
        if (isNull(fileDateTime)) {
            logger.warn("Cannot create file process history - invalid file date/time in fileName: {}", fileName);
            return null;
        }

        return saveFileProcessHistory(fileName, fileDateTime, content, rotaFileProcessHistoryService);
    }

    /**
     * Saves the file process history record with the extracted file information.
     *
     * @param fileName                      the name of the file
     * @param fileDateTime                  the extracted file date/time
     * @param content                       the byte content of the file
     * @param rotaFileProcessHistoryService the service for saving file process history
     * @return the saved RotaFileProcessHistory record
     */
    private RotaFileProcessHistory saveFileProcessHistory(final String fileName,
                                                          final OffsetDateTime fileDateTime,
                                                          final byte[] content,
                                                          final uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService rotaFileProcessHistoryService) {
        final String fileNamePrefix = getLJAFileNamePrefix(fileName);
        final String executionId = UUID.randomUUID().toString();
        
        final RotaFileProcessHistory rotaFileProcessHistory = rotaFileProcessHistoryService.save(
                fileNamePrefix, fileDateTime, content, executionId);
        
        logger.info("{}after rotaFileProcessHistoryRepository.save - executionId: {}", 
                LOG_PREFIX_DD_15703, executionId);
        
        return rotaFileProcessHistory;
    }

    /**
     * Logs the processing time for a blob file.
     *
     * @param logger       the logger instance to use
     * @param blobName     the name of the blob file
     * @param processStart the start time in nanoseconds
     * @param processEnd   the end time in nanoseconds
     */
    public void logProcessingTime(final org.slf4j.Logger logger, final String blobName, 
                                 final long processStart, final long processEnd) {
        logger.info("PRF: Processing and parsing completed for blob {} in {} ms",
                blobName, convertNanosToMillis(processEnd - processStart));
    }

    /**
     * Updates the file process history with end date if history exists.
     *
     * @param logger                the logger instance to use
     * @param rotaFileProcessHistory the file process history to update
     * @param blobName              the name of the blob file
     * @param rotaFileProcessHistoryService the service for updating file process history
     */
    public void updateFileProcessHistory(final org.slf4j.Logger logger,
                                        final RotaFileProcessHistory rotaFileProcessHistory,
                                        final String blobName,
                                        final uk.gov.moj.cpp.courtscheduler.common.service.RotaFileProcessHistoryService rotaFileProcessHistoryService) {
        if (rotaFileProcessHistory != null) {
            rotaFileProcessHistoryService.update(rotaFileProcessHistory);
            logger.info("Updated file process history with end date for blob: {}", blobName);
        }
    }
}
