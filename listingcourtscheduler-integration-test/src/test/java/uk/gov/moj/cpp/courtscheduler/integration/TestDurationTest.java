package uk.gov.moj.cpp.courtscheduler.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestDurationExtension.class)
public class TestDurationTest {

    @Test
    public void testDurationTracking() {
        long startTime = System.currentTimeMillis();
        System.out.println("Running test to verify duration tracking");
        try {
            // Simulate some work
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;
        assertTrue(duration >= 1000, "Test execution time should be at least 1000ms");
    }
} 