package com.tfi.tfr12.phase2.dbi.partcharge;

import com.tfi.tfr12.phase2.dbi.Capabilities;
import com.tfi.tfr12.phase2.dbi.DbiEngine;
import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyedRepository;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Characterization tests for TF_Part_Charge's key-qualification rule,
 * RULE-016 (F115IPC0.src:946-1086, H100-Check-Qualification), confirmed
 * against the source rather than trusted from the BUSINESS_RULES.md summary:
 *
 * <p>7 mandatory elements and their exact legacy error mnemonics:
 * <ol>
 *   <li>Fin-Inst-ID / Fin-Inst-No -&gt; {@code TF-FIN-INST-MISSING}</li>
 *   <li>Contract-Type -&gt; {@code TF-CONTRACT-MISSING}</li>
 *   <li>Contract-No -&gt; {@code TF-CONTRACT-MISSING}</li>
 *   <li>CA-Currency -&gt; {@code TF-PART-AMOUNT-MISSING} (extra vs F115ICH0)</li>
 *   <li>PA-Seq-No -&gt; {@code TF-PART-AMOUNT-MISSING} (extra vs F115ICH0)</li>
 *   <li>Role-Type -&gt; {@code TF-ROLE-MISSING}</li>
 *   <li>CT-Type -&gt; {@code TF-CHARGE-MISSING}</li>
 * </ol>
 *
 * <p>Insert (G000-Insert, F115IPC0.src:860-904) performs none of these checks
 * — the same documented asymmetry RULE-016 calls out for F115ICH0.
 */
class PartChargeServiceTest {

    /** A row with all 7 RULE-016 mandatory key elements present. */
    private static PartChargeRow completeRow() {
        return new PartChargeRow(
                123456789L,             // finInstId
                null,                   // finInstNo (not needed, ID already resolved)
                "ILC",                  // contractType
                4711L,                  // contractNo
                "USD",                  // caCurrency
                1,                      // paSeqNo
                "APPL",                 // roleType
                "COMMISSION",           // ctType
                "USD",                  // currency
                new BigDecimal("125.50"), // amount
                "APPL",                 // rolePayingParty
                new BigDecimal("0.500"), // chargePct
                "Part charge text");    // chargeText
    }

    private static final FinInstResolver NEVER_CALLED = finInstNo -> {
        throw new AssertionError("resolver should not have been called for finInstNo=" + finInstNo);
    };

    // ---------------------------------------------------------------
    // RULE-016: all 7 fields present -> no exception
    // ---------------------------------------------------------------

    @Test
    void rule016_allSevenMandatoryFieldsPresent_validatesWithoutException() {
        PartChargeKeyPolicy policy = new PartChargeKeyPolicy();
        policy.resolveKey(completeRow(), NEVER_CALLED);
        // no exception == pass; F115IPC0.src:946-1086 falls through to H200-Update-Row
    }

    // ---------------------------------------------------------------
    // RULE-016: field 1 - Fin-Inst (F115IPC0.src:948-976)
    // ---------------------------------------------------------------

    @Test
    void rule016_missingFinInstIdAndFinInstNo_throwsFinInstMissing() {
        PartChargeRow row = withFinInst(completeRow(), 0L, null);
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
    }

    @Test
    void rule016_finInstIdZero_butFinInstNoResolvable_fallsBackViaResolver_noException() {
        PartChargeRow row = withFinInst(completeRow(), 0L, "0000000000012345");
        FinInstResolver resolver = finInstNo -> {
            assertEquals("0000000000012345", finInstNo);
            return 123456789L;
        };
        new PartChargeKeyPolicy().resolveKey(row, resolver);
        // F115IPC0.src:952-956 — N100-Fetch-Fin-Inst-ID resolves External No -> internal ID
    }

