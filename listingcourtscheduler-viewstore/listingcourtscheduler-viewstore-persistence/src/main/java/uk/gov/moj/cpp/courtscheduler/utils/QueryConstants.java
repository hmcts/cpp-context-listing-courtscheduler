package uk.gov.moj.cpp.courtscheduler.utils;

public enum QueryConstants {
    EXISTS_PROVISIONAL_DATA_COURT_SCHEDULE("select 1 from provisional_booking pb where pb.active = true and pb.court_schedule_id = cs.id");

    private final String query;

    QueryConstants(final String query) {this.query = query;}

    public String getQuery() { return query; }
}
