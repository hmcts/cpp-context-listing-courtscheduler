package uk.gov.moj.cpp.courtscheduler.integration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestDurationExtension.class)
public class TestDurationTest {

    @Test
    public void testDurationTracking() {
        System.out.println("Running test to verify duration tracking");
        try {
            // Simulate some work
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
} 