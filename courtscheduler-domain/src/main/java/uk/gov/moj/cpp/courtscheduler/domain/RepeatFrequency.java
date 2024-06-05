package uk.gov.moj.cpp.courtscheduler.domain;

public enum RepeatFrequency {
    ONCE("ONCE"),
    EVERY_WEEK("EVERY_WEEK");

    private final String frequency;

    private RepeatFrequency(String repeatFrequency) {
        this.frequency = repeatFrequency;
    }

    public String getRepeatFrequency() {
        return this.frequency;
    }
}

