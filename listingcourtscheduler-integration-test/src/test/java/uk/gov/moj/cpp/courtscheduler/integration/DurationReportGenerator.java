package uk.gov.moj.cpp.courtscheduler.integration;

import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestPlan;

public class DurationReportGenerator implements TestExecutionListener {
    @Override
    public void testPlanExecutionFinished(final TestPlan testPlan) {
        if (Boolean.getBoolean("generate.test.report")) {
            DurationTrackingListener.generateReport();
        }
    }
} 