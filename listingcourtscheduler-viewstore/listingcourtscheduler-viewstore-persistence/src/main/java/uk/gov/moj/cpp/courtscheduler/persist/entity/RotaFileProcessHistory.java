package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.sql.Timestamp;
import java.util.Date;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;
import jakarta.persistence.TemporalType;

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
    private Timestamp fileDate;
    @CreationTimestamp
    @Column(name = "process_start_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date processStartDate;
    @Column(name = "process_end_date")
    @Temporal(TemporalType.TIMESTAMP)
    private Date processEndDate;
    @Column(name = "processed_on", nullable = false)
    private Timestamp processedOn;

    public RotaFileProcessHistory() {
        //For JPA
    }

    public Timestamp getProcessedOn() {
        return new Timestamp(processedOn.getTime());
    }

    public void setProcessedOn(Timestamp processedOn) {
        this.processedOn = new Timestamp(processedOn.getTime());
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

    public Timestamp getFileDate() {
        return fileDate;
    }

    public void setFileDate(final Timestamp fileDate) {
        this.fileDate = fileDate;
    }

    public Date getProcessStartDate() {
        return processStartDate;
    }

    public void setProcessStartDate(final Date processStartDate) {
        this.processStartDate = processStartDate;
    }

    public Date getProcessEndDate() {
        return processEndDate;
    }

    public void setProcessEndDate(final Date processEndDate) {
        this.processEndDate = processEndDate;
    }

    @Override
    public boolean equals(Object o) {
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
