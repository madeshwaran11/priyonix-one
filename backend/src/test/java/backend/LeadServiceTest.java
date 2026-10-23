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
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LeadServiceTest {

    @Mock
    private LeadRepository leadRepository;

    @Mock
    private ClientRepository clientRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private FollowUpRepository followUpRepository;

    @Mock
    private CampaignRepository campaignRepository;

    @Mock
    private ClientService clientService;

    private LeadService leadService;

    @BeforeEach
    void setUp() {
        leadService = new LeadService(
                leadRepository, clientRepository, employeeRepository,
                followUpRepository, campaignRepository, clientService);
    }

    private LeadRequest request(String email, String status, String priority) {
        return new LeadRequest(
                "Priya Sharma", "Northwind Traders", email, "+91 90000 00000",
                "Website", status, priority, null, null, null, "Interested",
                null);
    }

    private Lead storedLead(String status) {
        Lead lead = new Lead();
        lead.setId("lead-doc-1");
        lead.setName("Priya Sharma");
        lead.setEmail("priya@northwind.com");
        lead.setStatus(status);
        lead.setPriority("MEDIUM");
        lead.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return lead;
    }

    private ClientResponse clientResponse(String id, String name) {
        Instant now = Instant.parse("2024-01-01T00:00:00Z");
        return new ClientResponse(id, name, "Northwind Ltd",
                "priya@northwind.com", null, null, null, "ACTIVE", null, null,
                null, "lead-doc-1", 0, 0, now, now);
    }

    // ----- create --------------------------------------------------------

    @Test
    void createRejectsUnknownAssignee() {
        when(employeeRepository.findById("ghost")).thenReturn(Optional.empty());

        assertThrows(InvalidRequestException.class,
                () -> leadService.create(
                        new LeadRequest("Priya", "Northwind", "p@acme.com", null,
                                "Website", "NEW", "MEDIUM", "ghost", null, null,
                                null, null)));

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void createRejectsInvalidStatus() {
        assertThrows(InvalidRequestException.class,
                () -> leadService.create(
                        request("p@acme.com", "NOT_A_STATUS", "MEDIUM")));

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void createRejectsInvalidPriority() {
        assertThrows(InvalidRequestException.class,
                () -> leadService.create(
                        request("p@acme.com", "NEW", "URGENT")));

        verify(leadRepository, never()).save(any(Lead.class));
    }

    @Test
    void createNormalizesEmailAndDefaults() {
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> {
                    Lead lead = invocation.getArgument(0);
                    lead.setId("lead-new");
                    return lead;
                });

        leadService.create(request("  Priya@Northwind.com ", null, null));

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        Lead saved = captor.getValue();

        assertEquals("priya@northwind.com", saved.getEmail());
        assertEquals("NEW", saved.getStatus());
        assertEquals("MEDIUM", saved.getPriority());
        assertNotNull(saved.getCreatedAt());
        assertNotNull(saved.getUpdatedAt());
    }

    // ----- conversion ----------------------------------------------------

    @Test
    void convertRejectsNonWonLead() {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(storedLead("CONTACTED")));

        assertThrows(InvalidRequestException.class,
                () -> leadService.convert("lead-doc-1"));

        verify(clientRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void convertRejectsAlreadyConvertedLead() {
        Lead lead = storedLead("WON");
        lead.setConvertedClientId("client-1");
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(lead));

        assertThrows(ConflictException.class,
                () -> leadService.convert("lead-doc-1"));

        verify(clientService, never()).createFromLead(any());
    }

    @Test
    void convertCreatesClientWhenNoneExists() {
        Lead lead = storedLead("WON");
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(lead));
        when(clientRepository.findByEmailIgnoreCase("priya@northwind.com"))
                .thenReturn(Optional.empty());
        when(clientService.createFromLead(lead))
                .thenReturn(clientResponse("client-1", "Priya Sharma"));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeadConversionResponse conversion = leadService.convert("lead-doc-1");

        assertTrue(conversion.created());
        assertEquals("client-1", conversion.client().id());
        assertEquals("client-1", conversion.lead().convertedClientId());

        ArgumentCaptor<Lead> captor = ArgumentCaptor.forClass(Lead.class);
        verify(leadRepository).save(captor.capture());
        assertNotNull(captor.getValue().getConvertedAt());
    }

    @Test
    void convertReusesExistingClientByEmail() {
        Lead lead = storedLead("WON");
        Client existing = new Client();
        existing.setId("client-existing");
        existing.setName("Priya Sharma");

        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(lead));
        when(clientRepository.findByEmailIgnoreCase("priya@northwind.com"))
                .thenReturn(Optional.of(existing));
        when(clientService.toResponse(existing))
                .thenReturn(clientResponse("client-existing", "Priya Sharma"));
        when(leadRepository.save(any(Lead.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LeadConversionResponse conversion = leadService.convert("lead-doc-1");

        assertFalse(conversion.created());
        assertEquals("client-existing", conversion.client().id());
        verify(clientService, never()).createFromLead(any());
    }

    // ----- delete --------------------------------------------------------

    @Test
    void deleteRejectsLeadWithFollowUpsWithoutCascade() {
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(storedLead("NEW")));
        when(followUpRepository.findByLeadId("lead-doc-1"))
                .thenReturn(List.of(new FollowUp()));

        assertThrows(ConflictException.class,
                () -> leadService.delete("lead-doc-1", false));

        verify(leadRepository, never()).delete(any(Lead.class));
    }

    @Test
    void deleteCascadesFollowUpsWhenRequested() {
        Lead lead = storedLead("NEW");
        when(leadRepository.findById("lead-doc-1"))
                .thenReturn(Optional.of(lead));
        when(followUpRepository.findByLeadId("lead-doc-1"))
                .thenReturn(List.of(new FollowUp()));

        leadService.delete("lead-doc-1", true);

        verify(followUpRepository).deleteByLeadId("lead-doc-1");
        verify(leadRepository).delete(lead);
    }

    // ----- list ----------------------------------------------------------

    @Test
    void listFiltersByStatusAndPriority() {
        Lead won = storedLead("WON");
        Lead lost = storedLead("LOST");
        lost.setId("lead-doc-2");
        lost.setPriority("HIGH");

        when(leadRepository.findAllByOrderByCreatedAtDesc())
                .thenReturn(List.of(won, lost));

        PagedResult<LeadResponse> byStatus =
                leadService.list(null, "WON", null, null, null, 1, 20);
        assertEquals(1, byStatus.total());
        assertEquals("WON", byStatus.items().get(0).status());

        PagedResult<LeadResponse> byPriority =
                leadService.list(null, null, "HIGH", null, null, 1, 20);
        assertEquals(1, byPriority.total());
        assertEquals("lead-doc-2", byPriority.items().get(0).id());
    }
}
