package backend;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import io.jsonwebtoken.Jwts;

/** Exercises the real SecurityFilterChain and database-backed JWT role selection. */
class PlatformSecurityTest {
    AnnotationConfigWebApplicationContext context;
    MockMvc mvc;
    @BeforeEach void setup() {
        context = new AnnotationConfigWebApplicationContext(); context.setServletContext(new MockServletContext());
        context.register(Config.class); context.refresh();
        JwtService jwt = context.getBean(JwtService.class); UserRepository users = context.getBean(UserRepository.class);
        when(jwt.validateToken("admin-token")).thenReturn(Jwts.claims().subject("admin@example.test").build());
        when(jwt.validateToken("employee-token")).thenReturn(Jwts.claims().subject("worker@example.test").build());
        when(users.findByEmailIgnoreCase("admin@example.test")).thenReturn(Optional.of(PlatformServiceTest.user("1", "admin@example.test", "ADMIN")));
        when(users.findByEmailIgnoreCase("worker@example.test")).thenReturn(Optional.of(PlatformServiceTest.user("2", "worker@example.test", "EMPLOYEE")));
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(context.getBean(FilterChainProxy.class)).build();
    }
    @AfterEach void close() { context.close(); }
    @Test void sensitiveManagementApisRequireAdmin() throws Exception {
        for (String path : new String[]{"/api/employees", "/api/finance/summary", "/api/invoices", "/api/payments", "/api/expenses", "/api/platform/users", "/api/platform/audit", "/api/platform/status"}) {
            mvc.perform(get(path).header("Authorization", "Bearer employee-token")).andExpect(status().isForbidden());
            mvc.perform(get(path).header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        }
    }
    @Test void organizationReadRequiresAuthenticationAndWriteRequiresAdmin() throws Exception {
        mvc.perform(get("/api/platform/organization")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/platform/organization").header("Authorization", "Bearer employee-token")).andExpect(status().isOk());
        mvc.perform(put("/api/platform/organization").header("Authorization", "Bearer employee-token")).andExpect(status().isForbidden());
        mvc.perform(put("/api/platform/organization").header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
    }
    @Test void employeeOwnWorkspaceRemainsAccessible() throws Exception {
        mvc.perform(get("/api/employee/overview").header("Authorization", "Bearer employee-token")).andExpect(status().isOk());
        mvc.perform(get("/api/workspace/leave").header("Authorization", "Bearer employee-token")).andExpect(status().isOk());
    }
    @Configuration @EnableWebSecurity @EnableWebMvc @Import(SecurityConfig.class)
    static class Config {
        @Bean JwtService jwtService() { return mock(JwtService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean JwtAuthenticationFilter filter(JwtService jwt, UserRepository users) { return new JwtAuthenticationFilter(jwt, users); }
        @Bean ProbeController probes() { return new ProbeController(); }
    }
    @RestController static class ProbeController {
        @RequestMapping({"/api/employees", "/api/finance/summary", "/api/invoices", "/api/payments", "/api/expenses", "/api/platform/users", "/api/platform/audit", "/api/platform/status", "/api/platform/organization", "/api/employee/overview", "/api/workspace/leave"})
        String ok() { return "ok"; }
    }
}
