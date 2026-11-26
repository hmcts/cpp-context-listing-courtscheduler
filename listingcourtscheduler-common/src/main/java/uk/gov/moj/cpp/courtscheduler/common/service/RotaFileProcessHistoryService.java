package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.persist.entity.RotaFileProcessHistory;
import uk.gov.moj.cpp.courtscheduler.repository.RotaFileProcessHistoryRepository;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.OffsetDateTime;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RotaFileProcessHistoryService {

    private static final int SINGLE_HEX_DIGIT_LENGTH = 1;

    @Inject
    private RotaFileProcessHistoryRepository rotaFileProcessHistoryRepository;

    private String computeFileHash(final byte[] content) {
        try {
            final MessageDigest digest = MessageDigest.getInstance("MD5");
            final byte[] hashBytes = digest.digest(content);

            final StringBuilder hexString = new StringBuilder();
            for (final byte b : hashBytes) {
                final String hex = Integer.toHexString(0xff & b);
                if (hex.length() == SINGLE_HEX_DIGIT_LENGTH) {
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
    public RotaFileProcessHistory save(final String fileNamePrefix, final OffsetDateTime fileDate, final byte[] content, final String executionId) {
        final Instant fileDateAsInstant = fileDate.toInstant();

        final RotaFileProcessHistory rotaFileProcessHistory = new RotaFileProcessHistory();
        rotaFileProcessHistory.setExecutionId(executionId);
        rotaFileProcessHistory.setProcessedOn(Instant.now());
        rotaFileProcessHistory.setFileNamePrefix(fileNamePrefix);
        rotaFileProcessHistory.setFileDate(fileDateAsInstant);
        rotaFileProcessHistory.setFileName(fileNamePrefix
                + fileDate.getYear()
                + String.format("%02d", fileDate.getMonthValue())
                + String.format("%02d", fileDate.getDayOfMonth())
                + ".xml");
        rotaFileProcessHistory.setFileHash(computeFileHash(content));
        rotaFileProcessHistory.setProcessStartDate(Instant.now());
        return rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);
    }

    @Transactional
    public RotaFileProcessHistory update(final RotaFileProcessHistory rotaFileProcessHistory) {
        rotaFileProcessHistory.setProcessEndDate(Instant.now());
        return rotaFileProcessHistoryRepository.save(rotaFileProcessHistory);
    }
}
