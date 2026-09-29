package com.tfi.tfr12.phase2.dbi.contractamount;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins RULE-018 (mandatory composite natural key for TF_Contract_Amount)
 * as literally coded in legacy/TFR12/src/F115ICA0.src's H100-Check-Qualification
 * section (lines 806-894), reached via {@link ContractAmountKeyPolicy}. The
 * file is ISO-8859-1/Latin-1, not UTF-8; column 7 '*' marks a comment, columns
 * 8-72 are the code area. F115ICA0 requires an embedded-SQL DB2 precompiler
 * this environment does not have, so it could not be compiled or run; every
 * expected value below was computed by hand-tracing the control flow, not
 * observed from a running program.
 *
 * <p>Per {@link ContractAmountKeyPolicy}'s own javadoc: H100-Check-Qualification
 * is called ONLY from H000-Update in the real F115ICA0 (F115ICA0.src:796) —
 * F000-Select and I000-Delete route through F100-Prepare-Qualification instead,
 * which performs no such check. The generic {@link DbiEngine} this table plugs
 * into nonetheless runs {@link ContractAmountKeyPolicy} before Select, Update
 * AND Delete uniformly (a deliberate modernization decision, not a literal
 * translation of F115ICA0's Select/Delete paths) — see
 * {@link DbiEngineWiringTests#rule018_selectRejectsIncompleteKey_widerThanLegacySelect()}
 * for the test that pins exactly this widening, so it is visible before the
 * pattern is replicated to the other 8 F115I*0 tables.
 *
 * <p>{@link DbiEngine} itself (select/update/delete key-check wiring,
 * capability checks, insert bypassing the key check) is already covered
 * generically once elsewhere; the wiring tests below only prove that THIS
 * table's key policy and capabilities plug into it correctly, they do not
 * re-test the engine's own logic.
 *
 * <p>No credential-like values appear in F115ICA0.src; nothing here needed
 * substitution.
 */
class ContractAmountServiceTest {

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** Simple Map-backed stand-in for the real (not-yet-built) DB2 access. */
    private static final class FakeContractAmountRepository
            implements KeyedRepository<ContractAmountKey, ContractAmountRow> {
        private final Map<ContractAmountKey, ContractAmountRow> rows = new HashMap<>();
        int selectOneCalls = 0;
        int getRangeCalls = 0;
        int insertCalls = 0;
        int updateCalls = 0;
        int deleteCalls = 0;

        FakeContractAmountRepository with(ContractAmountKey key, ContractAmountRow row) {
            rows.put(key, row);
            return this;
        }

        @Override
        public Optional<ContractAmountRow> selectOne(ContractAmountKey key) {
            selectOneCalls++;
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<ContractAmountRow> getRange(ContractAmountKey partialKey) {
            getRangeCalls++;
            return new ArrayList<>(rows.values());
        }

        @Override
        public void insert(ContractAmountRow record) {
            insertCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void update(ContractAmountRow record) {
            updateCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void delete(ContractAmountKey key) {
            deleteCalls++;
            rows.remove(key);
        }

        private static ContractAmountKey keyOf(ContractAmountRow r) {
            return new ContractAmountKey(r.finInstId(), r.contractType(), r.contractNo(), r.currency());
        }
    }

    /** Configurable stand-in for F5879010 (via N100-Fetch-Fin-Inst-ID). */
    private static final class FakeFinInstResolver implements FinInstResolver {
        private final Map<String, Long> resolvable = new HashMap<>();
        int callCount = 0;

        FakeFinInstResolver knowing(String finInstNo, long finInstId) {
            resolvable.put(finInstNo, finInstId);
            return this;
        }

        @Override
        public long resolveFinInstId(String finInstNo) {
            callCount++;
            Long id = resolvable.get(finInstNo);
            if (id == null) {
                throw new MissingKeyFieldException(ContractAmountKeyPolicy.FIN_INST_MISSING);
            }
            return id;
        }
    }

    private static ContractAmountRow validRow() {
        return new ContractAmountRow(
                1001L, "", "ILC", 4500001L, "USD",
                new BigDecimal("100000.000"), new BigDecimal("75000.000"),
                new BigDecimal("100000.000"), 5, 10);
    }

    // =====================================================================
    // RULE-018: ContractAmountKeyPolicy, exercised directly
    // =====================================================================
    @Nested
    @DisplayName("RULE-018: TF_Contract_Amount mandatory composite key (H100-Check-Qualification)")
    class KeyPolicyTests {

        private final ContractAmountKeyPolicy policy = new ContractAmountKeyPolicy();
        private final FinInstResolver noResolutionNeeded = finInstNo -> {
            throw new AssertionError("resolver should not be called when Fin-Inst-ID is already present");
        };

        @Test
        @DisplayName("rule018_completeKey_passesValidation")
        void rule018_completeKey_passesValidation() {
            policy.resolveKey(validRow(), noResolutionNeeded);
            // no exception -> validation passed (F115ICA0.src:808-892, all four checks fall through)
        }

        @Test
        @DisplayName("rule018_finInstIdPresent_skipsResolverEntirely")
        void rule018_finInstIdPresent_skipsResolverEntirely() {
            // F115ICA0.src:809-810 - "When R115ICA0-Fin-Inst-ID Not = +0 -> Continue"
            policy.resolveKey(validRow(), noResolutionNeeded);
        }

        @Test
        @DisplayName("rule018_finInstIdZero_resolvesFromFinInstNo")
        void rule018_finInstIdZero_resolvesFromFinInstNo() {
            // F115ICA0.src:812-816
            ContractAmountRow row = new ContractAmountRow(
                    0L, "00123456789012345", "ILC", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            FakeFinInstResolver resolver = new FakeFinInstResolver().knowing("00123456789012345", 1001L);

            policy.resolveKey(row, resolver);

            assertEquals(1, resolver.callCount, "resolver must be invoked exactly once when Fin-Inst-ID is 0");
        }

        @Test
        @DisplayName("rule018_finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing")
        void rule018_finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing() {
            // F115ICA0.src:829-836 - "When other" branch: neither field usable
            ContractAmountRow row = new ContractAmountRow(
                    0L, "", "ILC", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(ContractAmountKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_finInstNoUnresolvable_propagatesFinInstMissingFromResolver")
        void rule018_finInstNoUnresolvable_propagatesFinInstMissingFromResolver() {
            ContractAmountRow row = new ContractAmountRow(
                    0L, "NO-SUCH-INST", "ILC", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            FakeFinInstResolver resolver = new FakeFinInstResolver(); // knows nothing

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, resolver));

            assertEquals(ContractAmountKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_contractTypeBlank_throwsContractMissing")
        void rule018_contractTypeBlank_throwsContractMissing() {
            // F115ICA0.src:848-854
            ContractAmountRow row = new ContractAmountRow(
                    1001L, "", "", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(ContractAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_contractNoZero_throwsContractMissing")
        void rule018_contractNoZero_throwsContractMissing() {
            // F115ICA0.src:867-873 - same mnemonic as a missing Contract-Type
            ContractAmountRow row = new ContractAmountRow(
                    1001L, "", "ILC", 0L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(ContractAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_currencyBlank_throwsCurrencyMissing")
        void rule018_currencyBlank_throwsCurrencyMissing() {
            // F115ICA0.src:884-890
            ContractAmountRow row = new ContractAmountRow(
                    1001L, "", "ILC", 4500001L, "",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(ContractAmountKeyPolicy.CURRENCY_MISSING, ex.legacyErrorCode());
        }

        // -----------------------------------------------------------------
        // "Last failure wins" - F115ICA0.src runs all four Evaluate blocks
        // unconditionally in sequence (no early exit); the one visible error
        // is whichever check failed LAST, not the first.
        // -----------------------------------------------------------------
        @Nested
        @DisplayName("QUIRK: with more than one field missing, the LAST failing check wins, not the first "
                + "(F115ICA0.src:808-892 has no early exit between its 4 Evaluate blocks)")
        class LastFailureWinsTests {

            @Test
            @DisplayName("rule018_finInstAndCurrencyBothMissing_currencyMissingWins")
            void rule018_finInstAndCurrencyBothMissing_currencyMissingWins() {
                ContractAmountRow row = new ContractAmountRow(
                        0L, "", "ILC", 4500001L, "",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(ContractAmountKeyPolicy.CURRENCY_MISSING, ex.legacyErrorCode(),
                        "Currency is checked last (F115ICA0.src:877-892), so its failure overwrites "
                                + "the earlier Fin-Inst failure");
            }

            @Test
            @DisplayName("rule018_finInstAndContractTypeBothMissing_contractMissingWins")
            void rule018_finInstAndContractTypeBothMissing_contractMissingWins() {
                ContractAmountRow row = new ContractAmountRow(
                        0L, "", "", 4500001L, "USD",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(ContractAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode(),
                        "Contract-Type is checked after Fin-Inst (F115ICA0.src:839-856), so it "
                                + "overwrites the earlier Fin-Inst failure, and nothing later fails "
                                + "here to overwrite it again");
            }

            @Test
            @DisplayName("rule018_allFourFieldsMissing_currencyMissingWins")
            void rule018_allFourFieldsMissing_currencyMissingWins() {
                ContractAmountRow row = new ContractAmountRow(
                        0L, "", "", 0L, "",
                        BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(ContractAmountKeyPolicy.CURRENCY_MISSING, ex.legacyErrorCode());
            }
        }
    }

    // =====================================================================
    // DbiEngine wiring: proves Select/Update/Delete run this table's key
    // policy first and Insert does not (RULE-016 / RULE-018).
    // =====================================================================
    @Nested
    @DisplayName("DbiEngine wiring for TF_Contract_Amount (Capabilities.FULL)")
    class DbiEngineWiringTests {

        private final ContractAmountKeyPolicy keyPolicy = new ContractAmountKeyPolicy();
        private final FakeFinInstResolver resolver = new FakeFinInstResolver();
        private final FakeContractAmountRepository repository = new FakeContractAmountRepository();
        private final DbiEngine<ContractAmountKey, ContractAmountRow> engine =
                new DbiEngine<>(repository, keyPolicy, Capabilities.FULL, resolver);

        @Test
        @DisplayName("rule018_selectWithCompleteKey_invokesPolicyThenRepository")
        void rule018_selectWithCompleteKey_invokesPolicyThenRepository() {
            ContractAmountKey key = new ContractAmountKey(1001L, "ILC", 4500001L, "USD");
            repository.with(key, validRow());

            Optional<ContractAmountRow> found = engine.selectOne(validRow());

            assertTrue(found.isPresent());
            assertEquals(1, repository.selectOneCalls);
        }

        @Test
        @DisplayName("rule018_selectResolvesFinInstNoAndUsesResolvedIdForRepositoryLookup")
        void rule018_selectResolvesFinInstNoAndUsesResolvedIdForRepositoryLookup() {
            // Regression test for a HIGH-severity architecture-review finding
            // (see TRANSFORMATION_NOTES.md): an earlier DbiEngine design took a
            // separately caller-supplied key and never reconciled it with the id
            // KeyPolicy resolved from an external Fin-Inst-No, so the repository
            // was queried with an unresolved (zero) key even when resolution
            // succeeded. This proves the RESOLVED id is what actually reaches
            // the repository, not the raw external number or a zero/default key.
            resolver.knowing("00123456789012345", 1001L);
            ContractAmountKey resolvedKey = new ContractAmountKey(1001L, "ILC", 4500001L, "USD");
            repository.with(resolvedKey, validRow());
            ContractAmountRow rowWithOnlyExternalNumber = new ContractAmountRow(
                    0L, "00123456789012345", "ILC", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            Optional<ContractAmountRow> found = engine.selectOne(rowWithOnlyExternalNumber);

            assertTrue(found.isPresent(),
                    "repository must be queried with the RESOLVED id (1001L), not the unresolved key (0L) "
                            + "or the raw external number");
            assertEquals(1, resolver.callCount);
        }

        @Test
        @DisplayName("rule018_updateDoesNotPersistTheResolvedFinInstId_knownGap")
        void rule018_updateDoesNotPersistTheResolvedFinInstId_knownGap() {
            // FLAGGED GAP, not a passing characterization: unlike selectOne/delete
            // (fixed above), DbiEngine.update(R) passes the ORIGINAL record straight
            // to the repository after validating it - it does not inject the
            // resolved Fin-Inst-ID back into the record before persisting. In the
            // real legacy module, H100-Check-Qualification moves the resolved id
            // into the SAME mutable working-storage field that G100-Move-Input-To-DCL
            // / H200-Update-Row then persist, so the legacy DOES write the resolved
            // id. This Java port does not yet reproduce that for Update specifically
            // (Select/Delete are fixed because they only need the resolved KEY, not
            // a modified RECORD). Narrow real-world impact: only triggers when a
            // caller updates using ONLY the external Fin-Inst-No, never the internal
            // id. Tracked in TRANSFORMATION_NOTES.md as a follow-up, not silently
            // dropped.
            resolver.knowing("00123456789012345", 1001L);
            ContractAmountRow rowWithOnlyExternalNumber = new ContractAmountRow(
                    0L, "00123456789012345", "ILC", 4500001L, "USD",
                    new BigDecimal("100000.000"), new BigDecimal("75000.000"),
                    new BigDecimal("100000.000"), 5, 10);

            engine.update(rowWithOnlyExternalNumber);

            ContractAmountRow persisted = repository.selectOne(new ContractAmountKey(1001L, "ILC", 4500001L, "USD"))
                    .orElse(null);
            // This SHOULD be non-null once the gap is closed; today it is null
            // because the repository received finInstId=0, not the resolved 1001L.
            assertEquals(null, persisted,
                    "documents the current gap: the resolved id is not yet persisted on Update "
                            + "(remove this assertion and assert the row IS found once fixed)");
        }

        @Test
        @DisplayName("rule018_selectRejectsIncompleteKey_widerThanLegacySelect")
        void rule018_selectRejectsIncompleteKey_widerThanLegacySelect() {
            // NOTE: this is the modernized DbiEngine's behavior, not F115ICA0's
            // literal Select path (F000-Select never calls H100-Check-Qualification
            // - see ContractAmountKeyPolicy's javadoc "Scope note"). Pinned here so
            // the widening is visible, not silently inherited by the other 8 tables.
            ContractAmountRow incompleteKeyRecord = new ContractAmountRow(
                    1001L, "", "", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            ContractAmountKey key = new ContractAmountKey(1001L, "", 4500001L, "USD");

            assertThrows(MissingKeyFieldException.class, () -> engine.selectOne(incompleteKeyRecord));
            assertEquals(0, repository.selectOneCalls, "repository must not be reached when the key policy rejects");
        }

        @Test
        @DisplayName("rule018_updateWithCompleteKey_invokesPolicyThenRepository")
        void rule018_updateWithCompleteKey_invokesPolicyThenRepository() {
            engine.update(validRow());

            assertEquals(1, repository.updateCalls);
        }

        @Test
        @DisplayName("rule018_updateRejectsIncompleteKey_matchesLegacyHFor000Update")
        void rule018_updateRejectsIncompleteKey_matchesLegacyHFor000Update() {
            // This one DOES match literal legacy behavior: H000-Update really does
            // call H100-Check-Qualification (F115ICA0.src:796).
            ContractAmountRow missingCurrency = new ContractAmountRow(
                    1001L, "", "ILC", 4500001L, "",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> engine.update(missingCurrency));

            assertEquals(ContractAmountKeyPolicy.CURRENCY_MISSING, ex.legacyErrorCode());
            assertEquals(0, repository.updateCalls, "repository must not be reached when the key policy rejects");
        }

        @Test
        @DisplayName("rule018_deleteWithCompleteKey_invokesPolicyThenRepository")
        void rule018_deleteWithCompleteKey_invokesPolicyThenRepository() {
            ContractAmountKey key = new ContractAmountKey(1001L, "ILC", 4500001L, "USD");
            repository.with(key, validRow());

            engine.delete(validRow());

            assertEquals(1, repository.deleteCalls);
        }

        @Test
        @DisplayName("rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete")
        void rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete() {
            // NOTE: also wider than legacy - I000-Delete uses F100-Prepare-Qualification,
            // not H100-Check-Qualification, so F115ICA0 itself never rejects this case.
            ContractAmountRow noContractType = new ContractAmountRow(
                    1001L, "", "", 4500001L, "USD",
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0);
            ContractAmountKey key = new ContractAmountKey(1001L, "", 4500001L, "USD");

            assertThrows(MissingKeyFieldException.class, () -> engine.delete(noContractType));
            assertEquals(0, repository.deleteCalls);
        }

        @Test
        @DisplayName("rule016_insertNeverInvokesKeyPolicy_evenWithBlankKeyFields")
        void rule016_insertNeverInvokesKeyPolicy_evenWithBlankKeyFields() {
            // RULE-016's documented asymmetry: G000-Insert / G100-Move-Input-To-DCL
            // (F115ICA0.src:723-764) takes the key wholly from input and performs no
            // equivalent check - unlike Update.
            ContractAmountRow blankKeyRow = new ContractAmountRow(
                    0L, "", "", 0L, "",
                    new BigDecimal("50.000"), new BigDecimal("50.000"), new BigDecimal("50.000"), 0, 0);

            engine.insert(blankKeyRow);

            assertEquals(1, repository.insertCalls, "insert must succeed with no key validation at all");
        }
    }
}
