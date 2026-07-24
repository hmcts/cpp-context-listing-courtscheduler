package uk.gov.moj.cpp.courtscheduler.common.converter;

@FunctionalInterface
public interface Converter<S, T> {
    T convert(S var1);
}
