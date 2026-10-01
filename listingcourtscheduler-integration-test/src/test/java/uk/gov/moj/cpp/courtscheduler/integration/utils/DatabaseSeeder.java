package uk.gov.moj.cpp.courtscheduler.integration.utils;


import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.CROWN;
import static uk.gov.moj.cpp.courtscheduler.common.Jurisdiction.MAGISTRATES;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;

import uk.gov.moj.cpp.courtscheduler.domain.AvailabilityDayOfWeek;
import uk.gov.moj.cpp.courtscheduler.openapi.model.JudiciaryUnavailability;
import uk.gov.moj.cpp.courtscheduler.openapi.model.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.exception.PersistenceStoreException;
import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedulerMigrationStatus;
import uk.gov.moj.cpp.courtscheduler.persist.entity.ProvisionalBooking;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public class DatabaseSeeder {

    private static final String USERNAME = System.getProperty("db.user", "courtscheduler");
    private static final String PASSWORD = System.getProperty("db.password", "courtscheduler");
    private static final String DATABASE = System.getProperty("db.name", "courtscheduler");
    private static final java.util.Set<String> ALLOWED_JURISDICTIONS =
            java.util.Set.of(MAGISTRATES.getJurisdiction(), CROWN.getJurisdiction());

    private static final String COURT_SCHEDULE_INSERT_SQL = "INSERT INTO court_schedule (" +
            "id, court_listing_profile_id, oucode, court_room_id, court_room_number, court_house_id, court_house_name," +
            "court_room_name, operational_unit, rota_business_type, panel, court_session, is_slot_based, session_start, " +
            "max_slot, max_duration_mins, available_slot, available_duration_mins, support_ad_split, max_ad_morning_duration, max_ad_afternoon_duration, session_start_time, session_end_time, national_break_time, is_overbooking_allowed, is_draft, jurisdiction) \n" +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

    private static final String UPDATE_SESSION_END_TIME_SQL =
            "UPDATE court_schedule SET session_end_time = ? WHERE id = ?";

    private static final String ALLOCATED_LISTING_INSERT_SQL = "INSERT INTO allocated_listings (" +
            "id, court_schedule_id, booking_id, hearing_id, oucode, court_room_id, rota_business_type," +
            "duration, hearing_start_time, updated_on, created_on) \n" +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?,?)";

    private static final String PROVISIONAL_BOOKING_INSERT_SQL = "INSERT INTO provisional_booking (" +
            "court_schedule_id, booking_id, active, updated_on, created_on, hearing_start_time) \n" +
            "VALUES(?, ?, ?, ?, ?, ?)";

    public static final String UPSERT_CSJ_QRY =
            " INSERT INTO COURT_SCHEDULE_JUDICIARY" +
                    " (court_schedule_id, court_listing_profile_id, " +
                    " judiciary_id, rota_judiciary_id, title, forenames, surname, email, judiciary_type, is_bench_chairman, is_deputy, position) " +
                    " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)" +
                    " ON CONFLICT ON CONSTRAINT combination_primary_key DO " +
                    " UPDATE SET " +
                    " court_listing_profile_id = ?," +
                    " judiciary_id = ?," +
                    " rota_judiciary_id = ?," +
                    " title = ?," +
                    " forenames = ?," +
                    " surname = ?," +
                    " email = ?," +
                    " judiciary_type = ?," +
                    " is_bench_chairman = ?," +
                    " is_deputy = ?," +
                    " position = ?," +
                    " updated_on = CURRENT_TIMESTAMP" +
                    " WHERE  COURT_SCHEDULE_JUDICIARY.court_schedule_id = ? AND COURT_SCHEDULE_JUDICIARY.judiciary_id = ? " +
                    ";";

    private static final String COURT_SCHEDULE_MIGRATION_STATUS_INSERT_SQL = "INSERT INTO courtscheduler_migration_status (" +
            "oucode, court_centre_id, migrated, updated_on) VALUES(?, ?, ?, ?)";
    public static final String INSERT_PROVISIONAL_SLOTS_QRY = "INSERT INTO provisional_booking (booking_id, court_schedule_id, hearing_start_time) VALUES (?, ?, ?)";
    private static final String COURT_SCHEDULE_DELETE_SQL = "TRUNCATE TABLE court_schedule CASCADE";
    private static final String ALLOCATED_LISTING_DELETE_SQL = "TRUNCATE TABLE allocated_listings CASCADE";
    private static final String PROVISIONAL_BOOKING_DELETE_SQL = "TRUNCATE TABLE provisional_booking CASCADE";

    private static final String COURT_SCHEDULE_JUDICIARY_DELETE_SQL = "TRUNCATE TABLE court_schedule_judiciary CASCADE";
    private static final String COURT_SCHEDULE_JUDICIARY_DELETE_BY_PROFILE_ID_SQL = "DELETE FROM court_schedule_judiciary where court_listing_profile_id = ?";
    private static final String MIGRATION_STATUS_DELETE_SQL = "TRUNCATE TABLE courtscheduler_migration_status CASCADE";
    private static final String ROTA_FILE_PROCESS_HISTORY_DELETE_SQL = "TRUNCATE TABLE rota_file_process_history CASCADE";
    private static final String ROTA_LOG_PROCESS_DELETE_SQL = "TRUNCATE TABLE rota_process_log CASCADE";
    private static final String JUDICIARY_AVAILABILITY_RULE_DELETE_SQL = "TRUNCATE TABLE judiciary_availability_rule CASCADE";

    private static final String JUDICIARY_AVAILABILITY_RULE_INSERT_SQL = "INSERT INTO judiciary_availability_rule (" +
            "id, judiciary_id, court_house_id, from_date, to_date, session_type, created_on, updated_on) " +
            "VALUES(?, ?, ?, ?, ?, 'AD', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private static final String JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL = "INSERT INTO judiciary_availability_rule_repeat_day (" +
            "rule_id, day_of_week) VALUES(?, ?)";

    private static final String JUDICIARY_UNAVAILABILITY_INSERT_SQL = "INSERT INTO judiciary_unavailability (" +
            "id, availability_rule_id, from_date, to_date, reason, created_on, updated_on) " +
            "VALUES(?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";


    private static final String COURT_SCHEDULE_SET_LISTING_PROFILE_ID_AS_NULL_SQL = "UPDATE court_schedule SET court_listing_profile_id = null WHERE oucode = ?";
    private static final String UPDATE_AVAILABLE_SLOT_FOR_COURT_SCHEDULE = "UPDATE court_schedule SET available_slot = available_slot - 1 WHERE court_listing_profile_id = ?";

    private static final String UPDATE_SESSION_TYPE_SQL = "UPDATE judiciary_availability_rule SET session_type = ? WHERE id = ?";

    // Process in chunks to avoid memory issues with very large batches
    private static final int BATCH_SIZE = 5000;

    private final ConnectionProvider connectionProvider = new ConnectionProvider();

    public Connection getNewConnection() throws SQLException {
        return connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
    }

    public void cleanCourtScheduleTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanAllocatedListingTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanProvisionalBookingTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(PROVISIONAL_BOOKING_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanCourtScheduleJudiciaryTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_JUDICIARY_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void deleteJudiciaryByProfileId(final String listingProfileId) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_JUDICIARY_DELETE_BY_PROFILE_ID_SQL)) {
            preparedStatement.setString(1, listingProfileId);
            preparedStatement.executeUpdate();
        }
    }

    public void cleanMigrationStatusTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(MIGRATION_STATUS_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanRotaFileProcessHistoryTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(ROTA_FILE_PROCESS_HISTORY_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

        public void cleanRotaProcessLogTable() throws SQLException {
            try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
                 PreparedStatement preparedStatement = connection.prepareStatement(ROTA_LOG_PROCESS_DELETE_SQL)) {
                preparedStatement.executeUpdate();
            }
        }


    private static String normalizeJurisdiction(final String j) {
        if (j == null) {
            return MAGISTRATES.getJurisdiction();
        }
        final String up = j.trim().toUpperCase(Locale.ROOT);
        return ALLOWED_JURISDICTIONS.contains(up) ? up : MAGISTRATES.getJurisdiction();
    }

    public void insertCourtSchedule(final CourtSchedule courtSchedule) throws SQLException {


        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_INSERT_SQL)) {

            preparedStatement.setObject(1, courtSchedule.getCourtScheduleId());
            preparedStatement.setString(2, courtSchedule.getListingProfileId());
            preparedStatement.setString(3, courtSchedule.getOuCode());
            preparedStatement.setString(4, courtSchedule.getCourtRoomId());
            preparedStatement.setInt(5, courtSchedule.getCourtRoomNumber());
            preparedStatement.setString(6, courtSchedule.getCourtHouseId());
            preparedStatement.setString(7, courtSchedule.getCourtHouseName());
            preparedStatement.setString(8, courtSchedule.getCourtRoomName());
            preparedStatement.setString(9, courtSchedule.getOperationalUnit());
            preparedStatement.setString(10, courtSchedule.getBusinessType());
            preparedStatement.setString(11, courtSchedule.getPanel());
            preparedStatement.setString(12, courtSchedule.getCourtSession());
            preparedStatement.setBoolean(13, courtSchedule.isSlotBased());
            preparedStatement.setDate(14, Date.valueOf(courtSchedule.getSessionDate()));
            preparedStatement.setInt(15, courtSchedule.getMaxSlots());
            preparedStatement.setInt(16, courtSchedule.getMaxDuration());
            preparedStatement.setInt(17, courtSchedule.getAvailableSlots());
            preparedStatement.setInt(18, courtSchedule.getAvailableDuration());
            preparedStatement.setBoolean(19, courtSchedule.isSupportAdSplit());
            preparedStatement.setInt(20, courtSchedule.getMaxAdMorningDuration());
            preparedStatement.setInt(21, courtSchedule.getMaxAdAfternoonDuration());
            preparedStatement.setTimestamp(22, Timestamp.from(courtSchedule.getSessionStartTime()));
            preparedStatement.setTimestamp(23, Timestamp.from(courtSchedule.getSessionEndTime()));
            preparedStatement.setTimestamp(24, Timestamp.from(courtSchedule.getNationalBreakTime()));
            preparedStatement.setBoolean(25, courtSchedule.isOverbookingAllowed());
            preparedStatement.setBoolean(26, courtSchedule.isDraft());
            preparedStatement.setString(27, normalizeJurisdiction(courtSchedule.getJurisdiction()));

            preparedStatement.executeUpdate();
        }
    }

    public void insertCourtSchedulesBatch(final List<CourtSchedule> courtSchedules) throws SQLException {
        if (courtSchedules.isEmpty()) {
            return;
        }

        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_INSERT_SQL)) {

            connection.setAutoCommit(false);

            for (final CourtSchedule courtSchedule : courtSchedules) {
                preparedStatement.setObject(1, courtSchedule.getCourtScheduleId());
                preparedStatement.setString(2, courtSchedule.getListingProfileId());
                preparedStatement.setString(3, courtSchedule.getOuCode());
                preparedStatement.setString(4, courtSchedule.getCourtRoomId());
                preparedStatement.setInt(5, courtSchedule.getCourtRoomNumber());
                preparedStatement.setString(6, courtSchedule.getCourtHouseId());
                preparedStatement.setString(7, courtSchedule.getCourtHouseName());
                preparedStatement.setString(8, courtSchedule.getCourtRoomName());
                preparedStatement.setString(9, courtSchedule.getOperationalUnit());
                preparedStatement.setString(10, courtSchedule.getBusinessType());
                preparedStatement.setString(11, courtSchedule.getPanel());
                preparedStatement.setString(12, courtSchedule.getCourtSession());
                preparedStatement.setBoolean(13, courtSchedule.isSlotBased());
                preparedStatement.setDate(14, Date.valueOf(courtSchedule.getSessionDate()));
                preparedStatement.setInt(15, courtSchedule.getMaxSlots());
                preparedStatement.setInt(16, courtSchedule.getMaxDuration());
                preparedStatement.setInt(17, courtSchedule.getAvailableSlots());
                preparedStatement.setInt(18, courtSchedule.getAvailableDuration());
                preparedStatement.setBoolean(19, courtSchedule.isSupportAdSplit());
                preparedStatement.setInt(20, courtSchedule.getMaxAdMorningDuration());
                preparedStatement.setInt(21, courtSchedule.getMaxAdAfternoonDuration());
                preparedStatement.setTimestamp(22, Timestamp.from(courtSchedule.getSessionStartTime()));
                preparedStatement.setTimestamp(23, Timestamp.from(courtSchedule.getSessionEndTime()));
                preparedStatement.setTimestamp(24, Timestamp.from(courtSchedule.getNationalBreakTime()));
                preparedStatement.setBoolean(25, courtSchedule.isOverbookingAllowed());
                preparedStatement.setBoolean(26, courtSchedule.isDraft());
                preparedStatement.setString(27, normalizeJurisdiction(courtSchedule.getJurisdiction()));
                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();
            connection.commit();
        }
    }

    public void updateSessionEndTime(final String courtScheduleId, final Instant sessionEndTime) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement ps = connection.prepareStatement(UPDATE_SESSION_END_TIME_SQL)) {

            ps.setTimestamp(1, Timestamp.from(sessionEndTime));
            ps.setString(2, courtScheduleId);

            ps.executeUpdate();
        }
    }

    public void insertAllocatedListing(final AllocatedListing allocatedListing) throws SQLException {


        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_INSERT_SQL)) {

            preparedStatement.setObject(1, allocatedListing.getId());
            preparedStatement.setString(2, allocatedListing.getCourtScheduleId());
            preparedStatement.setString(3, allocatedListing.getBookingId());
            preparedStatement.setString(4, allocatedListing.getHearingId());
            preparedStatement.setString(5, allocatedListing.getOucode());
            preparedStatement.setInt(6, allocatedListing.getCourtRoomId());
            preparedStatement.setString(7, allocatedListing.getRotaBusinessType());
            preparedStatement.setInt(8, allocatedListing.getDuration());
            if (isNull(allocatedListing.getHearingStartTime())) {
                preparedStatement.setTimestamp(9, Timestamp.from(Instant.now()));
            } else {
                preparedStatement.setTimestamp(9, Timestamp.from(allocatedListing.getHearingStartTime()));
            }

            preparedStatement.setTimestamp(10, Timestamp.from(Instant.now()));
            preparedStatement.setTimestamp(11, Timestamp.from(Instant.now()));
            preparedStatement.executeUpdate();
        }
    }

    public void insertAllocatedListingsBatch(final List<AllocatedListing> allocatedListings) throws SQLException {
        insertAllocatedListingsBatch(allocatedListings, null);
    }

    public void insertAllocatedListingsBatch(final List<AllocatedListing> allocatedListings, final Connection existingConnection) throws SQLException {
        if (allocatedListings.isEmpty()) {
            return;
        }

        if (existingConnection != null) {
            addAllocatedListingsBatch(allocatedListings, existingConnection);
            return;
        }

        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            addAllocatedListingsBatch(allocatedListings, connection);
            connection.commit();
        }
    }

    private static void addAllocatedListingsBatch(final List<AllocatedListing> allocatedListings, final Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_INSERT_SQL)) {
            for (final AllocatedListing allocatedListing : allocatedListings) {
                preparedStatement.setObject(1, allocatedListing.getId());
                preparedStatement.setString(2, allocatedListing.getCourtScheduleId());
                preparedStatement.setString(3, allocatedListing.getBookingId());
                preparedStatement.setString(4, allocatedListing.getHearingId());
                preparedStatement.setString(5, allocatedListing.getOucode());
                preparedStatement.setInt(6, allocatedListing.getCourtRoomId());
                preparedStatement.setString(7, allocatedListing.getRotaBusinessType());
                preparedStatement.setInt(8, allocatedListing.getDuration());
                if (isNull(allocatedListing.getHearingStartTime())) {
                    preparedStatement.setTimestamp(9, Timestamp.from(Instant.now()));
                } else {
                    preparedStatement.setTimestamp(9, Timestamp.from(allocatedListing.getHearingStartTime()));
                }

                preparedStatement.setTimestamp(10, Timestamp.from(Instant.now()));
                preparedStatement.setTimestamp(11, Timestamp.from(Instant.now()));
                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();
        }
    }

    public void insertProvisionalBooking(final ProvisionalBooking provisionalBooking) throws SQLException {


        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(PROVISIONAL_BOOKING_INSERT_SQL)) {

            preparedStatement.setObject(1, provisionalBooking.getProvisionalBookingKey().getCourtSchedule().getCourtScheduleId());
            preparedStatement.setString(2, provisionalBooking.getProvisionalBookingKey().getBookingId());
            preparedStatement.setBoolean(3, provisionalBooking.isActive());
            preparedStatement.setTimestamp(4, Timestamp.from(Instant.now()));
            preparedStatement.setTimestamp(5, Timestamp.from(Instant.now()));
            preparedStatement.setTimestamp(6, Timestamp.from(Instant.now()));
            preparedStatement.executeUpdate();
        }
    }

    public Integer saveJudiciarySchedule(final CourtScheduleJudiciary mapping) throws Exception {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement stmt = connection.prepareStatement(UPSERT_CSJ_QRY)) {
            stmt.setString(1, mapping.getId().getCourtScheduleId());
            stmt.setString(2, mapping.getCourtListingProfileId());
            stmt.setString(3, mapping.getId().getJudiciaryId());
            stmt.setString(4, mapping.getRotaJudiciaryId());
            stmt.setString(5, mapping.getTitle());
            stmt.setString(6, mapping.getForenames());
            stmt.setString(7, mapping.getSurname());
            stmt.setString(8, mapping.getEmail());
            stmt.setString(9, mapping.getJudiciaryType());
            stmt.setObject(10, mapping.isBenchChairman(), Types.BIT);
            stmt.setObject(11, mapping.isDeputy(), Types.BIT);
            stmt.setString(12, mapping.getPosition());

            stmt.setString(13, mapping.getCourtListingProfileId());
            stmt.setString(14, mapping.getId().getJudiciaryId());
            stmt.setString(15, mapping.getRotaJudiciaryId());
            stmt.setString(16, mapping.getTitle());
            stmt.setString(17, mapping.getForenames());
            stmt.setString(18, mapping.getSurname());
            stmt.setString(19, mapping.getEmail());
            stmt.setString(20, mapping.getJudiciaryType());
            stmt.setObject(21, mapping.isBenchChairman(), Types.BIT);
            stmt.setObject(22, mapping.isDeputy(), Types.BIT);
            stmt.setString(23, mapping.getPosition());

            stmt.setString(24, mapping.getId().getCourtScheduleId());
            stmt.setString(25, mapping.getId().getJudiciaryId());

            stmt.addBatch();
            return stmt.executeBatch().length;
        } catch (SQLException ex) {
            throw new Exception(ex);
        }
    }

    public void updateCourtScheduleSetListingProfileIdAsNull(final String ouCode) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_SET_LISTING_PROFILE_ID_AS_NULL_SQL)) {

            preparedStatement.setString(1, ouCode);
            preparedStatement.executeUpdate();
        }
    }

    public void setUpdateAvailableSlotForCourtSchedule(final String listingProfileId) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_AVAILABLE_SLOT_FOR_COURT_SCHEDULE)) {

            preparedStatement.setString(1, listingProfileId);
            preparedStatement.executeUpdate();
        }
    }

    public void insertCourtScheduleMigrationStatus(final CourtSchedulerMigrationStatus courtSchedulerMigrationStatus) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_MIGRATION_STATUS_INSERT_SQL)) {

            preparedStatement.setObject(1, courtSchedulerMigrationStatus.getOuCode());
            preparedStatement.setString(2, courtSchedulerMigrationStatus.getCourtCentreId());
            preparedStatement.setBoolean(3, courtSchedulerMigrationStatus.isMigrated());
            preparedStatement.setTimestamp(4, Timestamp.from(Instant.now()));
            preparedStatement.executeUpdate();
        }
    }

    public void bookSlots(final Collection<ProvisionalSlot> provisionalSlots, final String bookingId) {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement stmt = connection.prepareStatement(INSERT_PROVISIONAL_SLOTS_QRY)) {
            for (final ProvisionalSlot provisionalSlot : provisionalSlots) {
                stmt.setString(1, bookingId);
                stmt.setString(2, provisionalSlot.getCourtScheduleId());
                stmt.setTimestamp(3, toRoundedTimestamp(provisionalSlot.getHearingStartTime()));

                stmt.addBatch();
            }
            stmt.executeBatch();
        } catch (SQLException ex) {
            throw new PersistenceStoreException(ex);
        }
    }

    public void cleanJudiciaryAvailabilityRuleTable() throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement preparedStatement = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void insertJudiciaryAvailabilityRule(
            final String ruleId,
            final String judiciaryId,
            final String courtHouseId,
            final List<JudiciaryUnavailability> unavailabilities,
            final LocalDate fromDate,
            final LocalDate toDate,
            final List<AvailabilityDayOfWeek> repeatDays) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            try (PreparedStatement ruleStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_INSERT_SQL);
                 PreparedStatement repeatDaysStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL);
                 PreparedStatement unavailabilityStmt = connection.prepareStatement(JUDICIARY_UNAVAILABILITY_INSERT_SQL)) {

                // Always insert the rule (recurring_type column removed)
                ruleStmt.setString(1, ruleId);
                ruleStmt.setString(2, judiciaryId);
                ruleStmt.setString(3, courtHouseId);
                ruleStmt.setDate(4, Date.valueOf(fromDate));
                ruleStmt.setDate(5, Date.valueOf(toDate));

                ruleStmt.executeUpdate();

                // Insert repeat days
                for (final AvailabilityDayOfWeek dayOfWeek : repeatDays) {
                    repeatDaysStmt.setString(1, ruleId);
                    repeatDaysStmt.setString(2, dayOfWeek.getWireValue());
                    repeatDaysStmt.addBatch();
                }
                repeatDaysStmt.executeBatch();

                // If availabilityType is UNAVAILABLE, create a corresponding JudiciaryUnavailability record
                if (unavailabilities != null && !unavailabilities.isEmpty()) {

                    for(final JudiciaryUnavailability request : unavailabilities) {

                        final String unavailabilityId = java.util.UUID.randomUUID().toString();
                        unavailabilityStmt.setString(1, unavailabilityId);
                        unavailabilityStmt.setString(2, ruleId);
                        unavailabilityStmt.setDate(3, Date.valueOf(request.getStartDate()));
                        unavailabilityStmt.setDate(4, Date.valueOf(request.getEndDate()));
                        if (request.getReason() != null) {
                            unavailabilityStmt.setString(5, request.getReason());
                        } else {
                            unavailabilityStmt.setNull(5, Types.VARCHAR);
                        }
                        unavailabilityStmt.executeUpdate();
                    }
                }

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void updateJudiciaryAvailabilityRuleSessionType(final String ruleId, final String sessionType) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             PreparedStatement stmt = connection.prepareStatement(UPDATE_SESSION_TYPE_SQL)) {
            stmt.setString(1, sessionType);
            stmt.setString(2, ruleId);
            stmt.executeUpdate();
        }
    }

    public void insertJudiciaryAvailabilityRulesBatch(
            final List<RuleData> rules) throws SQLException {
        try (Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            try (PreparedStatement ruleStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_INSERT_SQL);
                 PreparedStatement repeatDaysStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL);
                 PreparedStatement unavailabilityStmt = connection.prepareStatement(JUDICIARY_UNAVAILABILITY_INSERT_SQL)) {

                int processedCount = 0;
                for (final RuleData rule : rules) {
                    // Insert rule (availability_type column removed)
                    ruleStmt.setString(1, rule.ruleId);
                    ruleStmt.setString(2, rule.judiciaryId);
                    ruleStmt.setString(3, rule.courtHouseId);
                    ruleStmt.setDate(4, Date.valueOf(rule.fromDate));
                    ruleStmt.setDate(5, Date.valueOf(rule.toDate));

                    ruleStmt.addBatch();

                    // Prepare repeat days for batch
                    for (final String dayOfWeek : rule.repeatDays) {
                        repeatDaysStmt.setString(1, rule.ruleId);
                        // Convert to title case to match enum (e.g., "Friday" not "FRIDAY")
                        final String dayOfWeekTitleCase = dayOfWeek.substring(0, 1).toUpperCase(Locale.ROOT) + dayOfWeek.substring(1).toLowerCase(Locale.ROOT);
                        repeatDaysStmt.setString(2, dayOfWeekTitleCase);
                        repeatDaysStmt.addBatch();
                    }

                    // If availabilityType is UNAVAILABLE, create a corresponding JudiciaryUnavailability record
                    if (rule.unavailabilities != null && !rule.unavailabilities.isEmpty()) {
                        for(final JudiciaryUnavailability request : rule.unavailabilities) {
                            // Skip unavailability records without startDate (from_date has NOT NULL constraint)
                            if (request.getStartDate() == null || request.getEndDate() == null) {
                                continue;
                            }
                            final String unavailabilityId = java.util.UUID.randomUUID().toString();
                            unavailabilityStmt.setString(1, unavailabilityId);
                            unavailabilityStmt.setString(2, rule.ruleId);
                            unavailabilityStmt.setDate(3, Date.valueOf(request.getStartDate()));
                            unavailabilityStmt.setDate(4, Date.valueOf(request.getEndDate()));
                            if (request.getReason() != null) {
                                unavailabilityStmt.setString(5, request.getReason());
                            } else {
                                unavailabilityStmt.setNull(5, Types.VARCHAR);
                            }
                            unavailabilityStmt.addBatch();
                        }
                    }

                    processedCount++;
                    // Execute in chunks to avoid memory issues
                    if (processedCount % BATCH_SIZE == 0) {
                        ruleStmt.executeBatch();
                        repeatDaysStmt.executeBatch();
                        unavailabilityStmt.executeBatch();
                        connection.commit();
                        connection.setAutoCommit(false); // Re-enable for next chunk
                        // Clear unavailability batch for next chunk
                        unavailabilityStmt.clearBatch();
                    }
                }

                // Execute remaining batches
                if (processedCount % BATCH_SIZE != 0) {
                    ruleStmt.executeBatch();
                    repeatDaysStmt.executeBatch();
                    unavailabilityStmt.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public static class RuleData {
        private final String ruleId;
        private final String judiciaryId;
        private final String courtHouseId;
        private final List<JudiciaryUnavailability> unavailabilities;
        private final LocalDate fromDate;
        private final LocalDate toDate;
        private final List<String> repeatDays;

        public RuleData(final String ruleId, final String judiciaryId, final String courtHouseId, final List<JudiciaryUnavailability> unavailabilities,
                        final LocalDate fromDate, final LocalDate toDate,
                        final List<String> repeatDays) {
            this.ruleId = ruleId;
            this.judiciaryId = judiciaryId;
            this.courtHouseId = courtHouseId;
            this.unavailabilities = unavailabilities;
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.repeatDays = repeatDays;
        }
    }


    public void cleanDb() throws SQLException {
        // Async rota-file processing started by a previous test can still be running
        // when the next @BeforeEach fires; its open transaction holds AccessShareLock
        // on court_schedule while our TRUNCATE … CASCADE wants AccessExclusiveLock,
        // and the two end up in a deadlock that Postgres breaks by killing one side.
        // Retry on serialization-failure (40P01 = deadlock_detected) so the tear-down
        // succeeds once the in-flight rota transaction has been rolled back.
        runWithDeadlockRetry(this::cleanProvisionalBookingTable);
        runWithDeadlockRetry(this::cleanAllocatedListingTable);
        runWithDeadlockRetry(this::cleanCourtScheduleTable);
        runWithDeadlockRetry(this::cleanCourtScheduleJudiciaryTable);
        runWithDeadlockRetry(this::cleanMigrationStatusTable);
        runWithDeadlockRetry(this::cleanRotaFileProcessHistoryTable);
        runWithDeadlockRetry(this::cleanRotaProcessLogTable);
        runWithDeadlockRetry(this::cleanJudiciaryAvailabilityRuleTable);
    }

    @FunctionalInterface
    private interface SqlAction {
        void run() throws SQLException;
    }

    private static void runWithDeadlockRetry(final SqlAction action) throws SQLException {
        SQLException last = null;
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                action.run();
                return;
            } catch (final SQLException e) {
                last = e;
                if (!"40P01".equals(e.getSQLState())) {
                    throw e;
                }
                try {
                    Thread.sleep(200L * (attempt + 1));
                } catch (final InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
        throw last;
    }
}
