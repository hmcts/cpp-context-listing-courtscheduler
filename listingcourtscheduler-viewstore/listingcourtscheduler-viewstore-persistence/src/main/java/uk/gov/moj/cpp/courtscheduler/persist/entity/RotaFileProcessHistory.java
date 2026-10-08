package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;

@SuppressWarnings({"squid:S1845"})
@Entity
@Table(name = "rota_file_process_history")
public class RotaFileProcessHistory {
    @Id
    @Column(name = "execution_id", nullable = false)
    private String executionId;
    @Column(name = "file_name", nullable = false)
    private String fileName;
    @Column(name = "file_hash")
    private String fileHash;
    @Column(name = "file_name_prefix", nullable = false)
    private String fileNamePrefix;
    @Column(name = "file_date", nullable = false)
    private Instant fileDate;
    @CreationTimestamp
    @Column(name = "process_start_date")
    private Instant processStartDate;
    @Column(name = "process_end_date")
    private Instant processEndDate;
    @Column(name = "processed_on", nullable = false)
    private Instant processedOn;


    public Instant getProcessedOn() {
        return processedOn;
    }

    public void setProcessedOn(final Instant processedOn) {
        this.processedOn = processedOn;
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(final String executionId) {
        this.executionId = executionId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(final String fileName) {
        this.fileName = fileName;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(final String fileHash) {
        this.fileHash = fileHash;
    }

    public String getFileNamePrefix() {
        return fileNamePrefix;
    }

    public void setFileNamePrefix(final String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    public Instant getFileDate() {
        return fileDate;
    }

    public void setFileDate(final Instant fileDate) {
        this.fileDate = fileDate;
    }

    public Instant getProcessStartDate() {
        return processStartDate;
    }

    public void setProcessStartDate(final Instant processStartDate) {
        this.processStartDate = processStartDate;
    }

    public Instant getProcessEndDate() {
        return processEndDate;
    }

    public void setProcessEndDate(final Instant processEndDate) {
        this.processEndDate = processEndDate;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RotaFileProcessHistory that = (RotaFileProcessHistory) o;
        return Objects.equals(executionId, that.executionId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId);
    }

    @Override
    public String toString() {
        return "RotaFileProcessHistory{" +
                ", executionId=" + executionId +
                ", fileName=" + fileName +
                ", fileHash=" + fileHash +
                ", fileNamePrefix=" + fileNamePrefix +
                ", fileDate=" + fileDate +
                ", processStartDate=" + processStartDate +
                ", processEndDate=" + processEndDate +
                ", processedOn=" + processedOn +
                '}';
    }
}
