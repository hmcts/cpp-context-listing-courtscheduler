package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JudiciaryAssignment {

    private String judiciaryId;
    private String rotaJudiciaryId;
    private List<String> sessionIds = new ArrayList<>();
    private Boolean isDeputy;
    private Boolean isBenchChairman;
    private String position;

    public JudiciaryAssignment() {
        // default constructor
    }

    public JudiciaryAssignment(final String judiciaryId, final List<String> sessionIds) {
        this.judiciaryId = judiciaryId;
        this.sessionIds = sessionIds;
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public void setJudiciaryId(final String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public List<String> getSessionIds() {
        return sessionIds;
    }

    public void setSessionIds(final List<String> sessionIds) {
        this.sessionIds = sessionIds;
    }

    public Boolean getIsDeputy() {
        return isDeputy;
    }

    public void setIsDeputy(final Boolean isDeputy) {
        this.isDeputy = isDeputy;
    }

    public Boolean getIsBenchChairman() {
        return isBenchChairman;
    }

    public void setIsBenchChairman(final Boolean isBenchChairman) {
        this.isBenchChairman = isBenchChairman;
    }

    public String getPosition() {
        return position;
    }

    public void setPosition(final String position) {
        this.position = position;
    }

    public String getRotaJudiciaryId() {
        return rotaJudiciaryId;
    }

    public void setRotaJudiciaryId(final String rotaJudiciaryId) {
        this.rotaJudiciaryId = rotaJudiciaryId;
    }

    public static JudiciaryAssignmentBuilder builder() {
        return new JudiciaryAssignmentBuilder();
    }

    public static final class JudiciaryAssignmentBuilder {
        private String judiciaryId;
        private String rotaJudiciaryId;
        private final List<String> sessionIds = new ArrayList<>();
        private Boolean isDeputy;
        private Boolean isBenchChairman;
        private String position;

        private JudiciaryAssignmentBuilder() {
        }

        public JudiciaryAssignmentBuilder withJudiciaryId(final String judiciaryId) {
            this.judiciaryId = judiciaryId;
            return this;
        }

        public JudiciaryAssignmentBuilder withSessionIds(final List<String> sessionIds) {
            this.sessionIds.clear();
            if (Objects.nonNull(sessionIds)) {
                this.sessionIds.addAll(sessionIds);
            }
            return this;
        }

        public JudiciaryAssignmentBuilder addSessionId(final String sessionId) {
            if (Objects.nonNull(sessionId)) {
                this.sessionIds.add(sessionId);
            }
            return this;
        }

        public JudiciaryAssignmentBuilder withIsDeputy(final Boolean isDeputy) {
            this.isDeputy = isDeputy;
            return this;
        }

        public JudiciaryAssignmentBuilder withIsBenchChairman(final Boolean isBenchChairman) {
            this.isBenchChairman = isBenchChairman;
            return this;
        }

        public JudiciaryAssignmentBuilder withPosition(final String position) {
            this.position = position;
            return this;
        }

        public JudiciaryAssignmentBuilder withRotaJudiciaryId(final String rotaJudiciaryId) {
            this.rotaJudiciaryId = rotaJudiciaryId;
            return this;
        }

        public JudiciaryAssignment build() {
            final JudiciaryAssignment assignment = new JudiciaryAssignment(judiciaryId, new ArrayList<>(sessionIds));
            assignment.setIsDeputy(isDeputy);
            assignment.setIsBenchChairman(isBenchChairman);
            assignment.setPosition(position);
            assignment.setRotaJudiciaryId(rotaJudiciaryId);
            return assignment;
        }
    }
}

