package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Date;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

@SuppressWarnings({"pmd:BeanMembersShouldSerialize", "squid:S00121", "squid:S00122", "squid:S1067"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RotaProcessLog {

    private String executionId;

    private Date timestamp;

    private String errorCode;

    private String errorText;

    @SuppressWarnings("squid:S1186")
    public RotaProcessLog() {

    }

    public RotaProcessLog(final Builder builder) {
        this.executionId = builder.executionId;
        this.timestamp = builder.timestamp;
        this.errorCode = builder.errorCode;
        this.errorText = builder.errorText;
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

    public static Builder judiciary() {
        return new RotaProcessLog.Builder();
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
                ", timestamp=" + timestamp +
                ", errorCode='" + errorCode + '\'' +
                ", errorText='" + errorText + '\'' +
                '}';
    }

    public static class Builder {

        private String executionId;

        private Date timestamp;

        private String errorCode;

        private String errorText;

        public Builder withExecutionId(final String executionId) {
            this.executionId = executionId;
            return this;
        }

        public Builder withTimestamp(final Date timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder withErrorCode(final String errorCode) {
            this.errorCode = errorCode;
            return this;
        }

        public Builder withErrorText(final String errorText) {
            this.errorText = errorText;
            return this;
        }

        public RotaProcessLog build() {
            return new RotaProcessLog(this);
        }
    }
}
