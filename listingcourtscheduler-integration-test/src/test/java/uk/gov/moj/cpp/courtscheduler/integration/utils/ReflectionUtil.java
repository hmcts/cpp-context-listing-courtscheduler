package uk.gov.moj.cpp.courtscheduler.integration.utils;

import java.lang.reflect.Field;

/**
 * Replacement for {@code org.springframework.test.util.ReflectionTestUtils}.
 * The legacy IT tests use this to override Spring-managed configuration values on the
 * already-instantiated bean (e.g. to point at a different Azure Blob storage during tests).
 */
public final class ReflectionUtil {

    private ReflectionUtil() {
    }

    public static void setField(final Object target, final String fieldName, final Object value) {
        Class<?> type = target.getClass();
        while (type != null) {
            try {
                final Field field = type.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to set field " + fieldName, e);
            }
        }
        throw new RuntimeException("Field " + fieldName + " not found on " + target.getClass());
    }
}
