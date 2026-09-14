package uk.gov.moj.cpp.courtscheduler.api.service;

import static java.util.UUID.randomUUID;
import static org.apache.commons.collections.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static uk.gov.moj.cpp.courtscheduler.domain.RequestParameterConstant.PROVISIONAL_SLOTS;

import uk.gov.moj.cpp.courtscheduler.common.converter.ListToJsonArrayConverter;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingInfo;
import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalBookingSlots;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;
import uk.gov.moj.cpp.courtscheduler.repository.AllocatedListingRepository;
import uk.gov.moj.cpp.courtscheduler.repository.CourtScheduleRepository;
import uk.gov.moj.cpp.courtscheduler.repository.ProvisionalBookingRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
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
    private static final String BOOKING_ID = "bookingId";
    private static final String STATUS = "status";
    private static final String SAFE_TO_SHARE = "safeToShare";
    private static final String STATUS_RESERVED = "RESERVED";
    private static final String STATUS_SHARED = "SHARED";
    private static final String STATUS_LEGACY = "LEGACY";
    private static final String STATUS_NONE = "NONE";
    @Inject
    private ProvisionalBookingRepository provisionalBookingRepository;
    @Inject
    private CourtScheduleRepository courtScheduleRepository;
    @Inject
    private ReservationService reservationService;
    @Inject
    private AllocatedListingRepository allocatedListingRepository;
    private final ListToJsonArrayConverter<ProvisionalBookingInfo> listToJsonArrayConverter = new ListToJsonArrayConverter<>();

    /**
     * Mints a bookingId, or reuses a supplied one, and reserves every requested session under it.
     * The reservation replaces the provisional_booking row that used to be written here: it holds
     * real capacity and carries an expiry, which a provisional booking never did.
     *
     * <p><b>Why a caller may name the bookingId.</b> When a clerk changes their mind and picks a
     * different session, the re-pick arrives under the bookingId the draft already holds. Because
     * a reservation's hearing_id is its bookingId, saveBookedSlots' hearing-wide release then
     * wipes every row of the previous pick before taking the new ones — so the abandoned session's
     * capacity comes back in the same transaction, with no second release mechanism and nothing
     * left for the nightly purge to find. An absent bookingId means a first pick, and one is
     * minted.
     *
     * <p>All or nothing, and structurally so: every slot of the booking shares the minted
     * bookingId as its hearing_id, and saveBookedSlots opens with a hearing-wide release keyed on
     * that id. Reserving slot by slot would therefore make each slot release the previous ones of
     * the same booking, leaving a three-day pick holding one day. {@code reserveAll} makes exactly
     * one saveBookedSlots call for the whole booking. The method is transactional on top of that,
     * so a NoCapacityException still rolls back anything already written.
     */
    public JsonObject bookProvisionalSlots(final ProvisionalBookingSlots provisionalBookingSlots) {
        final String bookingId = isNotBlank(provisionalBookingSlots.getBookingId())
                ? provisionalBookingSlots.getBookingId()
                : randomUUID().toString();
        reservationService.reserveAll(bookingId, provisionalBookingSlots.getProvisionalSlots());

        return Json.createObjectBuilder()
                .add(BOOKING_ID, bookingId)
                .build();
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
            final List<AllocatedListing> reservations = allocatedListingRepository.findByHearingId(bookingId).stream()
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
     * <p>A legacy provisional_booking row counts as live. Those drafts never had a reservation,
     * and reporting them as expired would block every pre-go-live magistrates draft at share.
     *
     * <p>Checks each booking id individually rather than batching the legacy lookup (contrast
     * {@link #fetchProvisionalSlots}, which batches unresolved ids into one query) — this method
     * only needs a per-id boolean, not the full session detail that justifies batching there.
     *
     * <p><b>Contract.</b> {@code status} is one of {@code RESERVED} (a reservation still holds
     * capacity), {@code SHARED} (a confirmed row carries this booking id, so the result was
     * already shared and its hold correctly released), {@code LEGACY} (a pre-reserve-a-slot
     * provisional_booking row, active or not), or {@code NONE} (nothing found — the hold expired
     * and was purged). {@code safeToShare} is {@code status != NONE}.
     *
     * <p>Unlike the {@code live} flag this replaces, the answer no longer conflates an expired
     * hold with an already-shared booking, so a caller may gate a re-share on it directly.
     */
    public JsonObject getBookingStatus(final String bookingIds) {
        final List<String> bookingIdList = Stream.of(bookingIds.split(","))
                .map(String::trim)
                .toList();

        final JsonArrayBuilder bookings = Json.createArrayBuilder();
        for (final String bookingId : bookingIdList) {
            final String status = statusOf(bookingId);
            bookings.add(Json.createObjectBuilder()
                    .add(BOOKING_ID, bookingId)
                    .add(SAFE_TO_SHARE, !STATUS_NONE.equals(status))
                    .add(STATUS, status));
        }
        return Json.createObjectBuilder().add("bookings", bookings).build();
    }

    /**
     * Resolves a booking id to one of four states, in precedence order.
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
        final boolean hasReservation = allocatedListingRepository.findByHearingId(bookingId).stream()
                .anyMatch(ProvisionalBookingService::isReservation);
        if (hasReservation) {
            return STATUS_RESERVED;
        }
        // The confirmed row BUG-3 stamps at share time. Its mere existence is the answer: a
        // reservation never carries booking_id, so nothing expiring can appear here.
        if (!allocatedListingRepository.findByBookingId(bookingId).isEmpty()) {
            return STATUS_SHARED;
        }
        // Drafts saved before reserve-a-slot shipped. findByBookingIdIn does not filter on the
        // active flag, so a soft-deleted row still answers — which is what we want: a legacy
        // draft is safe to share whether or not it already was.
        if (!provisionalBookingRepository.findByBookingIdIn(List.of(bookingId)).isEmpty()) {
            return STATUS_LEGACY;
        }
        return STATUS_NONE;
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
        final List<uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary> courtScheduleJudiciariesArrayList = new ArrayList<>();
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
            List<CourtScheduleJudiciary> courtScheduleJudiciaries = courtScheduleRepository.getCourtScheduleJudiciariesForProvisionalBooking(courtSchedulesWithListingProfile);
            courtScheduleJudiciaries.forEach(courtScheduleJudiciary -> {
                final uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary domain =
                        modelMapper.map(courtScheduleJudiciary, uk.gov.moj.cpp.courtscheduler.domain.CourtScheduleJudiciary.class);
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
        return new ProvisionalBookingInfo.ProvisionalBookingInfoBuilder()
                .withCourtScheduleId(courtSchedule.getCourtScheduleId())
                .withListingProfileId(courtSchedule.getListingProfileId())
                .withOuCode(courtSchedule.getOuCode())
                .withCourtHouseId(courtSchedule.getCourtHouseId())
                .withCourtHouseName(courtSchedule.getCourtHouseName())
                .withCourtRoomId(courtSchedule.getCourtRoomId())
                .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                .withCourtRoomName(courtSchedule.getCourtRoomName())
                .withBusinessType(courtSchedule.getBusinessType())
                .withCourtSession(courtSchedule.getCourtSession())
                .withSessionDate(courtSchedule.getSessionDate())
                .withPanel(courtSchedule.getPanel())
                .withOperationalUnit(courtSchedule.getOperationalUnit())
                .withAvailableSlots(courtSchedule.getAvailableSlots())
                .withAvailableDuration(courtSchedule.getAvailableDuration())
                .withMaxSlots(courtSchedule.getMaxSlots())
                .withMaxDuration(courtSchedule.getMaxDuration())
                .withBookingId(reservation.getHearingId())
                .withHearingStartTime(reservation.getHearingStartTime())
                .build();
    }

    private ProvisionalBookingInfo buildProvisionalInfo(final ProvisionalBooking provisionalBooking) {
        final ProvisionalBookingInfo.ProvisionalBookingInfoBuilder provisionalBookingInfoBuilder = new ProvisionalBookingInfo.ProvisionalBookingInfoBuilder();
        CourtSchedule courtSchedule = provisionalBooking.getProvisionalBookingKey().getCourtSchedule();
        provisionalBookingInfoBuilder.withCourtScheduleId(courtSchedule.getCourtScheduleId())
                .withListingProfileId(courtSchedule.getListingProfileId())
                .withOuCode(courtSchedule.getOuCode())
                .withCourtHouseId(courtSchedule.getCourtHouseId())
                .withCourtHouseName(courtSchedule.getCourtHouseName())
                .withCourtRoomId(courtSchedule.getCourtRoomId())
                .withCourtRoomNumber(courtSchedule.getCourtRoomNumber())
                .withCourtRoomName(courtSchedule.getCourtRoomName())
                .withBusinessType(courtSchedule.getBusinessType())
                .withCourtSession(courtSchedule.getCourtSession())
                .withSessionDate(courtSchedule.getSessionDate())
                .withPanel(courtSchedule.getPanel())
                .withOperationalUnit(courtSchedule.getOperationalUnit())
                .withAvailableSlots(courtSchedule.getAvailableSlots())
                .withAvailableDuration(courtSchedule.getAvailableDuration())
                .withMaxSlots(courtSchedule.getMaxSlots())
                .withMaxDuration(courtSchedule.getMaxDuration())
                .withBookingId(provisionalBooking.getProvisionalBookingKey().getBookingId())
                .withHearingStartTime(provisionalBooking.getHearingStartTime());
        return provisionalBookingInfoBuilder.build();
    }
}
