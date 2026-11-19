package uk.gov.moj.cpp.courtscheduler.repository;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertThrows;

import uk.gov.moj.cpp.courtscheduler.persist.entity.AllocatedListing;

import java.util.List;
import java.util.Map;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * SPRDT-1587: the Liquibase changelog must leave the two tables with the index shapes the hot
 * queries need. Runs against the Testcontainers Postgres that {@link AbstractRepositoryTest}
 * builds by replaying the production changelog, so it fails if changesets 052/053 do not apply.
 * {@code @DataJpaTest} rolls every test back, so no explicit clean-up is needed (and none is possible
 * after the deliberate constraint violation, which aborts the transaction).
 */
class SchemaIndexesTest extends AbstractRepositoryTest {

    private static final String INDEX_DEF_SQL =
            "select indexname, indexdef from pg_indexes where schemaname = current_schema() and tablename = :table order by indexname";

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AllocatedListingRepository allocatedListingRepository;

    @Test
    void allocatedListingsIsIndexedOnHearingIdFirstAndTheOldOrderIsGone() {
        final Map<String, String> indexes = indexesOf("allocated_listings");

        assertThat(indexes.keySet(), hasItem("allocated_listings_hearing_id_court_schedule_id_idx"));
        assertThat(indexes.get("allocated_listings_hearing_id_court_schedule_id_idx"), containsString("UNIQUE INDEX"));
        assertThat(indexes.get("allocated_listings_hearing_id_court_schedule_id_idx"), containsString("(hearing_id, court_schedule_id)"));
        assertThat(indexes.keySet(), not(hasItem("court_schedule_hearing_id_idx")));
        assertThat(indexes.keySet(), hasItem("idx_allocated_listings_court_schedule_id"));
    }

    @Test
    void courtScheduleIsIndexedOnCourtHouseIdAndSessionStart() {
        final Map<String, String> indexes = indexesOf("court_schedule");

        assertThat(indexes.get("idx_court_schedule_court_house_id_session_start"), containsString("(court_house_id, session_start)"));
        assertThat(indexes.keySet(), hasItem("court_act_business_date_session_idx_am"));
        assertThat(indexes.keySet(), hasItem("court_act_business_date_session_idx_pm"));
    }

    @Test
    void oneHearingCanStillBeBookedOnlyOncePerSession() {
        final AllocatedListing first = newAllocatedListingWithSavedSchedule();
        allocatedListingRepository.save(first);
        entityManager.flush();

        final AllocatedListing duplicate = random(AllocatedListing.class);
        duplicate.setCourtScheduleId(first.getCourtScheduleId());
        duplicate.setHearingId(first.getHearingId());

        assertThrows(PersistenceException.class, () -> {
            allocatedListingRepository.save(duplicate);
            entityManager.flush();
        });
        entityManager.clear();
    }

    private Map<String, String> indexesOf(final String table) {
        @SuppressWarnings("unchecked")
        final List<Object[]> rows = entityManager.createNativeQuery(INDEX_DEF_SQL).setParameter("table", table).getResultList();
        final Map<String, String> indexes = new java.util.LinkedHashMap<>();
        for (final Object[] row : rows) {
            indexes.put(String.valueOf(row[0]), String.valueOf(row[1]));
        }
        assertThat("table " + table + " has indexes", indexes.isEmpty(), is(false));
        return indexes;
    }
}
