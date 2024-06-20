package uk.gov.moj.cpp.courtscheduler.domain.utils;

import static java.util.Objects.isNull;

import java.math.BigDecimal;

public class ConvertionUtil {

    private ConvertionUtil() {}

    public static BigDecimal intToBigDecimal(final Integer value) {
        if (isNull(value)) {
            return null;
        }
        return BigDecimal.valueOf(value);
    }

    public static BigDecimal strToBigDecimal(final String value) {
        if (isNull(value)) {
            return null;
        }
        return new BigDecimal(value);
    }
}
