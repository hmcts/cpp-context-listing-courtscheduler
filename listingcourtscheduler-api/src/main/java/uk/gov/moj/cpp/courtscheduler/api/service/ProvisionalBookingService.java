package uk.gov.moj.cpp.courtscheduler.api.service;

import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PROVISIONAL_SLOTS;

import uk.gov.moj.cpp.courtscheduler.persist.entity.BookingState;
import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.BookingLogRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import jakarta.inject.Inject;
import jakarta.json.Json;
import jakarta.json.JsonArrayBuilder;
import jakarta.json.JsonObject;

import org.modelmapper.ModelMapper;

@Service
@org.springframework.transaction.annotation.Transactional
public class ProvisionalBookingService {

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger(ProvisionalBookingService.class);
    private static final String BOOKING_ID = "bookingId";
    private static final String STATUS = "status";
    private static final String SAFE_TO_SHARE = "safeToShare";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_SHARED = "SHARED";
    private static final String STATUS_NOT_FOUND = "NOT_FOUND";
    private static final String STATUS_REBOOKED = "REBOOKED";
    private static final String STATUS_SURPLUS = "SURPLUS";
    private static final String STATUS_UNLISTED = "UNLISTED";
    private static final String STATUS_EXPIRED = "EXPIRED";
    private static final String STATUS_RELEASED = "RELEASED";
    // Placeable at share. SURPLUS and UNLISTED have no rows, but progression places them from its
    // own record of the share that resolved them, whatever is held here now.
    private static final Set<String> SAFE_TO_SHARE_STATUSES =
            Set.of(STATUS_RESERVED, STATUS_REBOOKED, STATUS_SHARED, STATUS_SURPLUS, STATUS_UNLISTED);
    private static final String REASON_DROPPED_FROM_DRAFT = "DROPPED_FROM_DRAFT";
    private static final String REASON_SURPLUS = "SURPLUS";
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private ReservationService reservationService;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    @Inject
    private BookingLogRepository bookingLogRepository;
    private final ListToJsonArrayConverter<ProvisionalBookingInfo> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

    /**
     * Reserves every requested session and answers with the booking id they are held under. The
     * reservation replaces the provisional_booking row that used to be written here: it holds
     * real capacity and carries an expiry, which a provisional booking never did.
     *
     * <p>A caller may send the booking id its result line already holds. Whether that id is kept
     * or a fresh one minted is decided by {@link ReservationService#reserveAllUnderBooking} - see
     * there for the rule (LPT-2592) - so the response carries the id actually used, and the UI
     * writes exactly that back onto the line.
     *
     * <p>All or nothing, and structurally so: every slot of the booking shares one booking id,
     * and saveBookedSlots opens with a release keyed on it. Reserving slot by slot would therefore
     * make each slot release the previous ones of the same booking, leaving a three-day pick
     * holding one day. {@code reserveAll} makes exactly one saveBookedSlots call for the whole
     * booking, inside one transaction, so a NoCapacityException rolls back anything written.
     */
    public JsonObject bookProvisionalSlots(final ProvisionalBookingSlots provisionalBookingSlots) {
        final String bookingId = reservationService.reserveAllUnderBooking(
                provisionalBookingSlots.getBookingId(), provisionalBookingSlots.getProvisionalSlots());

        return Json.createObjectBuilder()
                .add(BOOKING_ID, bookingId)
                .build();
    }

    /**
     * Releases the unconfirmed holds of each booking id - see
     * {@link ReservationService#releaseUnconfirmedBookings} for why it can never touch a confirmed
     * listing. Delegated like {@link #bookProvisionalSlots}: reservations live there.
     *
     * @param reason the caller's reason as sent on the wire, recorded in the booking log:
     *               {@code DROPPED_FROM_DRAFT}, {@code SURPLUS}, or null for a plain release
     */
    public void releaseUnconfirmedBookings(final List<String> bookingIds, final String reason) {
        reservationService.releaseUnconfirmedBookings(bookingIds, releaseStateFor(reason));
    }

    private static BookingState releaseStateFor(final String reason) {
        if (REASON_DROPPED_FROM_DRAFT.equals(reason)) {
            return BookingState.DRAFT_RELEASED;
        }
        if (REASON_SURPLUS.equals(reason)) {
            return BookingState.SURPLUS_RELEASED;
        }
        return BookingState.RELEASED;
    }

