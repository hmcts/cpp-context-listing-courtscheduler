package uk.gov.moj.cpp.courtscheduler.persist.entity;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Objects;

@Embeddable
public class RotaFileProcessHistoryKey implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "file_name_prefix", nullable = false)
    private String fileNamePrefix;

    @Column(name = "file_date", nullable = false)
    private Timestamp fileDate;

    public RotaFileProcessHistoryKey() {
        //For JPA
    }

    public String getFileNamePrefix() {
        return fileNamePrefix;
    }

    public void setFileNamePrefix(String fileNamePrefix) {
        this.fileNamePrefix = fileNamePrefix;
    }

    public Timestamp getFileDate() {
        return new Timestamp(fileDate.getTime());
    }

    public void setFileDate(Timestamp fileDate) {
        this.fileDate = new Timestamp(fileDate.getTime());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final RotaFileProcessHistoryKey that = (RotaFileProcessHistoryKey) o;
        return Objects.equals(fileNamePrefix, that.fileNamePrefix) && Objects.equals(fileDate, that.fileDate);
    }

    @Override
    public int hashCode() {
        return Objects.hash(fileNamePrefix, fileDate);
    }

    @Override
    public String toString() {
        return "RotaFileProcessHistoryKey{" +
                "fileNamePrefix='" + fileNamePrefix + '\'' +
                ", fileDate=" + fileDate +
                '}';
    }
}