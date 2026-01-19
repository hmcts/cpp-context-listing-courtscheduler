package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static java.lang.String.format;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FileUtil {

    private static final Logger logger = LoggerFactory.getLogger(FileUtil.class);

    private static final String FILE_NAME_TIMESTAMP_PATTERN = "yyyyMMdd'T'HHmmss'Z'";
    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String XML_NAME_PART = ".xml";
    private static final int TIMESTAMP_STRING_LENGTH = 16; // Format: yyyyMMddTHHmmssZ (8 date + 1 T + 6 time + 1 Z = 16)

    private FileUtil() {
    }

    public static String getLJASnapshotFileTimeStampAsString(final String fileName) {
        return fileName.substring(fileName.indexOf(SNAPSHOT_NAME_PART) + SNAPSHOT_NAME_PART.length(),
                fileName.length() - XML_NAME_PART.length());
    }

    public static OffsetDateTime getLJASnapshotFileTimeStampAsOffsetDateTime(final String fileName) {
        final String timeStampAsString = getLJASnapshotFileTimeStampAsString(fileName);
        final OffsetDateTime fileDateTime;

        try {
            fileDateTime = LocalDateTime.parse(timeStampAsString, DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN)).atOffset(ZoneOffset.UTC);
            return fileDateTime;
        } catch (DateTimeParseException e) {
            logger.warn(format("Received file with invalid filename format, hence skipping file processing, for file : %s. Exception received is : %s", fileName, e));
        }

        return null;
    }

    public static String getLJASnapshotFileNamePrefix(final String fileName) {
        return fileName.substring(0, (fileName.length() - (getLJASnapshotFileTimeStampAsString(fileName).length() + ".xml".length())));
    }

    /**
     * Extracts the timestamp string from a file name.
     * For snapshot files (containing "_snapshot_"), extracts the timestamp after "_snapshot_".
     * For non-snapshot files, extracts the timestamp from the end of the filename (before .xml).
     * If no timestamp is found in a non-snapshot file, returns the current timestamp.
     *
     * @param fileName the name of the file
     * @return the timestamp string, or current timestamp if not found (for non-snapshot files)
     */
    public static String getLJAFileTimeStampAsString(final String fileName) {
        if (!fileName.endsWith(XML_NAME_PART)) {
            return null;
        }

        // If it's a snapshot file, use the same logic as getLJASnapshotFileTimeStampAsString
        if (fileName.contains(SNAPSHOT_NAME_PART)) {
            return fileName.substring(fileName.indexOf(SNAPSHOT_NAME_PART) + SNAPSHOT_NAME_PART.length(),
                    fileName.length() - XML_NAME_PART.length());
        }

        // For non-snapshot files, try to extract timestamp from the end
        final int minFileNameLength = TIMESTAMP_STRING_LENGTH + XML_NAME_PART.length();
        
        if (fileName.length() < minFileNameLength) {
            // If no timestamp found, return current timestamp
            return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN));
        }

        final String timestampCandidate = fileName.substring(
                fileName.length() - XML_NAME_PART.length() - TIMESTAMP_STRING_LENGTH,
                fileName.length() - XML_NAME_PART.length());

        try {
            LocalDateTime.parse(timestampCandidate, DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN));
            return timestampCandidate;
        } catch (DateTimeParseException e) {
            // If timestamp parsing fails, return current timestamp
            return LocalDateTime.now(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN));
        }
    }

    /**
     * Extracts the timestamp as OffsetDateTime from a file name by looking for the timestamp pattern
     * at the end of the filename (before .xml). This method does not require "_snapshot_"
     * to be present in the filename.
     *
     * @param fileName the name of the file
     * @return the OffsetDateTime if found, null otherwise
     */
    public static OffsetDateTime getLJAFileTimeStampAsOffsetDateTime(final String fileName) {
        final String timeStampAsString = getLJAFileTimeStampAsString(fileName);
        if (timeStampAsString == null) {
            return null;
        }

        try {
            return LocalDateTime.parse(timeStampAsString, DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN))
                    .atOffset(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            logger.warn(format("Received file with invalid filename format, hence skipping file processing, for file : %s. Exception received is : %s", fileName, e));
            return null;
        }
    }

    /**
     * Extracts the file name prefix (everything before the timestamp) from a file name.
     * This method does not require "_snapshot_" to be present in the filename.
     *
     * @param fileName the name of the file
     * @return the file name prefix, or the original filename if timestamp not found
     */
    public static String getLJAFileNamePrefix(final String fileName) {
        final String timeStampAsString = getLJAFileTimeStampAsString(fileName);
        if (timeStampAsString == null) {
            return fileName;
        }

        // Check if the timestamp actually exists in the filename (not generated)
        if (!fileName.contains(timeStampAsString)) {
            return fileName;
        }

        return fileName.substring(0, (fileName.length() - (timeStampAsString.length() + XML_NAME_PART.length())));
    }
}
