package uk.gov.moj.cpp.courtscheduler.integration.utils;


import static java.util.Collections.unmodifiableList;
import static java.util.Objects.nonNull;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang3.tuple.Pair;

public class DatabaseReader {
    private static final String EXCEPTION_WHILE_QUERYING_THE_DB = "Exception while querying the DB";


    private static final String USERNAME = System.getProperty("db.user", "courtscheduler");
    private static final String PASSWORD = System.getProperty("db.password", "courtscheduler");
    private static final String DATABASE = System.getProperty("db.name", "courtscheduler");

    private static final String COURT_SCHEDULE_GET_SQL = "SELECT * FROM court_schedule WHERE active is true ORDER BY session_start";
    private static final String COURT_SCHEDULE_JUDICIARY_GET_SQL = "SELECT * FROM court_schedule_judiciary WHERE active is true";
    private static final String ALLOCATED_LISTINGS_GET_SQL = "SELECT * FROM allocated_listings";
    private static final String COURT_SCHEDULE_MAX_UPDATED_ON_CREATED_ON_SQL = "SELECT max(created_on) maxCreatedOn, max(updated_on) maxUpdatedOn FROM court_schedule WHERE active is true";
    private static final String COURT_SCHEDULE_JUDICIARY_MAX_UPDATED_ON_CREATED_ON_SQL = "SELECT max(created_on) maxCreatedOn, max(updated_on) maxUpdatedOn FROM court_schedule_judiciary WHERE active is true";
    private static final String COURT_SCHEDULE_CREATED_AFTER_SQL = "SELECT * FROM court_schedule WHERE active is true AND created_on > ? ORDER BY session_start";
    private static final String COURT_SCHEDULE_UPDATED_AFTER_SQL = "SELECT * FROM court_schedule WHERE active is true AND updated_on > ? ORDER BY session_start";
    private static final String COURT_SCHEDULE_JUDICIARY_CREATED_AFTER_SQL = "SELECT * FROM court_schedule_judiciary WHERE active is true AND created_on > ?";
    private static final String COURT_SCHEDULE_BY_ID_SQL = "SELECT * FROM court_schedule WHERE id = ?";
    private static final String ROTA_PROCESS_LOG_SINCE_SQL = "SELECT error_text FROM rota_process_log WHERE error_code = ? AND timestamp >= ? ORDER BY timestamp";
    private static final String ROTA_PROCESS_LOG_ALL_SINCE_SQL = "SELECT error_code, error_text FROM rota_process_log WHERE timestamp >= ? ORDER BY timestamp";

    private final ConnectionProvider connectionProvider = new ConnectionProvider();

    public List<CourtSchedule> courtSchedules() {
        return executeCourtScheduleQuery();
    }

    public List<CourtScheduleJudiciary> courtScheduleJudiciaries() {
        return executeCourtScheduleJudiciaryQuery();
    }

    public List<AllocatedListing> allocatedListings() {
        return executeAllocatedListingsQuery();
    }

    public List<CourtSchedule> courtSchedulesCreatedAfter(final LocalDateTime createdOn) {
        return executeCourtScheduleCreatedAfterQuery(createdOn);
    }

    public List<CourtSchedule> courtSchedulesUpdatedAfter(final LocalDateTime updatedOn) {
        return executeCourtScheduleUpdatedAfterQuery(updatedOn);
    }

    public List<CourtScheduleJudiciary> courtScheduleJudiciariesCreatedAfter(final LocalDateTime createdOn) {
        return executeCourtScheduleJudiciariesCreatedAfterQuery(createdOn);
    }

