
package backend;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    /** Endpoints that may be called without a token. */
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/api/auth/forgot-password",
            "/api/auth/reset-password",
            "/api/auth/admin/reset-password"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final List<String> allowedOrigins;

    public SecurityConfig(
            JwtAuthenticationFilter jwtAuthenticationFilter,
            Environment environment) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;

        // Comma-separated list so a deployed frontend can be allowed without
        // editing code. Defaults to the local Vite dev server.
        String configured = environment.getProperty(
                "CORS_ALLOWED_ORIGINS", "http://localhost:5173");
        List<String> origins = Stream.of(configured.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
        this.allowedOrigins = origins.isEmpty()
                ? List.of("http://localhost:5173")
                : origins;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http) throws Exception {

        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            .formLogin(form -> form.disable())
            .httpBasic(basic -> basic.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(exceptions -> exceptions
                // No/invalid credentials: 401.
                .authenticationEntryPoint((request, response, exception) -> {
                    response.setStatus(
                            HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":\"ERROR\",\"message\":\"Unauthorized\"}");
                })
                // Valid credentials but insufficient role: 403. Without an
                // explicit handler Spring can surface this as a 401, which
                // wrongly tells the client to sign in again.
                .accessDeniedHandler((request, response, exception) -> {
                    response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                    response.setContentType("application/json");
                    response.getWriter().write(
                        "{\"status\":\"ERROR\",\"message\":\"Forbidden\"}");
                }))
            .authorizeHttpRequests(auth -> auth
                // CORS preflight must never require authentication.
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                .requestMatchers("/api/health").permitAll()
                // Only the public authentication endpoints are open. Every
                // other /api/auth route (for example /api/auth/me) needs a
                // valid token.
                .requestMatchers(PUBLIC_AUTH_ENDPOINTS).permitAll()
                // HR and finance management contain organization-wide sensitive records.
                // Employees use /api/employee/overview and ownership-scoped workspace APIs.
                .requestMatchers("/api/employees/**", "/api/finance/**", "/api/invoices/**",
                        "/api/payments/**", "/api/expenses/**", "/api/dashboard/**")
                        .hasRole(UserRoles.ADMIN)
                .requestMatchers("/api/platform/users/**", "/api/platform/audit", "/api/platform/status")
                        .hasRole(UserRoles.ADMIN)
                .requestMatchers(HttpMethod.PUT, "/api/platform/organization").hasRole(UserRoles.ADMIN)
                // The administrative namespace is restricted to ADMIN.
                .requestMatchers("/api/admin/**").hasRole(UserRoles.ADMIN)
                // The employee namespace is restricted to EMPLOYEE.
                .requestMatchers("/api/employee/**")
                        .hasRole(UserRoles.EMPLOYEE)
                // Deleting an employee is an administrative operation.
                .requestMatchers(HttpMethod.DELETE, "/api/employees/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a project (and its tasks) is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/projects/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a task or subtask is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/tasks/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a client is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/clients/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a lead is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/leads/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a marketing campaign is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/campaigns/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a follow-up is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/follow-ups/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting an invoice (and its payments) is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/invoices/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting a payment is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/payments/**")
                        .hasRole(UserRoles.ADMIN)
                // Deleting an expense is administrative.
                .requestMatchers(HttpMethod.DELETE, "/api/expenses/**")
                        .hasRole(UserRoles.ADMIN)
                // Approving / rejecting / reimbursing an expense is
                // administrative.
                .requestMatchers(HttpMethod.PATCH, "/api/expenses/*/status")
                        .hasRole(UserRoles.ADMIN)
                // Workspace controllers additionally enforce module and record ownership rules.
                .anyRequest().authenticated())
            .addFilterBefore(
                jwtAuthenticationFilter,
                UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();

        // A wildcard origin cannot be combined with credentials, so the
        // wildcard case deliberately disables them instead of failing at
        // runtime. Production deployments list their real origins.
        boolean wildcard = allowedOrigins.contains("*");

        if (wildcard) {
            config.setAllowedOriginPatterns(List.of("*"));
        } else {
            config.setAllowedOrigins(allowedOrigins);
        }

        config.setAllowedMethods(
                List.of("GET", "POST", "PUT", "PATCH",
                        "DELETE", "OPTIONS"));

        config.setAllowedHeaders(List.of("*"));

        // Credentials are required for the HttpOnly refresh cookie that
        // backs the "Remember me" option.
        config.setAllowCredentials(!wildcard);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
