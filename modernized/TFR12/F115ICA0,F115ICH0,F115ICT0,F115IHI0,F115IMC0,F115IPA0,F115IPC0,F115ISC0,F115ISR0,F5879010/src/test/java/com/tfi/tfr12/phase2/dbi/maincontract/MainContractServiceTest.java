package com.tfi.tfr12.phase2.dbi.maincontract;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * <p>Pins RULE-018 (mandatory composite natural key for TF_Main_Contract) as
 * literally coded in legacy/TFR12/src/F115IMC0.src's H100-Check-Qualification
 * section (lines 915-987), reached via {@link MainContractKeyPolicy}. The
 * file is ISO-8859-1/Latin-1, not UTF-8; column 7 '*' marks a comment, columns
 * 8-72 are the code area. F115IMC0 requires an embedded-SQL DB2 precompiler
 * this environment does not have, so it could not be compiled or run; every
 * expected value below was computed by hand-tracing the control flow, not
 * observed from a running program.
 *
 * <p>No specific business-rule id in analysis/TFR12/BUSINESS_RULES.md names
 * F115IMC0 by module, but RULE-018 ("the same pattern... is repeated
 * independently in every F115I*0 table-interface module") and RULE-016 (the
 * insert/update key-check asymmetry) both explicitly describe this table's
 * pattern generically, so tests below are named against those two ids.
 *
 * <p>Per {@link MainContractKeyPolicy}'s own javadoc: H100-Check-Qualification
 * is called ONLY from H000-Update in the real F115IMC0 (F115IMC0.src:905) —
 * F000-Select and I000-Delete route through F100-Prepare-Qualification
 * instead, which performs no such check. The generic {@link DbiEngine} this
 * table plugs into nonetheless runs {@link MainContractKeyPolicy} before
 * Select, Update AND Delete uniformly (a deliberate modernization decision,
 * not a literal translation of F115IMC0's Select/Delete paths) — see
 * {@link DbiEngineWiringTests#rule018_selectRejectsIncompleteKey_widerThanLegacySelect()}
 * for the test that pins exactly this widening.
 *
 * <p>{@link DbiEngine} itself (select/update/delete key-check wiring,
 * capability checks, insert bypassing the key check) is already covered
 * generically once elsewhere; the wiring tests below only prove that THIS
 * table's key policy and capabilities plug into it correctly, they do not
 * re-test the engine's own logic.
 *
 * <p><b>Capabilities:</b> F115IMC0's C000-Interface (F115IMC0.src:318-356)
 * dispatches all six actions — Get-1st, Get-Next, Select, Insert, Update,
 * Delete — each to its own paragraph, with no condition-name commented out
 * (unlike F115ISR0/RULE-027 or F115IHI0/RULE-028); the 'When Other' arm
 * (F115IMC0.src:345-352) is only reached for a genuinely unrecognized
 * -In-Action value and abends with 'GL-IN-AKSJON-UV'. So this table uses
 * {@link Capabilities#FULL}, not a restricted variant.
 *
 * <p>No credential-like values appear in F115IMC0.src; nothing here needed
 * substitution.
 */
class MainContractServiceTest {

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** Simple Map-backed stand-in for the real (not-yet-built) DB2 access. */
    private static final class FakeMainContractRepository
            implements KeyedRepository<MainContractKey, MainContractRow> {
        private final Map<MainContractKey, MainContractRow> rows = new HashMap<>();
        int selectOneCalls = 0;
        int getRangeCalls = 0;
        int insertCalls = 0;
        int updateCalls = 0;
        int deleteCalls = 0;

        FakeMainContractRepository with(MainContractKey key, MainContractRow row) {
            rows.put(key, row);
            return this;
        }

        @Override
        public Optional<MainContractRow> selectOne(MainContractKey key) {
            selectOneCalls++;
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<MainContractRow> getRange(MainContractKey partialKey) {
            getRangeCalls++;
            return new ArrayList<>(rows.values());
        }

        @Override
        public void insert(MainContractRow record) {
            insertCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void update(MainContractRow record) {
            updateCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void delete(MainContractKey key) {
            deleteCalls++;
            rows.remove(key);
        }

        private static MainContractKey keyOf(MainContractRow r) {
            return new MainContractKey(r.finInstId(), r.contractType(), r.contractNo());
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
                throw new MissingKeyFieldException(MainContractKeyPolicy.FIN_INST_MISSING);
            }
            return id;
        }
    }

    private static MainContractRow validRow() {
        return new MainContractRow(
                1001L, "", "ILC", 4500001L,
                7001L, "OPEN", "S", "N");
    }

    // =====================================================================
    // RULE-018: MainContractKeyPolicy, exercised directly
    // =====================================================================
    @Nested
    @DisplayName("RULE-018: TF_Main_Contract mandatory composite key (H100-Check-Qualification)")
    class KeyPolicyTests {

        private final MainContractKeyPolicy policy = new MainContractKeyPolicy();
        private final FinInstResolver noResolutionNeeded = finInstNo -> {
            throw new AssertionError("resolver should not be called when Fin-Inst-ID is already present");
        };

        @Test
        @DisplayName("rule018_completeKey_passesValidation")
        void rule018_completeKey_passesValidation() {
            policy.resolveKey(validRow(), noResolutionNeeded);
            // no exception -> validation passed (F115IMC0.src:917-985, all three checks fall through)
        }

        @Test
        @DisplayName("rule018_finInstIdPresent_skipsResolverEntirely")
        void rule018_finInstIdPresent_skipsResolverEntirely() {
            // F115IMC0.src:918-920 - "When R115IMC0-Fin-Inst-ID Not = +0 -> Continue"
            policy.resolveKey(validRow(), noResolutionNeeded);
        }

        @Test
        @DisplayName("rule018_finInstIdZero_resolvesFromFinInstNo")
        void rule018_finInstIdZero_resolvesFromFinInstNo() {
            // F115IMC0.src:921-926
            MainContractRow row = new MainContractRow(
                    0L, "00123456789012345", "ILC", 4500001L,
                    0L, "", "", "");
            FakeFinInstResolver resolver = new FakeFinInstResolver().knowing("00123456789012345", 1001L);

            policy.resolveKey(row, resolver);

            assertEquals(1, resolver.callCount, "resolver must be invoked exactly once when Fin-Inst-ID is 0");
        }

        @Test
        @DisplayName("rule018_finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing")
        void rule018_finInstIdZeroAndFinInstNoBlank_throwsFinInstMissing() {
            // F115IMC0.src:939-946 - "When other" branch: neither field usable
            MainContractRow row = new MainContractRow(
                    0L, "", "ILC", 4500001L,
                    0L, "", "", "");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(MainContractKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_finInstNoUnresolvable_propagatesFinInstMissingFromResolver")
        void rule018_finInstNoUnresolvable_propagatesFinInstMissingFromResolver() {
            MainContractRow row = new MainContractRow(
                    0L, "NO-SUCH-INST", "ILC", 4500001L,
                    0L, "", "", "");
            FakeFinInstResolver resolver = new FakeFinInstResolver(); // knows nothing

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, resolver));

            assertEquals(MainContractKeyPolicy.FIN_INST_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_contractTypeBlank_throwsContractMissing")
        void rule018_contractTypeBlank_throwsContractMissing() {
            // F115IMC0.src:958-965
            MainContractRow row = new MainContractRow(
                    1001L, "", "", 4500001L,
                    0L, "", "", "");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule018_contractNoZero_throwsContractMissing")
        void rule018_contractNoZero_throwsContractMissing() {
            // F115IMC0.src:977-984 - same mnemonic as a missing Contract-Type
            MainContractRow row = new MainContractRow(
                    1001L, "", "ILC", 0L,
                    0L, "", "", "");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> policy.resolveKey(row, noResolutionNeeded));

            assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
        }

        // -----------------------------------------------------------------
        // "Last failure wins" - F115IMC0.src runs all three Evaluate blocks
        // unconditionally in sequence (no early exit); the one visible error
        // is whichever check failed LAST, not the first.
        // -----------------------------------------------------------------
        @Nested
        @DisplayName("QUIRK: with more than one field missing, the LAST failing check wins, not the first "
                + "(F115IMC0.src:917-985 has no early exit between its 3 Evaluate blocks)")
        class LastFailureWinsTests {

            @Test
            @DisplayName("rule018_finInstAndContractTypeBothMissing_contractMissingWins")
            void rule018_finInstAndContractTypeBothMissing_contractMissingWins() {
                MainContractRow row = new MainContractRow(
                        0L, "", "", 4500001L,
                        0L, "", "", "");

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode(),
                        "Contract-Type is checked after Fin-Inst (F115IMC0.src:949-966), so it "
                                + "overwrites the earlier Fin-Inst failure");
            }

            @Test
            @DisplayName("rule018_finInstAndContractNoBothMissing_contractMissingWins")
            void rule018_finInstAndContractNoBothMissing_contractMissingWins() {
                MainContractRow row = new MainContractRow(
                        0L, "", "ILC", 0L,
                        0L, "", "", "");

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode(),
                        "Contract-No is checked last (F115IMC0.src:968-985), so it overwrites "
                                + "the earlier Fin-Inst failure");
            }

            @Test
            @DisplayName("rule018_allThreeFieldsMissing_contractMissingWins")
            void rule018_allThreeFieldsMissing_contractMissingWins() {
                MainContractRow row = new MainContractRow(
                        0L, "", "", 0L,
                        0L, "", "", "");

                MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                        () -> policy.resolveKey(row, noResolutionNeeded));

                assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
            }
        }
    }

    // =====================================================================
    // DbiEngine wiring: proves Select/Update/Delete run this table's key
    // policy first, Insert does not, and the cursor path (Get-1st/Get-Next)
    // is enabled (RULE-016 / RULE-018, Capabilities.FULL).
    // =====================================================================
    @Nested
    @DisplayName("DbiEngine wiring for TF_Main_Contract (Capabilities.FULL)")
    class DbiEngineWiringTests {

        private final MainContractKeyPolicy keyPolicy = new MainContractKeyPolicy();
        private final FakeFinInstResolver resolver = new FakeFinInstResolver();
        private final FakeMainContractRepository repository = new FakeMainContractRepository();
        private final DbiEngine<MainContractKey, MainContractRow> engine =
                new DbiEngine<>(repository, keyPolicy, Capabilities.FULL, resolver);

        @Test
        @DisplayName("rule018_selectWithCompleteKey_invokesPolicyThenRepository")
        void rule018_selectWithCompleteKey_invokesPolicyThenRepository() {
            MainContractKey key = new MainContractKey(1001L, "ILC", 4500001L);
            repository.with(key, validRow());

            Optional<MainContractRow> found = engine.selectOne(validRow());

            assertTrue(found.isPresent());
            assertEquals(1, repository.selectOneCalls);
        }

        @Test
        @DisplayName("rule018_selectRejectsIncompleteKey_widerThanLegacySelect")
        void rule018_selectRejectsIncompleteKey_widerThanLegacySelect() {
            // NOTE: this is the modernized DbiEngine's behavior, not F115IMC0's
            // literal Select path (F000-Select never calls H100-Check-Qualification
            // - see MainContractKeyPolicy's javadoc "Scope note"). Pinned here so
            // the widening is visible rather than silently assumed.
            MainContractRow incompleteKeyRecord = new MainContractRow(
                    1001L, "", "", 4500001L,
                    0L, "", "", "");
            MainContractKey key = new MainContractKey(1001L, "", 4500001L);

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
        @DisplayName("rule018_updateRejectsIncompleteKey_matchesLegacyH100Update")
        void rule018_updateRejectsIncompleteKey_matchesLegacyH100Update() {
            // This one DOES match literal legacy behavior: H000-Update really does
            // call H100-Check-Qualification (F115IMC0.src:905).
            MainContractRow missingContractNo = new MainContractRow(
                    1001L, "", "ILC", 0L,
                    0L, "", "", "");

            MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                    () -> engine.update(missingContractNo));

            assertEquals(MainContractKeyPolicy.CONTRACT_MISSING, ex.legacyErrorCode());
            assertEquals(0, repository.updateCalls, "repository must not be reached when the key policy rejects");
        }

        @Test
        @DisplayName("rule018_deleteWithCompleteKey_invokesPolicyThenRepository")
        void rule018_deleteWithCompleteKey_invokesPolicyThenRepository() {
            MainContractKey key = new MainContractKey(1001L, "ILC", 4500001L);
            repository.with(key, validRow());

            engine.delete(validRow());

            assertEquals(1, repository.deleteCalls);
        }

        @Test
        @DisplayName("rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete")
        void rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete() {
            // NOTE: also wider than legacy - I000-Delete uses F100-Prepare-Qualification,
            // not H100-Check-Qualification, so F115IMC0 itself never rejects this case.
            MainContractRow noContractType = new MainContractRow(
                    1001L, "", "", 4500001L,
                    0L, "", "", "");
            MainContractKey key = new MainContractKey(1001L, "", 4500001L);

            assertThrows(MissingKeyFieldException.class, () -> engine.delete(noContractType));
            assertEquals(0, repository.deleteCalls);
        }

        @Test
        @DisplayName("rule016_insertNeverInvokesKeyPolicy_evenWithBlankKeyFields")
        void rule016_insertNeverInvokesKeyPolicy_evenWithBlankKeyFields() {
            // RULE-016's documented asymmetry: G000-Insert / G100-Move-Input-To-DCL
            // (F115IMC0.src:764-873) takes the key wholly from input and performs no
            // equivalent check - unlike Update.
            MainContractRow blankKeyRow = new MainContractRow(
                    0L, "", "", 0L,
                    0L, "", "", "");

            engine.insert(blankKeyRow);

            assertEquals(1, repository.insertCalls, "insert must succeed with no key validation at all");
        }

        @Test
        @DisplayName("cursorGetRange_isEnabled_matchingC000InterfaceDispatchOfGet1stAndGetNext")
        void cursorGetRange_isEnabled_matchingC000InterfaceDispatchOfGet1stAndGetNext() {
            // C000-Interface (F115IMC0.src:318-356) dispatches R115IMC0-In-Get-1st
            // and R115IMC0-In-Get-Next like every other action, with no condition-name
            // commented out - this table's Capabilities.cursor() must be true.
            MainContractKey partialKey = new MainContractKey(1001L, "", 0L);
            repository.with(new MainContractKey(1001L, "ILC", 4500001L), validRow());

            List<MainContractRow> rows = engine.getRange(partialKey);

            assertEquals(1, rows.size());
            assertEquals(1, repository.getRangeCalls);
        }
    }
}
