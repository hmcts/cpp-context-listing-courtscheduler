package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JudiciarySpecialism {
    private String judiciaryId;
    private List<JudiciarySpecialismType> specialisms;

    public JudiciarySpecialism() {
        this.specialisms = new ArrayList<>();
    }

    public JudiciarySpecialism(String judiciaryId, List<JudiciarySpecialismType> specialisms) {
        this.judiciaryId = judiciaryId;
        this.specialisms = specialisms != null ? new ArrayList<>(specialisms) : new ArrayList<>();
    }

    public String getJudiciaryId() {
        return judiciaryId;
    }

    public void setJudiciaryId(String judiciaryId) {
        this.judiciaryId = judiciaryId;
    }

    public List<JudiciarySpecialismType> getSpecialisms() {
        return specialisms;
    }

    public void setSpecialisms(List<JudiciarySpecialismType> specialisms) {
        this.specialisms = specialisms != null ? new ArrayList<>(specialisms) : new ArrayList<>();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        JudiciarySpecialism that = (JudiciarySpecialism) o;
        return Objects.equals(judiciaryId, that.judiciaryId) &&
                Objects.equals(specialisms, that.specialisms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(judiciaryId, specialisms);
    }

    @Override
    public String toString() {
        return "JudiciarySpecialism{" +
                "judiciaryId='" + judiciaryId + '\'' +
                ", specialisms=" + specialisms +
                '}';
    }
}

