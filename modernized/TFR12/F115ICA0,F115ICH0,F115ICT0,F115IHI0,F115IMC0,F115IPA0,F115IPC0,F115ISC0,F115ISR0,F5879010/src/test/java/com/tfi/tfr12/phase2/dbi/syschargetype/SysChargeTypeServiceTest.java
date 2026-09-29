package com.tfi.tfr12.phase2.dbi.syschargetype;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;
import org.junit.jupiter.api.BeforeEach;
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
 * <p>Pins the RULE-018 mandatory-key pattern (analysis/TFR12/BUSINESS_RULES.md,
 * "the same pattern... is repeated independently in every F115I*0
 * table-interface module") as implemented by
 * {@code legacy/TFR12/src/F115ICT0.src}, the DBI module for table
 * TF_Sys_Charge_Type: H100-Check-Qualification (F115ICT0.src:738-787),
 * performed only by H000-Update (line 728), must resolve CT-Type (error
 * {@code TF-TYPE-MISSING}) and Fin-Inst-Id/Fin-Inst-No (error
 * {@code TF-FININST-MISSING}, falling back to N100-Fetch-Fin-Inst-ID) before
 * an update proceeds. G000-Insert (F115ICT0.src:655-663) performs no
 * equivalent check at all - the same insert/update asymmetry documented for
 * the sibling tables. This table was not independently flagged as
 * distinctive during rule mining (no RULE-NNN id was minted for it
 * specifically), so this suite cites RULE-018's generic card plus direct
 * F115ICT0.src line citations rather than a table-specific rule id.
 *
 * <p>The file is ISO-8859-1/Latin-1, not UTF-8; columns 8-72 are the code
 * area, column 7 '*' marks a comment. F115ICT0 could not be compiled or run:
 * it requires a DB2 precompiler not available in this environment. Every
 * expected value below was computed by hand-tracing F115ICT0.src's control
 * flow, not observed from a running program.
 *
 * <p><b>Discrepancy flagged (see {@link SysChargeTypeKeyPolicy}'s javadoc for
 * detail):</b> in the real F115ICT0, only H000-Update calls
 * H100-Check-Qualification. F000-Select and I000-Delete both call
 * F100-Prepare-Qualification instead (F115ICT0.src:562-607, 817-824,
 * 609-627), which performs none of these checks. {@link DbiEngine} — shared
 * across every F115I*0 table in this module and out of this file's scope to
 * change — applies {@link KeyPolicy#validate} uniformly before Select, Update
 * and Delete for every table wired through it. The "select"/"delete invoke
 * the policy" tests below therefore pin {@code DbiEngine}'s own generic
 * wiring contract, not a claim that F115ICT0's own Select or Delete
 * paragraphs perform this validation - they provably do not. No test in this
 * file asserts that F115ICT0's real Select/Delete behavior matches this.
 *
 * <p><b>Capabilities:</b> C000-Interface's dispatch Evaluate
 * (F115ICT0.src:275-311) has a live, uncommented {@code When} branch for
 * every one of Get-1st, Get-Next, Select, Insert, Update and Delete - unlike
 * F115ISR0 (Update's condition-name is commented out) and F115IHI0 (Select
 * and Update are both commented out). F115ICT0 uses {@link Capabilities#FULL}.
 */
class SysChargeTypeServiceTest {

    private static SysChargeTypeRow completeRow() {
        return new SysChargeTypeRow(
                "HANDLING", 1001L, "0001", 42,
                "Handling fee", "P", "A", 700123L,
                new BigDecimal("2.50"));
    }

    /** Never resolves anything: any call is a test-setup bug unless explicitly stubbed. */
    private static final FinInstResolver UNUSED_RESOLVER = finInstNo -> {
        throw new AssertionError("resolver should not have been called for finInstNo=" + finInstNo);
    };

    // ------------------------------------------------------------------
    // KeyPolicy direct tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("SysChargeTypeKeyPolicy.validate (F115ICT0.src:738-787)")
    class KeyPolicyTests {

        private final SysChargeTypeKeyPolicy policy = new SysChargeTypeKeyPolicy();

        @Test
        @DisplayName("allMandatoryFieldsPresent_noException")
        void allMandatoryFieldsPresent_noException() {
            policy.resolveKey(completeRow(), UNUSED_RESOLVER);
            // No exception: H100-Check-Qualification falls through both
            // Evaluates with "Continue" (F115ICT0.src:742, 758).
        }

        @Test
        @DisplayName("missingCtType_blank_throwsTypeMissing")
        void missingCtType_blank_throwsTypeMissing() {
            SysChargeTypeRow row = withCtType(completeRow(), " ");

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICT0.src:747-753 - "When other" branch of the CT-Type Evaluate.
            assertEquals("TF-TYPE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("missingCtType_null_throwsTypeMissing")
        void missingCtType_null_throwsTypeMissing() {
            SysChargeTypeRow row = withCtType(completeRow(), null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            assertEquals("TF-TYPE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("missingFinInstId_andFinInstNo_throwsFininstMissing")
        void missingFinInstId_andFinInstNo_throwsFininstMissing() {
            SysChargeTypeRow row = withFinInst(completeRow(), 0L, null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICT0.src:779-786 - "When other" branch of the Fin-Inst
            // Evaluate. Note the exact mnemonic spelling: TF-FININST-MISSING
            // (F115ICT0.src:781), not TF-FIN-INST-MISSING as in the sibling
            // Main/Part-Charge modules - see SysChargeTypeKeyPolicy's javadoc.
            assertEquals("TF-FININST-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("finInstIdZero_butFinInstNoPresent_resolvesViaN100_noException")
        void finInstIdZero_butFinInstNoPresent_resolvesViaN100_noException() {
            SysChargeTypeRow row = withFinInst(completeRow(), 0L, "0001");
            FinInstResolver resolver = finInstNo -> {
                assertEquals("0001", finInstNo);
                return 1001L;
            };

            // F115ICT0.src:765-770 - Fin-Inst-No branch calls N100-Fetch-Fin-Inst-ID.
            policy.resolveKey(row, resolver);
        }

        @Test
        @DisplayName("finInstNoUnresolvable_resolverThrows_propagatesFininstMissing")
        void finInstNoUnresolvable_resolverThrows_propagatesFininstMissing() {
            SysChargeTypeRow row = withFinInst(completeRow(), 0L, "9999");
            FinInstResolver resolver = finInstNo -> {
                throw new MissingKeyFieldException("TF-FIN-INST-MISSING");
            };

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, resolver));
            assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("regnoZero_stillNoException_notIndependentlyValidated")
        void regnoZero_stillNoException_notIndependentlyValidated() {
            SysChargeTypeRow row = withRegno(completeRow(), 0);

            // See SysChargeTypeKey's javadoc: H100 has exactly 2 Evaluate
            // blocks (CT-Type, Fin-Inst) and none for Regno - no
            // TF-REGNO-MISSING-shaped mnemonic exists in F115ICT0.src.
            policy.resolveKey(row, UNUSED_RESOLVER);
        }

        @Test
        @DisplayName("bothMandatoryFieldsMissing_lastFailureWins")
        void bothMandatoryFieldsMissing_lastFailureWins() {
            SysChargeTypeRow row = withFinInst(withCtType(completeRow(), " "), 0L, null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // "Last failure wins" (standardized across every table's KeyPolicy,
            // see KeyPolicy's class javadoc): CT-Type is checked before Fin-Inst
            // in H100's source order (F115ICT0.src:740 precedes :756) with no
            // short-circuit between them, so when both are missing, Fin-Inst's
            // error - checked last - is what actually survives to the caller.
            assertEquals("TF-FININST-MISSING", ex.legacyErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // DbiEngine wiring tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("DbiEngine<SysChargeTypeKey, SysChargeTypeRow> wiring")
    class EngineWiringTests {

        private FakeSysChargeTypeRepository repository;
        private CountingKeyPolicy policy;
        private DbiEngine<SysChargeTypeKey, SysChargeTypeRow> engine;

        @BeforeEach
        void setUp() {
            repository = new FakeSysChargeTypeRepository();
            policy = new CountingKeyPolicy(new SysChargeTypeKeyPolicy());
            // Capabilities.FULL - see class-level javadoc: every action's
            // dispatch branch in C000-Interface (F115ICT0.src:277-309) is
            // live, none is commented out.
            engine = new DbiEngine<>(repository, policy, Capabilities.FULL, UNUSED_RESOLVER);
        }

        @Test
        @DisplayName("insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey")
        void insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey() {
            // Deliberately incomplete: blank CT-Type.
            SysChargeTypeRow incomplete = withCtType(completeRow(), " ");

            // G000-Insert (F115ICT0.src:655-663) calls G100-Move-Input-To-DCL
            // directly with no qualification check.
            engine.insert(incomplete);

            assertEquals(0, policy.invocationCount, "insert must not invoke the key policy");
            assertTrue(repository.inserted.contains(incomplete), "insert must still reach the repository");
        }

        @Test
        @DisplayName("update_invokesKeyPolicy_rejectsIncompleteKey")
        void update_invokesKeyPolicy_rejectsIncompleteKey() {
            SysChargeTypeRow incomplete = withFinInst(completeRow(), 0L, null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> engine.update(incomplete));

            assertEquals("TF-FININST-MISSING", ex.legacyErrorCode());
            assertEquals(1, policy.invocationCount);
            assertTrue(repository.updated.isEmpty(), "H200-Update-Row must not run when H100 rejects the record");
        }

        @Test
        @DisplayName("update_invokesKeyPolicy_allowsCompleteKey")
        void update_invokesKeyPolicy_allowsCompleteKey() {
            SysChargeTypeRow complete = completeRow();

            engine.update(complete);

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.updated.contains(complete));
        }

        @Test
        @DisplayName("engineContract_select_invokesKeyPolicy_beforeReachingRepository")
        void engineContract_select_invokesKeyPolicy_beforeReachingRepository() {
            // See class-level javadoc: this pins DbiEngine's own generic
            // wiring, not literal F115ICT0 Select parity (F000-Select uses
            // F100-Prepare-Qualification, not H100-Check-Qualification, and
            // performs no such check in the real module).
            SysChargeTypeRow incomplete = withCtType(completeRow(), "");

            assertThrows(MissingKeyFieldException.class,
                    () -> engine.selectOne(incomplete));

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.selectOneCalls.isEmpty(), "repository must not be reached when the policy rejects");
        }

        @Test
        @DisplayName("engineContract_delete_invokesKeyPolicy_beforeReachingRepository")
        void engineContract_delete_invokesKeyPolicy_beforeReachingRepository() {
            // See class-level javadoc: this pins DbiEngine's own generic
            // wiring, not literal F115ICT0 Delete parity (I000-Delete uses
            // F100-Prepare-Qualification, not H100-Check-Qualification, and
            // performs no such check in the real module).
            SysChargeTypeRow incomplete = withFinInst(completeRow(), 0L, "");

            assertThrows(MissingKeyFieldException.class,
                    () -> engine.delete(incomplete));

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.deleteCalls.isEmpty(), "repository must not be reached when the policy rejects");
        }

        @Test
        @DisplayName("engineContract_delete_allowsCompleteKey")
        void engineContract_delete_allowsCompleteKey() {
            SysChargeTypeRow complete = completeRow();

            engine.delete(complete);

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.deleteCalls.contains(complete.key()));
        }

        @Test
        @DisplayName("engineContract_getRange_doesNotInvokeKeyPolicy")
        void engineContract_getRange_doesNotInvokeKeyPolicy() {
            // D000-Get-1st/E000-Get-Next never perform H100-Check-Qualification
            // either (only H000-Update does); DbiEngine mirrors that for the
            // cursor path, so this one IS literal F115ICT0 parity, not just an
            // engine-level convenience.
            repository.rowsByKey.put(completeRow().key(), completeRow());

            List<SysChargeTypeRow> rows = engine.getRange(completeRow().key());

            assertEquals(1, rows.size());
            assertEquals(0, policy.invocationCount);
        }
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Wraps a real policy while counting invocations, to prove insert skips it entirely. */
    private static final class CountingKeyPolicy implements KeyPolicy<SysChargeTypeKey, SysChargeTypeRow> {
        private final KeyPolicy<SysChargeTypeKey, SysChargeTypeRow> delegate;
        int invocationCount = 0;

        CountingKeyPolicy(KeyPolicy<SysChargeTypeKey, SysChargeTypeRow> delegate) {
            this.delegate = delegate;
        }

        @Override
        public SysChargeTypeKey resolveKey(SysChargeTypeRow record, FinInstResolver resolver) {
            invocationCount++;
            return delegate.resolveKey(record, resolver);
        }
    }

    /** Minimal in-memory fake standing in for F115ICTR / F115ICTU / DCLTF-Sys-Charge-Type access. */
    private static final class FakeSysChargeTypeRepository
            implements KeyedRepository<SysChargeTypeKey, SysChargeTypeRow> {
        final Map<SysChargeTypeKey, SysChargeTypeRow> rowsByKey = new HashMap<>();
        final List<SysChargeTypeRow> inserted = new ArrayList<>();
        final List<SysChargeTypeRow> updated = new ArrayList<>();
        final List<SysChargeTypeKey> selectOneCalls = new ArrayList<>();
        final List<SysChargeTypeKey> deleteCalls = new ArrayList<>();

        @Override
        public Optional<SysChargeTypeRow> selectOne(SysChargeTypeKey key) {
            selectOneCalls.add(key);
            return Optional.ofNullable(rowsByKey.get(key));
        }

        @Override
        public List<SysChargeTypeRow> getRange(SysChargeTypeKey partialKey) {
            return List.copyOf(rowsByKey.values());
        }

        @Override
        public void insert(SysChargeTypeRow record) {
            inserted.add(record);
            rowsByKey.put(record.key(), record);
        }

        @Override
        public void update(SysChargeTypeRow record) {
            updated.add(record);
            rowsByKey.put(record.key(), record);
        }

        @Override
        public void delete(SysChargeTypeKey key) {
            deleteCalls.add(key);
            rowsByKey.remove(key);
        }
    }

    // ------------------------------------------------------------------
    // Row-mutation helpers (SysChargeTypeRow has no setters by design)
    // ------------------------------------------------------------------

    private static SysChargeTypeRow withCtType(SysChargeTypeRow r, String ctType) {
        return new SysChargeTypeRow(ctType, r.finInstId(), r.finInstNo(), r.regno(), r.description(),
                r.methodCalc(), r.credit(), r.glCollAcc(), r.splitPct());
    }

    private static SysChargeTypeRow withFinInst(SysChargeTypeRow r, long finInstId, String finInstNo) {
        return new SysChargeTypeRow(r.ctType(), finInstId, finInstNo, r.regno(), r.description(),
                r.methodCalc(), r.credit(), r.glCollAcc(), r.splitPct());
    }

    private static SysChargeTypeRow withRegno(SysChargeTypeRow r, int regno) {
        return new SysChargeTypeRow(r.ctType(), r.finInstId(), r.finInstNo(), regno, r.description(),
                r.methodCalc(), r.credit(), r.glCollAcc(), r.splitPct());
    }
}
