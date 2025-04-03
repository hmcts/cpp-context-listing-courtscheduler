package uk.gov.moj.cpp.courtscheduler.domain;

import java.util.List;
import java.util.Objects;

public class SearchCourtSchedulesByIdRequestParam {

    private List<String> courtScheduleIds;

    public List<String> getCourtScheduleIds() {
        return courtScheduleIds;
    }

    public SearchCourtSchedulesByIdRequestParam(List<String> courtScheduleIds) {
        this.courtScheduleIds = courtScheduleIds;
    }

    public static final class SearchCourtSchedulesByIdRequestParamBuilder {
        private List<String> courtScheduleIds;

        private SearchCourtSchedulesByIdRequestParamBuilder() {
        }

        public static SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder searchCourtSchedulesByIdRequestParamBuilder() {
            return new SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder();
        }

        public SearchCourtSchedulesByIdRequestParam.SearchCourtSchedulesByIdRequestParamBuilder withCourtScheduleIds(List<String> courtScheduleIds) {
            this.courtScheduleIds = courtScheduleIds;
            return this;
        }

        public SearchCourtSchedulesByIdRequestParam build() {
            return new SearchCourtSchedulesByIdRequestParam(courtScheduleIds);
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (!(o instanceof final SearchCourtSchedulesByIdRequestParam that)) return false;
        return Objects.equals(getCourtScheduleIds(), that.getCourtScheduleIds());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getCourtScheduleIds());
    }

    @Override
    public String toString() {
        return "SearchCourtSchedulesByIdRequestParam{" +
                "courtScheduleIds=" + courtScheduleIds +
                '}';
    }
}
