package uk.gov.moj.cpp.courtscheduler.rotafileprocessor.provisionaldata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProvisionalDataLookUpKeyTest {

    @Test
    void shouldCompareTo() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        int result = key1.compareTo(key2);

        assertEquals(0, result);
    }

    @Test
    void shouldEquals() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        boolean result = key1.equals(key2);

        assertTrue(result);
    }

    @Test
    void shouldBeEqualForTheSameObject() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        boolean result = key1.equals(key1);

        assertTrue(result);
    }

    @Test
    void shouldNotBeEqual() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(18, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        boolean result = key1.equals(key2);

        assertFalse(result);
    }

    @Test
    void shouldNotBeEqualForNullObject() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(18, LocalDate.of(2024, 10, 10));

        boolean result = key1.equals(null);

        assertFalse(result);
    }

    @Test
    void shouldNotBeEqualAsDatesAreDifferent() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 11, 10));

        boolean result = key1.equals(key2);

        assertFalse(result);
    }

    @Test
    void shouldNotBeEqualAsOneOfTheDatesIsNull() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, null);

        boolean result = key1.equals(key2);

        assertFalse(result);
    }

    @Test
    void shouldGiveHashCode() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        int hashCodeOfKey1 = key1.hashCode();
        int hashCodeOfKey2 = key2.hashCode();

        assertEquals(hashCodeOfKey1, hashCodeOfKey2);
    }

    @Test
    void shouldGiveAsString() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        final String key1ToString = key1.toString();

        assertNotNull(key1ToString);
        assertEquals("ProvisionalDataLookUpKey{populateCycle=10, extractDate=2024-10-10}", key1ToString);
    }
}
