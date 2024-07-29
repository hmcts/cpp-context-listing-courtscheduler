package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


@ApplicationScoped
public class SlotsUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlotsUpdateService.class.getName());

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;

    public void update(final List<AllocatedSlot> slots) {

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("CHECK: SlotsUpdateService");
        }
        if (isBookingBasedSlot(slots)) {
            LOGGER.info("CHECK: isBookingBasedSlot");
            final AllocatedSlot singleBookingSlot = slots.get(0);
            final List<String> bookingSlots = List.of(singleBookingSlot.getBookingId());
            final Map<String, Date> provisionalBookingCourtScheduleInfo = provisionalBookingRepository.getCourtScheduleInfo(bookingSlots);

            final List<String> provisionalBookingCourtScheduleIdList = new ArrayList<>(provisionalBookingCourtScheduleInfo.keySet());

            if (CollectionUtils.isEmpty(provisionalBookingCourtScheduleIdList)) {
                throw new ProvisionalSlotNotFoundException(format("There is no provisional slot with this bookingId : %s", bookingSlots));
            }

            final List<String> slotsCourtScheduleIdList = slots.stream()
                    .map(AllocatedSlot::getCourtScheduleId)
                    .sorted()
                    .toList();

            if (!isCourtScheduleIdsMatching(slotsCourtScheduleIdList, provisionalBookingCourtScheduleIdList)) {
                throw new CourtScheduleIdNotMatchingException(format("courtScheduleIds not matching. slotsCourtScheduleIdList : %s, provisionalBookingCourtScheduleIdList : %s",
                        slotsCourtScheduleIdList, provisionalBookingCourtScheduleIdList));
            }

            slots.forEach(allocatedSlot -> {
                Date date = provisionalBookingCourtScheduleInfo.get(allocatedSlot.getCourtScheduleId());
                String isoString = DateUtils.toIsoString(new Timestamp(date.getTime()));
                allocatedSlot.setHearingStartTime(isoString);
            });

            courtScheduleRepository.saveBookedSlots(slots, true);
        } else {
            LOGGER.info("CHECK: isNotBookingBasedSlot");
            courtScheduleRepository.saveBookedSlots(slots, false);
        }
    }

    private boolean isCourtScheduleIdsMatching(final List<String> slotsCourtScheduleIdList, final List<String> provisionalBookingCourtScheduleIdList) {
        Collections.sort(provisionalBookingCourtScheduleIdList);

        return slotsCourtScheduleIdList.equals(provisionalBookingCourtScheduleIdList);
    }

    private boolean isBookingBasedSlot(List<AllocatedSlot> slots) {
        return slots.stream()
                .anyMatch(slot -> Objects.nonNull(slot.getBookingId()));
    }

}
