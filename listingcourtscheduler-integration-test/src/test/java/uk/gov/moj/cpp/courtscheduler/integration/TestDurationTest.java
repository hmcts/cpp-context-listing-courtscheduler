package uk.gov.moj.cpp.courtscheduler.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ExtendWith(DurationTrackingExtension.class)
class TestDurationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDurationTest.class);

    @Test
    void testDurationTracking() {
        final long startTime = System.currentTimeMillis();
        LOGGER.info("Running test to verify duration tracking");
        try {
            // Simulate some work
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        final long endTime = System.currentTimeMillis();
        final long duration = endTime - startTime;
        assertTrue(duration >= 1000, "Test execution time should be at least 1000ms");
    }
} 