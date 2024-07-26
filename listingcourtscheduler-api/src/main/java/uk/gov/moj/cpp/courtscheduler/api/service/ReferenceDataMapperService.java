package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Objects.isNull;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoomSessionAllocation;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;

import java.util.List;
import java.util.Optional;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ReferenceDataMapperService {

    private static Logger logger = LoggerFactory.getLogger(ReferenceDataMapperService.class);

    @Inject
    private ReferenceDataCache referenceDataCache;

    private List<Judiciary> judiciaries;

    private List<CourtRoomSessionAllocation> courtRoomSessionAllocations;

    public Optional<Judiciary> findByEmail(final Requester requester, final String email) {
        logger.info("judiciary findByEmail being called for email {}", email);
        this.judiciaries = isNull(judiciaries) ? referenceDataCache.getJudiciaries(requester) : judiciaries;

        final Optional<Judiciary> judiciaryOptional = judiciaries
                .stream()
                .filter(judiciary -> judiciary.getEmailAddress().equals(email))
                .findFirst();

        logger.info("judiciary found for email {} with judiciary : {}", email, judiciaryOptional.orElse(null));

        return judiciaryOptional;
    }

    public Optional<CourtRoomSessionAllocation> findByOuCodeAndRoomIdAndListingSessionAndBusinessType(final Requester requester,
                                                                                                      final String ouCode,
                                                                                                      final Integer roomId,
                                                                                                      final String listingSession,
                                                                                                      final String businessType) {
        courtRoomSessionAllocations = isNull(courtRoomSessionAllocations) ? referenceDataCache.getCourtRoomSessionAllocations(requester) : courtRoomSessionAllocations;
        return courtRoomSessionAllocations
                .stream()
                .filter(courtRoomSessionAllocation -> ouCode.equals(courtRoomSessionAllocation.getOucode()) &&
                        roomId.equals(courtRoomSessionAllocation.getCourtRoomId()) &&
                        listingSession.equals(courtRoomSessionAllocation.getCourtSession()) &&
                        businessType.equals(courtRoomSessionAllocation.getRotaBusinessTypeCode()))
                .findAny();
    }
}
