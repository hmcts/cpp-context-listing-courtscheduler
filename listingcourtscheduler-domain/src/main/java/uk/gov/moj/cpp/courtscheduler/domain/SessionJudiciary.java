package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.Objects;

/**
 * Judiciary line item for user-facing session assignment (cartesian with court schedules).
 */
public class SessionJudiciary {

    private String judicialId;
    private String judiciaryType;
    private Boolean isBenchChairman;
    private Boolean isDeputy;

    public SessionJudiciary() {
    }

    public String getJudicialId() {
        return judicialId;
    }

    public void setJudicialId(final String judicialId) {
        this.judicialId = judicialId;
    }

    public String getJudiciaryType() {
        return judiciaryType;
    }

    public void setJudiciaryType(final String judiciaryType) {
        this.judiciaryType = judiciaryType;
    }

    public Boolean getIsBenchChairman() {
        return isBenchChairman;
    }

    public void setIsBenchChairman(final Boolean benchChairman) {
        isBenchChairman = benchChairman;
    }

    public Boolean getIsDeputy() {
        return isDeputy;
    }

    public void setIsDeputy(final Boolean deputy) {
        isDeputy = deputy;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String judicialId;
        private String judiciaryType;
        private Boolean isBenchChairman;
        private Boolean isDeputy;

        public Builder withJudicialId(final String judicialId) {
            this.judicialId = judicialId;
            return this;
        }

        public Builder withJudiciaryType(final String judiciaryType) {
            this.judiciaryType = judiciaryType;
            return this;
        }

        public Builder withIsBenchChairman(final Boolean isBenchChairman) {
            this.isBenchChairman = isBenchChairman;
            return this;
        }

        public Builder withIsDeputy(final Boolean isDeputy) {
            this.isDeputy = isDeputy;
            return this;
        }

        public SessionJudiciary build() {
            final SessionJudiciary s = new SessionJudiciary();
            s.setJudicialId(judicialId);
            s.setJudiciaryType(judiciaryType);
            s.setIsBenchChairman(isBenchChairman);
            s.setIsDeputy(isDeputy);
            return s;
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SessionJudiciary that = (SessionJudiciary) o;
        return Objects.equals(judicialId, that.judicialId)
                && Objects.equals(judiciaryType, that.judiciaryType)
                && Objects.equals(isBenchChairman, that.isBenchChairman)
                && Objects.equals(isDeputy, that.isDeputy);
    }

    @Override
    public int hashCode() {
        return Objects.hash(judicialId, judiciaryType, isBenchChairman, isDeputy);
    }
}
