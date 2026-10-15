package uk.gov.moj.cpp.courtscheduler.common.service;

import uk.gov.moj.cpp.courtscheduler.common.service.mapper.CourtScheduleJudiciaryMapper;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleJudiciaryRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import jakarta.inject.Inject;
import org.springframework.stereotype.Service;

@Service
public class JudiciaryChangeDetectionService {

    @Inject
    private CourtScheduleJudiciaryRepository courtScheduleJudiciaryRepository;

    public Map<String, List<Integer>> buildCourtScheduleJudiciaryHashMap(final List<String> courtScheduleIds) {
        if (courtScheduleIds.isEmpty()) {
            return Map.of();
        }
        final Map<String, List<CourtScheduleJudiciary>> byScheduleId = new HashMap<>();
        courtScheduleJudiciaryRepository.findInCourtScheduleIds(courtScheduleIds)
                .forEach(entity -> byScheduleId
                        .computeIfAbsent(entity.getId().getCourtScheduleId(), k -> new ArrayList<>())
                        .add(CourtScheduleJudiciaryMapper.toDomain(entity)));
        final Map<String, List<Integer>> hashMap = new HashMap<>();
        byScheduleId.forEach((id, judiciaries) ->
                hashMap.put(id, judiciaries.stream().map(this::hash).toList()));
        return hashMap;
    }

    public List<String> findChangedCourtScheduleIds(
            final Map<String, List<Integer>> preHashMap,
            final Map<String, List<Integer>> postHashMap) {
        final Set<String> allIds = new HashSet<>(preHashMap.keySet());
        allIds.addAll(postHashMap.keySet());
        return allIds.stream()
                .filter(id -> hashesDiffer(preHashMap.get(id), postHashMap.get(id)))
                .toList();
    }

    private boolean hashesDiffer(final List<Integer> preHashes, final List<Integer> postHashes) {
        if (preHashes == null || postHashes == null) {
            return true;
        }
        if (preHashes.size() != postHashes.size()) {
            return true;
        }
        return !preHashes.stream().sorted().toList().equals(postHashes.stream().sorted().toList());
    }

    private int hash(final CourtScheduleJudiciary csj) {
        return Objects.hash(
                csj.getJudiciaryId(),
                csj.getRotaJudiciaryId(),
                csj.getTitle(),
                csj.getForenames(),
                csj.getSurname(),
                csj.getEmailAddress(),
                csj.getCourtScheduleId(),
                csj.getCourtListingProfileId(),
                csj.getJudiciaryType(),
                csj.getPosition(),
                csj.isBenchChairman(),
                csj.isDeputy(),
                csj.isActive());
    }
}