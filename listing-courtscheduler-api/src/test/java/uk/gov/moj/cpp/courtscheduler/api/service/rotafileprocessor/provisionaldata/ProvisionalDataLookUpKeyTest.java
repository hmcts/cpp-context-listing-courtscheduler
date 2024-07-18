package uk.gov.moj.cpp.courtscheduler.api.service.rotafileprocessor.provisionaldata;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void shouldGiveHashCode() {
        final ProvisionalDataLookUpKey key1 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));
        final ProvisionalDataLookUpKey key2 = new ProvisionalDataLookUpKey(10, LocalDate.of(2024, 10, 10));

        int hashCodeOfKey1 = key1.hashCode();
        int hashCodeOfKey2 = key2.hashCode();

        assertEquals(hashCodeOfKey1, hashCodeOfKey2);
    }
}
