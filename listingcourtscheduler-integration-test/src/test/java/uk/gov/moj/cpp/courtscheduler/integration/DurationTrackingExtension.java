package uk.gov.moj.cpp.courtscheduler.integration;

import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.TestWatcher;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.TestPlan;

public class DurationTrackingExtension implements TestWatcher, TestExecutionListener {
    private final DurationTrackingListener listener = new DurationTrackingListener();
    private static final boolean ENABLED = Boolean.getBoolean("enable.test.duration.tracking");

    @Override
    public void testPlanExecutionStarted(final TestPlan testPlan) {
        if (ENABLED) {
            listener.testPlanExecutionStarted(testPlan);
        }
    }

    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        if (ENABLED) {
            listener.testPlanExecutionFinished(testPlan);
        }
    }

    @Override
    public void executionStarted(final TestIdentifier testIdentifier) {
        if (ENABLED) {
            listener.executionStarted(testIdentifier);
        }
    }

    @Override
    public void executionFinished(final TestIdentifier testIdentifier, final TestExecutionResult testExecutionResult) {
        if (ENABLED) {
            listener.executionFinished(testIdentifier, testExecutionResult);
        }
    }

    @Override
    public void testSuccessful(final ExtensionContext context) {
        // Test was successful, no need to do anything as the DurationTrackingListener
        // already handles recording test durations through its TestExecutionListener methods
    }

    @Override
    public void testFailed(final ExtensionContext context, final Throwable cause) {
        // Test failed, no need to do anything as the DurationTrackingListener
        // already handles recording test durations through its TestExecutionListener methods
    }

    @Override
    public void testAborted(final ExtensionContext context, final Throwable cause) {
        // Test was aborted, no need to do anything as the DurationTrackingListener
        // already handles recording test durations through its TestExecutionListener methods
    }
} 