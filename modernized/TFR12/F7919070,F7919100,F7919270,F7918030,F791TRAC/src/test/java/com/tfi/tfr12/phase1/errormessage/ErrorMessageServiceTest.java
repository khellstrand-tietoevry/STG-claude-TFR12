package com.tfi.tfr12.phase1.errormessage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins the behavior of legacy/STG/TFR12/src/F7918030.src ("Interface for
 * henting av korrekt feilmelding" - looks up localized error message text for a
 * message code, reading table PO_Feilmelding via embedded SQL in section
 * D310-SQL). The file is ISO-8859-1/Latin-1, not UTF-8; columns 8-72 are the
 * code area, column 7 '*' marks a comment.
 *
 * <p>This module could not be compiled/run: it requires a DB2 precompiler that
 * is not available in this environment. Every expected value below was
 * computed by hand-tracing F7918030.src's control flow (D000-Hent-Feilmelding,
 * D100/D200/D300-Les-Feilmelding, D310-SQL, E000/E100-Bytt-Ut-Variabler), not
 * observed from a running program.
 *
 * <p>Scope note: the legacy module supports a two-tier lookup source (an
 * in-memory copybook mirror tried first, DB2 as fallback - F7918030.src
 * D100-Les-Feilmelding). This contract collapses that into a single injected
 * {@link MessageRepository} port; the tests below pin the ORGANIZATION/LANGUAGE
 * fallback-key search order (identical against either source in the legacy
 * code) and the message-assembly/variable-substitution logic, which are pure
 * and source-independent. They do not attempt to characterize the copy-vs-DB2
 * tier selection itself, nor the CICS/IMS detection performed by the external
 * F7919220 (represented here as a plain {@code cicsEnvironment} boolean).
 *
 * <p>No credential-like values appear in this module's source; the only
 * "sensitive-looking" literal is a message-code column value, which is not
 * secret. Implemented by {@link ErrorMessageServiceImpl}; since the legacy
 * module could not run locally, these tests remain the only proof of
 * equivalence for it.
 */
class ErrorMessageServiceTest {

    private final ErrorMessageService service = new ErrorMessageServiceImpl();

    /** Simple in-memory stand-in for either lookup tier (copy-area or DB2). */
    private static final class FakeMessageRepository implements MessageRepository {
        private final Map<MessageKey, MessageRow> rows = new HashMap<>();

        FakeMessageRepository with(long orgId, String messageCode, String language, String text) {
            MessageKey key = new MessageKey(orgId, messageCode, language);
            rows.put(key, new MessageRow(key, text));
            return this;
        }

        @Override
        public Optional<MessageRow> find(MessageKey key) {
            return Optional.ofNullable(rows.get(key));
        }
    }

    private static final class FailingMessageRepository implements MessageRepository {
        @Override
        public Optional<MessageRow> find(MessageKey key) {
            throw new MessageRepositoryFailureException("F7918030 : Feil ved les av tabell PO_Feilmelding, SQLcode: -911");
        }
    }

    private static ErrorMessageRequest request(String code, String language, long orgId) {
        return new ErrorMessageRequest(code, language, orgId, List.of(), false, null);
    }

    // =====================================================================
    // Preformatted bypass
    // =====================================================================
    @Test
    @DisplayName("a preformatted message is used verbatim, with no table lookup at all")
    void lookupMessage_preformattedMessageBypassesLookupEntirely() {
        ErrorMessageRequest req = new ErrorMessageRequest(
                "ERR001", "EN", 42L, List.of(), true, "Already-formatted text.");
        // Deliberately empty repository: if the implementation performed a
        // lookup anyway it would get nothing back and this assertion would fail.
        MessageRepository emptyRepository = new FakeMessageRepository();

        ErrorMessageResult result = service.lookupMessage(req, emptyRepository, false);

        assertEquals("Already-formatted text.", result.messageText());
    }

    // =====================================================================
    // Fallback key search order (F7918030.src D200-Les-Copy / D300-Les-DB2)
    // =====================================================================
    @Nested
    @DisplayName("organization/language fallback search order")
    class FallbackOrderTests {

        @Test
        @DisplayName("finds an exact (organization, language) match on the first attempt")
        void lookupMessage_exactMatch() {
            MessageRepository repo = new FakeMessageRepository()
                    .with(42L, "ERR001", "EN", "Balance too low.");

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "EN", 42L), repo, false);

