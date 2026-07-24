package uk.gov.moj.cpp.courtscheduler.persist.entity;

import static jakarta.persistence.TemporalType.TIMESTAMP;

import java.util.Date;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Temporal;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.GenericGenerator;

@Entity
@Table(name = "rota_process_log")
public class RotaProcessLog {

    @Id
    @GeneratedValue(generator = "uuid2")
    @GenericGenerator(name = "uuid2", strategy = "org.hibernate.id.UUIDGenerator")
    @Column(name = "id", columnDefinition = "uuid")
    private UUID id;

    @Column(name = "execution_id")
    private String executionId;

    @CreationTimestamp
    @Temporal(TIMESTAMP)
    @Column(name = "timestamp", nullable = false)
    private Date timestamp;

    @Column(name = "error_code", nullable = false)
    private String errorCode;

    @Column(name = "error_text", nullable = false)
    private String errorText;

    public RotaProcessLog() {
        //For JPA
    }

    public String getExecutionId() {
        return executionId;
    }

    public void setExecutionId(final String executionId) {
        this.executionId = executionId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(final String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorText() {
        return errorText;
    }

    public void setErrorText(final String errorText) {
        this.errorText = errorText;
    }

    public UUID getId() {
        return id;
    }

    public void setId(final UUID id) {
        this.id = id;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final RotaProcessLog that = (RotaProcessLog) o;
        return Objects.equals(executionId, that.executionId) && Objects.equals(timestamp, that.timestamp) && Objects.equals(errorCode, that.errorCode) && Objects.equals(errorText, that.errorText);
    }

    @Override
    public int hashCode() {
        return Objects.hash(executionId, timestamp, errorCode, errorText);
    }

    @Override
    public String toString() {
        return "RotaProcessLog{" +
                "executionId='" + executionId + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", errorCode='" + errorCode + '\'' +
                ", errorText='" + errorText + '\'' +
                '}';
    }

    public static final class RotaProcessLogBuilder {
        private String executionId;
        private Date timestamp;
        private String errorCode;
        private String errorText;

        private RotaProcessLogBuilder() {
        }

        public static RotaProcessLogBuilder rotaProcessLog() {
            return new RotaProcessLogBuilder();
        }

        public RotaProcessLogBuilder withExecutionId(String executionId) {
            this.executionId = executionId;
            return this;
        }

        public RotaProcessLogBuilder withTimestamp(Date timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public RotaProcessLogBuilder withErrorCode(String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public RotaProcessLogBuilder withErrorText(String errorText) {
            this.errorText = errorText;
            return this;
        }

        public RotaProcessLog build() {
            RotaProcessLog rotaProcessLog = new RotaProcessLog();
            rotaProcessLog.setExecutionId(executionId);
            rotaProcessLog.setTimestamp(timestamp);
            rotaProcessLog.setErrorCode(errorCode);
            rotaProcessLog.setErrorText(errorText);
            return rotaProcessLog;
        }
    }
}

