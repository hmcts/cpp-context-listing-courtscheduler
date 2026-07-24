package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;

public class JudiciarySpecialismsResponse {
    private List<JudiciarySpecialism> judiciarySpecialisms;

    public JudiciarySpecialismsResponse() {
    }

    public List<JudiciarySpecialism> getJudiciarySpecialisms() {
        return judiciarySpecialisms;
    }

    public void setJudiciarySpecialisms(List<JudiciarySpecialism> judiciarySpecialisms) {
        this.judiciarySpecialisms = judiciarySpecialisms;
    }
}

