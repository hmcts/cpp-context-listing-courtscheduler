package uk.gov.moj.cpp.courtscheduler.api.service;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;

import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
public class ReferenceDataMapperService {

    @Inject
    private ReferenceDataCache referenceDataCache;

    public Optional<Judiciary> findByEmail(final Requester requester, final String email) {
        return referenceDataCache.getJudiciaries(requester)
                .stream()
                .filter(judiciary -> judiciary.getEmailAddress().equals(email))
                .findFirst();
    }

    public Optional<CourtRoomSessionAllocation> findByOuCodeAndRoomIdAndListingSessionAndBusinessType(final Requester requester,
                                                                                                              final String ouCode,
                                                                                                              final Integer roomId,
                                                                                                              final String listingSession,
                                                                                                              final String businessType) {
        return referenceDataCache.getCourtRoomSessionAllocations(requester)
                .stream()
                .filter(courtRoomSessionAllocation -> ouCode.equals(courtRoomSessionAllocation.getOucode()) &&
                        roomId.equals(courtRoomSessionAllocation.getCourtRoomId()) &&
                        listingSession.equals(courtRoomSessionAllocation.getCourtSession()) &&
                        businessType.equals(courtRoomSessionAllocation.getRotaBusinessTypeCode()))
                .findAny();
    }
}
