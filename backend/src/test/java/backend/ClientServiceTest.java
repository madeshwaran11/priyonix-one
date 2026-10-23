package backend;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
class ClientServiceTest {

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    private ClientService clientService;

    @BeforeEach
    void setUp() {
        clientService = new ClientService(
                clientRepository, employeeRepository, followUpRepository);
    }

    private Employee employee(String id, String employeeId, String name) {
        Employee employee = new Employee();
        employee.setId(id);
        employee.setEmployeeId(employeeId);
        employee.setFullName(name);
        employee.setWorkEmail(employeeId.toLowerCase() + "@acme.com");
        return employee;
    }

    private ClientRequest request(String email, String ownerId, String status) {
        return new ClientRequest(
                "Northwind Traders", "Northwind Ltd", email, "+91 90000 00000",
                "12 Market Street", "Retail", status, ownerId, "Key account");
    }

    private Client storedClient() {
        Client client = new Client();
        client.setId("client-doc-1");
        client.setName("Northwind Traders");
        client.setEmail("hello@northwind.com");
        client.setStatus("ACTIVE");
        client.setAssignedToId("emp-1");
        client.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return client;
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRejectsDuplicateEmail() {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> clientService.create(
                        request("hello@northwind.com", null, null)));

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void createRejectsUnknownOwner() {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(false);
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> clientService.create(
                        request("hello@northwind.com", "ghost", null)));

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void createRejectsInvalidStatus() {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(false);

        assertThrows(InvalidRequestException.class,
                () -> clientService.create(
                        request("hello@northwind.com", null, "NOPE")));

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void createNormalizesEmailAndDefaults() {
        when(clientRepository.existsByEmailIgnoreCase("hello@northwind.com"))
                .thenReturn(false);
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> {
                    Client client = invocation.getArgument(0);
                    client.setId("client-new");
                    return client;
                });

        clientService.create(request("  Hello@Northwind.com ", null, null));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());
        Client saved = captor.getValue();

        assertEquals("hello@northwind.com", saved.getEmail());
        assertEquals("ACTIVE", saved.getStatus());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    // ----- update --------------------------------------------------------

    @Test
    void updateRejectsDuplicateEmailWhenChanged() {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(storedClient()));
        when(clientRepository.existsByEmailIgnoreCase("other@acme.com"))
                .thenReturn(true);

        assertThrows(DuplicateResourceException.class,
                () -> clientService.update("client-doc-1",
                        request("other@acme.com", null, null)));

        verify(clientRepository, never()).save(any(Client.class));
    }

    @Test
    void updateKeepsCreatedAtAndSetsUpdatedAt() {
        Client existing = storedClient();
        existing.setUpdatedAt(Instant.parse("2024-01-01T00:00:00Z"));

        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(existing));
        when(clientRepository.save(any(Client.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        clientService.update("client-doc-1",
                request("hello@northwind.com", null, null));

        ArgumentCaptor<Client> captor = ArgumentCaptor.forClass(Client.class);
        verify(clientRepository).save(captor.capture());

        assertEquals(Instant.parse("2024-01-01T00:00:00Z"),
                captor.getValue().getCreatedAt());
        assertTrue(captor.getValue().getUpdatedAt()
                .isAfter(Instant.parse("2024-01-01T00:00:00Z")));
    }

    // ----- delete --------------------------------------------------------

    @Test
    void deleteRejectsClientWithFollowUpsWithoutCascade() {
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(storedClient()));

        FollowUp followUp = new FollowUp();
        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of(followUp));

        assertThrows(ConflictException.class,
                () -> clientService.delete("client-doc-1", false));

        verify(clientRepository, never()).delete(any(Client.class));
    }

    @Test
    void deleteCascadesFollowUpsWhenRequested() {
        Client client = storedClient();
        when(clientRepository.findById("client-doc-1"))
                .thenReturn(Optional.of(client));
        when(followUpRepository.findByClientId("client-doc-1"))
                .thenReturn(List.of(new FollowUp()));

        clientService.delete("client-doc-1", true);

        verify(followUpRepository).deleteByClientId("client-doc-1");
        verify(clientRepository).delete(client);
    }

    @Test
    void deleteRejectsUnknownClient() {
        when(clientRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> clientService.delete("missing", false));
    }

    // ----- list ----------------------------------------------------------

    @Test
    void listFiltersByStatusAndSearch() {
        Client active = storedClient();
        Client prospect = storedClient();
        prospect.setId("client-doc-2");
        prospect.setName("Contoso");
        prospect.setEmail("hi@contoso.com");
        prospect.setStatus("PROSPECT");

        when(clientRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(active, prospect));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        PagedResult<ClientResponse> byStatus =
                clientService.list(null, "PROSPECT", 1, 20);
        assertEquals(1, byStatus.total());
        assertEquals("Contoso", byStatus.items().get(0).name());

        PagedResult<ClientResponse> bySearch =
                clientService.list("northwind", null, 1, 20);
        assertEquals(1, bySearch.total());
        assertEquals("Northwind Traders", bySearch.items().get(0).name());
    }

    @Test
    void listComputesFollowUpCounts() {
        when(clientRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(storedClient()));
        when(employeeRepository.findAllById(any()))
                .thenReturn(List.of(employee("emp-1", "EMP-1", "Asha")));

        FollowUp overdue = new FollowUp();
        overdue.setClientId("client-doc-1");
        overdue.setDueDate(LocalDate.now().minusDays(2));
        overdue.setCompleted(false);

        FollowUp future = new FollowUp();
        future.setClientId("client-doc-1");
        future.setDueDate(LocalDate.now().plusDays(2));

        when(followUpRepository.findByClientIdIn(any()))
                .thenReturn(List.of(overdue, future));

        PagedResult<ClientResponse> result =
                clientService.list(null, null, 1, 20);

        assertEquals(2, result.items().get(0).followUpCount());
        assertEquals(1, result.items().get(0).overdueFollowUpCount());
    }
}
