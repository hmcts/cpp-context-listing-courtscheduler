package uk.gov.moj.cpp.courtscheduler.integration;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class TestDurationListener implements TestExecutionListener {
    private static final Map<String, TestDuration> testDurations = new ConcurrentHashMap<>();
    private static final List<TestDuration> completedTests = new ArrayList<>();
    private static final Map<String, Instant> startTimes = new ConcurrentHashMap<>();
    private static final boolean ENABLED = Boolean.getBoolean("enable.test.duration.tracking");
    private static int testCount = 0;

    static {
        if (ENABLED) {
            System.out.println("TestDurationListener initialized - Duration tracking is ENABLED");
        } else {
            System.out.println("TestDurationListener initialized - Duration tracking is DISABLED");
        }
    }

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (!ENABLED) {
            return;
        }
        System.out.println("Test plan execution started");
        System.out.println("Total tests to be executed: " + testPlan.countTestIdentifiers(TestIdentifier::isTest));
        // Clear any previous test data
        testDurations.clear();
        completedTests.clear();
        startTimes.clear();
        testCount = 0;
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (!ENABLED) {
            return;
        }
        System.out.println("Test plan execution finished, generating report...");
        System.out.println("Total tests executed: " + testCount);
        generateReport();
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (!ENABLED) {
            return;
        }
        if (testIdentifier.isTest()) {
            String testId = testIdentifier.getUniqueId();
            startTimes.put(testId, Instant.now());
            System.out.println("Test started: " + testIdentifier.getDisplayName());
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (!ENABLED) {
            return;
        }
        if (testIdentifier.isTest()) {
            testCount++;
            String testId = testIdentifier.getUniqueId();
            Instant startTime = startTimes.remove(testId);
            if (startTime != null) {
                Duration duration = Duration.between(startTime, Instant.now());
                String className = testIdentifier.getSource()
                        .filter(source -> source instanceof MethodSource)
                        .map(source -> ((MethodSource) source).getClassName())
                        .orElse("Unknown");
                String testName = testIdentifier.getDisplayName();

                TestDuration testDuration = new TestDuration(className, testName, duration.toMillis() / 1000.0);
                testDurations.put(testId, testDuration);
                completedTests.add(testDuration);

                System.out.println("Test completed: " + testName + " (Duration: " + testDuration.getDuration() + " seconds)");
            }
        }
    }

    public static void generateReport() {
        if (!ENABLED) {
            return;
        }
        System.out.println("Generating test report...\n");

        // Sort tests by duration
        completedTests.sort(Comparator.comparingDouble(TestDuration::getDuration).reversed());

        // Calculate statistics
        double totalDuration = completedTests.stream()
                .mapToDouble(TestDuration::getDuration)
                .sum();
        double averageDuration = completedTests.isEmpty() ? 0 : totalDuration / completedTests.size();

        // Print summary
        System.out.println("=====================================================");
        System.out.println("============= TEST EXECUTION SUMMARY =================");
        System.out.println("=====================================================");
        System.out.printf("Total Tests Executed: %d%n", completedTests.size());
        System.out.printf("Total Execution Time: %.2f seconds (%.2f minutes)%n",
                totalDuration, totalDuration / 60.0);
        System.out.printf("Average Test Duration: %.2f seconds%n%n", averageDuration);

        // Print rankings
        System.out.println("=== Test Duration Rankings ===");
        System.out.printf("%-5s | %-30s | %-30s | %-15s%n",
                "Rank", "Class Name", "Test Name", "Duration (seconds)");
        System.out.println("-".repeat(85));

        for (int i = 0; i < completedTests.size(); i++) {
            TestDuration test = completedTests.get(i);
            System.out.printf("%-5d | %-30s | %-30s | %.2f%n",
                    i + 1,
                    test.getClassName(),
                    test.getTestName(),
                    test.getDuration());
        }

        // Write to CSV
        File reportDir = new File("target/test-results");
        reportDir.mkdirs();
        File csvFile = new File(reportDir, "test-durations.csv");

        try (PrintWriter writer = new PrintWriter(new FileWriter(csvFile))) {
            writer.println("Class Name,Test Name,Duration (seconds)");
            for (TestDuration test : completedTests) {
                writer.printf("%s,%s,%.2f%n",
                        test.getClassName(),
                        test.getTestName(),
                        test.getDuration());
            }
            System.out.println("\nDetailed results written to: " + csvFile.getAbsolutePath());
        } catch (IOException e) {
            System.err.println("Error writing CSV report: " + e.getMessage());
        }

        System.out.println("=====================================================");
    }

    private static class TestDuration {
        private final String className;
        private final String testName;
        private final double duration;

        public TestDuration(String className, String testName, double duration) {
            this.className = className;
            this.testName = testName;
            this.duration = duration;
        }

        public String getClassName() {
            return className;
        }

        public String getTestName() {
            return testName;
        }

        public double getDuration() {
            return duration;
        }
    }
} 