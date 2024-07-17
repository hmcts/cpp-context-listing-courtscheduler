package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.Objects.nonNull;

import uk.gov.justice.services.core.requester.Requester;
import uk.gov.moj.cpp.courtscheduler.api.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.BusinessType;
import uk.gov.moj.cpp.courtscheduler.domain.CourtRoom;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.CreateSessionRequestParam;
import uk.gov.moj.cpp.courtscheduler.domain.OuCodeMigrateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatFrequency;
import uk.gov.moj.cpp.courtscheduler.domain.RepeatPattern;
import uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.domain.Session;
import uk.gov.moj.cpp.courtscheduler.domain.SessionsParam;
import uk.gov.moj.cpp.courtscheduler.domain.UpdateCourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtMigrationRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.apache.deltaspike.data.api.QueryInvocationException;

@ApplicationScoped
public class SessionsService {
    private static final String BUSINESS_TYPE_NOT_FOUND = "Business Type not found";
    private static final String COURTROOM_NOT_FOUND = "Court Room not found";
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private CourtMigrationRepository courtMigrationRepository;
    @Inject
    private ReferenceDataCache referenceDataCache;

    public void create(CreateSessionRequestParam createSessionRequestParam, Requester requester) {
        final List<CourtSchedule> courtScheduleList = new ArrayList<>();
        final List<Session> sessionList = createSessionRequestParam.getSessionList();
        final RepeatPattern repeatPattern = createSessionRequestParam.getRepeatPattern();
        final LocalDate startDate = repeatPattern.getStartDate();
        final LocalDate endDate = repeatPattern.getEndDate();

        if (repeatPattern.getFrequency().equals(RepeatFrequency.ONCE)) {
            processOnceFrequency(sessionList, startDate, courtScheduleList,requester);
        } else if (repeatPattern.getFrequency().equals(RepeatFrequency.EVERY_WEEK)) {
            processWeeklyFrequency(sessionList, startDate, endDate, repeatPattern.getRepeatFor(), courtScheduleList,requester);
        }

        saveCourtSchedules(courtScheduleList);
    }

