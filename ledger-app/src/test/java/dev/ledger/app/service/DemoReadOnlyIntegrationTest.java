package dev.ledger.app.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ledger.app.PostgresIntegrationTest;
import dev.ledger.app.repo.BudgetRepository;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.repo.TransactionRepository;
import dev.ledger.imports.Fixtures;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The public demo must be safe to leave on the internet with no authentication.
 *
 * <p>Two separate promises, and both are tested here because both are security properties rather
 * than conveniences:
 *
 * <ul>
 *   <li>nothing can be changed by a visitor, so the demo does not drift and cannot be vandalised;
 *   <li>a statement someone uploads is read and then discarded. Somebody will eventually drop a
 *       real bank statement onto a public URL, and if that persisted it would be visible to every
 *       other visitor — a privacy leak, not merely an untidy demo.
 * </ul>
 */
@AutoConfigureMockMvc
@TestPropertySource(properties = "ledger.demo.read-only=true")
class DemoReadOnlyIntegrationTest extends PostgresIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private StatementRepository statements;
  @Autowired private TransactionRepository transactions;
  @Autowired private BudgetRepository budgets;

  @Test
  @DisplayName("an uploaded statement is parsed honestly and then not stored")
  void uploadsAreParsedButNeverPersisted() throws Exception {
    long statementsBefore = statements.count();
    long transactionsBefore = transactions.count();

    mockMvc
        .perform(
            multipart("/api/statements")
                .file(
                    new MockMultipartFile(
                        "file",
                        "garanti-2026-01.pdf",
                        "application/pdf",
                        Fixtures.bytes("garanti-2026-01.pdf"))))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("PARSED_NOT_STORED"))
        // Honest about what it read, so the importer is still demonstrable...
        .andExpect(jsonPath("$.transactionsImported").value(10))
        // ...and explicit that it kept none of it.
        .andExpect(jsonPath("$.statementId").doesNotExist());

    assertThat(statements.count()).isEqualTo(statementsBefore);
    assertThat(transactions.count()).isEqualTo(transactionsBefore);
  }

  @Test
  @DisplayName("every other write is refused, whatever the endpoint")
  void writesAreRefused() throws Exception {
    UUID any = UUID.randomUUID();
    // One container is shared by the whole suite, so other tests leave rows behind. What
    // matters is that none of the requests below adds any.
    long budgetsBefore = budgets.count();

    mockMvc
        .perform(
            post("/api/rules")
                .contentType("application/json")
                .content(
                    "{\"priority\":1,\"matchType\":\"CONTAINS\",\"pattern\":\"X\",\"categoryId\":\"yemek\"}"))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("read only")));

    mockMvc.perform(delete("/api/rules/" + any)).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/rules/reevaluate")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/budgets/yemek")
                .contentType("application/json")
                .content("{\"amount\":\"1.00\",\"currency\":\"TRY\"}"))
        .andExpect(status().isForbidden());
    mockMvc.perform(delete("/api/budgets/yemek")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/duplicates/" + any + "/confirm")).andExpect(status().isForbidden());
    mockMvc.perform(post("/api/duplicates/" + any + "/reject")).andExpect(status().isForbidden());
    mockMvc
        .perform(
            put("/api/transactions/" + any + "/category")
                .contentType("application/json")
                .content("{\"categoryId\":\"yemek\"}"))
        .andExpect(status().isForbidden());
    mockMvc
        .perform(delete("/api/transactions/" + any + "/category"))
        .andExpect(status().isForbidden());
    // Refreshing the price index reaches out to TCMB and writes; a visitor cannot do either.
    mockMvc.perform(post("/api/cpi/refresh")).andExpect(status().isForbidden());

    assertThat(budgets.count()).isEqualTo(budgetsBefore);
  }

  @Test
  @DisplayName("reading is untouched, so the demo is still a demo")
  void readsStillWork() throws Exception {
    mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
    mockMvc.perform(get("/api/transactions")).andExpect(status().isOk());
    mockMvc.perform(get("/api/rules")).andExpect(status().isOk());
    mockMvc.perform(get("/api/rules/preview")).andExpect(status().isOk());
    mockMvc.perform(get("/api/duplicates")).andExpect(status().isOk());
    mockMvc.perform(get("/api/budgets")).andExpect(status().isOk());
    mockMvc.perform(get("/api/alerts")).andExpect(status().isOk());
    mockMvc.perform(get("/api/cpi")).andExpect(status().isOk());
    mockMvc.perform(get("/api/reports/months")).andExpect(status().isOk());
    mockMvc.perform(get("/api/reports/monthly?month=2026-01")).andExpect(status().isOk());
  }
}
