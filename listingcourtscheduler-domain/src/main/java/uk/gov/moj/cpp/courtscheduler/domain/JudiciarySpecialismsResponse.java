package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class JudiciarySpecialismsResponse {
    private List<JudiciarySpecialism> judiciarySpecialisms;

    public List<JudiciarySpecialism> getJudiciarySpecialisms() {
        return judiciarySpecialisms;
    }

    public void setJudiciarySpecialisms(final List<JudiciarySpecialism> judiciarySpecialisms) {
        this.judiciarySpecialisms = judiciarySpecialisms;
    }
}

