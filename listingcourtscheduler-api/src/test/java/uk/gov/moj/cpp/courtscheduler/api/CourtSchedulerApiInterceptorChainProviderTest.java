package uk.gov.moj.cpp.courtscheduler.api;

import org.junit.jupiter.api.Disabled;

/**
 * <strong>Removed — production class no longer exists.</strong>
 *
 * <p>The {@code CourtSchedulerApiInterceptorChainProvider} this test asserted on was a
 * Justice Services framework hook ({@code uk.gov.justice.services.core.interceptor.InterceptorChainEntry})
 * that registered a chain of CDI {@code @Interceptor} classes (audit, access control,
 * metrics) for the legacy WildFly deployment. None of those interceptors exist in the
 * Spring Boot port — audit is now a Spring {@code OncePerRequestFilter}
 * ({@code config/AuditFilterConfig}), access control is {@code cp-auth-rules-filter},
 * and request metrics come from Spring Boot Actuator + Micrometer.</p>
 *
 * <p>The placeholder class is kept so the file path remains visible in {@code git diff}
 * (per the in-place rule) but the only honest test body is none — the production class
 * was intentionally deleted, not migrated.</p>
 */
@Disabled("Production class removed — replaced by Spring filters/auto-configuration.")
class CourtSchedulerApiInterceptorChainProviderTest {
}