    /**
     * Resolves booking ids to their sessions. Reservations are the current representation;
     * provisional_booking rows are the legacy one, written before reserve-a-slot shipped.
     * Drafts have no TTL, so the fallback is permanent — a pre-go-live magistrates draft can be
     * shared at any point in the future and must still resolve.
     */
    public JsonObject fetchProvisionalSlots(final String bookingIds) {
        final List<String> bookingIdList = Stream.of(bookingIds.split(","))
                .map(String::trim)
                .toList();

        final List<ProvisionalBookingInfo> infos = new ArrayList<>();
        final List<String> unresolved = new ArrayList<>();

        for (final String bookingId : bookingIdList) {
            final List<AllocatedListing> reservations = allocatedListingRepository.findByBookingId(bookingId).stream()
                    .filter(ProvisionalBookingService::isReservation)
                    .toList();
            if (reservations.isEmpty()) {
                unresolved.add(bookingId);
            } else {
                reservations.forEach(row -> infos.add(buildInfoFromReservation(row)));
            }
        }

        if (!unresolved.isEmpty()) {
            provisionalBookingRepository.findByBookingIdIn(unresolved)
                    .forEach(legacy -> infos.add(buildProvisionalInfo(legacy)));
        }

        attachJudiciaries(infos);

        return Json.createObjectBuilder()
                .add(PROVISIONAL_SLOTS.getLabel(), listToJsonArrayConverter.convert(infos))
                .build();
    }

    /**
     * Reports whether each booking id still has a hold behind it. Used by the pre-share gate:
     * sharing is asynchronous, so the clerk has to be told before the command is sent.
     *
     * <p>Checks each booking id individually rather than batching (contrast
     * {@link #fetchProvisionalSlots}, which batches unresolved ids into one query) — this method
     * only needs a per-id boolean, not the full session detail that justifies batching there.
     *
     * <p><b>Contract.</b> {@code status} says where the booking is, and {@code safeToShare} whether
     * a share may go ahead with it - so callers act on {@code safeToShare} and need no rule of
     * their own. With rows: {@code RESERVED} (a reservation still holds capacity) or {@code SHARED}
     * (a confirmed row carries this booking id). Without rows, the booking log says why:
     * {@code SURPLUS} (a share merged its line onto another booking's session),
     * {@code UNLISTED} (its listing was freed, e.g. by a re-share), {@code EXPIRED} (the hold lapsed
     * and was purged), {@code RELEASED} (dropped from the draft, or released without a reason), or
     * {@code NOT_FOUND} (nothing recorded). {@code REBOOKED}: see below. Safe to share: RESERVED,
     * REBOOKED, SHARED, SURPLUS, UNLISTED.
     *
     * <p>Unlike the {@code live} flag this replaces, the answer no longer conflates an expired
     * hold with an already-shared booking, so a caller may gate a re-share on it directly.
     *
     * <p>With {@code reserveIfExpired} (LPT-2611) a booking that would answer {@code EXPIRED} is
     * first offered to {@link ReservationService#rebookExpired}: re-booked, it answers
     * {@code REBOOKED} and is safe to share. Without the flag this only reads.
     *
     * <p>No transaction of its own, so that each booking's re-book commits or rolls back on its
     * own: one full session must not undo another booking's successful re-book.
     */
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public JsonObject getBookingStatus(final List<String> bookingIdList, final boolean reserveIfExpired) {
        final JsonArrayBuilder bookings = Json.createArrayBuilder();
        for (final String bookingId : bookingIdList) {
            final String status = reserveIfExpired ? statusRebookingIfExpired(bookingId) : statusOf(bookingId);
            bookings.add(Json.createObjectBuilder()
                    .add(BOOKING_ID, bookingId)
                    .add(SAFE_TO_SHARE, SAFE_TO_SHARE_STATUSES.contains(status))
                    .add(STATUS, status));
        }
        return Json.createObjectBuilder().add("bookings", bookings).build();
    }

    // Deliberate: a re-book failure of ANY kind must stay with its own booking. Escaping here
    // fails the whole call, and listing then reports every booking in it UNKNOWN - safe to share -
    // including one that is really gone, so the share goes ahead with nothing held.
    @SuppressWarnings("PMD.AvoidCatchingGenericException")
    private String statusRebookingIfExpired(final String bookingId) {
        final String status = statusOf(bookingId);
        if (!STATUS_EXPIRED.equals(status)) {
            return status;
        }
        try {
            if (reservationService.rebookExpired(bookingId)) {
                return STATUS_REBOOKED;
            }
        } catch (final RuntimeException refused) {
            // No room, session gone, another request re-booked it first (the unique reservation
            // index), or something unexpected. Its transaction rolled back.
            LOGGER.warn("Booking {} could not be re-booked: {}", bookingId, refused.getMessage());
        }
        // Whatever the booking is now is the answer - including a hold another request (a double
        // click) re-booked between the two reads, which no longer qualifies but is held.
        return statusOf(bookingId);
    }

