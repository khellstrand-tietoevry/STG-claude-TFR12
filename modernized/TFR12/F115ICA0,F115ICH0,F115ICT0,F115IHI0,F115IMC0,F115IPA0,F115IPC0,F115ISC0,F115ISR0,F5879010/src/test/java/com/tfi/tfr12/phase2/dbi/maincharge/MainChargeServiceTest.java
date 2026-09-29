package com.tfi.tfr12.phase2.dbi.maincharge;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins RULE-016 (analysis/TFR12/BUSINESS_RULES.md) as implemented by
 * legacy/STG/TFR12/src/F115ICH0.src, the DBI module for table TF_Main_Charge:
 * H100-Check-Qualification (F115ICH0.src:868-996), performed only by
 * H000-Update (line 858), must fully resolve the 6-element natural key
 * (Fin-Inst-ID, Contract-Type, Contract-No, RP-Role-Type, Charge-Type,
 * CH-Seq-No) before an update proceeds, falling back to alternate
 * qualification input and to N100-Fetch-Fin-Inst-ID for the institution ID,
 * and rejecting with TF-FIN-INST-MISSING / TF-CONTRACT-MISSING /
 * TF-PARTY-MISSING / TF-CHARGE-MISSING otherwise. G000-Insert
 * (F115ICH0.src:783-826) performs no equivalent check at all - the documented
 * RULE-016 asymmetry.
 *
 * <p>The file is ISO-8859-1/Latin-1, not UTF-8; columns 8-72 are the code
 * area, column 7 '*' marks a comment. F115ICH0 could not be compiled or run:
 * it requires a DB2 precompiler not available in this environment. Every
 * expected value below was computed by hand-tracing F115ICH0.src's control
 * flow, not observed from a running program.
 *
 * <p><b>Discrepancy flagged (see {@link MainChargeKeyPolicy}'s javadoc for
 * detail):</b> in the real F115ICH0, only H000-Update calls
 * H100-Check-Qualification. F000-Select and I000-Delete both call
 * F100-Prepare-Qualification instead (F115ICH0.src:670-724, 1026-1033,
 * 726-753), which performs none of these checks. {@link DbiEngine} — shared
 * across every F115I*0 table in this module and out of this file's scope to
 * change — applies {@link KeyPolicy#validate} uniformly before Select, Update
 * and Delete for every table wired through it. The "select"/"delete invoke
 * the policy" tests below therefore pin {@code DbiEngine}'s own generic
 * wiring contract (a real and intentional property of the shared engine, and
 * of legacy RULE-018/F115ICA0's Update path), not a claim that F115ICH0's own
 * Select or Delete paragraphs perform this validation - they provably do not.
 * No test in this file asserts that F115ICH0's real Select/Delete behavior
 * matches this.
 */
class MainChargeServiceTest {

    private static MainChargeRow completeRow() {
        return new MainChargeRow(
                1001L, "0001",
                "LC", 555L,
                "USD", 1,
                "BUYER",
                "COMM",
                1,
                new BigDecimal("125.50"),
                "USD",
                new BigDecimal("0.500"),
                "Handling fee");
    }

    /** Never resolves anything: any call is a test-setup bug unless explicitly stubbed. */
    private static final FinInstResolver UNUSED_RESOLVER = finInstNo -> {
        throw new AssertionError("resolver should not have been called for finInstNo=" + finInstNo);
    };

    // ------------------------------------------------------------------
    // KeyPolicy direct tests - RULE-016
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("MainChargeKeyPolicy.validate - RULE-016 (F115ICH0.src:868-996)")
    class KeyPolicyTests {

        private final MainChargeKeyPolicy policy = new MainChargeKeyPolicy();

        @Test
        @DisplayName("rule016_allFieldsPresent_noException")
        void rule016_allFieldsPresent_noException() {
            policy.resolveKey(completeRow(), UNUSED_RESOLVER);
            // No exception: H100-Check-Qualification falls through all 6
            // Evaluates with "Continue" (F115ICH0.src:872, 903, 922, 941, 963, 981).
        }

        @Test
        @DisplayName("rule016_missingFinInstId_andFinInstNo_throwsFinInstMissing")
        void rule016_missingFinInstId_andFinInstNo_throwsFinInstMissing() {
            MainChargeRow row = withFinInst(completeRow(), 0L, null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:891-897 - "When other" branch of the Fin-Inst Evaluate.
            assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_finInstIdZero_butFinInstNoPresent_resolvesViaN100_noException")
        void rule016_finInstIdZero_butFinInstNoPresent_resolvesViaN100_noException() {
            MainChargeRow row = withFinInst(completeRow(), 0L, "0001");
            FinInstResolver resolver = finInstNo -> {
                assertEquals("0001", finInstNo);
                return 1001L;
            };

            // F115ICH0.src:874-878 - Fin-Inst-No branch calls N100-Fetch-Fin-Inst-ID.
            policy.resolveKey(row, resolver);
        }

        @Test
        @DisplayName("rule016_finInstNoUnresolvable_resolverThrows_propagatesFinInstMissing")
        void rule016_finInstNoUnresolvable_resolverThrows_propagatesFinInstMissing() {
            MainChargeRow row = withFinInst(completeRow(), 0L, "9999");
            FinInstResolver resolver = finInstNo -> {
                throw new MissingKeyFieldException("TF-FIN-INST-MISSING");
            };

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, resolver));
            assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_missingContractType_throwsContractMissing")
        void rule016_missingContractType_throwsContractMissing() {
            MainChargeRow row = withContractType(completeRow(), " ");

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:910-917.
            assertEquals("TF-CONTRACT-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_missingContractNo_throwsContractMissing")
        void rule016_missingContractNo_throwsContractMissing() {
            MainChargeRow row = withContractNo(completeRow(), 0L);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:929-936.
            assertEquals("TF-CONTRACT-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_missingRpRoleType_throwsPartyMissing")
        void rule016_missingRpRoleType_throwsPartyMissing() {
            MainChargeRow row = withRpRoleType(completeRow(), null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:952-958 - error mnemonic 'TF-PARTY-MISSING'.
            assertEquals("TF-PARTY-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_missingChargeType_throwsChargeMissing")
        void rule016_missingChargeType_throwsChargeMissing() {
            MainChargeRow row = withChargeType(completeRow(), "");

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:970-976.
            assertEquals("TF-CHARGE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_missingChSeqNo_throwsChargeMissing")
        void rule016_missingChSeqNo_throwsChargeMissing() {
            MainChargeRow row = withChSeqNo(completeRow(), 0);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // F115ICH0.src:987-993.
            assertEquals("TF-CHARGE-MISSING", ex.legacyErrorCode());
        }

        @Test
        @DisplayName("rule016_caCurrencyAndPaSeqNoBlank_stillNoException_notIndependentlyValidated")
        void rule016_caCurrencyAndPaSeqNoBlank_stillNoException_notIndependentlyValidated() {
            MainChargeRow base = completeRow();
            MainChargeRow row = new MainChargeRow(
                    base.finInstId(), base.finInstNo(),
                    base.contractType(), base.contractNo(),
                    null, 0, // CA-Currency / PA-Seq-No blank/zero
                    base.rpRoleType(), base.chargeType(), base.chSeqNo(),
                    base.chargeAmount(), base.currency(), base.chargePct(), base.chargeText());

            // See MainChargeKey's javadoc: H100 never independently tests
            // CA-Currency/PA-Seq-No for blank/zero (F115ICH0.src:939-951), so
            // this is NOT one of the RULE-016 mandatory elements for Main Charge
            // (unlike Part Charge, RULE-016's documented "2 additional" case).
            policy.resolveKey(row, UNUSED_RESOLVER);
        }

        @Test
        @DisplayName("rule016_multipleFieldsMissing_lastFailureWins")
        void rule016_multipleFieldsMissing_lastFailureWins() {
            MainChargeRow row = withChargeType(withContractType(completeRow(), " "), "");

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> policy.resolveKey(row, UNUSED_RESOLVER));

            // "Last failure wins" (standardized across every table's KeyPolicy,
            // see KeyPolicy's class javadoc): Contract-Type is checked before
            // Charge-Type in H100's source order (F115ICH0.src:901 precedes
            // :961) with no short-circuit between them, so when both are
            // missing, Charge-Type's error - checked last - is what actually
            // survives to the caller.
            assertEquals("TF-CHARGE-MISSING", ex.legacyErrorCode());
        }
    }

    // ------------------------------------------------------------------
    // DbiEngine wiring tests
    // ------------------------------------------------------------------

    @Nested
    @DisplayName("DbiEngine<MainChargeKey, MainChargeRow> wiring")
    class EngineWiringTests {

        private FakeMainChargeRepository repository;
        private CountingKeyPolicy policy;
        private DbiEngine<MainChargeKey, MainChargeRow> engine;

        @BeforeEach
        void setUp() {
            repository = new FakeMainChargeRepository();
            policy = new CountingKeyPolicy(new MainChargeKeyPolicy());
            engine = new DbiEngine<>(repository, policy, Capabilities.FULL, UNUSED_RESOLVER);
        }

        @Test
        @DisplayName("rule016_insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey")
        void rule016_insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey() {
            // Deliberately incomplete: blank Contract-Type, zero CH-Seq-No.
            MainChargeRow incomplete = withChSeqNo(withContractType(completeRow(), " "), 0);

            // G000-Insert (F115ICH0.src:783-826) calls G100-Move-Input-To-DCL
            // directly with no qualification check - RULE-016's asymmetry.
            engine.insert(incomplete);

            assertEquals(0, policy.invocationCount, "insert must not invoke the RULE-016 key policy");
            assertTrue(repository.inserted.contains(incomplete), "insert must still reach the repository");
        }

        @Test
        @DisplayName("rule016_update_invokesKeyPolicy_rejectsIncompleteKey")
        void rule016_update_invokesKeyPolicy_rejectsIncompleteKey() {
            MainChargeRow incomplete = withRpRoleType(completeRow(), null);

            MissingKeyFieldException ex =
                    assertThrows(MissingKeyFieldException.class, () -> engine.update(incomplete));

            assertEquals("TF-PARTY-MISSING", ex.legacyErrorCode());
            assertEquals(1, policy.invocationCount);
            assertTrue(repository.updated.isEmpty(), "H200-Update-Row must not run when H100 rejects the record");
        }

        @Test
        @DisplayName("rule016_update_invokesKeyPolicy_allowsCompleteKey")
        void rule016_update_invokesKeyPolicy_allowsCompleteKey() {
            MainChargeRow complete = completeRow();

            engine.update(complete);

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.updated.contains(complete));
        }

        @Test
        @DisplayName("engineContract_select_invokesKeyPolicy_beforeReachingRepository")
        void engineContract_select_invokesKeyPolicy_beforeReachingRepository() {
            // See class-level javadoc: this pins DbiEngine's own generic
            // wiring, not literal F115ICH0 Select parity (F000-Select uses
            // F100-Prepare-Qualification, not H100-Check-Qualification, and
            // performs no such check in the real module).
            MainChargeRow incomplete = withChargeType(completeRow(), "");

            assertThrows(MissingKeyFieldException.class,
                    () -> engine.selectOne(incomplete));

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.selectOneCalls.isEmpty(), "repository must not be reached when the policy rejects");
        }

        @Test
        @DisplayName("engineContract_delete_invokesKeyPolicy_beforeReachingRepository")
        void engineContract_delete_invokesKeyPolicy_beforeReachingRepository() {
            // See class-level javadoc: this pins DbiEngine's own generic
            // wiring, not literal F115ICH0 Delete parity (I000-Delete uses
            // F100-Prepare-Qualification, not H100-Check-Qualification, and
            // performs no such check in the real module).
            MainChargeRow incomplete = withChSeqNo(completeRow(), 0);

            assertThrows(MissingKeyFieldException.class,
                    () -> engine.delete(incomplete));

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.deleteCalls.isEmpty(), "repository must not be reached when the policy rejects");
        }

        @Test
        @DisplayName("engineContract_delete_allowsCompleteKey")
        void engineContract_delete_allowsCompleteKey() {
            MainChargeRow complete = completeRow();

            engine.delete(complete);

            assertEquals(1, policy.invocationCount);
            assertTrue(repository.deleteCalls.contains(complete.key()));
        }
    }

    // ------------------------------------------------------------------
    // Test doubles
    // ------------------------------------------------------------------

    /** Wraps a real policy while counting invocations, to prove insert skips it entirely. */
    private static final class CountingKeyPolicy implements KeyPolicy<MainChargeKey, MainChargeRow> {
        private final KeyPolicy<MainChargeKey, MainChargeRow> delegate;
        int invocationCount = 0;

        CountingKeyPolicy(KeyPolicy<MainChargeKey, MainChargeRow> delegate) {
            this.delegate = delegate;
        }

        @Override
        public MainChargeKey resolveKey(MainChargeRow record, FinInstResolver resolver) {
            invocationCount++;
            return delegate.resolveKey(record, resolver);
        }
    }

    /** Minimal in-memory fake standing in for F115ICHU / DCLTF-Main-Charge access. */
    private static final class FakeMainChargeRepository implements KeyedRepository<MainChargeKey, MainChargeRow> {
        final Map<MainChargeKey, MainChargeRow> rowsByKey = new HashMap<>();
        final List<MainChargeRow> inserted = new ArrayList<>();
        final List<MainChargeRow> updated = new ArrayList<>();
        final List<MainChargeKey> selectOneCalls = new ArrayList<>();
        final List<MainChargeKey> deleteCalls = new ArrayList<>();

        @Override
        public Optional<MainChargeRow> selectOne(MainChargeKey key) {
            selectOneCalls.add(key);
            return Optional.ofNullable(rowsByKey.get(key));
        }

        @Override
        public List<MainChargeRow> getRange(MainChargeKey partialKey) {
            return List.copyOf(rowsByKey.values());
        }

        @Override
        public void insert(MainChargeRow record) {
            inserted.add(record);
            rowsByKey.put(record.key(), record);
        }

        @Override
        public void update(MainChargeRow record) {
            updated.add(record);
            rowsByKey.put(record.key(), record);
        }

        @Override
        public void delete(MainChargeKey key) {
            deleteCalls.add(key);
            rowsByKey.remove(key);
        }
    }

    // ------------------------------------------------------------------
    // Row-mutation helpers (MainChargeRow has no setters by design)
    // ------------------------------------------------------------------

    private static MainChargeRow withFinInst(MainChargeRow r, long finInstId, String finInstNo) {
        return new MainChargeRow(finInstId, finInstNo, r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.rpRoleType(), r.chargeType(), r.chSeqNo(), r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }

    private static MainChargeRow withContractType(MainChargeRow r, String contractType) {
        return new MainChargeRow(r.finInstId(), r.finInstNo(), contractType, r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.rpRoleType(), r.chargeType(), r.chSeqNo(), r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }

    private static MainChargeRow withContractNo(MainChargeRow r, long contractNo) {
        return new MainChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), contractNo, r.caCurrency(),
                r.paSeqNo(), r.rpRoleType(), r.chargeType(), r.chSeqNo(), r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }

    private static MainChargeRow withRpRoleType(MainChargeRow r, String rpRoleType) {
        return new MainChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), rpRoleType, r.chargeType(), r.chSeqNo(), r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }

    private static MainChargeRow withChargeType(MainChargeRow r, String chargeType) {
        return new MainChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.rpRoleType(), chargeType, r.chSeqNo(), r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }

    private static MainChargeRow withChSeqNo(MainChargeRow r, int chSeqNo) {
        return new MainChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.rpRoleType(), r.chargeType(), chSeqNo, r.chargeAmount(), r.currency(),
                r.chargePct(), r.chargeText());
    }
}
