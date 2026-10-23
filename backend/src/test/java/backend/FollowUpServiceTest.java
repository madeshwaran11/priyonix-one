package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FollowUpServiceTest {

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    private FollowUpService followUpService;

    @BeforeEach
    void setUp() {
        followUpService = new FollowUpService(
                followUpRepository, leadRepository, clientRepository,
                employeeRepository);
    }

    private FollowUpRequest request(
            String activityType, LocalDate dueDate, Boolean completed,
            String leadId, String clientId) {
        return new FollowUpRequest(
                activityType, dueDate, "Call back", completed, null, leadId,
                clientId);
    }

    private FollowUp storedFollowUp(
            String leadId, String clientId, LocalDate dueDate,
            boolean completed) {
        FollowUp followUp = new FollowUp();
        followUp.setId("fu-doc-1");
        followUp.setActivityType("CALL");
        followUp.setDueDate(dueDate);
        followUp.setCompleted(completed);
        followUp.setLeadId(leadId);
        followUp.setClientId(clientId);
        followUp.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return followUp;
    }

    // ----- create validation ---------------------------------------------

    @Test
    void createRejectsInvalidActivityType() {
        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request(
                        "TEXT", LocalDate.now(), false, "lead-1", null)));

        verify(followUpRepository, never()).save(any(FollowUp.class));
    }

    @Test
    void createRejectsBothLeadAndClient() {
        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request(
                        "CALL", LocalDate.now(), false, "lead-1", "client-1")));

        verify(followUpRepository, never()).save(any(FollowUp.class));
    }

    @Test
    void createRejectsNeitherLeadNorClient() {
        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request(
                        "CALL", LocalDate.now(), false, null, null)));

        verify(followUpRepository, never()).save(any(FollowUp.class));
    }

    @Test
    void createRejectsUnknownLead() {
        when(leadRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request(
                        "CALL", LocalDate.now(), false, "ghost", null)));
    }

    @Test
    void createRejectsUnknownClient() {
        when(clientRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request(
                        "CALL", LocalDate.now(), false, null, "ghost")));
    }

    @Test
    void createRejectsUnknownAssignee() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(new Client()));
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        FollowUpRequest request = new FollowUpRequest(
                "CALL", LocalDate.now(), null, false, "ghost", null, "client-1");

        assertThrows(InvalidRequestException.class,
                () -> followUpService.create(request));
    }

    @Test
    void createComputesOverdueFlagAndStampsTimestamps() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(new Client()));
        when(followUpRepository.save(any(FollowUp.class)))
                .thenAnswer(invocation -> {
                    FollowUp followUp = invocation.getArgument(0);
                    followUp.setId("fu-new");
                    return followUp;
                });

        FollowUpResponse response = followUpService.create(request(
                "call", LocalDate.now().minusDays(1), false, null, "client-1"));

        assertEquals("CALL", response.activityType());
        assertTrue(response.overdue());

        ArgumentCaptor<FollowUp> captor =
                ArgumentCaptor.forClass(FollowUp.class);
        verify(followUpRepository).save(captor.capture());
        assertNotNull(captor.getValue().getCreatedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
    }

    @Test
    void completedFollowUpIsNeverOverdue() {
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(new Client()));
        when(followUpRepository.save(any(FollowUp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        FollowUpResponse response = followUpService.create(request(
                "EMAIL", LocalDate.now().minusDays(3), true, null, "client-1"));

        assertFalse(response.overdue());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updatePreservesCreatedAt() {
        FollowUp existing = storedFollowUp(null, "client-1",
                LocalDate.now().plusDays(1), false);
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(followUpRepository.findById("fu-doc-1"))
                .thenReturn(Optional.of(existing));
        when(clientRepository.findById("client-1"))
                .thenReturn(Optional.of(new Client()));
        when(followUpRepository.save(any(FollowUp.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        followUpService.update("fu-doc-1", request(
                "MEETING", LocalDate.now().plusDays(5), false, null, "client-1"));

        ArgumentCaptor<FollowUp> captor =
                ArgumentCaptor.forClass(FollowUp.class);
        verify(followUpRepository).save(captor.capture());
        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                captor.getValue().getCreatedAt());
    }

    // ----- queries -------------------------------------------------------

    @Test
    void listFiltersByCompletedAndOverdue() {
        FollowUp overdue = storedFollowUp(null, "client-1",
                LocalDate.now().minusDays(2), false);
        overdue.setId("fu-overdue");

        FollowUp done = storedFollowUp(null, "client-1",
                LocalDate.now().minusDays(2), true);
        done.setId("fu-done");

        FollowUp future = storedFollowUp(null, "client-1",
                LocalDate.now().plusDays(3), false);
        future.setId("fu-future");

        when(followUpRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(overdue, done, future));

        PagedResult<FollowUpResponse> openOnly =
                followUpService.list(null, null, null, null, "false", false,
                        1, 20);
        assertEquals(2, openOnly.total());

        PagedResult<FollowUpResponse> overdueOnly =
                followUpService.list(null, null, null, null, null, true,
                        1, 20);
        assertEquals(1, overdueOnly.total());
        assertEquals("fu-overdue", overdueOnly.items().get(0).id());
    }

    @Test
    void listRejectsInvalidCompletedFilter() {
        assertThrows(InvalidRequestException.class,
                () -> followUpService.list(
                        null, null, null, null, "maybe", false, 1, 20));
    }

    @Test
    void listByLeadRejectsUnknownLead() {
        when(leadRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> followUpService.listByLead("missing"));
    }

    @Test
    void deleteRejectsUnknownFollowUp() {
        when(followUpRepository.findById("missing"))
                .thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> followUpService.delete("missing"));
    }
}
