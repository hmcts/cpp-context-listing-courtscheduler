package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static java.lang.String.format;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.logging.Level;
import java.util.logging.Logger;

public class FileUtil {

    private static final String FILE_NAME_TIMESTAMP_PATTERN = "yyyyMMdd'T'HHmmss'Z'";
    private static final String SNAPSHOT_NAME_PART = "_snapshot_";
    private static final String XML_NAME_PART = ".xml";

    private FileUtil() {
    }

    public static String getLJASnapshotFileTimeStampAsString(final String fileName) {
        return fileName.substring(fileName.indexOf(SNAPSHOT_NAME_PART) + SNAPSHOT_NAME_PART.length(),
                fileName.length() - XML_NAME_PART.length());
    }

    public static OffsetDateTime getLJASnapshotFileTimeStampAsOffsetDateTime(final String fileName,
                                                                             final Logger logger) {
        final String timeStampAsString = getLJASnapshotFileTimeStampAsString(fileName);
        final OffsetDateTime fileDateTime;

        try {
            fileDateTime = LocalDateTime.parse(timeStampAsString, DateTimeFormatter.ofPattern(FILE_NAME_TIMESTAMP_PATTERN)).atOffset(ZoneOffset.UTC);
            return fileDateTime;
        } catch (DateTimeParseException e) {
            logger.log(Level.WARNING, () -> format("Received file with invalid filename format, hence skipping file processing, for file : %s. Exception received is : %s",
                    fileName, e));
        }

        return null;
    }

    public static String getLJASnapshotFileNamePrefix(final String fileName) {
        return fileName.substring(0, (fileName.length() - (getLJASnapshotFileTimeStampAsString(fileName).length() + ".xml".length())));
    }
}
