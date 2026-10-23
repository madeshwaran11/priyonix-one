
package backend;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
/**
 * Authentication API.
 *
 * <ul>
 *   <li>{@code POST /api/auth/login} &mdash; signs in with email and
 *       password. The optional {@code role} field states which sign-in option
 *       the user picked; the backend always verifies the role stored for the
 *       account in MongoDB, so the field can never grant extra permissions.
 *       When {@code rememberMe} is set the response additionally carries an
 *       HttpOnly refresh cookie that keeps the session alive across browser
 *       restarts.</li>
 *   <li>{@code POST /api/auth/refresh} &mdash; exchanges the refresh cookie
 *       for a new access token and rotates the refresh token.</li>
 *   <li>{@code POST /api/auth/logout} &mdash; revokes the refresh cookie and
 *       clears it in the browser.</li>
 *   <li>{@code POST /api/auth/forgot-password} &mdash; starts a password
 *       reset. The response is identical for existing and unknown addresses
 *       so the endpoint cannot be used to discover accounts.</li>
 *   <li>{@code POST /api/auth/reset-password} &mdash; consumes a reset token
 *       and stores the new password as a BCrypt hash. The user is not signed
 *       in automatically.</li>
 *   <li>{@code GET /api/auth/me} &mdash; returns the identity and role of the
 *       signed-in user.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** The one and only answer the forgot-password endpoint ever gives. */
    static final String GENERIC_RESET_MESSAGE =
            "If an account exists for this email, password reset instructions"
                    + " will be sent.";

    private static final String GENERIC_RESET_UNAVAILABLE_MESSAGE =
            "Password reset is temporarily unavailable. Please contact your"
                    + " administrator.";

    private final AuthService authService;
    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final PasswordResetService passwordResetService;
    private final ResetMailService mailService;
    private final RateLimiterService rateLimiter;
    private final AuthProperties authProperties;
    private final RefreshTokenService refreshTokenService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            UserRepository userRepository,
            PasswordResetService passwordResetService,
            ResetMailService mailService,
            RateLimiterService rateLimiter,
            AuthProperties authProperties,
            RefreshTokenService refreshTokenService) {

        this.authService = authService;
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.passwordResetService = passwordResetService;
        this.mailService = mailService;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
        this.refreshTokenService = refreshTokenService;
    }

    // ----- login ---------------------------------------------------------

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String expectedRole = UserRoles.parseRequestedRole(request.role());

        if (request.role() != null && !request.role().isBlank()
                && expectedRole == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error("Unknown role. Use ADMIN or EMPLOYEE."));
        }

        String emailKey = "login:email:" + email;
        String ipKey = "login:ip:" + clientIp(httpRequest);

        if (rateLimiter.isBlocked(emailKey,
                authProperties.getLoginMaxAttempts(),
                authProperties.getLoginWindow())
                || rateLimiter.isBlocked(ipKey,
                        authProperties.getLoginMaxIpAttempts(),
                        authProperties.getLoginWindow())) {
            return tooManyRequests(rateLimiter.retryAfterSeconds(
                    emailKey, authProperties.getLoginWindow()));
        }

        AuthService.AuthenticationResult result = authService.authenticate(
                email, request.password(), expectedRole);

        if (result.status() == AuthService.Status.ROLE_MISMATCH) {
            // The password was correct, so revealing the role mismatch does
            // not expose anything an attacker did not already know.
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(error("This account is not authorized to sign in"
                            + " with the selected option."));
        }

        if (result.status() != AuthService.Status.SUCCESS) {
            rateLimiter.recordFailure(emailKey,
                    authProperties.getLoginWindow());
            rateLimiter.recordFailure(ipKey,
                    authProperties.getLoginWindow());

            // Generic 401 for missing, unknown, disabled or wrong-password
            // accounts so the response never reveals which one failed.
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("Invalid email or password"));
        }

        rateLimiter.reset(emailKey);
        rateLimiter.reset(ipKey);

        User user = result.user();
        String role = UserRoles.canonical(user.getRole());
        String token = jwtService.generateToken(user.getEmail(), role);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Login successful");
        body.put("token", token);
        body.put("email", user.getEmail());
        body.put("role", role);
        body.put("expiresIn", jwtService.getExpirationSeconds());
        body.put("rememberMe", request.wantsRememberMe());

        if (!request.wantsRememberMe()) {
            // Without "Remember me" the session ends with the access token:
            // no refresh cookie is issued and any older one is removed.
            return ResponseEntity.ok()
                    .header(HttpHeaders.SET_COOKIE,
                            clearRefreshCookie().toString())
                    .body(body);
        }

        RefreshTokenService.IssuedToken refresh =
                refreshTokenService.issue(user);

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshCookie(refresh.rawToken()).toString())
                .body(body);
    }

    // ----- session refresh ------------------------------------------------

    /**
     * Exchanges the HttpOnly refresh cookie for a new access token.
     *
     * The refresh token is rotated on every use, so a replayed cookie is
     * rejected and the legitimate client transparently receives a successor.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refresh(
            HttpServletRequest httpRequest) {

        String rawToken = readRefreshCookie(httpRequest);

        Optional<RefreshTokenService.RotatedToken> rotated =
                refreshTokenService.rotate(rawToken);

        if (rotated.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE,
                            clearRefreshCookie().toString())
                    .body(error("Your session is no longer valid."));
        }

        User user = rotated.get().user();

        if (!user.isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .header(HttpHeaders.SET_COOKIE,
                            clearRefreshCookie().toString())
                    .body(error("Your session is no longer valid."));
        }

        String role = UserRoles.canonical(user.getRole());
        String token = jwtService.generateToken(user.getEmail(), role);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Session refreshed");
        body.put("token", token);
        body.put("email", user.getEmail());
        body.put("role", role);
        body.put("expiresIn", jwtService.getExpirationSeconds());

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        buildRefreshCookie(rotated.get().rawToken()).toString())
                .body(body);
    }

    // ----- sign out -------------------------------------------------------

    /**
     * Revokes the refresh token and clears the cookie. The endpoint always
     * answers with success so it cannot be used to probe for a valid cookie.
     */
    @PostMapping("/logout")
    public ResponseEntity<Map<String, Object>> logout(
            HttpServletRequest httpRequest) {

        refreshTokenService.revoke(readRefreshCookie(httpRequest));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Signed out");

        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        clearRefreshCookie().toString())
                .body(body);
    }

    // ----- cookie helpers -------------------------------------------------

    private String readRefreshCookie(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }

        for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
            if (authProperties.getRefreshCookieName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    private ResponseCookie buildRefreshCookie(String rawToken) {
        ResponseCookie.ResponseCookieBuilder builder =
                ResponseCookie.from(
                        authProperties.getRefreshCookieName(), rawToken)
                        .httpOnly(true)
                        .path("/api/auth")
                        .secure(authProperties.isRefreshCookieSecure())
                        .maxAge(authProperties.getRefreshTokenTtl());

        String domain = authProperties.getRefreshCookieDomain();

        if (domain != null && !domain.isBlank()) {
            builder.domain(domain);
        }

        String sameSite = authProperties.getRefreshCookieSameSite();

        if ("NONE".equalsIgnoreCase(sameSite)) {
            builder.sameSite("None");
        } else if ("STRICT".equalsIgnoreCase(sameSite)) {
            builder.sameSite("Strict");
        } else {
            builder.sameSite("Lax");
        }

        return builder.build();
    }

    private ResponseCookie clearRefreshCookie() {
        return ResponseCookie.from(
                authProperties.getRefreshCookieName(), "")
                .httpOnly(true)
                .path("/api/auth")
                .secure(authProperties.isRefreshCookieSecure())
                .maxAge(0)
                .sameSite(authProperties.getRefreshCookieSameSite()
                        .equalsIgnoreCase("None") ? "None" : "Lax")
                .build();
    }

    // ----- forgot password -----------------------------------------------

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, Object>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request,
            HttpServletRequest httpRequest) {

        String email = request.email().trim().toLowerCase(Locale.ROOT);
        String emailKey = "forgot:email:" + email;
        String ipKey = "forgot:ip:" + clientIp(httpRequest);

        // The limiter runs before the account lookup and counts every
        // request, so existing and unknown addresses behave identically.
        if (rateLimiter.isBlocked(emailKey,
                authProperties.getForgotMaxRequests(),
                authProperties.getForgotWindow())
                || rateLimiter.isBlocked(ipKey,
                        authProperties.getForgotMaxIpRequests(),
                        authProperties.getForgotWindow())) {
            return tooManyRequests(rateLimiter.retryAfterSeconds(
                    emailKey, authProperties.getForgotWindow()));
        }

        rateLimiter.recordFailure(emailKey,
                authProperties.getForgotWindow());
        rateLimiter.recordFailure(ipKey,
                authProperties.getForgotWindow());

        boolean deliveryAvailable = mailService.isConfigured()
                || authProperties.isPasswordResetDevMode();

        if (!deliveryAvailable) {
            // Nothing was sent, so the endpoint must not claim otherwise.
            // The same answer is returned for every address.
            System.out.println(
                    "[auth] password reset requested but no SMTP server is"
                    + " configured. Set MAIL_HOST, MAIL_FROM,"
                    + " MAIL_USERNAME and MAIL_PASSWORD to enable delivery.");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(error(GENERIC_RESET_UNAVAILABLE_MESSAGE));
        }

        Optional<User> candidate =
                userRepository.findByEmailIgnoreCase(email);

        if (candidate.isPresent() && candidate.get().isEnabled()) {
            deliverResetLink(candidate.get());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", GENERIC_RESET_MESSAGE);

        return ResponseEntity.ok(body);
    }

    private void deliverResetLink(User user) {
        PasswordResetService.IssuedToken issued =
                passwordResetService.issue(user);

        String resetLink = mailService.buildResetLink(issued.rawToken());

        if (mailService.isConfigured()) {
            try {
                mailService.sendPasswordReset(
                        user.getEmail(),
                        issued.rawToken(),
                        passwordResetService.getTokenTtl());
                return;
            } catch (RuntimeException ex) {
                // The response must stay identical for every address, so the
                // delivery failure is reported to the operator through the
                // log instead of through a different HTTP answer.
                System.out.println(
                        "[auth] password reset email could not be sent: "
                        + ex.getClass().getSimpleName());
                return;
            }
        }

        // Development only: print the link so a developer can complete the
        // flow without an SMTP server. Never enable this in production.
        System.out.println(
                "[auth] DEVELOPMENT MODE - password reset link for account "
                + user.getEmail() + " (valid for "
                + passwordResetService.getTokenTtl().toMinutes()
                + " minutes, single use):");
        System.out.println(resetLink);
        System.out.println(
                "[auth] PASSWORD_RESET_DEV_MODE is enabled: the link above "
                + "is a live credential. Disable it outside local"
                + " development.");
    }

    // ----- reset password ------------------------------------------------

    @PostMapping("/reset-password")
    public ResponseEntity<Map<String, Object>> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request,
            HttpServletRequest httpRequest) {

        String ipKey = "reset:ip:" + clientIp(httpRequest);

        if (rateLimiter.isBlocked(ipKey,
                authProperties.getResetMaxAttempts(),
                authProperties.getResetWindow())) {
            return tooManyRequests(rateLimiter.retryAfterSeconds(
                    ipKey, authProperties.getResetWindow()));
        }

        rateLimiter.recordFailure(ipKey,
                authProperties.getResetWindow());

        try {
            passwordResetService.resetPassword(
                    request.token(),
                    request.password(),
                    request.confirmPassword());
        } catch (InvalidResetTokenException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error(ex.getMessage()));
        } catch (InvalidRequestException ex) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(error(ex.getMessage()));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("message", "Your password has been updated. Please sign in"
                + " with your new password.");

        return ResponseEntity.ok(body);
    }

    // ----- current identity ----------------------------------------------

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        String email = authentication.getName();

        Optional<User> user = userRepository.findByEmailIgnoreCase(email);

        if (user.isEmpty() || !user.get().isEnabled()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(error("Unauthorized"));
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "SUCCESS");
        body.put("email", user.get().getEmail());
        body.put("role", UserRoles.canonical(user.get().getRole()));

        return ResponseEntity.ok(body);
    }

    // ----- helpers -------------------------------------------------------

    private Map<String, Object> error(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", "ERROR");
        body.put("message", message);
        return body;
    }

    private ResponseEntity<Map<String, Object>> tooManyRequests(
            long retryAfterSeconds) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER,
                        String.valueOf(retryAfterSeconds))
                .body(error("Too many attempts. Please wait a moment and try"
                        + " again."));
    }

    /**
     * Resolves the client address used by the rate limiter. The forwarded
     * header is only honoured when the deployment explicitly trusts it.
     */
    private String clientIp(HttpServletRequest request) {
        if (authProperties.isTrustForwardedFor()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                int comma = forwarded.indexOf(',');
                String first = comma > 0
                        ? forwarded.substring(0, comma)
                        : forwarded;
                return first.trim();
            }
        }

        String remote = request.getRemoteAddr();
        return remote == null ? "unknown" : remote;
    }

    // ----- request DTOs --------------------------------------------------

    public record LoginRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            String password,

            /** Optional: ADMIN or EMPLOYEE. Verified against the account. */
            String role,

            /**
             * Optional: keeps the session alive across browser restarts by
             * issuing an HttpOnly refresh cookie. Defaults to false, so a
             * plain sign-in ends when the access token expires.
             */
            Boolean rememberMe) {

        /**
         * True when the client asked for a remembered session. The value is
         * read defensively because the field is optional.
         */
        public boolean wantsRememberMe() {
            return Boolean.TRUE.equals(rememberMe);
        }
    }

    public record ForgotPasswordRequest(
            @NotBlank(message = "Email is required")
            @Email(message = "Enter a valid email address")
            String email) {
    }

    public record ResetPasswordRequest(
            @NotBlank(message = "Reset token is required")
            String token,

            @NotBlank(message = "New password is required")
            @Size(min = PasswordPolicy.MIN_LENGTH,
                    max = PasswordPolicy.MAX_LENGTH,
                    message = "Password must be between "
                            + PasswordPolicy.MIN_LENGTH + " and "
                            + PasswordPolicy.MAX_LENGTH + " characters")
            String password,

            @NotBlank(message = "Please confirm the new password")
            String confirmPassword) {
    }
}
