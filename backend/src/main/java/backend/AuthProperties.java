
package backend;

import java.time.Duration;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Tunable authentication settings, all sourced from environment variables so
 * nothing secret or deployment specific is hardcoded.
 *
 * <table>
 *   <caption>Environment variables</caption>
 *   <tr><th>Variable</th><th>Default</th><th>Purpose</th></tr>
 *   <tr><td>AUTH_LOGIN_MAX_ATTEMPTS</td><td>10</td>
 *       <td>Failed logins per email before the account is blocked</td></tr>
 *   <tr><td>AUTH_LOGIN_MAX_IP_ATTEMPTS</td><td>30</td>
 *       <td>Failed logins per client IP before the IP is blocked</td></tr>
 *   <tr><td>AUTH_LOGIN_WINDOW_MINUTES</td><td>15</td>
 *       <td>Length of the login rate limiting window</td></tr>
 *   <tr><td>AUTH_FORGOT_MAX_REQUESTS</td><td>5</td>
 *       <td>Password reset requests per email and window</td></tr>
 *   <tr><td>AUTH_FORGOT_MAX_IP_REQUESTS</td><td>20</td>
 *       <td>Password reset requests per client IP and window</td></tr>
 *   <tr><td>AUTH_FORGOT_WINDOW_MINUTES</td><td>60</td>
 *       <td>Length of the password reset rate limiting window</td></tr>
 *   <tr><td>AUTH_RESET_MAX_ATTEMPTS</td><td>10</td>
 *       <td>Password reset attempts per client IP and window</td></tr>
 *   <tr><td>AUTH_RESET_WINDOW_MINUTES</td><td>15</td>
 *       <td>Length of the reset attempt rate limiting window</td></tr>
 *   <tr><td>PASSWORD_RESET_TOKEN_TTL_MINUTES</td><td>15</td>
 *       <td>Lifetime of a password reset link</td></tr>
 *   <tr><td>PASSWORD_RESET_DEV_MODE</td><td>false</td>
 *       <td>Development only: print reset links to the console</td></tr>
 *   <tr><td>ROLE_MIGRATION_ENABLED</td><td>true</td>
 *       <td>Persist canonical roles for legacy accounts on startup</td></tr>
 *   <tr><td>AUTH_REFRESH_TOKEN_TTL_DAYS</td><td>30</td>
 *       <td>Lifetime of the "Remember me" refresh cookie</td></tr>
 *   <tr><td>AUTH_REFRESH_COOKIE_NAME</td><td>px_refresh</td>
 *       <td>Name of the refresh cookie</td></tr>
 *   <tr><td>AUTH_REFRESH_COOKIE_SECURE</td><td>false</td>
 *       <td>Send the refresh cookie only over HTTPS (true in production)</td></tr>
 *   <tr><td>AUTH_REFRESH_COOKIE_SAME_SITE</td><td>Lax</td>
 *       <td>SameSite policy of the refresh cookie</td></tr>
 *   <tr><td>AUTH_REFRESH_COOKIE_DOMAIN</td><td>-</td>
 *       <td>Optional domain attribute of the refresh cookie</td></tr>
 * </table>
 */
@Component
public class AuthProperties {

    private final int loginMaxAttempts;
    private final int loginMaxIpAttempts;
    private final Duration loginWindow;
    private final int forgotMaxRequests;
    private final int forgotMaxIpRequests;
    private final Duration forgotWindow;
    private final int resetMaxAttempts;
    private final Duration resetWindow;
    private final Duration resetTokenTtl;
    private final boolean passwordResetDevMode;
    private final boolean roleMigrationEnabled;
    private final boolean trustForwardedFor;
    private final Duration refreshTokenTtl;
    private final String refreshCookieName;
    private final boolean refreshCookieSecure;
    private final String refreshCookieSameSite;
    private final String refreshCookieDomain;

