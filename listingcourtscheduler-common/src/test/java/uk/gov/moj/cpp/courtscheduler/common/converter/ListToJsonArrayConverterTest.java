package uk.gov.moj.cpp.courtscheduler.common.converter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import uk.gov.moj.cpp.courtscheduler.domain.SlotStartTime;

import java.io.StringReader;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.json.Json;
import jakarta.json.JsonArray;
import jakarta.json.JsonObject;
import jakarta.json.JsonReader;
import org.junit.jupiter.api.Test;

/**
 * Regression test for the API-test {@code ClassCastException} seen at
 * {@code HearingSlotsHelper.hasSlot()} line 86 when reading the
 * {@code GET /hearingslots} response. The helper does:
 *
 * <pre>{@code
 * slot.getJsonArray("slotStartTimes").getValuesAs(JsonObject.class).stream()
 *     .filter(s -> s.containsKey("hearingStartTime"))
 *     .anyMatch(s -> s.getString("hearingStartTime").startsWith(time));
 * }</pre>
 *
 * <p>If a {@link SlotStartTime} has {@code hearingStartTime == null} and the
 * server-side converter does NOT exclude null fields, the response wire shape
 * is {@code "hearingStartTime": null}. {@link JsonObject#containsKey} returns
 * {@code true} for that key, but {@link JsonObject#getString} on a JSON
 * {@code null} throws {@link ClassCastException} — {@code JsonValueImpl}
 * cannot be cast to {@code JsonString}. This is exactly the trace seen in the
 * API-test failsafe reports.</p>
 */
class ListToJsonArrayConverterTest {

    /**
     * Reproduces the pre-fix bug: a plain {@code new ObjectMapper()} (no NON_NULL
     * inclusion) leaks {@code "hearingStartTime": null} into the JSON, and the
     * exact JSON-P access pattern used by the API tests throws
     * {@link ClassCastException}.
     */
    @Test
    void plainObjectMapperWithoutNonNullInclusion_reproducesApiTestClassCastException() {
        final SlotStartTime slotWithoutHearing = new SlotStartTime(
                "2026-06-28T09:00", "2026-06-28T10:00", null, 0L);

        final ObjectMapper legacyMapper = new ObjectMapper();
        final String json;
        try {
            json = legacyMapper.writeValueAsString(slotWithoutHearing);
        } catch (Exception e) {
            throw new AssertionError(e);
        }

        try (JsonReader reader = Json.createReader(new StringReader(json))) {
            final JsonObject parsed = reader.readObject();

            assertThat(parsed.containsKey("hearingStartTime"))
                    .as("the bug: the key is present in the JSON")
                    .isTrue();

            assertThatThrownBy(() -> parsed.getString("hearingStartTime"))
                    .as("the bug: JSON null value cannot be read as a JsonString")
                    .isInstanceOf(ClassCastException.class);
        }
    }

    /**
     * The fix: {@link ListToJsonArrayConverter} configures its
     * {@link ObjectMapper} with {@code NON_NULL} inclusion, mirroring the
     * project-wide {@code spring.jackson.default-property-inclusion} setting.
     * Null fields disappear from the wire and the API-test helper's
     * {@code containsKey} short-circuits before the bad {@code getString}.
     */
    @Test
    void converter_omitsNullFields_soApiTestPatternIsSafe() {
        final ListToJsonArrayConverter<SlotStartTime> converter = new ListToJsonArrayConverter<>();
        final SlotStartTime slotWithoutHearing = new SlotStartTime(
                "2026-06-28T09:00", "2026-06-28T10:00", null, 0L);

        final JsonArray arr = converter.convert(List.of(slotWithoutHearing));
        final JsonObject slotJson = arr.getJsonObject(0);

        assertThat(slotJson.containsKey("hearingStartTime"))
                .as("fix: null hearingStartTime is omitted from JSON")
                .isFalse();
        assertThat(slotJson.getString("sessionStartTime")).isEqualTo("2026-06-28T09:00");
        assertThat(slotJson.getString("sessionEndTime")).isEqualTo("2026-06-28T10:00");
    }

    /**
     * Populated values still survive — the NON_NULL fix only strips nulls,
     * everything else round-trips unchanged.
     */
    @Test
    void converter_keepsPopulatedHearingStartTime() {
        final ListToJsonArrayConverter<SlotStartTime> converter = new ListToJsonArrayConverter<>();
        final SlotStartTime slot = new SlotStartTime(
                "2026-06-28T09:00", "2026-06-28T10:00", "2026-06-28T09:30", 1L);

        final JsonObject slotJson = converter.convert(List.of(slot)).getJsonObject(0);

        assertThat(slotJson.containsKey("hearingStartTime")).isTrue();
        assertThat(slotJson.getString("hearingStartTime")).isEqualTo("2026-06-28T09:30");
    }

    /**
     * Direct end-to-end replay of the failing API-test code path against the
     * fixed converter: {@code HearingSlotsHelper.hasSlot()} no longer crashes
     * when the slot has no booked hearing yet.
     */
    @Test
    void hearingSlotsHelperPattern_doesNotThrowAfterFix() {
        final ListToJsonArrayConverter<SlotStartTime> converter = new ListToJsonArrayConverter<>();
        final JsonArray slotStartTimes = converter.convert(List.of(
                new SlotStartTime("2026-06-28T09:00", "2026-06-28T10:00", null, 0L),
                new SlotStartTime("2026-06-28T10:00", "2026-06-28T11:00", "2026-06-28T10:30", 1L)
        ));

        // Replicate HearingSlotsHelper.hasSlot exactly.
        final boolean found = slotStartTimes.getValuesAs(JsonObject.class).stream()
                .filter(s -> s.containsKey("hearingStartTime"))
                .anyMatch(s -> s.getString("hearingStartTime").startsWith("2026-06-28T10:30"));

        assertThat(found).isTrue();
    }

    /** Sanity-check {@link StringToJsonObjectConverter} dependency wiring. */
    @Test
    void converter_returnsEmptyArrayForEmptyInput() {
        final ListToJsonArrayConverter<SlotStartTime> converter = new ListToJsonArrayConverter<>();
        final JsonArray arr = converter.convert(List.of());
        assertThat(arr).isEmpty();
    }
}
