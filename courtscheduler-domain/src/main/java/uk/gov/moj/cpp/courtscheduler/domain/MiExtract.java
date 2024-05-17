package uk.gov.moj.cpp.courtscheduler.domain;

import java.io.Serializable;

public class MiExtract implements Serializable {
    private static final long serialVersionUID = 4507970585297966196L;
    private static final String NO_STORAGE_URI_SET = "";

    private final String tableName;
    private final String from;
    private final String to;
    private final String jobId;
    private final int requiredCsvCount;
    private final String storageUri;

    public MiExtract(final String tableName, final String from, final String to,
                     final String jobId, final int requiredCsvCount,
                     final String storageUri) {
        this.tableName = tableName;
        this.from = from;
        this.to = to;
        this.jobId = jobId;
        this.requiredCsvCount = requiredCsvCount;
        this.storageUri = storageUri;
    }

    public static MiExtract create(final String tableName, final MiExtractRange dateRange, final String jobId, final int requiredCsvCount) {
        return new MiExtract(tableName, dateRange.getFromDate(),
                dateRange.getToDate(), jobId, requiredCsvCount,
                NO_STORAGE_URI_SET);
    }

    public String getTableName() {
        return tableName;
    }

    public String getFrom() {
        return from;
    }

    public String getTo() {
        return to;
    }

    public String getJobId() {
        return jobId;
    }

    public int getRequiredCsvCount() {
        return requiredCsvCount;
    }

    public String getOutputDirectory() {
        return getJobId();
    }

    public String getStorageUri() {
        return storageUri;
    }

    @Override
    public String toString() {
        return "MiExtract{" +
                "tableName='" + tableName + '\'' +
                ", from='" + from + '\'' +
                ", to='" + to + '\'' +
                ", jobId='" + jobId + '\'' +
                ", requiredCsvCount='" + requiredCsvCount + '\'' +
                ", storageUri='" + storageUri + '\'' +
                '}';
    }

    public static class Builder {
        private final String tableName;
        private final String from;
        private final String to;
        private final String jobId;
        private final int requiredCsvCount;
        private String storageUri = "";

        public Builder(final MiExtract extract) {
            this.tableName = extract.tableName;
            this.from = extract.from;
            this.to = extract.to;
            this.jobId = extract.jobId;
            this.requiredCsvCount = extract.requiredCsvCount;
            this.storageUri = extract.storageUri;
        }

        public Builder withStorageUri(final String storageUri) {
            this.storageUri = storageUri;
            ;
            return this;
        }

        public MiExtract build() {
            return new MiExtract(tableName, from, to, jobId, requiredCsvCount, storageUri);
        }
    }

}
