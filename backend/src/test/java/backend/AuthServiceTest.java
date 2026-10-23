package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    private final PasswordEncoder passwordEncoder =
            new BCryptPasswordEncoder();

    private AuthService authService;

    private static final String RAW_PASSWORD = "Sup3rSecret-Passw0rd!";

    @BeforeEach
    void setUp() {
        authService = new AuthService(userRepository, passwordEncoder);
    }

    private User enabledUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode(RAW_PASSWORD));
        user.setRole("ADMIN");
        user.setEnabled(true);
        return user;
    }

    @Test
    void authenticatesWithCorrectPassword() {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledUser("admin@example.com")));

        Optional<User> result =
                authService.authenticate("admin@example.com", RAW_PASSWORD);

        assertTrue(result.isPresent());
        assertEquals("admin@example.com", result.get().getEmail());
    }

    @Test
    void lookupIsCaseInsensitive() {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledUser("admin@example.com")));

        Optional<User> result =
                authService.authenticate("AdMiN@ExAmPlE.CoM", RAW_PASSWORD);

        assertTrue(result.isPresent());
    }

    @Test
    void rejectsIncorrectPassword() {
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(enabledUser("admin@example.com")));

        assertFalse(authService.authenticate(
                "admin@example.com", "wrong-password").isPresent());
    }

    @Test
    void rejectsMissingUser() {
        when(userRepository.findByEmailIgnoreCase("nobody@example.com"))
                .thenReturn(Optional.empty());

        assertFalse(authService.authenticate(
                "nobody@example.com", RAW_PASSWORD).isPresent());
    }

    @Test
    void rejectsDisabledUser() {
        User disabled = enabledUser("admin@example.com");
        disabled.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(disabled));

        assertFalse(authService.authenticate(
                "admin@example.com", RAW_PASSWORD).isPresent());
    }

    @Test
    void rejectsUserWithMissingStoredHash() {
        User noHash = new User();
        noHash.setEmail("admin@example.com");
        noHash.setPassword(null);
        noHash.setEnabled(true);
        when(userRepository.findByEmailIgnoreCase("admin@example.com"))
                .thenReturn(Optional.of(noHash));

        assertFalse(authService.authenticate(
                "admin@example.com", RAW_PASSWORD).isPresent());
    }

    @Test
    void rejectsBlankInputsWithoutTouchingRepository() {
        assertFalse(authService.authenticate(null, RAW_PASSWORD).isPresent());
        assertFalse(authService.authenticate("admin@example.com", null)
                .isPresent());
        assertFalse(authService.authenticate("  ", RAW_PASSWORD).isPresent());
        assertFalse(authService.authenticate("admin@example.com", "  ")
                .isPresent());

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void rejectsMalformedEmailWithoutTouchingRepository() {
        assertFalse(authService.authenticate(
                "not-an-email", RAW_PASSWORD).isPresent());

        verify(userRepository, never()).findByEmailIgnoreCase(anyString());
    }
}
