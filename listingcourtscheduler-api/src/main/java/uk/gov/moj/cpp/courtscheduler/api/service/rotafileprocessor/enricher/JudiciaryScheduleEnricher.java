package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Objects.nonNull;
import static java.util.Optional.empty;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.MissingDataErrorMessages.JUDICIARY_ERR_MSG;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.COURT_LISTING_PROFILE_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.EMAIL_ADDRESS;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDGE_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDGE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDGE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDGE_TITLE;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.JUDICIARY_TYPE;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.MAGISTRATE_FORENAMES;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.MAGISTRATE_SURNAME;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.MAGS_EMAIL;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.MAGS_TITLE;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.ROTA_JUDICIARY_ID;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.SURNAME;
import static uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.enricher.RotaFileFieldNames.TITLE;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.service.ReferenceDataMapperService;
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

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;

@ApplicationScoped
@SuppressWarnings({"squid:S1134", "squid:CommentedOutCodeLine"})
public class JudiciaryScheduleEnricher {

    @Inject
    private JudiciaryBuilder judiciaryBuilder;

    @Inject
    private MissingReferenceDataMappingLogger missingMessageLogger;

    @Inject
    private ReferenceDataMapperService referenceDataMapperService;

    public Collection<CourtScheduleJudiciary> enrichJudiciarySchedules(final Map<String, CourtSchedule> courtScheduleMap,
                                                                       final Map<RotaPayload, Map<String, Map<String, String>>> records,
                                                                       final Requester requester) {
        final Map<String, String> errors = new HashMap<>();
        final List<CourtScheduleJudiciary> courtScheduleJudiciarySchedules = new ArrayList<>();

        final Collection<Map<String, String>> schedules = records.get(RotaPayload.SCHEDULE).values();
        final Map<String, Map<String, String>> judiciariesMap = getJudiciaryInfoMap(records);

        for (final Map<String, String> judiciarySchedule : schedules) {
            final String rotaJusticeId = judiciarySchedule.get(ROTA_JUDICIARY_ID);

            judiciarySchedule.putAll(getJudiciaryInfoFromRota(judiciariesMap, rotaJusticeId));

            enrichJudiciaryFromCppRefdata(judiciarySchedule, errors, requester);

            final String courtListingProfileId = judiciarySchedule.get(COURT_LISTING_PROFILE_ID);
            final CourtSchedule courtSchedule = courtScheduleMap.get(courtListingProfileId);
            if (nonNull(courtSchedule)) {
                final CourtScheduleJudiciary courtScheduleJudiciary = judiciaryBuilder.build(judiciarySchedule, courtSchedule.getCourtScheduleId());
                if (isNotEmpty(courtScheduleJudiciary.getJudiciaryId())) {
                    courtScheduleJudiciarySchedules.add(courtScheduleJudiciary);
                }
            }
        }

        if (!errors.isEmpty()) {
            missingMessageLogger.logJudiciaryMissingMessage(errors.values());
        }

        return courtScheduleJudiciarySchedules;
    }

    private Map<String, Map<String, String>> getJudiciaryInfoMap(final Map<RotaPayload, Map<String, Map<String, String>>> records) {
        final Map<String, Map<String, String>> judiciaryInfoMap = records.getOrDefault(RotaPayload.DISTRICT_JUDGES, new HashMap<>());

        judiciaryInfoMap.putAll(records.getOrDefault(RotaPayload.MAGISTRATES, emptyMap()));

        return judiciaryInfoMap;
    }

    private void enrichJudiciaryFromCppRefdata(final Map<String, String> schedule, final Map<String, String> errors, final Requester requester) {
        final String email = schedule.get(EMAIL_ADDRESS);

        final Optional<uk.gov.moj.cpp.courtscheduler.domain.Judiciary> judiciaryFromMapper = isNotEmpty(email) ? referenceDataMapperService.findByEmail(requester, email.toLowerCase()) : empty();

        if (judiciaryFromMapper.isPresent()) {
            final Judiciary judiciary = judiciaryFromMapper.get();

            schedule.put(JUDICIARY_ID, judiciary.getId());
            schedule.put(TITLE, judiciary.getTitlePrefix());
            schedule.put(FORENAMES, judiciary.getForenames());
            schedule.put(SURNAME, judiciary.getSurname());
            schedule.put(JUDICIARY_TYPE, judiciary.getJudiciaryType());
        } else {
            final String firstName = schedule.get(FORENAMES);
            final String lastName = schedule.get(SURNAME);

            errors.put(email, format(JUDICIARY_ERR_MSG, firstName, lastName, email));
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
}
