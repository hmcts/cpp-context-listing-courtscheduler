package uk.gov.moj.cpp.courtscheduler.integration;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class TestDurationExtension implements TestWatcher, TestExecutionListener {
    private final TestDurationListener listener = new TestDurationListener();
    private static final boolean ENABLED = Boolean.getBoolean("enable.test.duration.tracking");

    @Override
    public void testPlanExecutionStarted(TestPlan testPlan) {
        if (ENABLED) {
            listener.testPlanExecutionStarted(testPlan);
        }
    }

    @Override
    public void testPlanExecutionFinished(TestPlan testPlan) {
        if (ENABLED) {
            listener.testPlanExecutionFinished(testPlan);
        }
    }

    @Override
    public void executionStarted(TestIdentifier testIdentifier) {
        if (ENABLED) {
            listener.executionStarted(testIdentifier);
        }
    }

    @Override
    public void executionFinished(TestIdentifier testIdentifier, TestExecutionResult testExecutionResult) {
        if (ENABLED) {
            listener.executionFinished(testIdentifier, testExecutionResult);
        }
    }

    @Override
    public void testSuccessful(ExtensionContext context) {
        // Test was successful, no need to do anything as the TestDurationListener
        // already handles recording test durations through its TestExecutionListener methods
    }

    @Override
    public void testFailed(ExtensionContext context, Throwable cause) {
        // Test failed, no need to do anything as the TestDurationListener
        // already handles recording test durations through its TestExecutionListener methods
    }

    @Override
    public void testAborted(ExtensionContext context, Throwable cause) {
        // Test was aborted, no need to do anything as the TestDurationListener
        // already handles recording test durations through its TestExecutionListener methods
    }
} 