    /**
     * Resolves a booking id to its status: from its rows when it has any, otherwise from the latest
     * event in the booking log.
     *
     * <p><b>Order matters.</b> A live reservation outranks a confirmed row: if a booking somehow
     * carried both, the clerk is still holding capacity, and reporting it as already shared would
     * wave a second share through against a hold that is still consuming a slot.
     *
     * <p>Each check is a separate round trip rather than one batched lookup, matching the existing
     * shape of this method — it needs only a per-id verdict, not the session detail that justifies
     * batching in {@link #fetchProvisionalSlots}.
     */
    private String statusOf(final String bookingId) {
        // Both shapes now live in booking_id - a reservation and the confirmed listing it grows
        // into - so expiresAt, not the column, tells them apart. RESERVED is checked first and
        // short-circuits: a re-pick during an amendment leaves a confirmed listing AND a fresh
        // hold under one bookingId, and while the clerk is holding capacity the honest answer is
        // RESERVED. Reporting SHARED there would wave a second share through against a live hold.
        final List<AllocatedListing> rows = allocatedListingRepository.findByBookingId(bookingId);

        if (rows.stream().anyMatch(ProvisionalBookingService::isReservation)) {
            return STATUS_RESERVED;
        }
        // The confirmed row BUG-3 stamps at share time: same booking_id, no expiresAt.
        if (!rows.isEmpty()) {
            return STATUS_SHARED;
        }
        // No row under this booking id, in either shape, so there is no hold to share against.
        //
        // This used to fall back to the legacy provisional_booking table and report a fourth
        // state for a row found there, on the grounds that a pre-reserve-a-slot draft never had a
        // reservation and so should not be reported as expired. That fallback is gone
        // deliberately: a provisional_booking row proves only that a booking was once recorded,
        // never that a session is still held, so it waved through exactly the drafts most likely
        // to be stale - the oldest ones - which is the opposite of what the gate is for. Absence
        // of an unconfirmed booking now means what it says: the hold is gone.
        //
        // Why it is gone decides whether a share may still go ahead, and only the booking log
        // knows: a booking released as surplus is placeable, an expired one is not, and both
        // used to answer NOT_FOUND - which made callers rebuild the difference themselves.
        return bookingLogRepository.findLatestEvent(bookingId).stream()
                .findFirst()
                .map(event -> statusWithoutRows(event.getState()))
                .orElse(STATUS_NOT_FOUND);
    }

    private static String statusWithoutRows(final BookingState latest) {
        return switch (latest) {
            case SURPLUS_RELEASED -> STATUS_SURPLUS;
            // CONFIRMED with no rows: a listing freed before UNLISTED was recorded.
            case UNLISTED, CONFIRMED -> STATUS_UNLISTED;
            case EXPIRED -> STATUS_EXPIRED;
            case DRAFT_RELEASED, RELEASED, REPICK_RELEASED -> STATUS_RELEASED;
            // Held by the log's account, yet no rows: removed by something that does not log.
            case RESERVED, REBOOKED -> STATUS_NOT_FOUND;
        };
    }

    /**
     * Reservation rows are identified by a non-null {@code expiresAt}; a confirmed booking has
     * {@code expiresAt == null}. {@code source} is descriptive only, never the discriminator.
     */
    private static boolean isReservation(final AllocatedListing row) {
        return row.getExpiresAt() != null;
    }