    public List<CourtSchedule> getCourtSchedules(CourtScheduleRequestParam courtScheduleRequestParam, Requester requester) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.findBy(courtScheduleRequestParam);
        courtSchedules.forEach(courtSchedule -> courtSchedule.setBusinessDescription(enrichBusinessDescription(courtSchedule.getBusinessType(), requester)));
        return courtSchedules;
    }

    public Result update(UpdateCourtSchedule updateCourtSchedule, Requester requester) {
        uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule = courtScheduleRepository.findBy(updateCourtSchedule.getCourtScheduleId());
        if (Objects.isNull(persistedCourtSchedule)) {
            return new Result("Court Schedule not found", false);
        }
        final String persistedBusinessType = persistedCourtSchedule.getBusinessType();
        if (isBusinessTypeChangeInvalid(updateCourtSchedule, requester, persistedBusinessType)) {
            return new Result("Business Type cannot be changed from Slot to Non-Slot and vice versa", false);
        }
        updateAvailability(updateCourtSchedule, persistedCourtSchedule);

        String courtRoomId = updateCourtSchedule.getCourtRoomId();

        final Optional<CourtRoom> courtRoom;
        if (courtRoomId != null && !courtRoomId.equalsIgnoreCase(persistedCourtSchedule.getCourtRoomId())) {
            courtRoom = Optional.of(referenceDataCache.getRotaCourtRoomByCourtRoomId(courtRoomId,requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + courtRoomId)));
        } else {
            courtRoom = Optional.empty();
        }


        return courtScheduleRepository.update(persistedCourtSchedule, updateCourtSchedule, courtRoom);
    }

    private void updateAvailability(final UpdateCourtSchedule updateCourtSchedule, final uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule persistedCourtSchedule) {
        final Integer totalListedDuration = allocatedListingRepository.findTotalAllocatedDurationByCourtScheduleId(updateCourtSchedule.getCourtScheduleId());
        //Assuming that businessType won't be changing from slot to non-slot or vice versa
        if (persistedCourtSchedule.isSlotBased()) {
            updateCourtSchedule.setAvailableSlots(updateCourtSchedule.getMaxSlots() - (nonNull(totalListedDuration) ? totalListedDuration : 0));
            updateCourtSchedule.setMaxDuration(0);
            updateCourtSchedule.setAvailableDuration(0);
        } else {
            updateCourtSchedule.setAvailableDuration(updateCourtSchedule.getMaxDuration() - (nonNull(totalListedDuration) ? totalListedDuration: 0));
            updateCourtSchedule.setMaxSlots(0);
            updateCourtSchedule.setAvailableSlots(0);

        }
    }

    private  boolean isBusinessTypeChangeInvalid(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessType) {
        return !persistedBusinessType.equals(updateCourtSchedule.getBusinessType()) && !isBusinessTypeChangeAllowed(updateCourtSchedule, requester, persistedBusinessType);
    }

    private  boolean isBusinessTypeChangeAllowed(final UpdateCourtSchedule updateCourtSchedule, final Requester requester, final String persistedBusinessTypeCode) {
        final BusinessType persistedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(persistedBusinessTypeCode, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + persistedBusinessTypeCode));
        final BusinessType updatedBusinessType = referenceDataCache.getRotaBusinessTypeByCode(updateCourtSchedule.getBusinessType(), requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + updateCourtSchedule.getBusinessType()));
        return persistedBusinessType.isSlot()==updatedBusinessType.isSlot() && isUpdateRequestParamsAreValidForUpdate(updateCourtSchedule, updatedBusinessType.isSlot());
    }

    private static boolean isUpdateRequestParamsAreValidForUpdate(final UpdateCourtSchedule updateCourtSchedule, final boolean isSlotBased) {
        return (isSlotBased && updateCourtSchedule.getMaxDuration().equals(0)) || (!isSlotBased && updateCourtSchedule.getMaxSlots().equals(0));
    }

    public JsonObject deleteCourtScheduleSessions(final SessionsParam sessionsParam) {
        List<CourtSchedule> courtSchedules = courtScheduleRepository.deleteCourtSchedule(sessionsParam.getSessions());

        final ListToJsonArrayConverter<CourtSchedule> listToJsonArrayConverter = new ListToJsonArrayConverter<>();
        JsonArray jsonArray = courtSchedules.isEmpty() ? JsonValue.EMPTY_JSON_ARRAY : listToJsonArrayConverter.convert(courtSchedules);
        return Json.createObjectBuilder()
                .add(RequestParameterConstant.SESSIONS.getLabel(), jsonArray)
                .build();
    }

    public boolean isMigrated(final String ouCode) {
        return courtMigrationRepository.findByOuCode(ouCode).isMigrated();
    }

    public boolean isMigratedByCourtCentreId(final String courtCentreId) {
        return courtMigrationRepository.findByCourtCentreId(courtCentreId).isMigrated();
    }

    public Result migrateOuCodes(OuCodeMigrateRequest ouCodeMigrateRequest) {
        List<String> ouCodes = ouCodeMigrateRequest.getOuCodes();
        boolean migrated = ouCodeMigrateRequest.isMigrated();
        List<CourtSchedulerMigrationStatus> courtSchedulerMigrationStatusList = new ArrayList<>();
        final AtomicBoolean isOuCodeNotPresent = new AtomicBoolean(false);

        ouCodes.forEach(ouCode -> {
            CourtSchedulerMigrationStatus courtSchedulerMigrationStatus = courtMigrationRepository.findByOuCode(ouCode);
            if(courtSchedulerMigrationStatus == null) {
                isOuCodeNotPresent.set(true);
            }
            courtSchedulerMigrationStatusList.add(courtSchedulerMigrationStatus);
        });

        if (isOuCodeNotPresent.get()) {
            return new Result("One of the OuCode not present for migrate", false);
        }

        courtSchedulerMigrationStatusList.forEach(courtSchedulerMigrationStatus -> {
                courtSchedulerMigrationStatus.setMigrated(migrated);
                courtMigrationRepository.save(courtSchedulerMigrationStatus);
        });

        return Result.SUCCESS();
    }

    private String enrichBusinessDescription(final String businessType, final Requester requester) {
        return referenceDataCache.getRotaBusinessTypeByCode(businessType, requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + businessType)).getTypeDescription();
    }

    private void processOnceFrequency(List<Session> sessionList, LocalDate startDate, List<CourtSchedule> courtScheduleList,Requester requester) {
        for (Session session : sessionList) {
            for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                LocalDate sessionDateCandidate = startDate.with(TemporalAdjusters.nextOrSame(dayOfWeek));
                CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate,requester);
                courtScheduleList.add(courtSchedule);
            }
        }
    }

    private void processWeeklyFrequency(List<Session> sessionList, LocalDate startDate, LocalDate endDate, int repeatFor, List<CourtSchedule> courtScheduleList,Requester requester) {
        final long weeksBetween = ChronoUnit.WEEKS.between(startDate, endDate);
        for (long weekNumber = 0; weekNumber <= weeksBetween; weekNumber += repeatFor) {
            for (Session session : sessionList) {
                for (DayOfWeek dayOfWeek : session.getRepeatDays()) {
                    LocalDate sessionDateCandidate = startDate.plusWeeks(weekNumber).with(TemporalAdjusters.nextOrSame(dayOfWeek));
                    if (sessionDateCandidate.isAfter(endDate)) {
                        continue;
                    }
                    CourtSchedule courtSchedule = buildCourtSchedule(session, sessionDateCandidate,requester);
                    courtScheduleList.add(courtSchedule);
                }
            }
        }
    }

    private CourtSchedule buildCourtSchedule(Session session, LocalDate sessionDateCandidate,Requester requester) {
        final CourtSchedule.CourtScheduleBuilder courtScheduleBuilder = new CourtSchedule.CourtScheduleBuilder();
        courtScheduleBuilder.withCourtScheduleId(UUID.randomUUID().toString())
                .withBusinessType(session.getBusinessType())
                .withCourtHouseId(session.getCourtCentreId())
                .withCourtRoomId(session.getCourtRoomId())
                .withActive(true)
                .withSessionDate(sessionDateCandidate)
                .withCourtSession(session.getSessionType())
                .withPanel(session.getPanelType());
        enrichSession(courtScheduleBuilder, session.getSlotsOrDuration(),requester);
        return courtScheduleBuilder.build();
    }

    private void saveCourtSchedules(List<CourtSchedule> courtScheduleList) {
        List<uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule> courtScheduleEntities = courtScheduleList.stream()
                .map(CourtScheduleMapper::toEntity)
                .toList();
        courtScheduleEntities.forEach(courtSchedule -> {
            try {
                courtScheduleRepository.save(courtSchedule);
            } catch (QueryInvocationException queryInvocationException) {
                courtScheduleRepository.update(courtSchedule);
            }
        });
    }

    private void enrichSession(CourtSchedule.CourtScheduleBuilder builder, int maxSlotsorDuration,Requester requester) {
        final BusinessType businessType = referenceDataCache.getRotaBusinessTypeByCode(builder.getBusinessType(),requester).orElseThrow(() -> new RuntimeException(BUSINESS_TYPE_NOT_FOUND + builder.getBusinessType()));
        final CourtRoom courtRoom = referenceDataCache.getRotaCourtRoomByCourtRoomId(builder.getCourtRoomId(),requester).orElseThrow(() -> new RuntimeException(COURTROOM_NOT_FOUND + builder.getCourtRoomId()));
        if (businessType.isSlot()) {
            builder.withSlotBased(true);
            builder.withMaxSlots(maxSlotsorDuration);
            builder.withAvailableSlots(maxSlotsorDuration);
            builder.withMaxDuration(0);
            builder.withAvailableDuration(0);
        } else {
            builder.withSlotBased(false);
            builder.withMaxDuration(maxSlotsorDuration);
            builder.withAvailableDuration(maxSlotsorDuration);
            builder.withMaxSlots(0);
            builder.withAvailableSlots(0);
        }

        if (courtRoom != null) {
            builder.withOuCode(courtRoom.getOucode());
            builder.withCourtRoomName(courtRoom.getCourtroomName());
            builder.withCourtRoomNumber(courtRoom.getCppCourtRoomId());
            builder.withCourtHouseName(courtRoom.getOucodeL3Name());
            builder.withOperationalUnit(courtRoom.getOucodeL2Code());
        }
    }
}