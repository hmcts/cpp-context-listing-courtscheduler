package uk.gov.moj.cpp.courtscheduler.domain;

/**
 * Court-centre (organisation unit) reference data relevant to court-schedule creation.
 * Carries the configured default session start time used when the courtscheduler.create
 * payload does not supply an explicit session start time.
 */
public class OrganisationUnit {

    private String id;
    private String defaultStartTime;

    public String getId() {
        return id;
    }

    public String getDefaultStartTime() {
        return defaultStartTime;
    }

    public static final class OrganisationUnitBuilder {
        private String id;
        private String defaultStartTime;

        private OrganisationUnitBuilder() {
        }

        public static OrganisationUnitBuilder anOrganisationUnit() {
            return new OrganisationUnitBuilder();
        }

        public OrganisationUnitBuilder withId(final String id) {
            this.id = id;
            return this;
        }

        public OrganisationUnitBuilder withDefaultStartTime(final String defaultStartTime) {
            this.defaultStartTime = defaultStartTime;
            return this;
        }

        public OrganisationUnit build() {
            final OrganisationUnit organisationUnit = new OrganisationUnit();
            organisationUnit.id = this.id;
            organisationUnit.defaultStartTime = this.defaultStartTime;
            return organisationUnit;
        }
    }
}