    @Test
    void rule016_finInstNoUnresolvable_propagatesFinInstMissingFromResolver() {
        PartChargeRow row = withFinInst(completeRow(), 0L, "0000000000099999");
        FinInstResolver resolver = finInstNo -> {
            throw new MissingKeyFieldException("TF-FIN-INST-MISSING");
        };
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, resolver));
        assertEquals("TF-FIN-INST-MISSING", ex.legacyErrorCode());
    }

    // ---------------------------------------------------------------
    // RULE-016: fields 2-3 - Contract-Type / Contract-No (F115IPC0.src:979-1015)
    // ---------------------------------------------------------------

    @Test
    void rule016_missingContractType_throwsContractMissing() {
        PartChargeRow row = withContractType(completeRow(), " ");
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-CONTRACT-MISSING", ex.legacyErrorCode());
    }

    @Test
    void rule016_missingContractNo_throwsContractMissing() {
        PartChargeRow row = withContractNo(completeRow(), 0L);
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-CONTRACT-MISSING", ex.legacyErrorCode());
    }

    // ---------------------------------------------------------------
    // RULE-016: fields 4-5 - CA-Currency / PA-Seq-No, the 2 elements
    // F115IPC0 requires beyond F115ICH0's pattern (F115IPC0.src:1017-1052).
    // ---------------------------------------------------------------

    @Test
    void rule016_missingCaCurrency_throwsPartAmountMissing_theFirstExtraFieldVsMainCharge() {
        PartChargeRow row = withCaCurrency(completeRow(), "");
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-PART-AMOUNT-MISSING", ex.legacyErrorCode());
    }

    @Test
    void rule016_missingPaSeqNo_throwsPartAmountMissing_theSecondExtraFieldVsMainCharge() {
        PartChargeRow row = withPaSeqNo(completeRow(), 0);
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-PART-AMOUNT-MISSING", ex.legacyErrorCode());
    }

    // ---------------------------------------------------------------
    // RULE-016: field 6 - Role-Type (F115IPC0.src:1054-1069)
    // ---------------------------------------------------------------

    @Test
    void rule016_missingRoleType_throwsRoleMissing() {
        PartChargeRow row = withRoleType(completeRow(), null);
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-ROLE-MISSING", ex.legacyErrorCode());
    }

    // ---------------------------------------------------------------
    // RULE-016: field 7 - CT-Type (F115IPC0.src:1071-1086)
    // ---------------------------------------------------------------

    @Test
    void rule016_missingCtType_throwsChargeMissing() {
        PartChargeRow row = withCtType(completeRow(), "   ");
        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> new PartChargeKeyPolicy().resolveKey(row, NEVER_CALLED));
        assertEquals("TF-CHARGE-MISSING", ex.legacyErrorCode());
    }

    // ---------------------------------------------------------------
    // RULE-016: DbiEngine wiring - Insert skips the check, Update/Select/
    // Delete enforce it (asymmetry documented at F115IPC0.src:860-904 vs
    // 934-946/1089+).
    // ---------------------------------------------------------------

    @Test
    void rule016_insertViaEngine_performsNoQualificationCheck_blankKeyRowIsStoredAsIs() {
        InMemoryPartChargeRepository repo = new InMemoryPartChargeRepository();
        DbiEngine<PartChargeKey, PartChargeRow> engine =
                new DbiEngine<>(repo, new PartChargeKeyPolicy(), Capabilities.FULL, NEVER_CALLED);

        PartChargeRow blankKeyRow = withFinInst(completeRow(), 0L, null); // would fail the check
        engine.insert(blankKeyRow); // G000-Insert never calls H100-Check-Qualification

        assertTrue(repo.inserted.contains(blankKeyRow));
    }

    @Test
    void rule016_updateViaEngine_enforcesQualificationCheck_beforeReachingRepository() {
        InMemoryPartChargeRepository repo = new InMemoryPartChargeRepository();
        DbiEngine<PartChargeKey, PartChargeRow> engine =
                new DbiEngine<>(repo, new PartChargeKeyPolicy(), Capabilities.FULL, NEVER_CALLED);

        PartChargeRow blankCtTypeRow = withCtType(completeRow(), null);

        MissingKeyFieldException ex = assertThrows(MissingKeyFieldException.class,
                () -> engine.update(blankCtTypeRow));
        assertEquals("TF-CHARGE-MISSING", ex.legacyErrorCode());
        assertFalse(repo.updateCalled, "H200-Update-Row must not run when H100-Check-Qualification fails");
    }

    @Test
    void rule016_updateViaEngine_withAllSevenFieldsPresent_reachesRepository() {
        InMemoryPartChargeRepository repo = new InMemoryPartChargeRepository();
        DbiEngine<PartChargeKey, PartChargeRow> engine =
                new DbiEngine<>(repo, new PartChargeKeyPolicy(), Capabilities.FULL, NEVER_CALLED);

        PartChargeRow row = completeRow();
        engine.update(row);

        assertTrue(repo.updateCalled);
        assertEquals(row, repo.lastUpdated);
    }

    // ---------------------------------------------------------------
    // Row mutation helpers (records are immutable; each helper rebuilds
    // completeRow() with exactly one field changed, mirroring "every other
    // mandatory field present" test setup).
    // ---------------------------------------------------------------

    private static PartChargeRow withFinInst(PartChargeRow r, long finInstId, String finInstNo) {
        return new PartChargeRow(finInstId, finInstNo, r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.roleType(), r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withContractType(PartChargeRow r, String contractType) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), contractType, r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.roleType(), r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withContractNo(PartChargeRow r, long contractNo) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), contractNo, r.caCurrency(),
                r.paSeqNo(), r.roleType(), r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withCaCurrency(PartChargeRow r, String caCurrency) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), caCurrency,
                r.paSeqNo(), r.roleType(), r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withPaSeqNo(PartChargeRow r, int paSeqNo) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                paSeqNo, r.roleType(), r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withRoleType(PartChargeRow r, String roleType) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), roleType, r.ctType(), r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    private static PartChargeRow withCtType(PartChargeRow r, String ctType) {
        return new PartChargeRow(r.finInstId(), r.finInstNo(), r.contractType(), r.contractNo(), r.caCurrency(),
                r.paSeqNo(), r.roleType(), ctType, r.currency(), r.amount(), r.rolePayingParty(),
                r.chargePct(), r.chargeText());
    }

    /** A minimal in-memory fake standing in for F115IPCU (the real DB2 access module). */
    private static final class InMemoryPartChargeRepository implements KeyedRepository<PartChargeKey, PartChargeRow> {
        final Map<PartChargeKey, PartChargeRow> rows = new HashMap<>();
        final List<PartChargeRow> inserted = new java.util.ArrayList<>();
        boolean updateCalled = false;
        PartChargeRow lastUpdated;

        @Override
        public Optional<PartChargeRow> selectOne(PartChargeKey key) {
            return Optional.ofNullable(rows.get(key));
        }

        @Override
        public List<PartChargeRow> getRange(PartChargeKey partialKey) {
            return List.copyOf(rows.values());
        }

        @Override
        public void insert(PartChargeRow record) {
            inserted.add(record);
            rows.put(record.key(), record);
        }

        @Override
        public void update(PartChargeRow record) {
            updateCalled = true;
            lastUpdated = record;
            rows.put(record.key(), record);
        }

        @Override
        public void delete(PartChargeKey key) {
            rows.remove(key);
        }
    }
}