    public AuthProperties(Environment environment) {
        this.loginMaxAttempts = readInt(environment,
                "AUTH_LOGIN_MAX_ATTEMPTS", 10);
        this.loginMaxIpAttempts = readInt(environment,
                "AUTH_LOGIN_MAX_IP_ATTEMPTS", 30);
        this.loginWindow = Duration.ofMinutes(readInt(environment,
                "AUTH_LOGIN_WINDOW_MINUTES", 15));
        this.forgotMaxRequests = readInt(environment,
                "AUTH_FORGOT_MAX_REQUESTS", 5);
        this.forgotMaxIpRequests = readInt(environment,
                "AUTH_FORGOT_MAX_IP_REQUESTS", 20);
        this.forgotWindow = Duration.ofMinutes(readInt(environment,
                "AUTH_FORGOT_WINDOW_MINUTES", 60));
        this.resetMaxAttempts = readInt(environment,
                "AUTH_RESET_MAX_ATTEMPTS", 10);
        this.resetWindow = Duration.ofMinutes(readInt(environment,
                "AUTH_RESET_WINDOW_MINUTES", 15));
        this.resetTokenTtl = Duration.ofMinutes(readInt(environment,
                "PASSWORD_RESET_TOKEN_TTL_MINUTES", 15));
        this.passwordResetDevMode = readBoolean(environment,
                "PASSWORD_RESET_DEV_MODE", false);
        this.roleMigrationEnabled = readBoolean(environment,
                "ROLE_MIGRATION_ENABLED", true);
        this.trustForwardedFor = readBoolean(environment,
                "AUTH_TRUST_FORWARDED_FOR", false);
        this.refreshTokenTtl = Duration.ofDays(readInt(environment,
                "AUTH_REFRESH_TOKEN_TTL_DAYS", 30));
        this.refreshCookieName = readText(environment,
                "AUTH_REFRESH_COOKIE_NAME", "px_refresh");
        this.refreshCookieSecure = readBoolean(environment,
                "AUTH_REFRESH_COOKIE_SECURE", false);
        this.refreshCookieSameSite = readText(environment,
                "AUTH_REFRESH_COOKIE_SAME_SITE", "Lax");
        this.refreshCookieDomain = readText(environment,
                "AUTH_REFRESH_COOKIE_DOMAIN", null);
    }

    private static String readText(
            Environment environment,
            String name,
            String fallback) {

        String raw = environment.getProperty(name);

        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        return raw.trim();
    }

    private static int readInt(
            Environment environment,
            String name,
            int fallback) {
        String raw = environment.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }

        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static boolean readBoolean(
            Environment environment,
            String name,
            boolean fallback) {
        String raw = environment.getProperty(name);
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        return Boolean.parseBoolean(raw.trim());
    }

    public int getLoginMaxAttempts() {
        return loginMaxAttempts;
    }

    public int getLoginMaxIpAttempts() {
        return loginMaxIpAttempts;
    }

    public Duration getLoginWindow() {
        return loginWindow;
    }

    public int getForgotMaxRequests() {
        return forgotMaxRequests;
    }

    public int getForgotMaxIpRequests() {
        return forgotMaxIpRequests;
    }

    public Duration getForgotWindow() {
        return forgotWindow;
    }

    public int getResetMaxAttempts() {
        return resetMaxAttempts;
    }

    public Duration getResetWindow() {
        return resetWindow;
    }

    public Duration getResetTokenTtl() {
        return resetTokenTtl;
    }

    /**
     * Development switch that prints password reset links to the console
     * instead of sending them. It must never be enabled in production.
     */
    public boolean isPasswordResetDevMode() {
        return passwordResetDevMode;
    }

    public boolean isRoleMigrationEnabled() {
        return roleMigrationEnabled;
    }

    /**
     * When the API runs behind a reverse proxy this makes the rate limiter
     * use the {@code X-Forwarded-For} header instead of the proxy address.
     * Only enable it when the proxy is trusted, otherwise a client could
     * spoof its own address and bypass the per-IP limits.
     */
    public boolean isTrustForwardedFor() {
        return trustForwardedFor;
    }

    /** Lifetime of the "Remember me" refresh cookie. */
    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /** Name of the refresh cookie. */
    public String getRefreshCookieName() {
        return refreshCookieName;
    }

    /**
     * When true the refresh cookie is only sent over HTTPS. This must be
     * enabled in production; the default is false so the flow also works
     * against a local development server.
     */
    public boolean isRefreshCookieSecure() {
        return refreshCookieSecure;
    }

    /** SameSite policy of the refresh cookie. */
    public String getRefreshCookieSameSite() {
        return refreshCookieSameSite;
    }

    /** Optional domain attribute of the refresh cookie. */
    public String getRefreshCookieDomain() {
        return refreshCookieDomain;
    }
}
