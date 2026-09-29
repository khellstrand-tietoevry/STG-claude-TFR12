package com.tfi.tfr12.phase2.dbi.history;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiActionNotSupportedException;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

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
 * <p>Pins RULE-028 (TF_History is append-only: Select and Update are
 * disabled, only Get-1st/Get-Next/Insert/Delete work) as literally coded in
 * legacy/TFR12/src/F115IHI0.src's C000-Interface dispatcher
 * (lines 309-347). The file is ISO-8859-1/Latin-1, not UTF-8; column 7 '*'
 * marks a comment, columns 8-72 are the code area. F115IHI0 requires an
 * embedded-SQL DB2 precompiler this environment does not have, so it could
 * not be compiled or run; every expected value below was computed by
 * hand-tracing the control flow, not observed from a running program.
 *
 * <p><b>What C000-Interface actually dispatches</b> (F115IHI0.src:313-345):
 * <pre>
 *   When R115IHI0-In-Get-1st   -&gt; C200-Initialize-Output, D000-Get-1st, E000-Get-Next   (live, :314-317)
 *   When R115IHI0-In-Get-Next  -&gt; C200-Initialize-Output, E000-Get-Next                 (live, :319-321)
 *  *When R115IHI0-In-Select    -&gt; C200-Initialize-Output, F000-Select                   (COMMENTED OUT, :323-325)
 *   When R115IHI0-In-Insert    -&gt; G000-Insert                                           (live, :327-328)
 *  *When R115IHI0-In-Update    -&gt; H000-Update                                           (COMMENTED OUT, :330-331)
 *   When R115IHI0-In-Delete    -&gt; I000-Delete                                           (live, :333-334)
 *   When Other                 -&gt; 'GL-IN-AKSJON-UV', Z900-Abend                         (live, :336-343)
 * </pre>
 * A Select or Update request therefore falls through to "When Other" and
 * abends. {@code F000-Select} (F115IHI0.src:705-757) and {@code H000-Update}
 * (F115IHI0.src:880-891) both still exist as source text — every one of
 * their lines is prefixed with the column-7 comment marker '*' — confirming
 * the restriction is a deliberate disablement, not an accidental deletion.
 *
 * <p>{@link Capabilities#APPEND_ONLY} ({@code select=false, update=false},
 * cursor/insert/delete all {@code true}) matches this exactly: verified
 * against F115IHI0.src directly, not assumed from its own Javadoc.
 *
 * <p><b>Key validation finding (see {@link HistoryKeyPolicy}'s own Javadoc
 * for the full trace):</b> unlike ContractAmount/ContractCharge/PartCharge,
 * F115IHI0's H100-Check-Qualification (F115IHI0.src:892-1017) is called from
 * nowhere reachable — its only call site is inside the commented-out
 * H000-Update (F115IHI0.src:882). I000-Delete (F115IHI0.src:1045-1052) uses
 * F100-Prepare-Qualification instead, which performs no mandatory-field
 * check. So {@link HistoryKeyPolicy} is a genuine no-op here, not a stand-in
 * for missing work — {@code KeyPolicyIsNoOpTests} below pins that directly,
 * and because {@code Capabilities.APPEND_ONLY} disables Select and Update,
 * {@link DbiEngine} in practice never even reaches
 * {@link HistoryKeyPolicy#validate} for this table (the capability check
 * rejects Select/Update first) — {@code DbiEngineWiringTests} pins that too.
 *
 * <p>No credential-like values appear in F115IHI0.src; nothing here needed
 * substitution.
 */
class HistoryServiceTest {

    // =====================================================================
    // Test doubles
    // =====================================================================

    /** Simple Map-backed stand-in for the real (not-yet-built) DB2 access. */
    private static final class FakeHistoryRepository implements KeyedRepository<HistoryKey, HistoryRow> {
        private final Map<HistoryKey, HistoryRow> rows = new HashMap<>();
        int selectOneCalls = 0;
        int getRangeCalls = 0;
        int insertCalls = 0;
        int updateCalls = 0;
        int deleteCalls = 0;

        FakeHistoryRepository with(HistoryKey key, HistoryRow row) {
            rows.put(key, row);
            return this;
        }

