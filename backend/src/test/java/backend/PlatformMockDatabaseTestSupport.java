package backend;

import org.mockito.Answers;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Existing controller tests mock repositories and the Mongo factory. They verify HTTP contracts,
 * not persistence. Disable only startup migrations and replace transaction/guard/audit boundaries
 * for these tests; real database workflow tests and the application retain all invariants.
 */
@TestPropertySource(properties = {"platform.indexes.enabled=false", "ROLE_MIGRATION_ENABLED=false"})
abstract class PlatformMockDatabaseTestSupport {
    @MockitoBean(name = "workspaceTransactionManager", answers = Answers.RETURNS_DEEP_STUBS)
    PlatformTransactionManager transactionManager;
    @MockitoBean LegacyIntegrityService legacyIntegrityService;
    @MockitoBean AuditInterceptor auditInterceptor;
    @org.junit.jupiter.api.BeforeEach
    void allowControllerDispatch() throws Exception {
        org.mockito.Mockito.when(auditInterceptor.preHandle(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(true);
    }
}
