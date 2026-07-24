package uk.gov.moj.cpp.courtscheduler.api.converter;

@FunctionalInterface
public interface Converter<S, T> {
    T convert(S var1);
}
