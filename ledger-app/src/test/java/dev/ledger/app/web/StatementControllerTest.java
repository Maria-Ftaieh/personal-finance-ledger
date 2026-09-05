package dev.ledger.app.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.ledger.app.config.DemoProperties;
import dev.ledger.app.config.MoneyJsonModule;
import dev.ledger.app.repo.StatementRepository;
import dev.ledger.app.service.StatementImportAppService;
import dev.ledger.app.service.StatementImportAppService.ImportOutcome;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * SPEC §7.2, the web layer with the service mocked: what this checks is the HTTP contract — which
 * status code each import outcome produces, and that the password is passed on rather than
 * reflected back.
 */
@WebMvcTest(StatementController.class)
@Import(MoneyJsonModule.class)
// DemoReadOnlyFilter is a servlet filter and so is part of this slice; it needs its
// properties. Unset here, which means not a demo, which is what these assertions expect.
@EnableConfigurationProperties(DemoProperties.class)
class StatementControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private StatementImportAppService importer;

  @MockitoBean private StatementRepository statements;

  private static MockMultipartFile upload() {
    return new MockMultipartFile(
        "file", "ekstre.pdf", "application/pdf", "%PDF-1.4 fake".getBytes());
  }

  @Test
  @DisplayName("a successful import is 201 with what was stored")
  void importedIsCreated() throws Exception {
    UUID statementId = UUID.randomUUID();
    when(importer.importFile(any(), eq("ekstre.pdf"), isNull()))
        .thenReturn(new ImportOutcome(ImportOutcome.Status.IMPORTED, statementId, 10, 7, null));

    mockMvc
        .perform(multipart("/api/statements").file(upload()))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.status").value("IMPORTED"))
        .andExpect(jsonPath("$.statementId").value(statementId.toString()))
        .andExpect(jsonPath("$.transactionsImported").value(10))
        .andExpect(jsonPath("$.suspectedDuplicates").value(7));
  }

  @Test
  @DisplayName("re-uploading the same file is 200, not an error")
  void alreadyImportedIsOk() throws Exception {
    when(importer.importFile(any(), any(), isNull()))
        .thenReturn(
            new ImportOutcome(
                ImportOutcome.Status.ALREADY_IMPORTED,
                UUID.randomUUID(),
                0,
                0,
                "already imported"));

    mockMvc
        .perform(multipart("/api/statements").file(upload()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ALREADY_IMPORTED"));
  }

  @Test
  @DisplayName("an encrypted file is 422 with a status the client can prompt on")
  void needsPasswordIsUnprocessable() throws Exception {
    when(importer.importFile(any(), any(), isNull()))
        .thenReturn(
            new ImportOutcome(
                ImportOutcome.Status.NEEDS_PASSWORD, null, 0, 0, "password protected"));

    mockMvc
        .perform(multipart("/api/statements").file(upload()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("NEEDS_PASSWORD"))
        .andExpect(jsonPath("$.statementId").doesNotExist());
  }

  @Test
  @DisplayName("an unreadable scan and an unknown bank are both 422, distinguished by status")
  void failureOutcomesAreUnprocessable() throws Exception {
    when(importer.importFile(any(), any(), isNull()))
        .thenReturn(
            new ImportOutcome(ImportOutcome.Status.UNREADABLE, null, 0, 0, "no text layer"));

    mockMvc
        .perform(multipart("/api/statements").file(upload()))
        .andExpect(status().isUnprocessableEntity())
        .andExpect(jsonPath("$.status").value("UNREADABLE"))
        .andExpect(jsonPath("$.detail").value("no text layer"));
  }

  @Test
  @DisplayName("the password reaches the parser and never comes back in the response")
  void passwordIsForwardedAndNotEchoed() throws Exception {
    when(importer.importFile(any(), any(), eq("hunter2")))
        .thenReturn(
            new ImportOutcome(ImportOutcome.Status.IMPORTED, UUID.randomUUID(), 3, 0, null));

    String body =
        mockMvc
            .perform(multipart("/api/statements").file(upload()).param("password", "hunter2"))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    verify(importer).importFile(any(), eq("ekstre.pdf"), eq("hunter2"));
    org.assertj.core.api.Assertions.assertThat(body).doesNotContain("hunter2");
  }

  @Test
  @DisplayName("a blank password is treated as no password, not as a wrong one")
  void blankPasswordBecomesNull() throws Exception {
    when(importer.importFile(any(), any(), isNull()))
        .thenReturn(
            new ImportOutcome(ImportOutcome.Status.IMPORTED, UUID.randomUUID(), 1, 0, null));

    mockMvc
        .perform(multipart("/api/statements").file(upload()).param("password", "   "))
        .andExpect(status().isCreated());

    verify(importer).importFile(any(), eq("ekstre.pdf"), isNull());
  }
}
