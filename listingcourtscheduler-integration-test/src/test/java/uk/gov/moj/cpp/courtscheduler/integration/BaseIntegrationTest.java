package uk.gov.moj.cpp.courtscheduler.integration;

import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(TestDurationExtension.class)
public abstract class BaseIntegrationTest extends AbstractIT {
    // All integration tests will inherit the duration tracking functionality
} 