        @Override
        public Optional<HistoryRow> selectOne(HistoryKey key) {
            selectOneCalls++;
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<HistoryRow> getRange(HistoryKey partialKey) {
            getRangeCalls++;
            return new ArrayList<>(rows.values());
        }

        @Override
        public void insert(HistoryRow record) {
            insertCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void update(HistoryRow record) {
            updateCalls++;
            rows.put(keyOf(record), record);
        }

        @Override
        public void delete(HistoryKey key) {
            deleteCalls++;
            rows.remove(key);
        }

        private static HistoryKey keyOf(HistoryRow r) {
            return new HistoryKey(r.finInstId(), r.contractType(), r.contractNo(),
                    r.caCurrency(), r.paSeqNo(), r.hiSeqNo());
        }
    }

    /** Not expected to ever be called for this table's append-only flows below. */
    private static final FinInstResolver UNUSED_RESOLVER = finInstNo -> {
        throw new AssertionError("FinInstResolver should not be invoked by these tests");
    };

    private static HistoryRow row(int hiSeqNo) {
        return new HistoryRow(
                1001L, "", "ILC", 4500001L, "USD", 3, hiSeqNo,
                "CONTRACT-CREATED", "USER001", "2026-09-29-10.15.00.000000", 17, "Contract booked");
    }

    private static HistoryKey keyOf(HistoryRow r) {
        return new HistoryKey(r.finInstId(), r.contractType(), r.contractNo(),
                r.caCurrency(), r.paSeqNo(), r.hiSeqNo());
    }

    // =====================================================================
    // RULE-028: DbiEngine wiring for TF_History (Capabilities.APPEND_ONLY)
    // =====================================================================
    @Nested
    @DisplayName("RULE-028: TF_History is append-only (F115IHI0.src:309-347)")
    class DbiEngineWiringTests {

        private final HistoryKeyPolicy keyPolicy = new HistoryKeyPolicy();
        private final FakeHistoryRepository repository = new FakeHistoryRepository();
        private final DbiEngine<HistoryKey, HistoryRow> engine =
                new DbiEngine<>(repository, keyPolicy, Capabilities.APPEND_ONLY, UNUSED_RESOLVER);

        @Test
        @DisplayName("rule028_selectOne_throwsDbiActionNotSupported_becauseFOneOfZeroDashSelectIsCommentedOut")
        void rule028_selectOneThrowsActionNotSupported() {
            // F115IHI0.src:323-325 - the "When R115IHI0-In-Select" branch is
            // entirely commented out of C000-Interface's Evaluate, so a Select
            // request falls through to "When Other" (:336-343) and abends with
            // 'GL-IN-AKSJON-UV'. DbiActionNotSupportedException is the modern
            // equivalent of that abend.
            HistoryKey key = new HistoryKey(1001L, "ILC", 4500001L, "USD", 3, 1);
            repository.with(key, row(1));

            assertThrows(DbiActionNotSupportedException.class, () -> engine.selectOne(row(1)));
            assertEquals(0, repository.selectOneCalls,
                    "repository must never be reached - the capability check rejects Select before that");
        }

        @Test
        @DisplayName("rule028_update_throwsDbiActionNotSupported_becauseHZeroZeroZeroDashUpdateIsCommentedOut")
        void rule028_updateThrowsActionNotSupported() {
            // F115IHI0.src:330-331 - the "When R115IHI0-In-Update" branch is
            // also commented out; same "When Other" -> GL-IN-AKSJON-UV -> abend
            // fallthrough as Select.
            assertThrows(DbiActionNotSupportedException.class, () -> engine.update(row(1)));
            assertEquals(0, repository.updateCalls,
                    "repository must never be reached - the capability check rejects Update before that");
        }

        @Test
        @DisplayName("rule028_getRange_worksNormally_cursorGet1stAndGetNextAreLive")
        void rule028_getRangeWorksNormally() {
            // F115IHI0.src:313-321 - Get-1st and Get-Next are both live branches.
            HistoryKey partialKey = new HistoryKey(1001L, "ILC", 4500001L, "USD", 3, 0);
            repository.with(keyOf(row(1)), row(1)).with(keyOf(row(2)), row(2));

            List<HistoryRow> found = engine.getRange(partialKey);

            assertEquals(2, found.size());
            assertEquals(1, repository.getRangeCalls);
        }

        @Test
        @DisplayName("rule028_insert_worksNormally_gZeroZeroZeroDashInsertIsLive")
        void rule028_insertWorksNormally() {
            // F115IHI0.src:327-328 - Insert is a live branch, unconditionally.
            HistoryRow newRow = row(0); // HI-Seq-No 0 on input; DB2/the DCL assigns the real one (F115IHI0.src:865-867)

            engine.insert(newRow);

            assertEquals(1, repository.insertCalls);
        }

        @Test
        @DisplayName("rule028_delete_worksNormally_iZeroZeroZeroDashDeleteIsLive")
        void rule028_deleteWorksNormally() {
            // F115IHI0.src:333-334 - Delete is a live branch, unconditionally.
            HistoryKey key = new HistoryKey(1001L, "ILC", 4500001L, "USD", 3, 7);
            repository.with(key, row(7));

            engine.delete(row(7));

            assertEquals(1, repository.deleteCalls);
            assertFalse(repository.rows.containsKey(key));
        }
    }

    // =====================================================================
    // HistoryKeyPolicy exercised directly: it is a genuine no-op for this
    // table, not a stand-in for missing/future work. See its own Javadoc for
    // the full F115IHI0.src trace proving H100-Check-Qualification is dead
    // code here.
    // =====================================================================
    @Nested
    @DisplayName("HistoryKeyPolicy is a no-op - H100-Check-Qualification is unreachable for TF_History "
            + "(F115IHI0.src: only call site is line 882, inside the commented-out H000-Update, :880-891)")
    class KeyPolicyIsNoOpTests {

        private final HistoryKeyPolicy policy = new HistoryKeyPolicy();

        @Test
        @DisplayName("rule028_completelyBlankRecord_passesValidation_noCheckExistsForThisTable")
        void rule028_blankRecordPassesValidation() {
            HistoryRow blank = new HistoryRow(0L, "", "", 0L, "", 0, 0, "", "", "", 0, "");

            policy.resolveKey(blank, UNUSED_RESOLVER);
            // no exception, and UNUSED_RESOLVER (which throws if called) was
            // never invoked -> confirms no field is checked and no
            // Fin-Inst-No resolution is even attempted by this policy.
        }

        @Test
        @DisplayName("rule028_fullyPopulatedRecord_alsoPassesValidation_trivially")
        void rule028_populatedRecordPassesValidation() {
            policy.resolveKey(row(1), UNUSED_RESOLVER);
        }
    }

    // =====================================================================
    // Proves the DbiEngine capability check runs BEFORE the key policy for
    // this table's disabled actions - i.e. Select/Update are rejected by
    // RULE-028's capability gate, never reaching (the no-op) HistoryKeyPolicy
    // at all. This matters because it shows HistoryKeyPolicy is provably
    // unreachable via DbiEngine for Select/Update, consistent with
    // H100-Check-Qualification's unreachability in the legacy source itself.
    // =====================================================================
    @Nested
    @DisplayName("RULE-028: capability gate precedes key-policy for the disabled actions")
    class CapabilityGatePrecedesKeyPolicyTests {

        private final FakeHistoryRepository repository = new FakeHistoryRepository();

        @Test
        @DisplayName("rule028_selectOnDisabledCapability_neverReachesKeyPolicy")
        void rule028_selectNeverReachesKeyPolicy() {
            java.util.concurrent.atomic.AtomicBoolean policyInvoked = new java.util.concurrent.atomic.AtomicBoolean(false);
            com.tfi.tfr12.phase2.dbi.KeyPolicy<HistoryKey, HistoryRow> spyPolicy = (record, resolver) -> { policyInvoked.set(true); return null; };
            DbiEngine<HistoryKey, HistoryRow> engine =
                    new DbiEngine<>(repository, spyPolicy, Capabilities.APPEND_ONLY, UNUSED_RESOLVER);

            assertThrows(DbiActionNotSupportedException.class,
                    () -> engine.selectOne(row(1)));

            assertFalse(policyInvoked.get(),
                    "capability check for the disabled Select action must short-circuit before any key policy runs");
        }

        @Test
        @DisplayName("rule028_updateOnDisabledCapability_neverReachesKeyPolicy")
        void rule028_updateNeverReachesKeyPolicy() {
            java.util.concurrent.atomic.AtomicBoolean policyInvoked = new java.util.concurrent.atomic.AtomicBoolean(false);
            com.tfi.tfr12.phase2.dbi.KeyPolicy<HistoryKey, HistoryRow> spyPolicy = (record, resolver) -> { policyInvoked.set(true); return null; };
            DbiEngine<HistoryKey, HistoryRow> engine =
                    new DbiEngine<>(repository, spyPolicy, Capabilities.APPEND_ONLY, UNUSED_RESOLVER);

            assertThrows(DbiActionNotSupportedException.class, () -> engine.update(row(1)));

            assertFalse(policyInvoked.get(),
                    "capability check for the disabled Update action must short-circuit before any key policy runs");
        }
    }

    // =====================================================================
    // Sanity: the very fact that getRange/insert/delete run all the way
    // through to the fake repository (asserted above) is itself the proof
    // that these three actions were NOT rejected as unsupported - i.e. they
    // are provably distinct from Select/Update above.
    // =====================================================================
    @Test
    @DisplayName("rule028_capabilitiesAppendOnly_matchesWhatF115IHI0DotSrcActuallyDispatches")
    void rule028_capabilitiesFieldValuesMatchSource() {
        assertFalse(Capabilities.APPEND_ONLY.select(), "F115IHI0.src:323-325 - Select branch is commented out");
        assertTrue(Capabilities.APPEND_ONLY.cursor(), "F115IHI0.src:314-321 - Get-1st/Get-Next are live");
        assertTrue(Capabilities.APPEND_ONLY.insert(), "F115IHI0.src:327-328 - Insert is live");
        assertFalse(Capabilities.APPEND_ONLY.update(), "F115IHI0.src:330-331 - Update branch is commented out");
        assertTrue(Capabilities.APPEND_ONLY.delete(), "F115IHI0.src:333-334 - Delete is live");
    }
}
