
package backend;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;

/**
 * Sends the password reset email.
 *
 * The SMTP connection is built from {@link MailProperties} (environment
 * variables only). When no SMTP server is configured the service reports
 * {@link #isConfigured()} as false and the caller decides what to do; it
 * never pretends that a message was sent.
 */
@Service
public class ResetMailService {

    private final MailProperties mailProperties;

    public ResetMailService(MailProperties mailProperties) {
        this.mailProperties = mailProperties;
    }

    public boolean isConfigured() {
        return mailProperties.isConfigured();
    }

    /**
     * Builds the single-use reset link for the given token.
     */
    public String buildResetLink(String rawToken) {
        return mailProperties.getResetLinkBase()
                + "/reset-password?token="
                + URLEncoder.encode(rawToken, StandardCharsets.UTF_8);
    }

    /**
     * Sends the reset instructions.
     *
     * @throws IllegalStateException when no SMTP server is configured
     */
    public void sendPasswordReset(
            String toEmail,
            String rawToken,
            Duration tokenTtl) {

        if (!mailProperties.isConfigured()) {
            throw new IllegalStateException(
                    "SMTP is not configured for password reset delivery.");
        }

        String resetLink = buildResetLink(rawToken);

        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(mailProperties.getFrom());
        message.setTo(toEmail);
        message.setSubject("PriyoniX One - Password reset");
        message.setText(buildBody(resetLink, tokenTtl));

        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(mailProperties.getHost());
        sender.setPort(mailProperties.getPort());
        sender.setUsername(mailProperties.getUsername());
        sender.setPassword(mailProperties.getPassword());
        sender.setDefaultEncoding("UTF-8");

        java.util.Properties props = new java.util.Properties();
        props.setProperty("mail.smtp.auth",
                String.valueOf(mailProperties.isSmtpAuth()));
        props.setProperty("mail.smtp.starttls.enable",
                String.valueOf(mailProperties.isSmtpStartTls()));
        props.setProperty("mail.smtp.connectiontimeout", "10000");
        props.setProperty("mail.smtp.timeout", "10000");
        props.setProperty("mail.smtp.writetimeout", "10000");
        sender.setJavaMailProperties(props);

        sender.send(message);
    }

    private String buildBody(String resetLink, Duration tokenTtl) {
        long minutes = tokenTtl == null
                ? PasswordResetService.DEFAULT_TOKEN_TTL.toMinutes()
                : tokenTtl.toMinutes();

        return "Hello,\n\n"
                + "We received a request to reset the password of your "
                + "PriyoniX One account.\n"
                + "Use the secure link below to choose a new password. "
                + "The link can be used once and expires in "
                + minutes + " minutes.\n\n"
                + resetLink + "\n\n"
                + "If you did not request a password reset you can safely "
                + "ignore this message; your password stays unchanged.\n\n"
                + "PriyoniX One";
    }
}
