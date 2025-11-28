package uk.gov.moj.cpp.courtscheduler.integration.utils;


import static java.util.Objects.isNull;
import static uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils.toRoundedTimestamp;

import uk.gov.moj.cpp.courtscheduler.domain.ProvisionalSlot;
import uk.gov.moj.cpp.courtscheduler.domain.utils.DateUtils;
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
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

public class DatabaseSeeder {

    private static final String USERNAME = "scsl";
    private static final String PASSWORD = "scsl";
    private static final String DATABASE = "scsl";

    private static final String COURT_SCHEDULE_INSERT_SQL = "INSERT INTO court_schedule (" +
            "id, court_listing_profile_id, oucode, court_room_id, court_room_number, court_house_id, court_house_name," +
            "court_room_name, operational_unit, rota_business_type, panel, court_session, is_slot_based, session_start, " +
            "max_slot, max_duration_mins, available_slot, available_duration_mins, support_ad_split, max_ad_morning_duration, max_ad_afternoon_duration, session_start_time, session_end_time, national_break_time, is_overbooking_allowed) \n" +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

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
            "id, judiciary_id, court_house_id, group_type, from_date, to_date, recurring_type, reason, created_on, updated_on) " +
            "VALUES(?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)";

    private static final String JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL = "INSERT INTO judiciary_availability_rule_repeat_days (" +
            "rule_id, day_of_week, day_index) VALUES(?, ?, ?)";


    private static final String COURT_SCHEDULE_SET_LISTING_PROFILE_ID_AS_NULL_SQL = "UPDATE court_schedule SET court_listing_profile_id = null WHERE oucode = ?";
    private static final String UPDATE_AVAILABLE_SLOT_FOR_COURT_SCHEDULE = "UPDATE court_schedule SET available_slot = available_slot - 1 WHERE court_listing_profile_id = ?";

    private final ConnectionProvider connectionProvider = new ConnectionProvider();

    public Connection getNewConnection() throws SQLException {
        return connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
    }

