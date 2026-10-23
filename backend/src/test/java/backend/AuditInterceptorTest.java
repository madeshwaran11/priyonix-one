package backend;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class AuditInterceptorTest {
    AuditService audit = mock(AuditService.class);
    PlatformService platform = mock(PlatformService.class);
    AuditInterceptor interceptor = new AuditInterceptor(audit, platform, mock(MongoTemplate.class), mock(UserRepository.class));
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }
    @Test void successfulMutationCapturesActorActionAndResourceWithoutPayload() {
        SecurityContextHolder.getContext().setAuthentication(PlatformServiceTest.actor("admin@example.test", "ADMIN"));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/platform/users");
        request.setContent("{\"password\":\"secret\"}".getBytes());
        request.setQueryString("token=never-log");
        request.setAttribute("audit.resourceId", "new-user-id");
        MockHttpServletResponse response = new MockHttpServletResponse(); response.setStatus(201);
        interceptor.afterCompletion(request, response, new Object(), null);
        verify(audit).append("admin@example.test", "CREATE", "/api/platform/users/new-user-id");
        verify(platform).notifyUser(eq("admin@example.test"), anyString(), anyString());
    }
    @Test void failedMutationAndReadsDoNotCreateSuccessEvents() {
        SecurityContextHolder.getContext().setAuthentication(PlatformServiceTest.actor("admin@example.test", "ADMIN"));
        MockHttpServletResponse response = new MockHttpServletResponse(); response.setStatus(400);
        interceptor.afterCompletion(new MockHttpServletRequest("POST", "/api/clients"), response, new Object(), null);
        response.setStatus(200);
        interceptor.afterCompletion(new MockHttpServletRequest("GET", "/api/clients"), response, new Object(), null);
        verifyNoInteractions(audit, platform);
    }
}
