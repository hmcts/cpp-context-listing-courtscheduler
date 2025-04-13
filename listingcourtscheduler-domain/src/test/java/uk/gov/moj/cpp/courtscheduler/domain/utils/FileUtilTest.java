package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;


public class FileUtilTest {

    @Test
    public void shouldGetLJASnapshotFileTimeStampAsString() {
        final String actual = FileUtil.getLJASnapshotFileTimeStampAsString("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("20210122T120000Z"));
    }

    @Test
    public void shouldGetLJASnapshotFileTimeStampAsOffsetDateTime() {
        final OffsetDateTime actual = FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual.toString(), is("2021-01-22T12:00Z"));
    }

    @Test
    public void shouldReturnsNullGetLJASnapshotFileTimeStampAsOffsetDateTime() {
        final OffsetDateTime actual = FileUtil.getLJASnapshotFileTimeStampAsOffsetDateTime("lja_southessex_snapshot_202101000Z.xml");
        assertThat(actual, nullValue());
    }

    @Test
    public void shouldGetLJASnapshotFileNamePrefix() {
        final String actual = FileUtil.getLJASnapshotFileNamePrefix("lja_southessex_snapshot_20210122T120000Z.xml");
        assertThat(actual, is("lja_southessex_snapshot_"));
    }
}
