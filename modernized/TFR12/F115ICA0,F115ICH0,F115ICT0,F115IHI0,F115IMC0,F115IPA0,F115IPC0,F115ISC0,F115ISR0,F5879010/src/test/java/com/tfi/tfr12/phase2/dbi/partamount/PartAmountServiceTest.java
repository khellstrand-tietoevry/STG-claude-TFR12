package com.tfi.tfr12.phase2.dbi.partamount;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins the mandatory composite natural key for TF_Part_Amount as literally
 * coded in legacy/TFR12/src/F115IPA0.src's H100-Check-Qualification section
 * (lines 902-1010), reached via {@link PartAmountKeyPolicy}, and RULE-029
 * (analysis/TFR12/BUSINESS_RULES.md) — the confirmed decision to split the
 * legacy's overloaded {@code R115IPA0-Incom-Swift} byte
 * (copybooks/R115IPA0.copy:180-183) into two independent fields,
 * {@link PartAmountRow#hasBeenSettled()} and {@link PartAmountRow#incomSwift()}.
 * The source file is ISO-8859-1/Latin-1, not UTF-8; column 7 '*' marks a
 * comment, columns 8-72 are the code area. F115IPA0 requires an embedded-SQL
 * DB2 precompiler this environment does not have, so it could not be compiled
 * or run; every expected value below was computed by hand-tracing the control
 * flow, not observed from a running program.
 *
 * <p>Per {@link PartAmountKeyPolicy}'s own javadoc: H100-Check-Qualification
 * is called ONLY from H000-Update in the real F115IPA0 (F115IPA0.src:892) —
 * F000-Select and I000-Delete route through F100-Prepare-Qualification
 * instead, which performs no such check. This was verified directly for THIS
 * module (not assumed from the sibling tables' pattern). The generic
 * {@link DbiEngine} this table plugs into nonetheless runs
 * {@link PartAmountKeyPolicy} before Select, Update AND Delete uniformly (a
 * deliberate modernization decision, not a literal translation of F115IPA0's
 * Select/Delete paths) — see
 * {@link DbiEngineWiringTests#selectRejectsIncompleteKey_widerThanLegacySelect()}
 * for the test that pins exactly this widening.
 *
 * <p>{@link DbiEngine} itself (select/update/delete key-check wiring,
 * capability checks, insert bypassing the key check) is already covered
 * generically once elsewhere; the wiring tests below only prove that THIS
 * table's key policy and capabilities plug into it correctly, they do not
 * re-test the engine's own logic.
 *
 * <p>No credential-like values appear in F115IPA0.src; nothing here needed
 * substitution.
 */
class PartAmountServiceTest {

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** Simple Map-backed stand-in for the real (not-yet-built) DB2 access. */
    private static final class FakePartAmountRepository
            implements KeyedRepository<PartAmountKey, PartAmountRow> {
        private final Map<PartAmountKey, PartAmountRow> rows = new HashMap<>();
        int selectOneCalls = 0;
        int getRangeCalls = 0;
        int insertCalls = 0;
        int updateCalls = 0;
        int deleteCalls = 0;

        FakePartAmountRepository with(PartAmountKey key, PartAmountRow row) {
            rows.put(key, row);
            return this;
        }

        @Override
        public Optional<PartAmountRow> selectOne(PartAmountKey key) {
            selectOneCalls++;
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<PartAmountRow> getRange(PartAmountKey partialKey) {
            getRangeCalls++;
            return new ArrayList<>(rows.values());
        }

        @Override
        public void insert(PartAmountRow record) {
            insertCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void update(PartAmountRow record) {
            updateCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void delete(PartAmountKey key) {
            deleteCalls++;
            rows.remove(key);
        }

        private static PartAmountKey keyOf(PartAmountRow r) {
            return new PartAmountKey(r.finInstId(), r.contractType(), r.contractNo(), r.currency(), r.paSeqNo());
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
                throw new MissingKeyFieldException(PartAmountKeyPolicy.FIN_INST_MISSING);
            }
            return id;
        }
    }

    private static PartAmountRow validRow() {
        return new PartAmountRow(
                1001L, "", "ILC", 4500001L, "USD", 1,
                false, IncomSwift.UNSPECIFIED,
                new BigDecimal("10000.000"), "OPEN", "USERID01", false);
    }

    // =====================================================================
    // Mandatory key: PartAmountKeyPolicy, exercised directly
    // (H100-Check-Qualification, F115IPA0.src:902-1010)
    // =====================================================================
    @Nested
    @DisplayName("TF_Part_Amount mandatory composite key (H100-Check-Qualification, Update-only)")
    class KeyPolicyTests {

        private final PartAmountKeyPolicy policy = new PartAmountKeyPolicy();
        private final FinInstResolver noResolutionNeeded = finInstNo -> {
            throw new AssertionError("resolver should not be called when Fin-Inst-ID is already present");
        };

        @Test
        @DisplayName("completeKey_passesValidation")
        void completeKey_passesValidation() {
            policy.resolveKey(validRow(), noResolutionNeeded);
            // no exception -> validation passed (F115IPA0.src:904-1008, all five checks fall through)
        }

        @Test
        @DisplayName("finInstIdPresent_skipsResolverEntirely")
        void finInstIdPresent_skipsResolverEntirely() {
            // F115IPA0.src:905-906 - "When R115IPA0-Fin-Inst-ID Not = +0 -> Continue"
            policy.resolveKey(validRow(), noResolutionNeeded);
        }

        @Test
        @DisplayName("finInstIdZero_resolvesFromFinInstNo")
        void finInstIdZero_resolvesFromFinInstNo() {
            // F115IPA0.src:908-912
            PartAmountRow row = new PartAmountRow(
                    0L, "00123456789012345", "ILC", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            FakeFinInstResolver resolver = new FakeFinInstResolver().knowing("00123456789012345", 1001L);

            policy.resolveKey(row, resolver);

            assertEquals(1, resolver.callCount, "resolver must be invoked exactly once when Fin-Inst-ID is 0");
        }

        @Test
        @DisplayName("finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing")
        void finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing() {
            // F115IPA0.src:925-931 - "When other" branch: neither field usable
            PartAmountRow row = new PartAmountRow(
                    0L, "", "ILC", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("finInstNoUnresolvable_propagatesFinInstMissingFromResolver")
        void finInstNoUnresolvable_propagatesFinInstMissingFromResolver() {
            PartAmountRow row = new PartAmountRow(
                    0L, "NO-SUCH-INST", "ILC", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            FakeFinInstResolver resolver = new FakeFinInstResolver(); // knows nothing

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, resolver));

            assertEquals(PartAmountKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("contractTypeBlank_throwsContractMissing")
        void contractTypeBlank_throwsContractMissing() {
            // F115IPA0.src:944-950
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("contractNoZero_throwsContractMissing")
        void contractNoZero_throwsContractMissing() {
            // F115IPA0.src:963-969 - same mnemonic as a missing Contract-Type
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "ILC", 0L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("currencyBlank_throwsPartAmountMissing")
        void currencyBlank_throwsPartAmountMissing() {
            // F115IPA0.src:982-988
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("paSeqNoZero_throwsPartAmountMissing")
        void paSeqNoZero_throwsPartAmountMissing() {
            // F115IPA0.src:1000-1006 - same mnemonic as a missing Currency
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 0,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("currencyAndPaSeqNoMissing_sameMnemonicForBoth_indistinguishable")
        void currencyAndPaSeqNoMissing_sameMnemonicForBoth_indistinguishable() {
            // F115IPA0.src:973-1008 - Currency and PA-Seq-No share TF-PART-AMOUNT-MISSING,
            // unlike ContractAmountKeyPolicy's dedicated CURRENCY_MISSING; the legacy
            // mnemonic alone cannot tell these two missing-field cases apart.
            PartAmountRow bothMissing = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "", 0,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            PartAmountRow onlyCurrencyMissing = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            PartAmountRow onlyPaSeqNoMissing = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 0,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex1 = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(bothMissing, noResolutionNeeded));
            MissingKeyFieldException ex2 = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(onlyCurrencyMissing, noResolutionNeeded));
            MissingKeyFieldException ex3 = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(onlyPaSeqNoMissing, noResolutionNeeded));

            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex1.legacyErrorCode());
            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex2.legacyErrorCode());
            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex3.legacyErrorCode());
        }

        // -----------------------------------------------------------------
        // "Last failure wins" - F115IPA0.src runs its five mandatory-field
        // checks as five independent Evaluate blocks in strict sequence with
        // no early exit; the one visible error is whichever check failed
        // LAST, not the first.
        // -----------------------------------------------------------------
        @Nested
        @DisplayName("QUIRK: with more than one field missing, the LAST failing check wins, not the first "
                + "(F115IPA0.src:904-1008 has no early exit between its 5 Evaluate blocks)")
        class LastFailureWinsTests {

            @Test
            @DisplayName("finInstAndPaSeqNoBothMissing_partAmountMissingWins")
            void finInstAndPaSeqNoBothMissing_partAmountMissingWins() {
                PartAmountRow row = new PartAmountRow(
                        0L, "", "ILC", 4500001L, "USD", 0,
                        false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode(),
                        "PA-Seq-No is checked last (F115IPA0.src:992-1008), so its failure overwrites "
                                + "the earlier Fin-Inst failure");
            }

            @Test
            @DisplayName("finInstAndContractTypeBothMissing_contractMissingWins")
            void finInstAndContractTypeBothMissing_contractMissingWins() {
                PartAmountRow row = new PartAmountRow(
                        0L, "", "", 4500001L, "USD", 1,
                        false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(PartAmountKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode(),
                        "Contract-Type is checked after Fin-Inst (F115IPA0.src:935-952), and nothing "
                                + "later fails here to overwrite it again (Contract-No, Currency and "
                                + "PA-Seq-No are all present)");
            }

            @Test
            @DisplayName("contractTypeAndCurrencyBothMissing_partAmountMissingWins")
            void contractTypeAndCurrencyBothMissing_partAmountMissingWins() {
                PartAmountRow row = new PartAmountRow(
                        1001L, "", "", 4500001L, "", 1,
                        false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode(),
                        "Currency (F115IPA0.src:973-990) is checked after Contract-Type, so it "
                                + "overwrites the earlier Contract-Type failure");
            }

            @Test
            @DisplayName("allFiveFieldsMissing_partAmountMissingWins")
            void allFiveFieldsMissing_partAmountMissingWins() {
                PartAmountRow row = new PartAmountRow(
                        0L, "", "", 0L, "", 0,
                        false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode());
            }
        }
    }

    // =====================================================================
    // RULE-029: hasBeenSettled and incomSwift are independently-typed fields,
    // not one shared overloaded byte (copybooks/R115IPA0.copy:180-183).
    // =====================================================================
    @Nested
    @DisplayName("RULE-029: hasBeenSettled and incomSwift are independent fields, not one overloaded byte")
    class Rule029FieldIndependenceTests {

        @Test
        @DisplayName("rule029_hasBeenSettledTrue_withIncomSwiftNo_isAValidDistinctCombination")
        void rule029_hasBeenSettledTrue_withIncomSwiftNo_isAValidDistinctCombination() {
            // The legacy single byte can be 'A' (Has-Been-Settled) OR 'N' (Incom-No),
            // never both at once. The split model can represent both simultaneously -
            // exactly the combination the single-byte legacy design could not cleanly
            // express, per RULE-029's "Suspected defect" note
            // (analysis/TFR12/BUSINESS_RULES.md).
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 1,
                    true, IncomSwift.NO,
                    new BigDecimal("500.000"), "SETTLED", "USERID01", false);

            assertTrue(row.hasBeenSettled());
            assertEquals(IncomSwift.NO, row.incomSwift());
        }

        @Test
        @DisplayName("rule029_hasBeenSettledTrue_withIncomSwiftYes_isAlsoAValidDistinctCombination")
        void rule029_hasBeenSettledTrue_withIncomSwiftYes_isAlsoAValidDistinctCombination() {
            PartAmountRow row = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 1,
                    true, IncomSwift.YES,
                    new BigDecimal("500.000"), "SETTLED", "USERID01", false);

            assertTrue(row.hasBeenSettled());
            assertEquals(IncomSwift.YES, row.incomSwift());
        }

        @Test
        @DisplayName("rule029_hasBeenSettledFalse_withEachIncomSwiftValue_allDistinctFromSettledRows")
        void rule029_hasBeenSettledFalse_withEachIncomSwiftValue_allDistinctFromSettledRows() {
            PartAmountRow notSettledYes = new PartAmountRow(1001L, "", "ILC", 4500001L, "USD", 1,
                    false, IncomSwift.YES, BigDecimal.TEN, "OPEN", "USERID01", false);
            PartAmountRow settledYes = new PartAmountRow(1001L, "", "ILC", 4500001L, "USD", 1,
                    true, IncomSwift.YES, BigDecimal.TEN, "OPEN", "USERID01", false);

            assertNotEquals(notSettledYes, settledYes,
                    "changing only hasBeenSettled must produce a distinct row (RULE-029: independent field)");
            assertFalse(notSettledYes.hasBeenSettled());
            assertTrue(settledYes.hasBeenSettled());
            assertEquals(IncomSwift.YES, notSettledYes.incomSwift());
            assertEquals(IncomSwift.YES, settledYes.incomSwift());
        }

        @Test
        @DisplayName("rule029_legacyByteDecode_A_meansSettled_andUnspecifiedIncomSwift")
        void rule029_legacyByteDecode_A_meansSettled_andUnspecifiedIncomSwift() {
            // copybooks/R115IPA0.copy:183 - R115IPA0-Has-Been-Settled Value 'A'.
            // From the Incom-Yes/Incom-No point of view, 'A' matches neither 88-level.
            assertTrue(PartAmountRow.hasBeenSettledFromLegacyByte('A'));
            assertEquals(IncomSwift.UNSPECIFIED, IncomSwift.fromLegacyByte('A'));
        }

        @Test
        @DisplayName("rule029_legacyByteDecode_Y_meansIncomYes_andNotSettled")
        void rule029_legacyByteDecode_Y_meansIncomYes_andNotSettled() {
            // copybooks/R115IPA0.copy:181 - R115IPA0-Incom-Yes Value 'Y'.
            assertEquals(IncomSwift.YES, IncomSwift.fromLegacyByte('Y'));
            assertFalse(PartAmountRow.hasBeenSettledFromLegacyByte('Y'));
        }

        @Test
        @DisplayName("rule029_legacyByteDecode_N_meansIncomNo_andNotSettled")
        void rule029_legacyByteDecode_N_meansIncomNo_andNotSettled() {
            // copybooks/R115IPA0.copy:182 - R115IPA0-Incom-No Value 'N'.
            assertEquals(IncomSwift.NO, IncomSwift.fromLegacyByte('N'));
            assertFalse(PartAmountRow.hasBeenSettledFromLegacyByte('N'));
        }

        @Test
        @DisplayName("rule029_legacyByteDecode_space_meansUnspecifiedAndNotSettled")
        void rule029_legacyByteDecode_space_meansUnspecifiedAndNotSettled() {
            // The byte's state after COBOL Initialize (space) matches none of the
            // three 88-level condition-names (copybooks/R115IPA0.copy:181-183).
            assertEquals(IncomSwift.UNSPECIFIED, IncomSwift.fromLegacyByte(' '));
            assertFalse(PartAmountRow.hasBeenSettledFromLegacyByte(' '));
        }

        @Test
        @DisplayName("rule029_keyPolicyValidation_isUnaffectedByEitherSplitField")
        void rule029_keyPolicyValidation_isUnaffectedByEitherSplitField() {
            // The mandatory-key check (H100-Check-Qualification) never looks at
            // Incom-Swift at all - confirming the split is purely a data-field
            // concern, orthogonal to key validation.
            PartAmountKeyPolicy policy = new PartAmountKeyPolicy();
            FinInstResolver noResolutionNeeded = finInstNo -> {
                throw new AssertionError("must not be called");
            };
            PartAmountRow settledRow = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 1,
                    true, IncomSwift.YES, BigDecimal.TEN, "OPEN", "USERID01", false);

            policy.resolveKey(settledRow, noResolutionNeeded); // must not throw
        }
    }

    // =====================================================================
    // DbiEngine wiring: proves Select/Update/Delete run this table's key
    // policy first and Insert does not (RULE-016).
    // =====================================================================
    @Nested
    @DisplayName("DbiEngine wiring for TF_Part_Amount (Capabilities.FULL)")
    class DbiEngineWiringTests {

        private final PartAmountKeyPolicy keyPolicy = new PartAmountKeyPolicy();
        private final FakeFinInstResolver resolver = new FakeFinInstResolver();
        private final FakePartAmountRepository repository = new FakePartAmountRepository();
        private final DbiEngine<PartAmountKey, PartAmountRow> engine =
                new DbiEngine<>(repository, keyPolicy, Capabilities.FULL, resolver);

        @Test
        @DisplayName("selectWithCompleteKey_invokesPolicyThenRepository")
        void selectWithCompleteKey_invokesPolicyThenRepository() {
            PartAmountKey key = new PartAmountKey(1001L, "ILC", 4500001L, "USD", 1);
            repository.with(key, validRow());

            Optional<PartAmountRow> found = engine.selectOne(validRow());

            assertTrue(found.isPresent());
            assertEquals(1, repository.selectOneCalls);
        }

        @Test
        @DisplayName("selectRejectsIncompleteKey_widerThanLegacySelect")
        void selectRejectsIncompleteKey_widerThanLegacySelect() {
            // NOTE: this is the modernized DbiEngine's behavior, not F115IPA0's
            // literal Select path (F000-Select never calls H100-Check-Qualification
            // - see PartAmountKeyPolicy's javadoc "Scope note"). Pinned here so
            // the widening is visible.
            PartAmountRow incompleteKeyRecord = new PartAmountRow(
                    1001L, "", "", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            PartAmountKey key = new PartAmountKey(1001L, "", 4500001L, "USD", 1);

            assertThrows(MissingKeyFieldException.class, () -> engine.selectOne(incompleteKeyRecord));
            assertEquals(0, repository.selectOneCalls, "repository must not be reached when the key policy rejects");
        }

        @Test
        @DisplayName("updateWithCompleteKey_invokesPolicyThenRepository")
        void updateWithCompleteKey_invokesPolicyThenRepository() {
            engine.update(validRow());

            assertEquals(1, repository.updateCalls);
        }

        @Test
        @DisplayName("updateRejectsIncompleteKey_matchesLegacyH000Update")
        void updateRejectsIncompleteKey_matchesLegacyH000Update() {
            // This one DOES match literal legacy behavior in terms of WHICH check
            // fires (H000-Update really does call H100-Check-Qualification,
            // F115IPA0.src:892) - though not the "always proceeds anyway" quirk
            // documented on PartAmountKeyPolicy, which this modernized engine
            // deliberately hardens by short-circuiting instead.
            PartAmountRow missingCurrency = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> engine.update(missingCurrency));

            assertEquals(PartAmountKeyPolicy.PART_AMOUNT_MISSING, ex.legacyErrorCode());
            assertEquals(0, repository.updateCalls, "repository must not be reached when the key policy rejects");
        }

        @Test
        @DisplayName("deleteWithCompleteKey_invokesPolicyThenRepository")
        void deleteWithCompleteKey_invokesPolicyThenRepository() {
            PartAmountKey key = new PartAmountKey(1001L, "ILC", 4500001L, "USD", 1);
            repository.with(key, validRow());

            engine.delete(validRow());

            assertEquals(1, repository.deleteCalls);
        }

        @Test
        @DisplayName("deleteRejectsIncompleteKey_widerThanLegacyDelete")
        void deleteRejectsIncompleteKey_widerThanLegacyDelete() {
            // NOTE: also wider than legacy - I000-Delete uses F100-Prepare-Qualification,
            // not H100-Check-Qualification, so F115IPA0 itself never rejects this case.
            PartAmountRow noContractType = new PartAmountRow(
                    1001L, "", "", 4500001L, "USD", 1,
                    false, IncomSwift.UNSPECIFIED, BigDecimal.ZERO, "", "", false);
            PartAmountKey key = new PartAmountKey(1001L, "", 4500001L, "USD", 1);

            assertThrows(MissingKeyFieldException.class, () -> engine.delete(noContractType));
            assertEquals(0, repository.deleteCalls);
        }

        @Test
        @DisplayName("insertNeverInvokesKeyPolicy_evenWithBlankKeyFields")
        void insertNeverInvokesKeyPolicy_evenWithBlankKeyFields() {
            // RULE-016's documented asymmetry: G000-Insert / G100-Move-Input-To-DCL
            // (F115IPA0.src:784-860) takes the key wholly from input and performs no
            // equivalent check - unlike Update. PA-Seq-No is also created by the
            // interface on Insert (copybooks/R115IPA0.copy:96), reinforcing that
            // Insert does not require the caller to supply a complete key up front.
            PartAmountRow blankKeyRow = new PartAmountRow(
                    0L, "", "", 0L, "", 0,
                    true, IncomSwift.YES,
                    new BigDecimal("50.000"), "", "", false);

            engine.insert(blankKeyRow);

            assertEquals(1, repository.insertCalls, "insert must succeed with no key validation at all");
        }

        @Test
        @DisplayName("insertCanPersistHasBeenSettledAndIncomSwiftIndependently")
        void insertCanPersistHasBeenSettledAndIncomSwiftIndependently() {
            // RULE-029: proves the split fields flow end-to-end through the engine
            // and fake repository as independent values, in a combination the
            // legacy's single overloaded byte could not represent.
            PartAmountRow settledButIncomNo = new PartAmountRow(
                    1001L, "", "ILC", 4500001L, "USD", 1,
                    true, IncomSwift.NO,
                    new BigDecimal("777.000"), "SETTLED", "USERID01", false);

            engine.insert(settledButIncomNo);

            PartAmountKey key = new PartAmountKey(1001L, "ILC", 4500001L, "USD", 1);
            Optional<PartAmountRow> stored = repository.selectOne(key);
            assertTrue(stored.isPresent());
            assertTrue(stored.get().hasBeenSettled());
            assertEquals(IncomSwift.NO, stored.get().incomSwift());
        }
    }
}