    /** The {@code error_text} of every rota_process_log row with {@code errorCode} written at or after {@code since}. */
    public List<String> rotaProcessLogErrorTextsSince(final String errorCode, final Instant since) {
        final List<String> errorTexts = new ArrayList<>();
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(ROTA_PROCESS_LOG_SINCE_SQL)) {
            statement.setString(1, errorCode);
            statement.setTimestamp(2, Timestamp.from(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    errorTexts.add(resultSet.getString("error_text"));
                }
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
        return errorTexts;
    }

    /** Every rota_process_log row written at or after {@code since}, as "error_code: error_text". */
    public List<String> rotaProcessLogSince(final Instant since) {
        final List<String> rows = new ArrayList<>();
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(ROTA_PROCESS_LOG_ALL_SINCE_SQL)) {
            statement.setTimestamp(1, Timestamp.from(since));
            try (ResultSet resultSet = statement.executeQuery()) {
                while (resultSet.next()) {
                    rows.add(resultSet.getString("error_code") + ": " + resultSet.getString("error_text"));
                }
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
        return rows;
    }

    public CourtSchedule courtScheduleById(final String courtScheduleId) {
        return executeCourtScheduleById(courtScheduleId);
    }

    public CourtSchedule courtScheduleById(final String courtScheduleId, final Connection connection) {
        try (PreparedStatement statement = connection.prepareStatement(COURT_SCHEDULE_BY_ID_SQL)) {
            statement.setString(1, courtScheduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (nonNull(resultSet) && resultSet.next()) {
                    return resultSetToCourtSchedule(resultSet);
                }
                return null;
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    public Pair<LocalDateTime, LocalDateTime> getMaxCreatedOnForCourtSchedule() {
        LocalDateTime maxCreatedOn = null;
        LocalDateTime maxUpdatedOn = null;
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_MAX_UPDATED_ON_CREATED_ON_SQL)) {
                while (resultSet.next()) {
                    maxCreatedOn = resultSet.getTimestamp("maxCreatedOn").toLocalDateTime();
                    maxUpdatedOn = resultSet.getTimestamp("maxUpdatedOn").toLocalDateTime();
                }
                return Pair.of(maxCreatedOn, maxUpdatedOn);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }


    public Pair<LocalDateTime, LocalDateTime> getMaxUpdatedAndCreatedOnForCourtScheduleJudiciary() {
        LocalDateTime maxCreatedOn = null;
        LocalDateTime maxUpdatedOn = null;
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_JUDICIARY_MAX_UPDATED_ON_CREATED_ON_SQL)) {
                while (resultSet.next()) {
                    maxCreatedOn = resultSet.getTimestamp("maxCreatedOn").toLocalDateTime();
                    maxUpdatedOn = resultSet.getTimestamp("maxUpdatedOn").toLocalDateTime();
                }
                return Pair.of(maxCreatedOn, maxUpdatedOn);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<CourtSchedule> executeCourtScheduleCreatedAfterQuery(final LocalDateTime createdOn) {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(COURT_SCHEDULE_CREATED_AFTER_SQL)) {
            statement.setTimestamp(1, Timestamp.valueOf(createdOn));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<CourtSchedule> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToCourtSchedule(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<CourtScheduleJudiciary> executeCourtScheduleJudiciariesCreatedAfterQuery(final LocalDateTime createdOn) {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(COURT_SCHEDULE_JUDICIARY_CREATED_AFTER_SQL)) {
            statement.setTimestamp(1, Timestamp.valueOf(createdOn));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<CourtScheduleJudiciary> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToCourtScheduleJudiciary(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private CourtSchedule executeCourtScheduleById(final String courtScheduleId) {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(COURT_SCHEDULE_BY_ID_SQL)) {
            statement.setString(1, courtScheduleId);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (nonNull(resultSet) && resultSet.next()) {
                    return resultSetToCourtSchedule(resultSet);
                }
                return null;
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<CourtSchedule> executeCourtScheduleUpdatedAfterQuery(final LocalDateTime updatedOn) {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement statement = connection.prepareStatement(COURT_SCHEDULE_UPDATED_AFTER_SQL)) {
            statement.setTimestamp(1, Timestamp.valueOf(updatedOn));
            try (ResultSet resultSet = statement.executeQuery()) {
                final List<CourtSchedule> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToCourtSchedule(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<CourtSchedule> executeCourtScheduleQuery() {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_GET_SQL)) {
                final List<CourtSchedule> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToCourtSchedule(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<CourtScheduleJudiciary> executeCourtScheduleJudiciaryQuery() {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_JUDICIARY_GET_SQL)) {
                final List<CourtScheduleJudiciary> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToCourtScheduleJudiciary(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private List<AllocatedListing> executeAllocatedListingsQuery() {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             Statement statement = connection.createStatement()) {
            try (ResultSet resultSet = statement.executeQuery(ALLOCATED_LISTINGS_GET_SQL)) {
                final List<AllocatedListing> rows = new ArrayList<>();
                while (resultSet.next()) {
                    rows.add(resultSetToAllocatedListing(resultSet));
                }
                return unmodifiableList(rows);
            }
        } catch (final SQLException exp) {
            throw new RuntimeException(EXCEPTION_WHILE_QUERYING_THE_DB, exp);
        }
    }

    private static Instant toInstant(final ResultSet resultSet, final String column) throws SQLException {
        return Optional.ofNullable(resultSet.getTimestamp(column)).map(Timestamp::toInstant).orElse(null);
    }

    private CourtSchedule resultSetToCourtSchedule(final ResultSet resultSet) throws SQLException {
        final CourtSchedule courtSchedule = new CourtSchedule();
        courtSchedule.setCourtScheduleId(resultSet.getString("id"));
        courtSchedule.setListingProfileId(resultSet.getString("court_listing_profile_id"));
        courtSchedule.setOuCode(resultSet.getString("oucode"));
        courtSchedule.setCourtHouseName(resultSet.getString("court_house_name"));
        courtSchedule.setCourtHouseId(resultSet.getString("court_house_id"));
        courtSchedule.setCourtRoomId(resultSet.getString("court_room_id"));
        courtSchedule.setCourtRoomNumber(resultSet.getInt("court_room_number"));
        courtSchedule.setCourtRoomName(resultSet.getString("court_room_name"));
        courtSchedule.setBusinessType(resultSet.getString("rota_business_type"));
        courtSchedule.setCourtSession(resultSet.getString("court_session"));
        courtSchedule.setSessionDate(resultSet.getDate("session_start").toLocalDate());
        courtSchedule.setPanel(resultSet.getString("panel"));
        courtSchedule.setOperationalUnit(resultSet.getString("operational_unit"));
        courtSchedule.setAvailableSlots(resultSet.getInt("available_slot"));
        courtSchedule.setAvailableDuration(resultSet.getInt("available_duration_mins"));
        courtSchedule.setMaxSlots(resultSet.getInt("max_slot"));
        courtSchedule.setMaxDuration(resultSet.getInt("max_duration_mins"));
        courtSchedule.setSlotBased(resultSet.getBoolean("is_slot_based"));
        courtSchedule.setSupportAdSplit(resultSet.getBoolean("support_ad_split"));
        courtSchedule.setMaxAdMorningDuration(resultSet.getInt("max_ad_morning_duration"));
        courtSchedule.setMaxAdAfternoonDuration(resultSet.getInt("max_ad_afternoon_duration"));
        courtSchedule.setActive(resultSet.getBoolean("active"));
        final Instant createdOnTimestamp = toInstant(resultSet, "created_on");
        if (nonNull(createdOnTimestamp)) {
            courtSchedule.setCreatedOn(createdOnTimestamp);
        }
        final Instant updatedOnTimestamp = toInstant(resultSet, "updated_on");
        if (nonNull(updatedOnTimestamp)) {
            courtSchedule.setUpdatedOn(updatedOnTimestamp);
        }

        final Instant sessionStartTime = toInstant(resultSet, "session_start_time");
        if (nonNull(sessionStartTime)) {
            courtSchedule.setSessionStartTime(sessionStartTime);
        }

        final Instant sessionEndTime = toInstant(resultSet, "session_end_time");
        if (nonNull(sessionEndTime)) {
            courtSchedule.setSessionEndTime(sessionEndTime);
        }

        courtSchedule.setIsOverbookingAllowed(resultSet.getBoolean("is_overbooking_allowed"));
        courtSchedule.setIsDraft(resultSet.getBoolean("is_draft"));
        courtSchedule.setJurisdiction(resultSet.getString("jurisdiction"));

        return courtSchedule;
    }

    private CourtScheduleJudiciary resultSetToCourtScheduleJudiciary(final ResultSet resultSet) throws SQLException {
        final CourtScheduleJudiciary courtScheduleJudiciary = new CourtScheduleJudiciary();
        final CourtScheduleJudiciaryKey courtScheduleJudiciaryKey = new CourtScheduleJudiciaryKey();
        courtScheduleJudiciaryKey.setJudiciaryId(resultSet.getString("judiciary_id"));
        courtScheduleJudiciaryKey.setCourtScheduleId(resultSet.getString("court_schedule_id"));

        courtScheduleJudiciary.setId(courtScheduleJudiciaryKey);
        courtScheduleJudiciary.setCourtListingProfileId(resultSet.getString("court_listing_profile_id"));
        courtScheduleJudiciary.setRotaJudiciaryId(resultSet.getString("rota_judiciary_id"));
        courtScheduleJudiciary.setTitle(resultSet.getString("title"));
        courtScheduleJudiciary.setForenames(resultSet.getString("forenames"));
        courtScheduleJudiciary.setSurname(resultSet.getString("surname"));
        courtScheduleJudiciary.setEmail(resultSet.getString("email"));
        courtScheduleJudiciary.setJudiciaryType(resultSet.getString("judiciary_type"));
        courtScheduleJudiciary.setBenchChairman(resultSet.getBoolean("is_bench_chairman"));
        courtScheduleJudiciary.setDeputy(resultSet.getBoolean("is_deputy"));
        courtScheduleJudiciary.setPosition(resultSet.getString("position"));
        courtScheduleJudiciary.setActive(resultSet.getBoolean("active"));
        courtScheduleJudiciary.setCreatedOn(resultSet.getTimestamp("created_on").toInstant());
        courtScheduleJudiciary.setUpdatedOn(resultSet.getTimestamp("updated_on").toInstant());

        return courtScheduleJudiciary;
    }

    private AllocatedListing resultSetToAllocatedListing(final ResultSet resultSet) throws SQLException {
        final AllocatedListing allocatedListing = new AllocatedListing();
        allocatedListing.setId(resultSet.getString("id"));
        allocatedListing.setCourtScheduleId(resultSet.getString("court_schedule_id"));
        allocatedListing.setHearingId(resultSet.getString("hearing_id"));
        allocatedListing.setOucode(resultSet.getString("oucode"));
        allocatedListing.setCourtRoomId(resultSet.getInt("court_room_id"));
        allocatedListing.setRotaBusinessType(resultSet.getString("rota_business_type"));
        allocatedListing.setDuration(resultSet.getInt("duration"));
        // getTimestamp, not getDate — java.sql.Date drops the time-of-day and throws on toInstant()
        allocatedListing.setHearingStartTime(resultSet.getTimestamp("hearing_start_time").toInstant());
        allocatedListing.setSource(resultSet.getString("source"));
        allocatedListing.setCreatedOn(resultSet.getTimestamp("created_on").toInstant());
        allocatedListing.setUpdatedOn(resultSet.getTimestamp("updated_on").toInstant());

        return allocatedListing;
    }
}
