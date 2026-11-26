package uk.gov.moj.cpp.courtscheduler.integration;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DurationTrackingListener implements TestExecutionListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(DurationTrackingListener.class);
    private static final String SEPARATOR = "=====================================================";
    private static final Map<String, DurationRecord> TEST_DURATIONS = new ConcurrentHashMap<>();
    private static final List<DurationRecord> COMPLETED_TESTS = new ArrayList<>();
    private static final Map<String, Instant> START_TIMES = new ConcurrentHashMap<>();
    private static final boolean ENABLED = Boolean.getBoolean("enable.test.duration.tracking");
    private static final AtomicInteger TEST_COUNT = new AtomicInteger();

    static {
        if (ENABLED) {
            LOGGER.info("DurationTrackingListener initialized - Duration tracking is ENABLED");
        } else {
            LOGGER.info("DurationTrackingListener initialized - Duration tracking is DISABLED");
        }
    }

    @Override
    public void testPlanExecutionStarted(final TestPlan testPlan) {
        if (!ENABLED) {
            return;
        }
        LOGGER.info("Test plan execution started");
        LOGGER.info("Total tests to be executed: {}", testPlan.countTestIdentifiers(TestIdentifier::isTest));
        // Clear any previous test data
        TEST_DURATIONS.clear();
        COMPLETED_TESTS.clear();
        START_TIMES.clear();
        TEST_COUNT.set(0);
    }

    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        if (!ENABLED) {
            return;
        }
        LOGGER.info("Test plan execution finished, generating report...");
        LOGGER.info("Total tests executed: {}", TEST_COUNT.get());
        generateReport();
    }

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        if (!ENABLED) {
            return;
        }
        if (testIdentifier.isTest()) {
            final String testId = testIdentifier.getUniqueId();
            START_TIMES.put(testId, Instant.now());
            LOGGER.info("Test started: {}", testIdentifier.getDisplayName());
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
        if (!ENABLED) {
            return;
        }
        if (testIdentifier.isTest()) {
            TEST_COUNT.incrementAndGet();
            final String testId = testIdentifier.getUniqueId();
            final Instant startTime = START_TIMES.remove(testId);
            if (startTime != null) {
                final Duration duration = Duration.between(startTime, Instant.now());
                final String className = testIdentifier.getSource()
                        .filter(source -> source instanceof MethodSource)
                        .map(source -> ((MethodSource) source).getClassName())
                        .orElse("Unknown");
                final String testName = testIdentifier.getDisplayName();

                final DurationRecord durationRecord = new DurationRecord(className, testName, duration.toMillis() / 1000.0);
                TEST_DURATIONS.put(testId, durationRecord);
                COMPLETED_TESTS.add(durationRecord);

                LOGGER.info("Test completed: {} (Duration: {} seconds)", testName, durationRecord.getDuration());
            }
        }
    }

    public static void generateReport() {
        if (!ENABLED) {
            return;
        }
        LOGGER.info("Generating test report...\n");

        // Sort tests by duration
        COMPLETED_TESTS.sort(Comparator.comparingDouble(DurationRecord::getDuration).reversed());

        // Calculate statistics
        final double totalDuration = COMPLETED_TESTS.stream()
                .mapToDouble(DurationRecord::getDuration)
                .sum();
        final double averageDuration = COMPLETED_TESTS.isEmpty() ? 0 : totalDuration / COMPLETED_TESTS.size();

        // Print summary
        LOGGER.info(SEPARATOR);
        LOGGER.info("============= TEST EXECUTION SUMMARY =================");
        LOGGER.info(SEPARATOR);
        LOGGER.info("Total Tests Executed: {}", COMPLETED_TESTS.size());
        LOGGER.info(String.format("Total Execution Time: %.2f seconds (%.2f minutes)",
                totalDuration, totalDuration / 60.0));
        LOGGER.info(String.format("Average Test Duration: %.2f seconds%n", averageDuration));

        // Print rankings
        LOGGER.info("=== Test Duration Rankings ===");
        LOGGER.info(String.format("%-5s | %-30s | %-30s | %-15s",
                "Rank", "Class Name", "Test Name", "Duration (seconds)"));
        LOGGER.info("-".repeat(85));

        for (int i = 0; i < COMPLETED_TESTS.size(); i++) {
            final DurationRecord test = COMPLETED_TESTS.get(i);
            LOGGER.info(String.format("%-5d | %-30s | %-30s | %.2f",
                    i + 1,
                    test.getClassName(),
                    test.getTestName(),
                    test.getDuration()));
        }

        // Write to CSV
        final File reportDir = new File("target/test-results");
        reportDir.mkdirs();
        final File csvFile = new File(reportDir, "test-durations.csv");

        try (PrintWriter writer = new PrintWriter(Files.newBufferedWriter(csvFile.toPath(), UTF_8))) {
            writer.println("Class Name,Test Name,Duration (seconds)");
            for (final DurationRecord test : COMPLETED_TESTS) {
                writer.printf("%s,%s,%.2f%n",
                        test.getClassName(),
                        test.getTestName(),
                        test.getDuration());
            }
            LOGGER.info("\nDetailed results written to: {}", csvFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.error("Error writing CSV report: {}", e.getMessage());
        }

        LOGGER.info(SEPARATOR);
    }

    private static class DurationRecord {
        private final String className;
        private final String testName;
        private final double duration;

        /* default */ DurationRecord(final String className, final String testName, final double duration) {
            this.className = className;
            this.testName = testName;
            this.duration = duration;
        }

        /* default */ String getClassName() {
            return className;
        }

        /* default */ String getTestName() {
            return testName;
        }

        /* default */ double getDuration() {
            return duration;
        }
    }
}
