package backend;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformServiceTest {
    MongoTemplate mongo = mock(MongoTemplate.class);
    UserRepository users = mock(UserRepository.class);
    PasswordEncoder encoder = mock(PasswordEncoder.class);
    RefreshTokenService tokens = mock(RefreshTokenService.class);
    PlatformService service = new PlatformService(mongo, users, encoder, tokens);
    Authentication admin = actor("admin@example.test", "ADMIN");
    Authentication employee = actor("worker@example.test", "EMPLOYEE");

    static Authentication actor(String email, String role) {
        return new UsernamePasswordAuthenticationToken(email, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));
    }
    static User user(String id, String email, String role) {
        User u = new User(); u.setId(id); u.setEmail(email); u.setRole(role); u.setPassword("never-return-this"); return u;
    }
    PlatformService.Organization organization(String currency, String timezone, int month) {
        return new PlatformService.Organization("Acme", "Acme Private Limited", "office@example.test", "", "", currency, timezone, month);
    }
    @Test void employeeCannotReadOrModifyAccountDirectory() {
        assertThrows(AccessDeniedException.class, () -> service.users(employee));
        assertThrows(AccessDeniedException.class, () -> service.createUser(new PlatformService.CreateUser("a@b.test", "Valid!Password1", "ADMIN"), employee));
        assertThrows(AccessDeniedException.class, () -> service.updateUser("1", new PlatformService.UpdateUser("ADMIN", true), employee));
        verifyNoInteractions(users);
    }
    @Test void userResponsesContainOnlySafeFields() {
        when(users.findAll()).thenReturn(List.of(user("1", "staff@example.test", "USER")));
        var result = service.users(admin).getFirst();
        assertEquals("EMPLOYEE", result.role());
        assertEquals(List.of("id", "email", "role", "enabled"), Arrays.stream(result.getClass().getRecordComponents()).map(java.lang.reflect.RecordComponent::getName).toList());
        assertFalse(result.toString().contains("never-return-this"));
    }
    @Test void accountCreationHashesPasswordAndNormalizesEmail() {
        when(encoder.encode("Valid!Password1")).thenReturn("encoded-value");
        when(users.save(any(User.class))).thenAnswer(i -> i.getArgument(0));
        var result = service.createUser(new PlatformService.CreateUser(" Staff@Example.test ", "Valid!Password1", "employee"), admin);
        var captor = org.mockito.ArgumentCaptor.forClass(User.class);
        verify(users).save(captor.capture());
        assertEquals("encoded-value", captor.getValue().getPassword());
        assertEquals("staff@example.test", result.email());
        assertEquals("EMPLOYEE", result.role());
    }
    @Test void weakPasswordsAndInvalidRolesAreRejectedBeforeSaving() {
        assertThrows(InvalidRequestException.class, () -> service.createUser(new PlatformService.CreateUser("staff@example.test", "short", "EMPLOYEE"), admin));
        assertThrows(InvalidRequestException.class, () -> service.createUser(new PlatformService.CreateUser("staff@example.test", "Valid!Password1", "ROOT"), admin));
        verify(users, never()).save(any());
    }
    @Test void cannotDisableOrDemoteSelf() {
        when(users.findById("1")).thenReturn(Optional.of(user("1", "admin@example.test", "ADMIN")));
        assertThrows(ConflictException.class, () -> service.updateUser("1", new PlatformService.UpdateUser(null, false), admin));
        assertThrows(ConflictException.class, () -> service.updateUser("1", new PlatformService.UpdateUser("EMPLOYEE", null), admin));
        verify(users, never()).save(any());
    }
    @Test void lastEnabledAdminCannotBeDisabled() {
        User only = user("1", "only@example.test", "ADMIN");
        when(users.findById("1")).thenReturn(Optional.of(only)); when(users.findAll()).thenReturn(List.of(only));
        assertThrows(ConflictException.class, () -> service.updateUser("1", new PlatformService.UpdateUser(null, false), admin));
        verify(users, never()).save(any());
    }
    @Test void organizationRequiresAdminAndValidCurrencyTimezoneAndMonth() {
        assertThrows(AccessDeniedException.class, () -> service.updateOrganization(organization("INR", "Asia/Kolkata", 4), employee));
        assertThrows(InvalidRequestException.class, () -> service.updateOrganization(organization("BAD", "Asia/Kolkata", 4), admin));
        assertThrows(InvalidRequestException.class, () -> service.updateOrganization(organization("INR", "No/Such_Zone", 4), admin));
        assertThrows(InvalidRequestException.class, () -> service.updateOrganization(organization("INR", "Asia/Kolkata", 13), admin));
        verify(mongo, never()).save(any(), anyString());
    }
    @Test void validOrganizationPersistsNormalizedValues() {
        assertEquals("INR", service.updateOrganization(organization("inr", "Asia/Kolkata", 4), admin).currency());
        verify(mongo).save(argThat((Document d) -> "organization".equals(d.getString("_id")) && "INR".equals(d.getString("currency"))), eq("platform_settings"));
    }
}
