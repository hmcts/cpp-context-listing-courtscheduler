package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.equalsIgnoreCase;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.common.exception.MissingDataError.JUDICIARY_ERR_MSG;
import static uk.gov.moj.cpp.courtscheduler.common.utils.ProcessingDataInfoMessages.MISSING_SLOT_FOR_JUDICIARY_WARNING_MSG;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDGE_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGISTRATE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.MAGS_TITLE;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.domain.rota.RotaFileFieldNames.TITLE;

// (removed) replaced by Spring CommonPlatformQueryClient
import uk.gov.moj.cpp.courtscheduler.common.service.ReferenceDataMapperService;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.domain.Judiciary;
import uk.gov.moj.cpp.courtscheduler.domain.rota.RotaPayload;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@SuppressWarnings({"squid:S1134", "squid:CommentedOutCodeLine"})
public class JudiciaryScheduleEnricher {

    @Inject
    private JudiciaryBuilder judiciaryBuilder;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    private static final Logger logger = LoggerFactory.getLogger(JudiciaryScheduleEnricher.class);

    public Collection<CourtScheduleJudiciary> enrichJudiciarySchedules(final Map<String, CourtSchedule> courtScheduleMap,
                                                                       final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                                       final boolean forMigrated,
                                                                       final List<CourtSchedule> activeCourtSchedulesByOuCodesWithinDateRange,
                                                                       final String executionId,
                                                                       final Map<String, String> errors,
                                                                       final Map<String, List<String>> missingSessionsByOuCode) {
        final List<CourtScheduleJudiciary> courtScheduleJudiciarySchedules = new ArrayList<>();

        final long enrichmentStart = System.nanoTime();
        final Collection<Map<String, String>> schedules = records.get(RotaPayload.SCHEDULE).values();
        final Map<String, Map<String, String>> judiciariesMap = getJudiciaryInfoMap(records);

        final Map<String, CourtSchedule> activeCourtScheduleMap = activeCourtSchedulesByOuCodesWithinDateRange.stream()
                .collect(Collectors.toMap(
                        this::getCourtScheduleKey,
                        cs -> cs,
                        (v1, v2) -> v1
                ));

        for (final Map<String, String> judiciarySchedule : schedules) {
            final String rotaJusticeId = judiciarySchedule.get(ROTA_JUDICIARY_ID);

            judiciarySchedule.putAll(getJudiciaryInfoFromRota(judiciariesMap, rotaJusticeId));

            enrichJudiciaryFromCppRefdata(judiciarySchedule, errors);

            final String courtListingProfileId = judiciarySchedule.get(COURT_LISTING_PROFILE_ID);
            final CourtSchedule courtSchedule = courtScheduleMap.get(courtListingProfileId);
            if (nonNull(courtSchedule)) {
                final String key = getCourtScheduleKey(courtSchedule);
                final CourtSchedule foundSchedule = activeCourtScheduleMap.get(key);
                final Optional<CourtSchedule> courtScheduleOptional = Optional.ofNullable(foundSchedule);

                if (!forMigrated || (courtScheduleOptional.isPresent() && equalsIgnoreCase(courtSchedule.getOuCode(), courtScheduleOptional.get().getOuCode()))) {
                    final CourtScheduleJudiciary courtScheduleJudiciary = judiciaryBuilder.build(judiciarySchedule, courtSchedule.getCourtScheduleId());
                    if (isNotEmpty(courtScheduleJudiciary.getJudiciaryId())) {
                        courtScheduleJudiciarySchedules.add(courtScheduleJudiciary);
                    }
                } else if (courtScheduleOptional.isEmpty()) {
                    logger.warn(format(MISSING_SLOT_FOR_JUDICIARY_WARNING_MSG,
                            courtSchedule.getSessionDate(),
                            courtSchedule.getCourtHouseName(),
                            courtSchedule.getCourtRoomName(),
                            courtSchedule.getBusinessType(),
                            courtSchedule.getCourtSession(),
                            courtSchedule.getPanel()));
                    recordMissingSession(missingSessionsByOuCode, courtSchedule);
                }
            } else {
                final String errorMessage = format("No matching court schedule found for court listing profile ID: %s", courtListingProfileId);
                logger.debug(errorMessage);
            }
        }
        final long enrichmentEnd = System.nanoTime();
        logger.info("PRF: Time taken for judiciary enrichment : {}", (enrichmentEnd - enrichmentStart) / 1000000);

        return courtScheduleJudiciarySchedules;
    }

