package uk.gov.moj.cpp.courtscheduler.persist.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * One row of {@code booking_log}: one allocated_listings row as it was when the booking entered
 * {@link #state}. Rows written together for one booking share an {@link #eventId}; the booking's
 * latest event (its current state) is the one with the latest {@link #recordedAt}.
 */
@Entity
@Table(name = "booking_log")
public class BookingLog {

    private static final AtomicReference<Instant> LAST_RECORDED_AT = new AtomicReference<>(Instant.EPOCH);

    @Id
    private String id;

    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "booking_id", nullable = false)
    private String bookingId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private BookingState state;

    @Column(name = "court_schedule_id")
    private String courtScheduleId;

    @Column(name = "hearing_id")
    private String hearingId;

    @Column(name = "oucode")
    private String oucode;

    @Column(name = "court_room_id")
    private Integer courtRoomId;

    @Column(name = "rota_business_type")
    private String rotaBusinessType;

    @Column(name = "hearing_start_time")
    private Instant hearingStartTime;

    @Column(name = "duration")
    private Integer duration;

    // The hold's expiry as it was on the row (null for a confirmed listing, and for a release that
    // found nothing held).
    @Column(name = "expires_at")
    private LocalDate expiresAt;

    // When the row was appended; orders a booking's events.
    @Column(name = "recorded_at", nullable = false)
    private Instant recordedAt;

    public static BookingLog fromHold(final String eventId, final BookingState state, final AllocatedListing row, final Instant recordedAt) {
        final BookingLog log = new BookingLog();
        log.id = UUID.randomUUID().toString();
        log.eventId = eventId;
        log.bookingId = row.getBookingId();
        log.state = state;
        log.courtScheduleId = row.getCourtScheduleId();
        log.hearingId = row.getHearingId();
        log.oucode = row.getOucode();
        log.courtRoomId = row.getCourtRoomId();
        log.rotaBusinessType = row.getRotaBusinessType();
        log.hearingStartTime = row.getHearingStartTime();
        log.duration = row.getDuration();
        log.expiresAt = row.getExpiresAt();
        log.recordedAt = recordedAt;
        return log;
    }

    /**
     * The time to record an event at: now, at the microsecond precision Postgres stores, and
     * strictly after the last one this instance handed out. Two events written in the same request
     * (a re-pick's REPICK_RELEASED then RESERVED) therefore never share a timestamp, so "the
     * latest event" is always one event.
     */
    public static Instant nextRecordedAt() {
        return LAST_RECORDED_AT.updateAndGet(last -> {
            final Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            return now.isAfter(last) ? now : last.plus(1, ChronoUnit.MICROS);
        });
    }

    public String getId() {
        return id;
    }

    public String getEventId() {
        return eventId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public BookingState getState() {
        return state;
    }

    public String getCourtScheduleId() {
        return courtScheduleId;
    }

    public String getHearingId() {
        return hearingId;
    }

    public String getOucode() {
        return oucode;
    }

    public Integer getCourtRoomId() {
        return courtRoomId;
    }

    public String getRotaBusinessType() {
        return rotaBusinessType;
    }

    public Instant getHearingStartTime() {
        return hearingStartTime;
    }

    public Integer getDuration() {
        return duration;
    }

    public LocalDate getExpiresAt() {
        return expiresAt;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(final Instant recordedAt) {
        this.recordedAt = recordedAt;
    }
}
