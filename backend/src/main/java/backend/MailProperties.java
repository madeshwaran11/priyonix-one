
package backend;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * SMTP configuration for password reset emails.
 *
 * <table>
 *   <caption>Environment variables</caption>
 *   <tr><th>Variable</th><th>Default</th><th>Purpose</th></tr>
 *   <tr><td>MAIL_HOST</td><td>-</td><td>SMTP server host</td></tr>
 *   <tr><td>MAIL_PORT</td><td>587</td><td>SMTP server port</td></tr>
 *   <tr><td>MAIL_USERNAME</td><td>-</td><td>SMTP username</td></tr>
 *   <tr><td>MAIL_PASSWORD</td><td>-</td><td>SMTP password or app password</td></tr>
 *   <tr><td>MAIL_FROM</td><td>-</td><td>Sender address of reset emails</td></tr>
 *   <tr><td>MAIL_RESET_LINK_BASE</td><td>http://localhost:5173</td>
 *       <td>Public base URL of the frontend, used to build the reset link</td></tr>
 *   <tr><td>MAIL_SMTP_AUTH</td><td>true</td><td>SMTP authentication</td></tr>
 *   <tr><td>MAIL_SMTP_STARTTLS</td><td>true</td><td>STARTTLS encryption</td></tr>
 * </table>
 *
 * Credentials are never hardcoded and never logged.
 */
@Component
public class MailProperties {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String from;
    private final String resetLinkBase;
    private final boolean smtpAuth;
    private final boolean smtpStartTls;

    public MailProperties(Environment environment) {
        this.host = trimToNull(environment.getProperty("MAIL_HOST"));
        this.port = readInt(environment, "MAIL_PORT", 587);
        this.username = trimToNull(environment.getProperty("MAIL_USERNAME"));
        this.password = environment.getProperty("MAIL_PASSWORD");
        this.from = trimToNull(environment.getProperty("MAIL_FROM"));
        this.resetLinkBase = trimToNull(
                environment.getProperty("MAIL_RESET_LINK_BASE"));
        this.smtpAuth = readBoolean(environment, "MAIL_SMTP_AUTH", true);
        this.smtpStartTls = readBoolean(
                environment, "MAIL_SMTP_STARTTLS", true);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
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

    /** True when a real SMTP server is configured. */
    public boolean isConfigured() {
        return host != null && from != null;
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getFrom() {
        return from;
    }

    public String getResetLinkBase() {
        return resetLinkBase == null
                ? "http://localhost:5173"
                : resetLinkBase;
    }

    public boolean isSmtpAuth() {
        return smtpAuth;
    }

    public boolean isSmtpStartTls() {
        return smtpStartTls;
    }
}
