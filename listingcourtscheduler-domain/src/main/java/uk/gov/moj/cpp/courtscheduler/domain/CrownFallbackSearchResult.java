package uk.gov.moj.cpp.courtscheduler.domain;

import uk.gov.moj.cpp.courtscheduler.openapi.model.CourtSchedule;

public record CrownFallbackSearchResult(CourtSchedule session, boolean overbooked) {
}