    public void cleanCourtScheduleTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanAllocatedListingTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanProvisionalBookingTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(PROVISIONAL_BOOKING_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanCourtScheduleJudiciaryTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_JUDICIARY_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void deleteJudiciaryByProfileId(final String listingProfileId) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_JUDICIARY_DELETE_BY_PROFILE_ID_SQL)) {
            preparedStatement.setString(1, listingProfileId);
            preparedStatement.executeUpdate();
        }
    }

    public void cleanMigrationStatusTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(MIGRATION_STATUS_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void cleanRotaFileProcessHistoryTable() throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(ROTA_FILE_PROCESS_HISTORY_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

        public void cleanRotaProcessLogTable() throws SQLException {
            try (final Connection connection = connectionProvider.getNewConnection(DatabaseSeeder.USERNAME, DatabaseSeeder.PASSWORD, DatabaseSeeder.DATABASE);
                 final PreparedStatement preparedStatement = connection.prepareStatement(DatabaseSeeder.ROTA_LOG_PROCESS_DELETE_SQL)) {
                preparedStatement.executeUpdate();
            }
        }


    public void insertCourtSchedule(CourtSchedule courtSchedule) throws SQLException {


        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_INSERT_SQL)) {

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
            preparedStatement.setBoolean(19, courtSchedule.getSupportAdSplit());
            preparedStatement.setInt(20, courtSchedule.getMaxAdMorningDuration());
            preparedStatement.setInt(21, courtSchedule.getMaxAdAfternoonDuration());
            preparedStatement.setTimestamp(22, new Timestamp(courtSchedule.getSessionStartTime().getTime()));
            preparedStatement.setTimestamp(23, new Timestamp(courtSchedule.getSessionEndTime().getTime()));
            preparedStatement.setTimestamp(24, new Timestamp(courtSchedule.getNationalBreakTime().getTime()));
            preparedStatement.setBoolean(25, courtSchedule.getIsOverbookingAllowed());

            preparedStatement.executeUpdate();
        }
    }

    public void updateSessionEndTime(String courtScheduleId, java.util.Date sessionEndTime) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement ps = connection.prepareStatement(UPDATE_SESSION_END_TIME_SQL)) {

            ps.setTimestamp(1, new java.sql.Timestamp(sessionEndTime.getTime()));
            ps.setString(2, courtScheduleId);

            ps.executeUpdate();
        }
    }

    public void insertAllocatedListing(AllocatedListing allocatedListing) throws SQLException {


        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_INSERT_SQL)) {

            preparedStatement.setObject(1, allocatedListing.getId());
            preparedStatement.setString(2, allocatedListing.getCourtScheduleId());
            preparedStatement.setString(3, allocatedListing.getBookingId());
            preparedStatement.setString(4, allocatedListing.getHearingId());
            preparedStatement.setString(5, allocatedListing.getOucode());
            preparedStatement.setInt(6, allocatedListing.getCourtRoomId());
            preparedStatement.setString(7, allocatedListing.getRotaBusinessType());
            preparedStatement.setInt(8, allocatedListing.getDuration());
            if (isNull(allocatedListing.getHearingStartTime())) {
                preparedStatement.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
            } else {
                preparedStatement.setTimestamp(9, new Timestamp(allocatedListing.getHearingStartTime().getTime()));
            }

            preparedStatement.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
            preparedStatement.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
            preparedStatement.executeUpdate();
        }
    }

    public void insertAllocatedListingsBatch(List<AllocatedListing> allocatedListings) throws SQLException {
        insertAllocatedListingsBatch(allocatedListings, null);
    }

    public void insertAllocatedListingsBatch(List<AllocatedListing> allocatedListings, Connection existingConnection) throws SQLException {
        if (allocatedListings.isEmpty()) {
            return;
        }

        boolean isExternalConnection = existingConnection != null;
        Connection connection = existingConnection;
        PreparedStatement preparedStatement = null;

        try {
            if (!isExternalConnection) {
                connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
            }

            preparedStatement = connection.prepareStatement(ALLOCATED_LISTING_INSERT_SQL);

            if (!isExternalConnection) {
                connection.setAutoCommit(false);
            }

            for (AllocatedListing allocatedListing : allocatedListings) {
                preparedStatement.setObject(1, allocatedListing.getId());
                preparedStatement.setString(2, allocatedListing.getCourtScheduleId());
                preparedStatement.setString(3, allocatedListing.getBookingId());
                preparedStatement.setString(4, allocatedListing.getHearingId());
                preparedStatement.setString(5, allocatedListing.getOucode());
                preparedStatement.setInt(6, allocatedListing.getCourtRoomId());
                preparedStatement.setString(7, allocatedListing.getRotaBusinessType());
                preparedStatement.setInt(8, allocatedListing.getDuration());
                if (isNull(allocatedListing.getHearingStartTime())) {
                    preparedStatement.setTimestamp(9, new Timestamp(System.currentTimeMillis()));
                } else {
                    preparedStatement.setTimestamp(9, new Timestamp(allocatedListing.getHearingStartTime().getTime()));
                }

                preparedStatement.setTimestamp(10, new Timestamp(System.currentTimeMillis()));
                preparedStatement.setTimestamp(11, new Timestamp(System.currentTimeMillis()));
                preparedStatement.addBatch();
            }

            preparedStatement.executeBatch();

            if (!isExternalConnection) {
                connection.commit();
            }
        } finally {
            if (preparedStatement != null && !isExternalConnection) {
                preparedStatement.close();
            }
            if (!isExternalConnection && connection != null) {
                connection.close();
            }
        }
    }

    public void insertProvisionalBooking(ProvisionalBooking provisionalBooking) throws SQLException {


        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(PROVISIONAL_BOOKING_INSERT_SQL)) {

            preparedStatement.setObject(1, provisionalBooking.getProvisionalBookingKey().getCourtSchedule().getCourtScheduleId());
            preparedStatement.setString(2, provisionalBooking.getProvisionalBookingKey().getBookingId());
            preparedStatement.setBoolean(3, provisionalBooking.getActive());
            preparedStatement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            preparedStatement.setTimestamp(5, new Timestamp(System.currentTimeMillis()));
            preparedStatement.setTimestamp(6, new Timestamp(System.currentTimeMillis()));
            preparedStatement.executeUpdate();
        }
    }

    public Integer saveJudiciarySchedule(final CourtScheduleJudiciary mapping) throws Exception {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement stmt = connection.prepareStatement(UPSERT_CSJ_QRY)) {
            int idx = 0;
            stmt.setString(++idx, mapping.getId().getCourtScheduleId());
            stmt.setString(++idx, mapping.getCourtListingProfileId());
            stmt.setString(++idx, mapping.getId().getJudiciaryId());
            stmt.setString(++idx, mapping.getRotaJudiciaryId());
            stmt.setString(++idx, mapping.getTitle());
            stmt.setString(++idx, mapping.getForenames());
            stmt.setString(++idx, mapping.getSurname());
            stmt.setString(++idx, mapping.getEmail());
            stmt.setString(++idx, mapping.getJudiciaryType());
            stmt.setObject(++idx, mapping.getBenchChairman(), Types.BIT);
            stmt.setObject(++idx, mapping.getDeputy(), Types.BIT);
            stmt.setString(++idx, mapping.getPosition());

            stmt.setString(++idx, mapping.getCourtListingProfileId());
            stmt.setString(++idx, mapping.getId().getJudiciaryId());
            stmt.setString(++idx, mapping.getRotaJudiciaryId());
            stmt.setString(++idx, mapping.getTitle());
            stmt.setString(++idx, mapping.getForenames());
            stmt.setString(++idx, mapping.getSurname());
            stmt.setString(++idx, mapping.getEmail());
            stmt.setString(++idx, mapping.getJudiciaryType());
            stmt.setObject(++idx, mapping.getBenchChairman(), Types.BIT);
            stmt.setObject(++idx, mapping.getDeputy(), Types.BIT);
            stmt.setString(++idx, mapping.getPosition());

            stmt.setString(++idx, mapping.getId().getCourtScheduleId());
            stmt.setString(++idx, mapping.getId().getJudiciaryId());

            stmt.addBatch();
            return stmt.executeBatch().length;
        } catch (SQLException ex) {
            throw new Exception(ex);
        }
    }

    public void updateCourtScheduleSetListingProfileIdAsNull(final String ouCode) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_SET_LISTING_PROFILE_ID_AS_NULL_SQL)) {

            preparedStatement.setString(1, ouCode);
            preparedStatement.executeUpdate();
        }
    }

    public void setUpdateAvailableSlotForCourtSchedule(final String listingProfileId) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(UPDATE_AVAILABLE_SLOT_FOR_COURT_SCHEDULE)) {

            preparedStatement.setString(1, listingProfileId);
            preparedStatement.executeUpdate();
        }
    }

    public void insertCourtScheduleMigrationStatus(CourtSchedulerMigrationStatus courtSchedulerMigrationStatus) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(COURT_SCHEDULE_MIGRATION_STATUS_INSERT_SQL)) {

            preparedStatement.setObject(1, courtSchedulerMigrationStatus.getOuCode());
            preparedStatement.setString(2, courtSchedulerMigrationStatus.getCourtCentreId());
            preparedStatement.setBoolean(3, courtSchedulerMigrationStatus.isMigrated());
            preparedStatement.setTimestamp(4, new Timestamp(System.currentTimeMillis()));
            preparedStatement.executeUpdate();
        }
    }

    public void bookSlots(final Collection<ProvisionalSlot> provisionalSlots, final String bookingId) {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement stmt = connection.prepareStatement(INSERT_PROVISIONAL_SLOTS_QRY)) {
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
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final PreparedStatement preparedStatement = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_DELETE_SQL)) {
            preparedStatement.executeUpdate();
        }
    }

    public void insertJudiciaryAvailabilityRule(
            final String ruleId,
            final String judiciaryId,
            final String courtHouseId,
            final String group,
            final LocalDate fromDate,
            final LocalDate toDate,
            final String recurringType,
            final String reason,
            final List<String> repeatDays) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            try (final PreparedStatement ruleStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_INSERT_SQL);
                 final PreparedStatement repeatDaysStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL)) {

                // Insert rule
                ruleStmt.setString(1, ruleId);
                ruleStmt.setString(2, judiciaryId);
                ruleStmt.setString(3, courtHouseId);
                ruleStmt.setString(4, group);
                ruleStmt.setDate(5, Date.valueOf(fromDate));
                ruleStmt.setDate(6, Date.valueOf(toDate));
                if (recurringType != null) {
                    ruleStmt.setString(7, recurringType);
                } else {
                    ruleStmt.setNull(7, Types.VARCHAR);
                }
                if (reason != null) {
                    ruleStmt.setString(8, reason);
                } else {
                    ruleStmt.setNull(8, Types.VARCHAR);
                }
                ruleStmt.executeUpdate();

                // Insert repeat days
                for (String dayOfWeek : repeatDays) {
                    repeatDaysStmt.setString(1, ruleId);
                    repeatDaysStmt.setString(2, dayOfWeek);
                    repeatDaysStmt.setInt(3, 0); // Default index is 0
                    repeatDaysStmt.addBatch();
                }
                repeatDaysStmt.executeBatch();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void insertJudiciaryAvailabilityRulesBatch(
            final List<RuleData> rules) throws SQLException {
        // Process in chunks to avoid memory issues with very large batches
        final int BATCH_SIZE = 5000;
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            try (final PreparedStatement ruleStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_INSERT_SQL);
                 final PreparedStatement repeatDaysStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL)) {

                int processedCount = 0;
                for (RuleData rule : rules) {
                    // Insert rule
                    ruleStmt.setString(1, rule.ruleId);
                    ruleStmt.setString(2, rule.judiciaryId);
                    ruleStmt.setString(3, rule.courtHouseId);
                    ruleStmt.setString(4, rule.group);
                    ruleStmt.setDate(5, Date.valueOf(rule.fromDate));
                    ruleStmt.setDate(6, Date.valueOf(rule.toDate));
                    if (rule.recurringType != null) {
                        ruleStmt.setString(7, rule.recurringType);
                    } else {
                        ruleStmt.setNull(7, Types.VARCHAR);
                    }
                    if (rule.reason != null) {
                        ruleStmt.setString(8, rule.reason);
                    } else {
                        ruleStmt.setNull(8, Types.VARCHAR);
                    }
                    ruleStmt.addBatch();

                    // Prepare repeat days for batch
                    for (String dayOfWeek : rule.repeatDays) {
                        repeatDaysStmt.setString(1, rule.ruleId);
                        repeatDaysStmt.setString(2, dayOfWeek);
                        repeatDaysStmt.setInt(3, 0); // Default index is 0
                        repeatDaysStmt.addBatch();
                    }

                    processedCount++;
                    // Execute in chunks to avoid memory issues
                    if (processedCount % BATCH_SIZE == 0) {
                        ruleStmt.executeBatch();
                        repeatDaysStmt.executeBatch();
                        connection.commit();
                        connection.setAutoCommit(false); // Re-enable for next chunk
                    }
                }

                // Execute remaining batches
                if (processedCount % BATCH_SIZE != 0) {
                    ruleStmt.executeBatch();
                    repeatDaysStmt.executeBatch();
                }
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public static class RuleData {
        final String ruleId;
        final String judiciaryId;
        final String courtHouseId;
        final String group;
        final LocalDate fromDate;
        final LocalDate toDate;
        final String recurringType;
        final String reason;
        final List<String> repeatDays;

        public RuleData(String ruleId, String judiciaryId, String courtHouseId, String group,
                       LocalDate fromDate, LocalDate toDate, String recurringType, String reason,
                       List<String> repeatDays) {
            this.ruleId = ruleId;
            this.judiciaryId = judiciaryId;
            this.courtHouseId = courtHouseId;
            this.group = group;
            this.fromDate = fromDate;
            this.toDate = toDate;
            this.recurringType = recurringType;
            this.reason = reason;
            this.repeatDays = repeatDays;
        }
    }

    public void insertJudiciaryAvailabilityRuleWithIndex(
            final String ruleId,
            final String judiciaryId,
            final String courtHouseId,
            final String group,
            final LocalDate fromDate,
            final LocalDate toDate,
            final String recurringType,
            final String reason,
            final List<Pair<String, Integer>> repeatDaysWithIndex) throws SQLException {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE)) {
            connection.setAutoCommit(false);
            try (final PreparedStatement ruleStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_INSERT_SQL);
                 final PreparedStatement repeatDaysStmt = connection.prepareStatement(JUDICIARY_AVAILABILITY_RULE_REPEAT_DAYS_INSERT_SQL)) {

                // Insert rule
                ruleStmt.setString(1, ruleId);
                ruleStmt.setString(2, judiciaryId);
                ruleStmt.setString(3, courtHouseId);
                ruleStmt.setString(4, group);
                ruleStmt.setDate(5, Date.valueOf(fromDate));
                ruleStmt.setDate(6, Date.valueOf(toDate));
                if (recurringType != null) {
                    ruleStmt.setString(7, recurringType);
                } else {
                    ruleStmt.setNull(7, Types.VARCHAR);
                }
                if (reason != null) {
                    ruleStmt.setString(8, reason);
                } else {
                    ruleStmt.setNull(8, Types.VARCHAR);
                }
                ruleStmt.executeUpdate();

                // Insert repeat days with index
                for (Pair<String, Integer> dayWithIndex : repeatDaysWithIndex) {
                    repeatDaysStmt.setString(1, ruleId);
                    repeatDaysStmt.setString(2, dayWithIndex.getFirst());
                    repeatDaysStmt.setInt(3, dayWithIndex.getSecond());
                    repeatDaysStmt.addBatch();
                }
                repeatDaysStmt.executeBatch();

                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            }
        }
    }

    public void cleanDb() throws SQLException {
        cleanProvisionalBookingTable();
        cleanAllocatedListingTable();
        cleanCourtScheduleTable();
        cleanCourtScheduleJudiciaryTable();
        cleanMigrationStatusTable();
        cleanRotaFileProcessHistoryTable();
        cleanRotaProcessLogTable();
        cleanJudiciaryAvailabilityRuleTable();
    }

    // Simple Pair class for internal use
    public static class Pair<T, U> {
        private final T first;
        private final U second;

        public Pair(T first, U second) {
            this.first = first;
            this.second = second;
        }

        public T getFirst() {
            return this.first;
        }

        public U getSecond() {
            return this.second;
        }
    }
}
