package uk.gov.moj.cpp.courtscheduler.converter;

@FunctionalInterface
public interface Converter<S, T> {
    T convert(S var1);
}
