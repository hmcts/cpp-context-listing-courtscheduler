package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.ArrayList;
import java.util.List;

@SuppressWarnings("squid:S2384")
public class AssignCourtroomResponse {

    private List<AssignCourtroomErrorGroup> errorGroups = new ArrayList<>();

    public List<AssignCourtroomErrorGroup> getErrorGroups() {
        return errorGroups;
    }

    public void setErrorGroups(final List<AssignCourtroomErrorGroup> errorGroups) {
        this.errorGroups = errorGroups != null ? errorGroups : new ArrayList<>();
    }
}


