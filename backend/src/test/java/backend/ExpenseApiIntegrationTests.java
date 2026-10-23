package backend;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * End-to-end tests for the Expenses API through the real Spring Security
 * filter chain: JWT authentication, the ADMIN-only status decision (approve /
 * reject / reimburse) and DELETE, and validation.
 */
@SpringBootTest(properties = {
        "spring.mongodb.uri=mongodb://127.0.0.1:27017/priyonix-test",
        "JWT_SECRET=dGVzdC1zZWNyZXQtZm9yLXVuaXQtYnVpbGRzLW9ubHktMzItYnl0ZXM=",
        "ADMIN_EMAIL=",
        "ADMIN_PASSWORD="
})
@AutoConfigureMockMvc
class ExpenseApiIntegrationTests extends PlatformMockDatabaseTestSupport {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockitoBean
    private ExpenseRepository expenseRepository;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean(answers = Answers.RETURNS_DEEP_STUBS)
    private MongoDatabaseFactory mongoDatabaseFactory;

    // ----- fixtures ------------------------------------------------------

    private User enabledUser(String role) {
        User user = new User();
        user.setEmail("pm@example.com");
        user.setPassword("{noop}unused");
        user.setRole(role);
        user.setEnabled(true);
        return user;
    }

    private String tokenFor(String role) {
        when(userRepository.findByEmailIgnoreCase("pm@example.com"))
                .thenReturn(Optional.of(enabledUser(role)));
        return jwtService.generateToken("pm@example.com", role);
    }

    private Expense sampleExpense(String status) {
        Expense expense = new Expense();
        expense.setId("exp-1");
        expense.setTitle("Flights to Delhi");
        expense.setCategory("TRAVEL");
        expense.setAmount(new BigDecimal("1250.50"));
        expense.setExpenseDate(LocalDate.now().minusDays(1));
        expense.setVendor("Acme Travel");
        expense.setPaymentMethod("CARD");
        expense.setStatus(status);
        expense.setCreatedAt(Instant.parse("2024-01-01T00:00:00Z"));
        return expense;
    }

    private String expenseJson(String category) {
        return "{"
                + "\"title\":\"Flights to Delhi\","
                + "\"category\":\"" + category + "\","
                + "\"amount\":1250.50,"
                + "\"expenseDate\":\"2026-01-20\","
                + "\"vendor\":\"Acme Travel\","
                + "\"paymentMethod\":\"CARD\","
                + "\"receiptReference\":\"RCPT-1\","
                + "\"notes\":\"Client visit\""
                + "}";
    }

    // ----- authentication ------------------------------------------------

    @Test
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/expenses"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createRequiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("TRAVEL")))
                .andExpect(status().isUnauthorized());
    }

    // ----- create --------------------------------------------------------

    @Test
    void createReturnsPendingExpense() throws Exception {
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> {
                    Expense expense = invocation.getArgument(0);
                    expense.setId("exp-new");
                    return expense;
                });

        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("TRAVEL")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.expense.id").value("exp-new"))
                .andExpect(jsonPath("$.expense.status").value("PENDING"))
                .andExpect(jsonPath("$.expense.category").value("TRAVEL"));
    }

    @Test
    void createRejectsInvalidCategory() throws Exception {
        mockMvc.perform(post("/api/expenses")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(expenseJson("SPACESHIP")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }

    // ----- status decisions + role checks -------------------------------

    @Test
    void updateStatusForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(patch("/api/expenses/exp-1/status")
                        .header("Authorization", "Bearer " + tokenFor("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStatusApprovesForAdmin() throws Exception {
        when(expenseRepository.findById("exp-1"))
                .thenReturn(Optional.of(sampleExpense("PENDING")));
        when(expenseRepository.save(any(Expense.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        mockMvc.perform(patch("/api/expenses/exp-1/status")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"APPROVED\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.expense.status").value("APPROVED"))
                .andExpect(jsonPath("$.expense.approvedBy")
                        .value("pm@example.com"));
    }

    @Test
    void deleteForbiddenForNonAdmin() throws Exception {
        mockMvc.perform(delete("/api/expenses/exp-1")
                        .header("Authorization", "Bearer " + tokenFor("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    void deleteAllowedForAdmin() throws Exception {
        when(expenseRepository.findById("exp-1"))
                .thenReturn(Optional.of(sampleExpense("PENDING")));

        mockMvc.perform(delete("/api/expenses/exp-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"));

        verify(expenseRepository).delete(any(Expense.class));
    }

    @Test
    void deleteApprovedExpenseIsRejectedEvenForAdmin() throws Exception {
        when(expenseRepository.findById("exp-1"))
                .thenReturn(Optional.of(sampleExpense("APPROVED")));

        mockMvc.perform(delete("/api/expenses/exp-1")
                        .header("Authorization", "Bearer " + tokenFor("ADMIN")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value("ERROR"));

        verify(expenseRepository, org.mockito.Mockito.never()).delete(any(Expense.class));
    }
}
