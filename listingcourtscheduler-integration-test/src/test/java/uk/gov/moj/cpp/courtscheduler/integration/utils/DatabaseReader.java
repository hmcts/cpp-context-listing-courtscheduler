package uk.gov.moj.cpp.courtscheduler.integration.utils;


import static java.util.Collections.unmodifiableList;

import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtSchedule;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciary;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey;
import uk.gov.moj.cpp.courtscheduler.persist.entity.CourtScheduleJudiciaryKey_;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

public class DatabaseReader {

    private static final String USERNAME = "scsl";
    private static final String PASSWORD = "scsl";
    private static final String DATABASE = "scsl";

    private static final String COURT_SCHEDULE_GET_SQL = "SELECT * FROM court_schedule WHERE active is true AND court_listing_profile_id is not null ORDER BY session_start";
    private static final String COURT_SCHEDULE_JUDICIARY_GET_SQL = "SELECT * FROM court_schedule_judiciary WHERE active is true";

    private final ConnectionProvider connectionProvider = new ConnectionProvider();

    public List<CourtSchedule> courtSchedules() {
        return executeCourtScheduleQuery();
    }

    public List<CourtScheduleJudiciary> courtScheduleJudiciaries() {
        return executeCourtScheduleJudiciaryQuery();
    }

    private List<CourtSchedule> executeCourtScheduleQuery() {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final Statement statement = connection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_GET_SQL);
            final List<CourtSchedule> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(resultSetToCourtSchedule(resultSet));
            }
            return unmodifiableList(rows);
        } catch (final SQLException exp) {
            throw new RuntimeException("Exception while querying the DB", exp);
        }
    }

    private List<CourtScheduleJudiciary> executeCourtScheduleJudiciaryQuery() {
        try (final Connection connection = connectionProvider.getNewConnection(USERNAME, PASSWORD, DATABASE);
             final Statement statement = connection.createStatement()) {
            final ResultSet resultSet = statement.executeQuery(COURT_SCHEDULE_JUDICIARY_GET_SQL);
            final List<CourtScheduleJudiciary> rows = new ArrayList<>();
            while (resultSet.next()) {
                rows.add(resultSetToCourtScheduleJudiciary(resultSet));
            }
            return unmodifiableList(rows);
        } catch (final SQLException exp) {
            throw new RuntimeException("Exception while querying the DB", exp);
        }
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
        courtSchedule.setActive(resultSet.getBoolean("active"));

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
        courtScheduleJudiciary.setCreatedOn(resultSet.getDate("created_on"));
        courtScheduleJudiciary.setUpdatedOn(resultSet.getDate("updated_on"));

        return courtScheduleJudiciary;
    }
}
