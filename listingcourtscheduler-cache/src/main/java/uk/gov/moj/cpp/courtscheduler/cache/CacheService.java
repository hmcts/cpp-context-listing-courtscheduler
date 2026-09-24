package uk.gov.moj.cpp.courtscheduler.cache;

public interface CacheService {

    String add(String key, String value);
    String get(String key);
    boolean remove(String key);
    String flushAllCacheKeys();
    String add(String key, String value, Integer timeToLive);
}