    /**
     * Attaches active judiciary assignments to the supplied bookings, keyed on
     * {@code listingProfileId} + {@code courtScheduleId}. Shared by both the reservation and
     * legacy provisional_booking sources — {@link #buildInfoFromReservation} and
     * {@link #buildProvisionalInfo} both populate those two fields, so this works unchanged for
     * either source.
     */
    private void attachJudiciaries(final List<ProvisionalBookingInfo> provisionalBookingInfoArrayList) {
        final List<uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary> courtScheduleJudiciariesArrayList = new ArrayList<>();
        final ModelMapper modelMapper = new ModelMapper();

        final List<CourtSchedule> courtSchedulesWithListingProfile = provisionalBookingInfoArrayList.stream()
                .filter(provisionalBooking -> provisionalBooking.getListingProfileId() != null)
                .map(provisionalBooking -> {
                    final CourtSchedule courtSchedule = new CourtSchedule();
                    courtSchedule.setCourtScheduleId(provisionalBooking.getCourtScheduleId());
                    courtSchedule.setListingProfileId(provisionalBooking.getListingProfileId());
                    return courtSchedule;
                })
                .toList();
        //judiciary details are not required for provisional bookings without listing profile(ghost rota)
        if (isNotEmpty(courtSchedulesWithListingProfile)) {
            final List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(courtSchedulesWithListingProfile);
            courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> {
                final uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary domain =
                        modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.openapi.model.CourtScheduleJudiciary.class);
                domain.setEmailAddress(courtScheduleJudiciary.getEmail());
                courtScheduleJudiciariesArrayList.add(domain);
            });
        }

        provisionalBookingInfoArrayList.forEach(provisionalBooking ->
                Optional.of(courtScheduleJudiciariesArrayList.stream()
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtListingProfileId().equals(provisionalBooking.getListingProfileId()))
                        .filter(courtScheduleJudiciary -> courtScheduleJudiciary.getCourtScheduleId().equals(provisionalBooking.getCourtScheduleId()))
                        .toList()).ifPresent(provisionalBooking.getJudiciaries()::addAll));
    }

    /**
     * {@code findBy} returns null for an unknown id, but that cannot happen here:
     * {@code allocated_listings.court_schedule_id} is {@code NOT NULL} (changeset 003) and carries
     * the foreign key {@code allocated_listings_court_schedule_id_fk} onto {@code court_schedule(id)}
     * (changeset 033). The database therefore guarantees a parent session exists for every row this
     * method is handed, and — since that FK has no {@code ON DELETE} clause — the parent cannot be
     * deleted out from under a live reservation either. No null guard is added rather than adding
     * dead code that would mask a broken constraint if the FK were ever dropped.
     */
    private ProvisionalBookingInfo buildInfoFromReservation(final AllocatedListing reservation) {
        final CourtSchedule courtSchedule = courtScheduleRepository.findBy(reservation.getCourtScheduleId());
        return new ProvisionalBookingInfo()
                .courtScheduleId(courtSchedule.getCourtScheduleId())
                .listingProfileId(courtSchedule.getListingProfileId())
                .ouCode(courtSchedule.getOuCode())
                .courtHouseId(courtSchedule.getCourtHouseId())
                .courtHouseName(courtSchedule.getCourtHouseName())
                .courtRoomId(courtSchedule.getCourtRoomId())
                .courtRoomNumber(courtSchedule.getCourtRoomNumber())
                .courtRoomName(courtSchedule.getCourtRoomName())
                .businessType(courtSchedule.getBusinessType())
                .courtSession(courtSchedule.getCourtSession())
                .sessionDate(courtSchedule.getSessionDate())
                .panel(courtSchedule.getPanel())
                .operationalUnit(courtSchedule.getOperationalUnit())
                .availableSlots(courtSchedule.getAvailableSlots())
                .availableDuration(courtSchedule.getAvailableDuration())
                .maxSlots(courtSchedule.getMaxSlots())
                .maxDuration(courtSchedule.getMaxDuration())
                .bookingId(reservation.getBookingId())
                .hearingStartTime(reservation.getHearingStartTime() == null ? null
                        : reservation.getHearingStartTime().atOffset(java.time.ZoneOffset.UTC));
    }

    private ProvisionalBookingInfo buildProvisionalInfo(final ProvisionalBooking provisionalBooking) {
        final ProvisionalBookingInfo provisionalBookingInfoBuilder = new ProvisionalBookingInfo();
        final CourtSchedule courtSchedule = provisionalBooking.getProvisionalBookingKey().getCourtSchedule();
        provisionalBookingInfoBuilder.courtScheduleId(courtSchedule.getCourtScheduleId())
                .listingProfileId(courtSchedule.getListingProfileId())
                .ouCode(courtSchedule.getOuCode())
                .courtHouseId(courtSchedule.getCourtHouseId())
                .courtHouseName(courtSchedule.getCourtHouseName())
                .courtRoomId(courtSchedule.getCourtRoomId())
                .courtRoomNumber(courtSchedule.getCourtRoomNumber())
                .courtRoomName(courtSchedule.getCourtRoomName())
                .businessType(courtSchedule.getBusinessType())
                .courtSession(courtSchedule.getCourtSession())
                .sessionDate(courtSchedule.getSessionDate())
                .panel(courtSchedule.getPanel())
                .operationalUnit(courtSchedule.getOperationalUnit())
                .availableSlots(courtSchedule.getAvailableSlots())
                .availableDuration(courtSchedule.getAvailableDuration())
                .maxSlots(courtSchedule.getMaxSlots())
                .maxDuration(courtSchedule.getMaxDuration())
                .bookingId(provisionalBooking.getProvisionalBookingKey().getBookingId())
                .hearingStartTime(provisionalBooking.getHearingStartTime() == null ? null
                        : provisionalBooking.getHearingStartTime().atOffset(java.time.ZoneOffset.UTC));
        return provisionalBookingInfoBuilder;
    }
}