            assertEquals("Balance too low.", result.messageText());
        }

        @Test
        @DisplayName("falls back to organization 0 with the same language when the exact organization has no row")
        void lookupMessage_fallsBackToDefaultOrganization() {
            MessageRepository repo = new FakeMessageRepository()
                    .with(0L, "ERR001", "EN", "Generic balance error.");

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "EN", 42L), repo, false);

            assertEquals("Generic balance error.", result.messageText());
        }

        @Test
        @DisplayName("falls back to language NO with the ORIGINAL organization when organization 0 also has no row")
        void lookupMessage_fallsBackToDefaultLanguageKeepingOriginalOrganization() {
            MessageRepository repo = new FakeMessageRepository()
                    .with(42L, "ERR001", "NO", "Saldo for lav.");

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "EN", 42L), repo, false);

            assertEquals("Saldo for lav.", result.messageText());
        }

        @Test
        @DisplayName("last resort: falls back to organization 0 AND language NO together")
        void lookupMessage_fallsBackToDefaultOrganizationAndLanguage() {
            MessageRepository repo = new FakeMessageRepository()
                    .with(0L, "ERR001", "NO", "Generisk saldo-feil.");

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "EN", 42L), repo, false);

            assertEquals("Generisk saldo-feil.", result.messageText());
        }

        @Test
        @DisplayName("a blank requested language is treated as NO from the very first attempt")
        void lookupMessage_blankLanguageDefaultsToNo() {
            MessageRepository repo = new FakeMessageRepository()
                    .with(42L, "ERR001", "NO", "Saldo for lav.");

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "", 42L), repo, false);

            assertEquals("Saldo for lav.", result.messageText());
        }
    }

    // =====================================================================
    // Not-found / repository-failure fallback text
    // =====================================================================
    @Nested
    @DisplayName("not-found and repository-failure fallback text")
    class FallbackTextTests {

        @Test
        @DisplayName("not found in batch (non-CICS) yields the comma-variant generic text")
        void lookupMessage_notFoundInBatchYieldsCommaVariant() {
            MessageRepository repo = new FakeMessageRepository(); // nothing configured

            ErrorMessageResult result = service.lookupMessage(
                    request("NO-SUCH-CODE", "EN", 42L), repo, false);

            assertEquals(ErrorMessageService.NOT_FOUND_TEXT_BATCH, result.messageText());
        }

        @Test
        @DisplayName("not found under CICS yields the period-variant generic text")
        void lookupMessage_notFoundUnderCicsYieldsPeriodVariant() {
            MessageRepository repo = new FakeMessageRepository();

            ErrorMessageResult result = service.lookupMessage(
                    request("NO-SUCH-CODE", "EN", 42L), repo, true);

            assertEquals(ErrorMessageService.NOT_FOUND_TEXT_CICS, result.messageText());
        }

        @Test
        @DisplayName("QUIRK: a repository failure ALWAYS yields the comma (batch) variant, even under CICS "
                + "- there is no CICS-specific text for this branch in the legacy source")
        void lookupMessage_repositoryFailureAlwaysYieldsCommaVariantEvenUnderCics() {
            MessageRepository repo = new FailingMessageRepository();

            ErrorMessageResult result = service.lookupMessage(
                    request("ERR001", "EN", 42L), repo, true);

            assertEquals(ErrorMessageService.NOT_FOUND_TEXT_BATCH, result.messageText());
        }
    }

    // =====================================================================
    // Symbolic-variable substitution (F7918030.src E100-Bytt-Ut-Variabler)
    // =====================================================================
    @Nested
    @DisplayName("symbolic variable substitution")
    class VariableSubstitutionTests {

        @Test
        @DisplayName("replaces a single resolvable &key# placeholder with its variable's value")
        void substituteVariables_resolvesSingleKey() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Balance &amount# is below minimum.",
                    List.of(new MessageVariable("amount", "100.00")));

            assertEquals("Balance 100.00 is below minimum.", result.text());
            assertFalse(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("replaces multiple distinct &key# placeholders")
        void substituteVariables_resolvesMultipleKeys() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Transfer from &from# to &to# failed.",
                    List.of(new MessageVariable("from", "ACC-1"), new MessageVariable("to", "ACC-2")));

            assertEquals("Transfer from ACC-1 to ACC-2 failed.", result.text());
            assertFalse(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("an unresolved key (not present in the variable table) is left as literal &key#, and flagged")
        void substituteVariables_unresolvedKeyLeftLiteralAndFlagged() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Unknown &missing# reference.",
                    List.of());

            assertEquals("Unknown &missing# reference.", result.text());
            assertTrue(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("a matching key whose value is entirely blank is treated the same as unresolved")
        void substituteVariables_blankVariableValueTreatedAsUnresolved() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Value: &v#.",
                    List.of(new MessageVariable("v", "   ")));

            assertEquals("Value: &v#.", result.text());
            assertTrue(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("trailing spaces in a variable's value are trimmed before substitution")
        void substituteVariables_trimsTrailingSpacesFromValue() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Name: &n#.",
                    List.of(new MessageVariable("n", "Ada    ")));

            assertEquals("Name: Ada.", result.text());
            assertFalse(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("a template with no placeholders at all passes through unchanged")
        void substituteVariables_noPlaceholdersPassesThrough() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Plain text, no variables here.",
                    List.of(new MessageVariable("unused", "x")));

            assertEquals("Plain text, no variables here.", result.text());
            assertFalse(result.hadUnresolvedVariable());
        }

        @Test
        @DisplayName("an unterminated '&' (no closing '#' before end of template) is emitted literally")
        void substituteVariables_unterminatedAmpersandEmittedLiterally() {
            VariableSubstitutionResult result = service.substituteVariables(
                    "Trailing ampersand &",
                    List.of());

            assertEquals("Trailing ampersand &", result.text());
        }
    }
}
