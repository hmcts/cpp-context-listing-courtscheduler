package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

@Service
public class CourtScheduleService {

    @Inject
    private CourtScheduleRepository courtScheduleRepository;

    public int deleteRedundantRotaData(final int cleanDataForPreviousMonths) {
        return courtScheduleRepository.deleteRedundantRotaData(cleanDataForPreviousMonths * 30);
    }
}