    private Map<String, Map<String, String>> getJudiciaryInfoMap(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> judiciaryInfoMap = records.getOrDefault(RotaPayload.DISTRICT_JUDGES, new HashMap<>());

        judiciaryInfoMap.putAll(records.getOrDefault(RotaPayload.MAGISTRATES, emptyMap()));

        return judiciaryInfoMap;
    }

    private void enrichJudiciaryFromCppRefdata(final Map<String, String> schedule, final Map<String, String> errors) {
        final String email = schedule.get(EMAIL_ADDRESS);

        // Only process if email is present and not blank
        if (isBlank(email)) {
            return;
        }

        final Optional<uk.gov.moj.cpp.courtscheduler.domain.Judiciary> judiciaryFromMapper = referenceDataMapperService.findByEmail(email);

        if (judiciaryFromMapper.isPresent()) {
            final Judiciary judiciary = judiciaryFromMapper.get();

            schedule.put(JUDICIARY_ID, judiciary.getId());
            schedule.put(TITLE, judiciary.getTitlePrefix());
            schedule.put(FORENAMES, judiciary.getForenames());
            schedule.put(SURNAME, judiciary.getSurname());
            schedule.put(JUDICIARY_TYPE, judiciary.getJudiciaryType());
        } else {
            // Only log if names and email are not blank
            final String firstName = schedule.get(FORENAMES);
            final String lastName = schedule.get(SURNAME);

            if (isNotEmpty(firstName) && isNotEmpty(lastName) && isNotEmpty(email)) {
                errors.put(email, JUDICIARY_ERR_MSG.format(firstName, lastName, email));
            }
        }
    }

    private Map<String, String> getJudiciaryInfoFromRota(final Map<String, Map<String, String>> judiciary, final String justiceId) {
        final Map<String, String> judiciaryDetails = new HashMap<>();

        final Map<String, String> judiciaryProps = judiciary.getOrDefault(justiceId, emptyMap());

        if (!judiciaryProps.isEmpty()) {
            judiciaryDetails.put(TITLE, getOrElse(judiciaryProps, MAGS_TITLE, JUDGE_TITLE));
            judiciaryDetails.put(FORENAMES, getOrElse(judiciaryProps, MAGISTRATE_FORENAMES, JUDGE_FORENAMES));
            judiciaryDetails.put(SURNAME, getOrElse(judiciaryProps, MAGISTRATE_SURNAME, JUDGE_SURNAME));
            judiciaryDetails.put(EMAIL_ADDRESS, getOrElse(judiciaryProps, MAGS_EMAIL, JUDGE_EMAIL));
        }

        return judiciaryDetails;
    }

    private String getOrElse(final Map<String, String> props, final String key, final String defaultKey) {
        final String value = props.get(key);

        if (!isBlank(value)) {
            return value;
        }

        return props.get(defaultKey);
    }

    private void recordMissingSession(final Map<String, List<String>> missingSessionsByOuCode, final CourtSchedule courtSchedule) {
        final String ouCode = defaultIfBlank(courtSchedule.getOuCode(), "UNKNOWN_OUCODE");
        final String sessionDetails = format("%s - %s - %s - %s - %s - %s",
                courtSchedule.getSessionDate(),
                defaultIfBlank(courtSchedule.getCourtHouseName(), "UNKNOWN_COURTHOUSE"),
                defaultIfBlank(courtSchedule.getCourtRoomName(), "UNKNOWN_COURTROOM"),
                defaultIfBlank(courtSchedule.getBusinessType(), "UNKNOWN_BUSINESS_TYPE"),
                defaultIfBlank(courtSchedule.getCourtSession(), "UNKNOWN_SESSION"),
                defaultIfBlank(courtSchedule.getPanel(), "UNKNOWN_PANEL"));
        missingSessionsByOuCode
                .computeIfAbsent(ouCode, key -> new ArrayList<>())
                .add(sessionDetails);
    }

    private String getCourtScheduleKey(final CourtSchedule cs) {
        return cs.getCourtRoomId() + "|" + cs.getSessionDate() + "|" + cs.getBusinessType() + "|" + cs.getCourtSession();
    }
}
