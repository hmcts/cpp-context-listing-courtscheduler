package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.lang.String.format;
import static jakarta.json.Json.createArrayBuilder;
import static jakarta.json.Json.createObjectBuilder;

import uk.gov.moj.cpp.courtscheduler.domain.AllocatedSlot;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ChangeCourtRoomForMultidayHearingResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackResponse;
import uk.gov.moj.cpp.courtscheduler.domain.CrownFallbackSearchResult;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.CrownSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Hearing;
import uk.gov.moj.cpp.courtscheduler.domain.ListHearingSlotsResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MagsSearchAndBookResponse;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateRequest;
import uk.gov.moj.cpp.courtscheduler.domain.MoveHearingToPastDateResponse;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedDay;
import uk.gov.moj.cpp.courtscheduler.domain.RequestedSlots;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingRequest;
import uk.gov.moj.cpp.courtscheduler.domain.ReserveUnconfirmedHearingResponse;
import uk.gov.moj.cpp.courtscheduler.domain.Result;
import uk.gov.moj.cpp.courtscheduler.common.service.ExtendMultidayHearingService;
import uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
import uk.gov.moj.cpp.courtscheduler.exception.ConfirmedBookingExistsException;
import uk.gov.moj.cpp.courtscheduler.exception.CourtScheduleIdNotMatchingException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackInvalidRequestException;
import uk.gov.moj.cpp.courtscheduler.exception.CrownFallbackNoSessionException;
import uk.gov.moj.cpp.courtscheduler.exception.NoAllocationOnDateException;
import uk.gov.moj.cpp.courtscheduler.exception.NoSessionAvailableException;
import uk.gov.moj.cpp.courtscheduler.exception.ProvisionalSlotNotFoundException;
import uk.gov.moj.cpp.courtscheduler.exception.SlotsBookException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.apache.commons.collections.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
@org.springframework.transaction.annotation.Transactional
public class SlotsUpdateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(SlotsUpdateService.class);
    private static final int MINUTES_IN_DAY = 360;

    public static final String HEARING_DATE = "hearingDate";
    public static final String COURT_SCHEDULE_ID = "courtScheduleId";
    public static final String SCHEDULES = "schedules";

    private static final String SOURCE_RESERVED_UNCONFIRMED = "RESERVED_UNCONFIRMED";

    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private ExtendMultidayHearingService extendMultidayHearingService;

    @Value("${courtscheduler.reserve-unconfirmed-hearing.ttl-days:1}")
    private int reserveUnconfirmedHearingTtlDays;

    public JsonObject update(final List<AllocatedSlot> slots) {
        final Result slotUpdateResult;
        if (isBookingBasedSlot(slots)) {
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

            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, true, false);
        } else {
            slotUpdateResult = courtScheduleRepository.saveBookedSlots(slots, false, false);
        }

        final JsonArrayBuilder hearingDaysJsonArrBuilder = createArrayBuilder();
        slotUpdateResult.getHearingDayCourtSchedules().forEach( (day, schedule) -> {
            JsonObject hearingDaySchedule = createObjectBuilder().add(HEARING_DATE, day).add(COURT_SCHEDULE_ID, schedule).build();
            hearingDaysJsonArrBuilder.add(hearingDaySchedule);
        });

        return createObjectBuilder().add(SCHEDULES, hearingDaysJsonArrBuilder).build();
    }

    /**
     * Reserve an unconfirmed hearing against a session (LPT-2433): books it through the same
     * capacity-decrementing pipeline as a real booking, but stamps {@code expires_at} on the
     * resulting {@code allocated_listings} row so the purge job (also LPT-2433) sweeps it up if
     * it's never confirmed. Rejects with {@link ConfirmedBookingExistsException} when the hearing
     * already has a CONFIRMED allocation elsewhere ({@code expires_at IS NULL}) — otherwise the
     * shared pipeline's hearing-wide release would silently drop the real booking. Re-reserving
     * over an existing UNCONFIRMED reservation is allowed (refreshes/moves it).
     */
    public ReserveUnconfirmedHearingResponse reserveUnconfirmedHearing(final String sessionId, final String hearingId,
                                                                       final ReserveUnconfirmedHearingRequest request) {
        final boolean hasConfirmedAllocation = allocatedListingRepository.findByHearingId(hearingId).stream()
                .anyMatch(allocation -> allocation.getExpiresAt() == null);
        if (hasConfirmedAllocation) {
            throw new ConfirmedBookingExistsException(
                    "Hearing " + hearingId + " already has a confirmed allocation — cannot reserve");
        }

        // findCourtScheduleById projects via convertForOverbooking, which only populates
        // courtScheduleId/isOverbookingAllowed/active — not ouCode/sessionDate, which this method
        // needs. getCourtSchedulesByIdList uses the full-fields projection instead (same fix
        // toResponseFromExisting applies above for the same reason).
        final CourtSchedule session = courtScheduleRepository.getCourtSchedulesByIdList(List.of(sessionId)).stream()
                .filter(CourtSchedule::isActive)
                .findFirst()
                .orElseThrow(() -> new NoSessionAvailableException("No session found for sessionId " + sessionId));

        // A fixed Duration from now gives every reservation the same hold length regardless of
        // time of day — a midnight-boundary TTL would let one made at 11pm expire in an hour while
        // one made at 9am lasts ~39 hours for the same reserveUnconfirmedHearingTtlDays. Matches
        // the purge job's own Instant.now() cutoff (AllocatedListingService#purgeExpiredReservedSessions).
        final Date expiresAt = Date.from(Instant.now().plus(Duration.ofDays(reserveUnconfirmedHearingTtlDays)));

        final AllocatedSlot slot = new AllocatedSlot();
        slot.setCourtScheduleId(sessionId);
        slot.setHearingId(hearingId);
        slot.setOuCode(session.getOuCode());
        slot.setSessionDate(session.getSessionDate().toString());
        slot.setDuration(request.getDuration());
        slot.setHearingStartTime(request.getHearingStartTime());
        slot.setSlotBased(request.isSlotBased());
        slot.setSource(SOURCE_RESERVED_UNCONFIRMED);
        slot.setExpiresAt(expiresAt);

        final Result result = courtScheduleRepository.saveBookedSlots(new ArrayList<>(List.of(slot)), false, false);
        throwIfPersistFailed(result, hearingId);

        return new ReserveUnconfirmedHearingResponse(
                sessionId, hearingId, DateUtils.toIsoString(new Timestamp(expiresAt.getTime())),
                request.isSlotBased(), request.getDuration(), request.getHearingStartTime(), SOURCE_RESERVED_UNCONFIRMED);
    }

    public ListHearingSlotsResponse listHearingSlots(final RequestedSlots hearingSlots) {

        ListHearingSlotsResponse response = new ListHearingSlotsResponse();

        List<Hearing> listHearingSlots = courtScheduleRepository.updateListHearingSlots(hearingSlots);

        response.setHearings(listHearingSlots);

        return response;
    }

    public CrownFallbackResponse crownFallbackSearchAndBook(final CrownFallbackRequest request) {
        LOGGER.info("[CROWN-FB] Received - hearingId: {}, centre: {}, date: {}, durationMins: {}, room: {}, source: {}",
                request.getHearingId(), request.getCourtCentreId(), request.getHearingDate(),
                request.getDurationInMinutes(), request.getCourtRoomId(), request.getSource());

        validateCrownFallbackRequest(request);

        final Optional<AllocatedListing> existing = courtScheduleRepository.findAllocatedListingByHearingId(request.getHearingId());
        if (existing.isPresent()) {
            LOGGER.info("[CROWN-FB] Idempotent hit - hearingId: {} already allocated to courtScheduleId: {}",
                    request.getHearingId(), existing.get().getCourtScheduleId());
            return toResponseFromExisting(existing.get());
        }

        final Optional<CrownFallbackSearchResult> found = courtScheduleRepository.searchCrownFallbackSlots(
                request.getCourtCentreId(),
                request.getHearingDate(),
                request.getDurationInMinutes(),
                request.getCourtRoomId(),
                request.getEarliestHearingTime());

        // SPRDT-1159: no bookable session on the requested date/room -> create one on the fly from
        // the request parameters (FINAL when a courtRoomId was supplied, DRAFT otherwise) so the
        // hearing allocates onto it in this same response.
        final CrownFallbackSearchResult searchResult = found.orElseGet(() -> autoCreateSession(request));

        // allocated_listings.source records HOW the allocation was made: EXEMPT_SAB for an
        // overbooking-exempt search-and-book onto an existing session, AUTO_CREATE_SAB when the
        // session was created on the fly. The caller's CROWN_FB_* label stays on the response
        // and in the logs for per-caller traceability.
        final String allocationSource = found.isPresent() ? SOURCE_EXEMPT_SAB : SOURCE_AUTO_CREATE_SAB;

        final CourtSchedule session = searchResult.session();
        final AllocatedSlot slot = buildAllocatedSlot(request, session, allocationSource);
        final Result persistResult = courtScheduleRepository.saveBookedSlots(new ArrayList<>(List.of(slot)), false, false);
        if (!persistResult.isSuccess()) {
            throw new CrownFallbackNoSessionException(
                    "Crown fallback failed to persist allocation for hearingId " + request.getHearingId()
                            + ": " + persistResult.getMsg());
        }

        // The existing saveBookedSlots pipeline doesn't reliably propagate the slot's source to
        // allocated_listings.source (observed null on IT in some branches of
        // getUpdatedAllocatedSlots). Force it explicitly so the EXEMPT_SAB / AUTO_CREATE_SAB
        // marker is guaranteed on the DB row.
        allocatedListingRepository.updateSourceByHearingId(
                request.getHearingId(), allocationSource);

        LOGGER.info("[CROWN-FB] Success - hearingId: {}, courtScheduleId: {}, isDraft: {}, overbooked: {}, source: {}, allocationSource: {}",
                request.getHearingId(), session.getCourtScheduleId(), session.isDraft(),
                searchResult.overbooked(), request.getSource(), allocationSource);

        return toResponse(request, searchResult);
    }

    /**
     * SPRDT-1159: creates a session on the fly when the fallback search finds nothing — FINAL when
     * the request carries a courtRoomId, DRAFT otherwise — so the hearing allocates onto it in the
     * same response. Logged at ERROR with a stable marker so auto-created sessions are traceable.
     */
    private CrownFallbackSearchResult autoCreateSession(final CrownFallbackRequest request) {
        final Optional<CrownFallbackSearchResult> created = courtScheduleRepository.createCrownFallbackSession(request);
        if (created.isEmpty()) {
            // SPRDT-1283: only reachable when the centre has no session to copy metadata from AND
            // the caller supplied no ouCode to build one from scratch.
            throw new CrownFallbackNoSessionException(
                    "No Crown session available at courtCentreId=" + request.getCourtCentreId()
                            + " on " + request.getHearingDate()
                            + " and auto-creation was not possible: the centre has no session to copy"
                            + " metadata from and the request carried no ouCode");
        }
        final CourtSchedule session = created.get().session();
        LOGGER.error("[CROWN-FB][AUTO-SESSION] No bookable session found — auto-created {} session"
                        + " courtScheduleId={} at courtCentreId={} courtRoomId={} on {} for hearingId={} (source={})",
                Boolean.TRUE.equals(session.isDraft()) ? "DRAFT" : "FINAL",
                session.getCourtScheduleId(), request.getCourtCentreId(), request.getCourtRoomId(),
                request.getHearingDate(), request.getHearingId(), request.getSource());
        return created.get();
    }

    private static void validateCrownFallbackRequest(final CrownFallbackRequest request) {
        if (request.getDurationInMinutes() > CrownFallbackRequest.MAX_SINGLE_DAY_MINUTES) {
            throw new CrownFallbackInvalidRequestException(
                    "Crown fallback search-and-book is single-day only; durationInMinutes="
                            + request.getDurationInMinutes() + " exceeds " + CrownFallbackRequest.MAX_SINGLE_DAY_MINUTES);
        }
        if (request.getDurationInMinutes() < 1) {
            throw new CrownFallbackInvalidRequestException(
                    "durationInMinutes must be >= 1; got " + request.getDurationInMinutes());
        }
        if (request.getHearingId() == null || request.getCourtCentreId() == null
                || request.getHearingDate() == null || request.getSource() == null) {
            throw new CrownFallbackInvalidRequestException(
                    "hearingId, courtCentreId, hearingDate, and source are required");
        }
    }

    private static AllocatedSlot buildAllocatedSlot(final CrownFallbackRequest request, final CourtSchedule session,
                                                    final String allocationSource) {
        final AllocatedSlot slot = new AllocatedSlot();
        slot.setHearingId(request.getHearingId());
        slot.setCourtScheduleId(session.getCourtScheduleId());
        slot.setCourtRoomUUId(session.getCourtRoomId());
        // ouCode is carried by the session (courtscheduler is the source of truth); the caller only supplies courtCentreId.
        slot.setOuCode(session.getOuCode());
        slot.setDuration(request.getDurationInMinutes());
        slot.setSessionDate(session.getSessionDate().toString());
        slot.setSource(allocationSource);
        // SPRDT-1283: allocated_listings reflects the SESSION — the requested time survives only
        // when it falls inside the session window (the same clamp rule getAdjustedHearingStartTime
        // applies on the other booking paths). The listing viewstore keeps the user-supplied time
        // regardless (SPRDT-1274); the two stores diverging on out-of-window times is accepted.
        slot.setHearingStartTime(resolveAllocatedListingStartTime(request, session));
        return slot;
    }

    private static String resolveAllocatedListingStartTime(final CrownFallbackRequest request, final CourtSchedule session) {
        final String sessionStartIso = session.getSessionStartTime() != null
                ? DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())) : null;
        if (!request.hasEarliestHearingTime()) {
            return sessionStartIso;
        }
        if (session.getSessionStartTime() == null || session.getSessionEndTime() == null) {
            return request.getEarliestHearingTime();
        }
        try {
            // toOffsetDateTime keeps the TRUE instant (lenient zoned parse); toExactTimestamp would
            // re-stamp the UTC wall-clock as local time and shift the epoch on non-UTC JVMs.
            final Instant requested = DateUtils.toOffsetDateTime(request.getEarliestHearingTime()).toInstant();
            final boolean withinSession = !requested.isBefore(session.getSessionStartTime().toInstant())
                    && !requested.isAfter(session.getSessionEndTime().toInstant());
            return withinSession ? request.getEarliestHearingTime() : sessionStartIso;
        } catch (final RuntimeException e) {
            LOGGER.warn("[CROWN-FB] Unparseable earliestHearingTime '{}' — allocated_listings takes the session start time",
                    request.getEarliestHearingTime());
            return sessionStartIso;
        }
    }

    /**
     * SPRDT-1274: courtRoomId is the session's court_room_id UUID. It was previously squeezed
     * through Integer.valueOf, which silently returned null for every real (UUID) room — so the
     * caller could never inject the courtroom into its hearing days. Draft sessions stay roomless
     * (ADR-005): the room is only settled at allocation.
     */
    private static CrownFallbackResponse toResponse(final CrownFallbackRequest request, final CrownFallbackSearchResult result) {
        final CourtSchedule session = result.session();
        return new CrownFallbackResponse(
                request.getHearingId(),
                session.getCourtScheduleId(),
                Boolean.TRUE.equals(session.isDraft()) ? null : session.getCourtRoomId(),
                session.getSessionDate().toString(),
                session.getSessionStartTime() != null
                        ? DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())) : null,
                session.getSessionEndTime() != null
                        ? DateUtils.toIsoString(new Timestamp(session.getSessionEndTime().getTime())) : null,
                request.getDurationInMinutes(),
                session.isDraft(),
                session.getBusinessType(),
                request.getSource(),
                result.overbooked());
    }

    private CrownFallbackResponse toResponseFromExisting(final AllocatedListing existing) {
        final String startIso = existing.getHearingStartTime() != null
                ? DateUtils.toIsoString(new Timestamp(existing.getHearingStartTime().getTime())) : null;
        final String sessionDate = startIso != null && startIso.length() >= 10 ? startIso.substring(0, 10) : null;
        // SPRDT-1274: allocated_listings carries the legacy Integer room NUMBER, not the room UUID
        // the caller needs — resolve the UUID from the allocated session itself. NB
        // findCourtScheduleById converts via convertForOverbooking, which drops the room, so the
        // full-fields list lookup is used instead.
        final String courtRoomUuid = courtScheduleRepository
                .getCourtSchedulesByIdList(List.of(existing.getCourtScheduleId()))
                .stream()
                .filter(session -> !Boolean.TRUE.equals(session.isDraft()))
                .map(CourtSchedule::getCourtRoomId)
                .findFirst()
                .orElse(null);
        return new CrownFallbackResponse(
                existing.getHearingId(),
                existing.getCourtScheduleId(),
                courtRoomUuid,
                sessionDate,
                startIso,
                null,
                existing.getDuration(),
                null,
                existing.getRotaBusinessType(),
                existing.getSource(),
                false);
    }

    private static final String SOURCE_MOVE_TO_PAST_DATE = "MOVE_TO_PAST_DATE";
    private static final String SOURCE_NONPOLICE = "NONPOLICE";
    private static final String SOURCE_MOVE = "MOVE";
    private static final String SOURCE_CHANGE_COURT_ROOM_MULTIDAY = "CHANGE_COURT_ROOM_MULTIDAY";
    private static final String SOURCE_MULTIDAY_COURTROOM_CHANGE = "MULTIDAY_COURTROOM_CHANGE";
    // SPRDT-1159 single-day Crown fallback allocations: EXEMPT_SAB = overbooking-exempt
    // search-and-book onto an existing session; AUTO_CREATE_SAB = booked onto a session created
    // on the fly. Stamped on allocated_listings.source; the caller's CROWN_FB_* label stays on
    // the response/logs only.
    private static final String SOURCE_EXEMPT_SAB = "EXEMPT_SAB";
    private static final String SOURCE_AUTO_CREATE_SAB = "AUTO_CREATE_SAB";
    private static final String JURISDICTION_CROWN = "CROWN";

    /**
     * CROWN search-and-book (SPRDT-1089, AC1/AC2/AC3/AC6).
     * Idempotency first; single-day strict (+ relaxed fallback when {@code source} present);
     * multi-day consecutive weekdays from the optional anchor, else searched across the court centre.
     */
    public CrownSearchAndBookResponse crownSearchAndBook(final CrownSearchAndBookRequest request) {
        LOGGER.info("[CROWN-SAB] hearingId: {}, centre: {}, date: {}, endDate: {}, durationMins: {}, anchor: {}, source: {}",
                request.getHearingId(), request.getCourtCentreId(), request.getHearingDate(), request.getEndDate(),
                request.getDurationInMinutes(), request.getCourtScheduleId(), request.getSource());

        if (isMultiDay(request.getDurationInMinutes(), request.getHearingDate(), request.getEndDate())) {
            return crownMultiDaySearchAndBook(request);
        }

        // Single-day idempotency + delegation — UNCHANGED. findAllocatedListingByHearingId returns
        // a single (Optional) row, which is correct here: single-day CROWN allocations are always
        // exactly one allocated_listings row per hearing.
        final Optional<AllocatedListing> existing =
                courtScheduleRepository.findAllocatedListingByHearingId(request.getHearingId());
        if (existing.isPresent()) {
            final AllocatedListing allocation = existing.get();
            LOGGER.info("[CROWN-SAB] Idempotent hit - hearingId: {} already allocated to courtScheduleId: {}",
                    request.getHearingId(), allocation.getCourtScheduleId());
            return new CrownSearchAndBookResponse(
                    request.getHearingId(), allocation.getCourtScheduleId(), allocation.getSource(),
                    Collections.emptyList(), null, null, null, null, null, null, null, null);
        }

        // Single-day: delegate to the existing single-day CROWN engine (crownFallbackSearchAndBook),
        // which searches (searchCrownFallbackSlots) and books. It requires a source; a single-day
        // request without one is rejected there (CrownFallbackInvalidRequestException) — a known single
        // session is a list.hearings-in-sessions case, not search-and-book.
        final CrownFallbackResponse fallbackResponse = crownFallbackSearchAndBook(toCrownFallbackRequest(request));
        return new CrownSearchAndBookResponse(
                fallbackResponse.hearingId(), fallbackResponse.courtScheduleId(),
                fallbackResponse.source(), Collections.emptyList(),
                fallbackResponse.courtRoomId(), fallbackResponse.sessionDate(),
                fallbackResponse.sessionStartTime(), fallbackResponse.sessionEndTime(),
                fallbackResponse.durationInMinutes(), fallbackResponse.isDraft(),
                fallbackResponse.businessType(), fallbackResponse.overbooked());
    }

    /**
     * Multi-day CROWN search-and-book (SPRDT-1089, AC2/AC3), plus the STE ns-ste-ccm-34 fix:
     * a multi-day hearing may already have allocated_listings rows — one per booked day, so
     * {@code findAllocatedListingByHearingId} (single Optional) is the wrong lookup here.
     *
     * <p>SPRDT-1273: with existing rows the decision is driven by the hearing's CURRENT allocation
     * state (its booked block), never by the payload's anchor courtScheduleId:</p>
     * <ul>
     *   <li>No existing rows: fresh search-and-book (unchanged AC2/AC3 behaviour — capacity never
     *       blocks, the court-calendar always-assign rule).</li>
     *   <li>Existing rows AND the requested start date equals the block's FIRST (earliest) session
     *       date: a RESIZE — delegated to {@link ExtendMultidayHearingService}, which no-ops when
     *       the end date is unchanged, EXTENDs by booking only the tail days (into the requested
     *       main courtroom when supplied, availability-checked, 422 NO_AVAILABILITY otherwise) and
     *       SHRINKs by releasing only the tail rows. Untouched days keep their sessions AND their
     *       rooms, so per-day room changes made via change-court-room-for-multiday-hearing survive.</li>
     *   <li>Existing rows AND a different start date: a MOVE. Search + validate the new consecutive
     *       run BEFORE releasing the prior allocation (mirrors moveHearingToPastDate's
     *       search-before-release ordering, so a search miss never orphans the hearing), then
     *       release via the existing payback logic and book, stamping source=MOVE directly on the
     *       persisted rows.</li>
     * </ul>
     */
    private CrownSearchAndBookResponse crownMultiDaySearchAndBook(final CrownSearchAndBookRequest request) {
        final List<AllocatedListing> existingAllocations =
                allocatedListingRepository.findByHearingId(request.getHearingId());

        if (!existingAllocations.isEmpty()) {
            final List<String> existingIds = existingAllocations.stream()
                    .map(AllocatedListing::getCourtScheduleId)
                    .toList();
            final List<CourtSchedule> existingSessions = new ArrayList<>(
                    courtScheduleRepository.getCourtSchedulesByIdList(existingIds));
            // getCourtSchedulesByIdList doesn't guarantee ordering; sort by date so the block's
            // earliest (first) day keys the same-start check below.
            existingSessions.sort(java.util.Comparator.comparing(CourtSchedule::getSessionDate));

            final LocalDate blockStart = existingSessions.isEmpty()
                    ? null
                    : existingSessions.get(0).getSessionDate();

            // An anchor pointing at a session the hearing does NOT currently hold is the caller
            // CHOOSING different sessions — the unallocated→allocated flow and the reschedule flow
            // both anchor on a NEW (final) session while keeping the start date. That is a MOVE,
            // never a resize: the resize's no-op would silently swallow the reallocation.
            final boolean anchorOutsideBlock = request.hasCourtScheduleId()
                    && !existingIds.contains(request.getCourtScheduleId());

            if (blockStart != null && blockStart.equals(request.getHearingDate()) && !anchorOutsideBlock) {
                final LocalDate requestedEnd = resolveRequestedEndDate(request);
                final int perDayMinutes = resolvePerDayMinutes(request, blockStart, requestedEnd);
                LOGGER.info("[CROWN-SAB] Multiday resize - hearingId: {}, existing block {}..{} ({} day(s)), requested end {} — extend/shrink in place, room {}, perDay {} mins",
                        request.getHearingId(), blockStart,
                        existingSessions.get(existingSessions.size() - 1).getSessionDate(),
                        existingSessions.size(), requestedEnd, request.getCourtRoomId(), perDayMinutes);
                final List<CourtSchedule> resized = extendMultidayHearingService.extend(
                        request.getHearingId(), blockStart, requestedEnd,
                        request.getDurationInMinutes(), request.getCourtRoomId(),
                        request.getEarliestHearingTime(), perDayMinutes);
                CourtScheduleRoomSanitiser.stripCourtRoomFromDraftSessions(resized);
                return new CrownSearchAndBookResponse(
                        request.getHearingId(), request.getCourtScheduleId(),
                        existingAllocations.get(0).getSource(), resized,
                        null, null, null, null, null, null, null, null);
            }

            return crownMultiDayMove(request, existingAllocations);
        }

        final int daysNeeded = crownDaysNeeded(request);
        final int perDay = perDayDuration(request.getDurationInMinutes(), daysNeeded);
        final List<CourtSchedule> sessions = request.hasCourtScheduleId()
                ? bookConsecutiveSessions(
                        courtScheduleRepository.findConsecutiveSessions(request.getCourtScheduleId(), daysNeeded),
                        daysNeeded, request.getHearingId(), perDay)
                : bookConsecutiveSessions(
                        courtScheduleRepository.findConsecutiveSessionsForCentre(
                                request.getCourtCentreId(), request.getHearingDate(), daysNeeded),
                        daysNeeded, request.getHearingId(), perDay);
        return new CrownSearchAndBookResponse(
                request.getHearingId(), null, null, sessions,
                null, null, null, null, null, null, null, null);
    }

    /** Multi-day MOVE: search + validate first, only release/book/tag once a run is confirmed. */
    private CrownSearchAndBookResponse crownMultiDayMove(
            final CrownSearchAndBookRequest request, final List<AllocatedListing> existingAllocations) {
        LOGGER.info("[CROWN-SAB] Multiday move - hearingId: {}, existing allocation(s): {}, new anchor: {}",
                request.getHearingId(), existingAllocations.size(), request.getCourtScheduleId());

        final int daysNeeded = crownDaysNeeded(request);
        final int perDay = perDayDuration(request.getDurationInMinutes(), daysNeeded);
        final List<CourtSchedule> rawCandidates = request.hasCourtScheduleId()
                ? courtScheduleRepository.findConsecutiveSessions(request.getCourtScheduleId(), daysNeeded)
                : courtScheduleRepository.findConsecutiveSessionsForCentre(
                        request.getCourtCentreId(), request.getHearingDate(), daysNeeded);
        // A candidate day may be one the SAME hearing already occupies (e.g. anchoring the move on a
        // continuation day of its own existing block — the STE 00b7b8bd/072b7512 scenario). totalBooked
        // is a live SUM(al.duration) over allocated_listings, so until the old rows are released (below)
        // it still counts this hearing's own not-yet-freed minutes against the day's capacity. Reclaim
        // them here so the availability check sees the day as it will be immediately after release,
        // rather than rejecting a day the hearing already legitimately holds.
        reclaimHearingsOwnCapacity(rawCandidates, existingAllocations);
        final List<CourtSchedule> sessions = selectConsecutiveSessions(rawCandidates, daysNeeded, request.getHearingId(), perDay);

        if (sessions.isEmpty()) {
            LOGGER.info("[CROWN-SAB] Multiday move - hearingId: {}, no qualifying run found; existing allocation left intact",
                    request.getHearingId());
            return new CrownSearchAndBookResponse(
                    request.getHearingId(), null, null, Collections.emptyList(),
                    null, null, null, null, null, null, null, null);
        }

        // Release the prior allocation(s) only now that a new run is confirmed bookable — reuses
        // the existing release/payback logic (restores availability counters on the freed sessions)
        // rather than hand-rolling counter math.
        courtScheduleRepository.releaseOldAllocatedListings(request.getHearingId());
        // Stamp source=MOVE on every row AT PERSIST TIME (not via a find-then-update pass
        // afterward). A prior version called allocatedListingRepository.updateSourceByHearingId
        // after persistSessions, which re-queried allocated_listings for the hearing; that query
        // could race the just-persisted rows and update only some of them (observed live: only the
        // anchor row ended up source=MOVE, continuation rows stayed at persistSessions' default).
        // Passing the source straight into persistSessions makes every row correct in one write.
        persistSessions(sessions, request.getHearingId(), false, perDay, SOURCE_MOVE);

        return new CrownSearchAndBookResponse(
                request.getHearingId(), null, SOURCE_MOVE, sessions,
                null, null, null, null, null, null, null, null);
    }

    /**
     * MAGS search-and-book (SPRDT-1089, AC4/AC5). No anchor. Multi-day books CONSECUTIVE weekday
     * sessions (one room + business type); single-day honours {@code isPolice} overbooking.
     */
    public MagsSearchAndBookResponse magsSearchAndBook(final MagsSearchAndBookRequest request) {
        LOGGER.info("[MAGS-SAB] hearingId: {}, centre: {}, date: {}, endDate: {}, durationMins: {}, isPolice: {}",
                request.getHearingId(), request.getCourtCentreId(), request.getHearingDate(), request.getEndDate(),
                request.getDurationInMinutes(), request.isPolice());

        final Optional<AllocatedListing> existing =
                courtScheduleRepository.findAllocatedListingByHearingId(request.getHearingId());
        if (existing.isPresent()) {
            LOGGER.info("[MAGS-SAB] Idempotent hit - hearingId: {}", request.getHearingId());
            return new MagsSearchAndBookResponse(request.getHearingId(), Collections.emptyList());
        }

        final int daysNeeded = daysNeeded(request.getDurationInMinutes(), request.getHearingDate(), request.getEndDate());
        final List<CourtSchedule> sessions = daysNeeded <= 1
                ? magsSingleDaySearchAndBook(request)
                : magsMultidayConsecutiveSearchAndBook(request, daysNeeded);
        return new MagsSearchAndBookResponse(request.getHearingId(), sessions);
    }

    /**
     * MAGS single-day search-and-book — restores the team/ccsph2 path. {@code searchBookHearingSlots}
     * runs the police/non-police matcher keyed on the courtCentreId <b>UUID</b> (court_house_id), with
     * the police relaxation cascade, then books the matched session by its courtScheduleId. Returns an
     * empty list (a clean "no availability") rather than throwing when nothing matches.
     */
    private List<CourtSchedule> magsSingleDaySearchAndBook(final MagsSearchAndBookRequest request) {
        final AllocatedSlot slot = new AllocatedSlot();
        slot.setHearingId(request.getHearingId());
        slot.setCourtCentreId(request.getCourtCentreId());
        slot.setCourtRoomUUId(request.getCourtRoomId());
        slot.setSessionDate(request.getHearingDate().toString());
        slot.setHearingStartTime(request.getHearingStartTime());
        slot.setHearingSessionDateSearchCutOff(request.getHearingSessionDateSearchCutOff());
        slot.setDuration(request.getDurationInMinutes());
        slot.setPolice(request.isPolice());

        final List<AllocatedSlot> slots = new ArrayList<>(List.of(slot));
        final boolean booked = courtScheduleRepository.searchBookHearingSlots(slots);
        if (!booked || slots.isEmpty()) {
            LOGGER.info("[MAGS-SAB] single-day: no session matched for hearingId {}", request.getHearingId());
            return Collections.emptyList();
        }
        // Return the FULL booked session(s) (all fields incl. courtRoomId UUID + sessionStartTime) so the
        // caller can map the allocation; convertForOverbooking drops courtRoomId, so re-fetch by id here.
        final List<String> bookedScheduleIds = slots.stream()
                .map(AllocatedSlot::getCourtScheduleId)
                .filter(Objects::nonNull)
                .toList();
        return courtScheduleRepository.getCourtSchedulesByIdList(bookedScheduleIds);
    }

    /**
     * MAGS multiday search-and-book — CONSECUTIVE business days only (no sparse). Finds one room +
     * business type in the centre with {@code daysNeeded} consecutive AD weekday sessions (keyed on the
     * courtCentreId UUID), then books each by its courtScheduleId (isSearchUpdate=false). Returns empty
     * if a consecutive run of the required length is not available.
     * <p>NB: reuses the AD-weekday consecutive search; if MAGS multiday must match non-AD/slot-based
     * sessions, this method is the single point to adjust.
     */
    private List<CourtSchedule> magsMultidayConsecutiveSearchAndBook(final MagsSearchAndBookRequest request,
                                                                     final int daysNeeded) {
        final List<CourtSchedule> sessions = courtScheduleRepository.findConsecutiveSessionsForCentre(
                request.getCourtCentreId(), request.getHearingDate(), daysNeeded);
        if (sessions.size() < daysNeeded) {
            LOGGER.info("[MAGS-SAB] multiday: found {} consecutive session(s) of {} needed for hearingId {}",
                    sessions.size(), daysNeeded, request.getHearingId());
            return Collections.emptyList();
        }
        persistSessions(sessions, request.getHearingId(), false,
                perDayDuration(request.getDurationInMinutes(), daysNeeded), SOURCE_NONPOLICE);
        return sessions;
    }

    /**
     * Move a hearing to a (typically past) date (SPRDT-1089, AC7). Releases the prior allocation, then
     * books CONSECUTIVE weekday sessions in one room + business type at the centre — CROWN via an optional
     * {@code courtScheduleId} anchor or a centre search, MAGISTRATES always via the centre search.
     * source=MOVE_TO_PAST_DATE. The past-only rule is owned by the caller (listing); courtscheduler books
     * whatever consecutive sessions it finds and does not reject future dates.
     */
    public MoveHearingToPastDateResponse moveHearingToPastDate(final MoveHearingToPastDateRequest request) {
        LOGGER.info("[MOVE-PAST] hearingId: {}, centre: {}, jurisdiction: {}, startDate: {}, endDate: {}, durationMins: {}",
                request.getHearingId(), request.getCourtCentreId(), request.getJurisdiction(),
                request.getStartDate(), request.getEndDate(), request.getDurationInMinutes());

        final int daysNeeded = daysNeeded(request.getDurationInMinutes(), request.getStartDate(), request.getEndDate());
        final int perDay = perDayDuration(request.getDurationInMinutes(), daysNeeded);

        // Select (search + validate) the past sessions BEFORE touching the prior allocation, so a search
        // miss can never orphan the hearing — the release is deferred until we have sessions to book onto.
        final List<CourtSchedule> sessions;
        if (JURISDICTION_CROWN.equalsIgnoreCase(request.getJurisdiction())) {
            // CROWN consecutive run: anchor (when present) keys findConsecutiveSessions; otherwise search the
            // court centre via findConsecutiveSessionsForCentre (findConsecutiveSessions needs a courtScheduleId).
            final List<CourtSchedule> candidates = request.hasCourtScheduleId()
                    ? courtScheduleRepository.findConsecutiveSessions(request.getCourtScheduleId(), daysNeeded)
                    : courtScheduleRepository.findConsecutiveSessionsForCentre(
                            request.getCourtCentreId(), request.getStartDate(), daysNeeded);
            sessions = selectConsecutiveSessions(candidates, daysNeeded, request.getHearingId(), perDay);
        } else {
            // MAGISTRATES: consecutive past weekdays in the centre (same room + business type),
            // mirroring the CROWN no-anchor path — no sparse allocation.
            sessions = selectConsecutiveSessions(
                    courtScheduleRepository.findConsecutiveSessionsForCentre(
                            request.getCourtCentreId(), request.getStartDate(), daysNeeded),
                    daysNeeded, request.getHearingId(), perDay);
        }

        if (sessions.isEmpty()) {
            // No session for a single-date request is a hard 404; the prior allocation is left intact.
            if (!request.hasEndDate()) {
                throw new NoSessionAvailableException(
                        "No past session available for hearingId " + request.getHearingId()
                                + " starting " + request.getStartDate());
            }
            // A date-range request that yields nothing is exploratory — leave the existing allocation intact.
            return new MoveHearingToPastDateResponse(
                    request.getHearingId(), SOURCE_MOVE_TO_PAST_DATE, Collections.emptyList());
        }

        // Only now that we have sessions to book: release the prior allocation, then persist the past booking.
        final Optional<AllocatedListing> prior =
                courtScheduleRepository.findAllocatedListingByHearingId(request.getHearingId());
        if (prior.isPresent()) {
            LOGGER.info("[MOVE-PAST] Releasing prior allocation - hearingId: {}, courtScheduleId: {}",
                    request.getHearingId(), prior.get().getCourtScheduleId());
            courtScheduleRepository.releaseOldAllocatedListings(request.getHearingId());
        }
        persistSessions(sessions, request.getHearingId(), false, perDay, SOURCE_MOVE_TO_PAST_DATE);

        return new MoveHearingToPastDateResponse(request.getHearingId(), SOURCE_MOVE_TO_PAST_DATE, sessions);
    }

    /**
     * Change the court room/session for specific day(s) of an existing multi-day hearing, leaving
     * every day NOT submitted untouched.
     *
     * <p>Validates ALL requested days first: a day with no existing allocation throws {@link
     * NoAllocationOnDateException}; a day whose target session doesn't exist throws {@link
     * NoSessionAvailableException}. Either way NOTHING is released or booked — not even days earlier
     * in the request that were themselves valid. A target session that lacks capacity does NOT reject
     * (court-calendar always-assign rule, F1): the day is booked and the overbooking is logged
     * advisorily, since all court-calendar journeys are overbooking exempt (SPRDT-1224).</p>
     *
     * <p>A day whose requested {@code courtScheduleId} equals its CURRENT allocation is an
     * idempotent no-op: no release, no (re)booking, but the session is still included in the
     * response. Only the genuinely changed dates are released ({@link
     * CourtScheduleRepository#releaseAllocatedListingsForDates}), and each changed day is booked
     * with ITS OWN {@code durationInMinutes} — sessions are grouped by duration before calling
     * {@link #persistSessions}, since that shared helper applies one duration to every session it
     * is given and per-day durations may differ.</p>
     */
    public ChangeCourtRoomForMultidayHearingResponse changeCourtRoomForMultidayHearing(
            final ChangeCourtRoomForMultidayHearingRequest request) {
        final String hearingId = request.getHearingId();
        LOGGER.info("[CHANGE-ROOM-MULTIDAY] hearingId: {}, requestedDays: {}",
                hearingId, request.getDays().size());

        final List<AllocatedListing> existingAllocations = allocatedListingRepository.findByHearingId(hearingId);
        final List<String> existingCourtScheduleIds = existingAllocations.stream()
                .map(AllocatedListing::getCourtScheduleId)
                .toList();
        final Map<String, LocalDate> existingSessionDateById = courtScheduleRepository
                .getCourtSchedulesByIdList(existingCourtScheduleIds).stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, CourtSchedule::getSessionDate));
        final Map<LocalDate, AllocatedListing> existingByDate =
                mapAllocationsBySessionDate(existingAllocations, existingSessionDateById);

        final List<String> targetCourtScheduleIds = request.getDays().stream()
                .map(RequestedDay::getCourtScheduleId)
                .distinct()
                .toList();
        final Map<String, CourtSchedule> targetSessionById = courtScheduleRepository
                .getCourtSchedulesByIdList(targetCourtScheduleIds).stream()
                .collect(Collectors.toMap(CourtSchedule::getCourtScheduleId, session -> session));

        final List<CourtSchedule> allocatedSchedules = new ArrayList<>();
        final List<LocalDate> datesToRelease = new ArrayList<>();
        final Map<String, Map<Integer, List<CourtSchedule>>> toBookBySourceThenDuration = new java.util.LinkedHashMap<>();

        for (final RequestedDay day : request.getDays()) {
            final AllocatedListing current = existingByDate.get(day.getSessionDate());
            if (current == null) {
                throw new NoAllocationOnDateException(
                        "Hearing " + hearingId + " has no allocation on " + day.getSessionDate());
            }

            final CourtSchedule target = targetSessionById.get(day.getCourtScheduleId());
            if (target == null) {
                throw new NoSessionAvailableException("No session " + day.getCourtScheduleId()
                        + " found for hearing " + hearingId + " on " + day.getSessionDate());
            }

            final boolean isNoop = day.getCourtScheduleId().equals(current.getCourtScheduleId());

            allocatedSchedules.add(target);
            if (!isNoop) {
                final boolean overbooking = !target.isOverbookingAllowed()
                        && getEffectiveAvailableDuration(target) < day.getDurationInMinutes();
                final String daySource = overbooking
                        ? SOURCE_MULTIDAY_COURTROOM_CHANGE : SOURCE_CHANGE_COURT_ROOM_MULTIDAY;
                datesToRelease.add(day.getSessionDate());
                toBookBySourceThenDuration
                        .computeIfAbsent(daySource, k -> new java.util.LinkedHashMap<>())
                        .computeIfAbsent(day.getDurationInMinutes(), k -> new ArrayList<>())
                        .add(target);
            }
        }

        if (!datesToRelease.isEmpty()) {
            courtScheduleRepository.releaseAllocatedListingsForDates(hearingId, datesToRelease);
            // Booking must NOT go through persistSessions' default saveBookedSlots call: that routes
            // to bookSlotsWithCourtScheduleId, which internally does its OWN hearing-wide release
            // (CourtScheduleRepository#releaseOldAllocatedListings) before booking. That would wipe
            // out the untouched days' allocations we just deliberately preserved above via the
            // date-scoped releaseAllocatedListingsForDates. Use the no-release booking variant instead.
            toBookBySourceThenDuration.forEach((source, byDuration) ->
                    byDuration.forEach((duration, sessions) ->
                            persistSessionsWithoutHearingRelease(sessions, hearingId, duration, source)));
        }

        return new ChangeCourtRoomForMultidayHearingResponse(
                hearingId, SOURCE_CHANGE_COURT_ROOM_MULTIDAY, allocatedSchedules);
    }

    /**
     * Key each allocation by its session date (resolved via {@code sessionDateById}); an allocation
     * whose courtScheduleId has no resolvable session is skipped rather than mapped to a null date.
     */
    private static Map<LocalDate, AllocatedListing> mapAllocationsBySessionDate(
            final List<AllocatedListing> allocations, final Map<String, LocalDate> sessionDateById) {
        final Map<LocalDate, AllocatedListing> byDate = new java.util.HashMap<>();
        allocations.forEach(allocation -> {
            final LocalDate date = sessionDateById.get(allocation.getCourtScheduleId());
            if (date != null) {
                byDate.put(date, allocation);
            }
        });
        return byDate;
    }

    /** Map a single-day CROWN search-and-book request onto the existing fallback engine's request shape. */
    private static CrownFallbackRequest toCrownFallbackRequest(final CrownSearchAndBookRequest request) {
        return new CrownFallbackRequest()
                .setHearingId(request.getHearingId())
                .setCourtCentreId(request.getCourtCentreId())
                .setCourtRoomId(request.getCourtRoomId())
                .setHearingDate(request.getHearingDate())
                .setEarliestHearingTime(request.getEarliestHearingTime())
                .setDurationInMinutes(request.getDurationInMinutes())
                .setSource(request.getSource())
                .setOuCode(request.getOuCode())
                .setCourtCentreName(request.getCourtCentreName())
                .setCourtRoomName(request.getCourtRoomName());
    }

    /** Multi-day when duration exceeds one court day OR an explicit date range is supplied (ADR-003 Option B). */
    private static boolean isMultiDay(final int durationInMinutes, final LocalDate startDate, final LocalDate endDate) {
        final boolean dateRange = endDate != null && startDate != null && endDate.isAfter(startDate);
        return durationInMinutes > MINUTES_IN_DAY || dateRange;
    }

    /**
     * Days needed for a span. Prefer the date-range count when an endDate is present (inclusive, calendar
     * days); otherwise {@code ceil(durationInMinutes / 360)}, with a floor of 1 (ADR-003 Option B).
     */
    private static int daysNeeded(final int durationInMinutes, final LocalDate startDate, final LocalDate endDate) {
        if (endDate != null && startDate != null && endDate.isAfter(startDate)) {
            return (int) (java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate) + 1);
        }
        final int byDuration = (int) Math.ceil(durationInMinutes / (double) MINUTES_IN_DAY);
        return Math.max(byDuration, 1);
    }

    /**
     * CROWN fresh-booking / move day count: DURATION-derived whenever a duration is supplied —
     * update-hearing-for-listing sends both duration AND endDate, and the endDate window may be
     * deliberately wider than the block (non-sitting days live inside it), so expanding it into
     * calendar days would over-book. The date-range form (endDate, no duration — AC6) keeps its
     * date-derived expansion. The endDate itself only shapes the same-start RESIZE decision.
     */
    private static int crownDaysNeeded(final CrownSearchAndBookRequest request) {
        if (request.getDurationInMinutes() > 0) {
            return daysNeeded(request.getDurationInMinutes(), request.getHearingDate(), null);
        }
        return daysNeeded(request.getDurationInMinutes(), request.getHearingDate(), request.getEndDate());
    }

    /**
     * Per-day minutes for a resize's tail days: the requested TOTAL spread over the requested
     * window's business days. The suites (and some rotas) run sessions far smaller than the
     * 360-minute default court day, so hardcoding FULL_DAY minutes would fail availability on
     * sessions that can perfectly well take their share.
     */
    private static int resolvePerDayMinutes(final CrownSearchAndBookRequest request,
                                            final LocalDate start, final LocalDate endInclusive) {
        int businessDays = 1;
        LocalDate cursor = start;
        while (cursor.isBefore(endInclusive)) {
            cursor = SessionAvailability.getNextBusinessDay(cursor);
            businessDays++;
        }
        return perDayDuration(request.getDurationInMinutes(), businessDays);
    }

    /**
     * The end date a resize (extend/shrink) targets: the request's own endDate when supplied,
     * otherwise derived from the requested duration laid out over consecutive BUSINESS days from
     * the start date (matching how a fresh multi-day booking expands a duration into days).
     */
    private static LocalDate resolveRequestedEndDate(final CrownSearchAndBookRequest request) {
        if (request.getEndDate() != null) {
            return request.getEndDate();
        }
        final int days = daysNeeded(request.getDurationInMinutes(), request.getHearingDate(), null);
        LocalDate end = request.getHearingDate();
        for (int i = 1; i < days; i++) {
            end = SessionAvailability.getNextBusinessDay(end);
        }
        return end;
    }

    /**
     * Select a valid consecutive run (dedupe, length, consecutive business days). Does NOT
     * persist — callers persist separately so a release-then-book sequence can be ordered safely.
     *
     * <p>Court-calendar rule (F1): a lack of session capacity must NEVER block the assignment —
     * hearings are placed even when a day is full and {@code is_overbooking_allowed=false}. The
     * per-date dedupe therefore prefers a row that can actually take {@code perDayMinutes}
     * (falling back to an overbooking-allowed row, then to any row), and the old hard
     * availability rejection is replaced by an advisory log of the days being overbooked.
     * Only STRUCTURAL failures (not enough session days, non-consecutive dates) reject the run.</p>
     */
    private List<CourtSchedule> selectConsecutiveSessions(
            final List<CourtSchedule> rawCandidates, final int daysNeeded, final String hearingId,
            final int perDayMinutes) {
        final List<CourtSchedule> candidates = dedupeByDatePreferringBookable(
                rawCandidates == null ? Collections.emptyList() : rawCandidates, perDayMinutes);
        if (candidates.size() < daysNeeded) {
            return Collections.emptyList();
        }
        final List<CourtSchedule> sessions = candidates.subList(0, daysNeeded);
        if (!areConsecutiveBusinessDays(sessions, hearingId)) {
            return Collections.emptyList();
        }
        logSessionsBookedBeyondCapacity(sessions, hearingId, perDayMinutes);
        return new ArrayList<>(sessions);
    }

    /**
     * Advisory replacement for the old all-or-nothing availability rejection: names each day being
     * booked beyond its remaining capacity (including overbooking-disallowed sessions) so support can
     * trace intentional overbooking, but never blocks the assignment.
     */
    private static void logSessionsBookedBeyondCapacity(
            final List<CourtSchedule> sessions, final String hearingId, final int perDayMinutes) {
        for (final CourtSchedule session : sessions) {
            final int available = getEffectiveAvailableDuration(session);
            if (available < perDayMinutes) {
                LOGGER.info("[CROWN-SAB] Overbooking session {} on {} for hearingId {} — {}mins available, {}mins needed, overbookingAllowed={} (court-calendar always-assign rule)",
                        session.getCourtScheduleId(), session.getSessionDate(), hearingId,
                        available, perDayMinutes, session.isOverbookingAllowed());
            }
        }
    }

    /**
     * Subtract each candidate's own not-yet-released contribution (from {@code existingAllocations})
     * out of its {@code totalBooked}, in place. {@code totalBooked} is a live aggregate over
     * allocated_listings, so a day the hearing itself already occupies still counts as fully consumed
     * until the release actually runs; without this the availability check in {@link
     * #selectConsecutiveSessions} would reject a MOVE that re-anchors onto one of the hearing's own
     * current days (they show 0 minutes available even though those minutes are about to be freed).
     */
    private static void reclaimHearingsOwnCapacity(
            final List<CourtSchedule> candidates, final List<AllocatedListing> existingAllocations) {
        if (CollectionUtils.isEmpty(candidates) || CollectionUtils.isEmpty(existingAllocations)) {
            return;
        }
        final Map<String, Integer> ownDurationByCourtScheduleId = existingAllocations.stream()
                .filter(allocation -> allocation.getDuration() != null)
                .collect(Collectors.toMap(AllocatedListing::getCourtScheduleId, AllocatedListing::getDuration, Integer::sum));
        candidates.forEach(session -> {
            final Integer ownDuration = ownDurationByCourtScheduleId.get(session.getCourtScheduleId());
            if (ownDuration != null && ownDuration > 0 && session.getTotalBooked() != null) {
                session.setTotalBooked(Math.max(0, session.getTotalBooked() - ownDuration));
            }
        });
    }

    /** Select a consecutive run and book it. Empty list => not bookable; throws on a persist failure. */
    private List<CourtSchedule> bookConsecutiveSessions(
            final List<CourtSchedule> rawCandidates, final int daysNeeded, final String hearingId,
            final int perDayDuration) {
        final List<CourtSchedule> sessions = selectConsecutiveSessions(rawCandidates, daysNeeded, hearingId, perDayDuration);
        if (!sessions.isEmpty()) {
            persistSessions(sessions, hearingId, false, perDayDuration, SOURCE_NONPOLICE);
        }
        return sessions;
    }


    /**
     * Per-day duration written to each booked slot: the request total split across the booked days
     * ({@code total/days}), or one full court day when no total is supplied (the date-range form).
     */
    private static int perDayDuration(final int totalDurationInMinutes, final int daysNeeded) {
        if (totalDurationInMinutes <= 0 || daysNeeded <= 0) {
            return MINUTES_IN_DAY;
        }
        return Math.max(totalDurationInMinutes / daysNeeded, 1);
    }

    /**
     * Persist booked slots for a hearing, stamping {@code source} on each row AT PERSIST TIME (rather
     * than via a separate find-then-update pass afterward, which can race a just-persisted row and
     * miss it — see {@link #crownMultiDayMove}). Throws {@link SlotsBookException} on a persist
     * failure so an infrastructure error surfaces rather than being swallowed as an empty (apparently
     * "no slot") result.
     */
    private void persistSessions(final List<CourtSchedule> sessions, final String hearingId,
                                 final boolean isPolice, final int perDayDuration, final String source) {
        final List<AllocatedSlot> slots = buildSlotsForBooking(sessions, hearingId, perDayDuration, source);
        final Result result = courtScheduleRepository.saveBookedSlots(new ArrayList<>(slots), false, isPolice);
        throwIfPersistFailed(result, hearingId);
    }

    /**
     * Sibling of {@link #persistSessions(List, String, boolean, int, String)} that books via
     * {@link CourtScheduleRepository}'s no-release variant (releaseExistingHearingAllocations=false),
     * so bookSlotsWithCourtScheduleId's internal hearing-wide release is skipped. For callers (e.g.
     * {@link #changeCourtRoomForMultidayHearing}) that have already scoped their own release to a
     * subset of the hearing's dates and must leave the remaining days' allocations untouched.
     */
    private void persistSessionsWithoutHearingRelease(final List<CourtSchedule> sessions, final String hearingId,
                                                       final int perDayDuration, final String source) {
        final List<AllocatedSlot> slots = buildSlotsForBooking(sessions, hearingId, perDayDuration, source);
        final Result result = courtScheduleRepository.saveBookedSlots(new ArrayList<>(slots), false, false, false);
        throwIfPersistFailed(result, hearingId);
    }

    private static List<AllocatedSlot> buildSlotsForBooking(final List<CourtSchedule> sessions, final String hearingId,
                                                             final int perDayDuration, final String source) {
        return sessions.stream()
                .map(session -> {
                    final AllocatedSlot slot = new AllocatedSlot();
                    slot.setCourtScheduleId(session.getCourtScheduleId());
                    slot.setHearingId(hearingId);
                    slot.setDuration(perDayDuration);
                    if (session.getSessionDate() != null) {
                        slot.setSessionDate(session.getSessionDate().toString());
                    }
                    slot.setOuCode(session.getOuCode());
                    slot.setCourtRoom(session.getCourtRoomName());
                    slot.setCourtRoomUUId(session.getCourtRoomId());
                    slot.setSource(source);
                    if (session.getSessionStartTime() != null) {
                        slot.setHearingStartTime(DateUtils.toIsoString(new Timestamp(session.getSessionStartTime().getTime())));
                    }
                    return slot;
                })
                .toList();
    }

    private static void throwIfPersistFailed(final Result result, final String hearingId) {
        if (!result.isSuccess()) {
            LOGGER.warn("[BOOKING] persist failed - hearingId: {}, reason: {}", hearingId, result.getMsg());
            throw new SlotsBookException(
                    "Failed to persist booking for hearingId " + hearingId + ": " + result.getMsg());
        }
    }

    static boolean areConsecutiveBusinessDays(final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions,
                                              final String hearingId) {
        for (int i = 1; i < sessions.size(); i++) {
            final LocalDate previousDate = sessions.get(i - 1).getSessionDate();
            final LocalDate currentDate = sessions.get(i).getSessionDate();
            final LocalDate expectedNextBusinessDay = getNextBusinessDay(previousDate);
            if (!currentDate.equals(expectedNextBusinessDay)) {
                LOGGER.info("[MULTIDAY-SEARCH] hearingId: {}, gap detected between {} ({}) and {} ({}), expected next business day: {}",
                        hearingId,
                        sessions.get(i - 1).getCourtScheduleId(), previousDate,
                        sessions.get(i).getCourtScheduleId(), currentDate,
                        expectedNextBusinessDay);
                return false;
            }
        }
        return true;
    }

    static LocalDate getNextBusinessDay(final LocalDate date) {
        return uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.getNextBusinessDay(date);
    }

    static List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> dedupeByDatePreferringBookable(
            final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> sessions,
            final int requiredPerDayMinutes) {
        final java.util.LinkedHashMap<LocalDate, uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> byDate =
                new java.util.LinkedHashMap<>();
        for (final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs : sessions) {
            if (cs.getSessionDate() == null) {
                continue;
            }
            byDate.merge(cs.getSessionDate(), cs, (existing, incoming) ->
                    preferBookable(existing, incoming, requiredPerDayMinutes));
        }
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule> out = new ArrayList<>(byDate.values());
        out.sort(java.util.Comparator.comparing(uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule::getSessionDate));
        return out;
    }

    private static uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule preferBookable(
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule existing,
            final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule incoming,
            final int requiredPerDayMinutes) {
        final boolean existingFits = getEffectiveAvailableDuration(existing) >= requiredPerDayMinutes;
        final boolean incomingFits = getEffectiveAvailableDuration(incoming) >= requiredPerDayMinutes;
        if (existingFits != incomingFits) {
            return existingFits ? existing : incoming;
        }
        if (existingFits) {
            // both fit: prefer NOT-overbookingAllowed, matching slot-search's preferNonOverbooking
            return existing.isOverbookingAllowed() && !incoming.isOverbookingAllowed() ? incoming : existing;
        }
        // neither fits: prefer the row where overbooking is explicitly allowed
        return !existing.isOverbookingAllowed() && incoming.isOverbookingAllowed() ? incoming : existing;
    }

    static int getEffectiveAvailableDuration(final uk.gov.moj.cpp.courtscheduler.domain.CourtSchedule cs) {
        return uk.gov.moj.cpp.courtscheduler.common.utils.SessionAvailability.getEffectiveAvailableDuration(cs);
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
