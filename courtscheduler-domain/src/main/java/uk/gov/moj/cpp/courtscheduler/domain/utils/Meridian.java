package uk.gov.moj.cpp.courtscheduler.domain.utils;

//To be deleted in GPE-15175
public enum Meridian {
    TWELVE_AM("00"),
    NINE_AM("09"),
    TEN_AM("10"),
    ELEVEN_AM("11"),
    TWELVE_PM("12"),
    ONE_PM("13"),
    TWO_PM("14"),
    THREE_PM("15"),
    FOUR_PM("16"),
    FIVE_PM("17");

    private String value;

    private Meridian(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }
}
