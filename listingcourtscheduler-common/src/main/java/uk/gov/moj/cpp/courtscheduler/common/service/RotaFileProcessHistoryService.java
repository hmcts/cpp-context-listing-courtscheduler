package uk.gov.moj.cpp.courtscheduler.common.service;

import static java.sql.Timestamp.valueOf;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.transaction.Transactional;

@ApplicationScoped
public class RotaFileProcessHistoryService {

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    private String computeFileHash(final String fileNamePrefix, final OffsetDateTime fileDate) {
        try {
            final String input = fileNamePrefix + fileDate.toString();
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            final byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));

            final StringBuilder hexString = new StringBuilder();
            for (final byte b : hashBytes) {
                final String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (final NoSuchAlgorithmException e) {
            throw new RuntimeException("MD5 algorithm not available", e);
        }
    }

    @Transactional
    public void update(final String fileNamePrefix, final OffsetDateTime fileDate) {
        final Timestamp fileDateAsTimestamp = Timestamp.from(fileDate.toInstant());
        rotaFileProcessHistoryRepository.deleteByFileNamePrefixAndFileDate(fileNamePrefix, fileDateAsTimestamp);

        final RotaFileProcessHistory rotaFileProcessHistory = new RotaFileProcessHistory();
        rotaFileProcessHistory.setProcessedOn(valueOf(LocalDateTime.now()));
        rotaFileProcessHistory.setFileNamePrefix(fileNamePrefix);
        rotaFileProcessHistory.setFileDate(fileDateAsTimestamp);
        rotaFileProcessHistory.setFileName(fileNamePrefix
                + fileDate.getYear()
                + String.format("%02d", fileDate.getMonthValue())
                + String.format("%02d", fileDate.getDayOfMonth())
                + ".xml");
        rotaFileProcessHistory.setFileHash(computeFileHash(fileNamePrefix, fileDate));
        rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);
    }
}
