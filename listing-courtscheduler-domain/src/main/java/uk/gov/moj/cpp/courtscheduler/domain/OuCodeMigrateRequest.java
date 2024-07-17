package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class OuCodeMigrateRequest {

    private List<String> ouCodes;
    private boolean migrated;

    public List<String> getOuCodes() {
        return ouCodes;
    }

    public void setOuCodes(final List<String> ouCodes) {
        this.ouCodes = ouCodes;
    }

    public boolean isMigrated() {
        return migrated;
    }

    public void setMigrated(final boolean migrated) {
        this.migrated = migrated;
    }
}
