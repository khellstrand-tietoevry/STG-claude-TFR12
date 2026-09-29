package com.tfi.tfr12.phase2.dbi.partcharge;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * RULE-016 for TF_Part_Charge: H100-Check-Qualification, F115IPC0.src:946-1086.
 *
 * <p>Run before Select/Update/Delete (never before Insert — G000-Insert,
 * F115IPC0.src:860-904, moves R115IPC0-Data straight to the DCL via
 * G100-Move-Input-To-DCL with no qualification check at all; the same
 * asymmetry RULE-016 documents for F115ICH0). {@link com.tfi.tfr12.phase2.dbi.DbiEngine}
 * enforces that wiring; this class only implements the per-field checks.
 *
 * <p>Exact source mapping (mandatory field -&gt; legacy error mnemonic):
 * <ul>
 *   <li>Fin-Inst-ID / Fin-Inst-No -&gt; {@code TF-FIN-INST-MISSING} (lines 948-976)</li>
 *   <li>Contract-Type -&gt; {@code TF-CONTRACT-MISSING} (lines 979-996)</li>
 *   <li>Contract-No -&gt; {@code TF-CONTRACT-MISSING} (lines 998-1015)</li>
 *   <li>CA-Currency -&gt; {@code TF-PART-AMOUNT-MISSING} (lines 1017-1034)</li>
 *   <li>PA-Seq-No -&gt; {@code TF-PART-AMOUNT-MISSING} (lines 1036-1052)</li>
 *   <li>Role-Type -&gt; {@code TF-ROLE-MISSING} (lines 1054-1069)</li>
 *   <li>CT-Type -&gt; {@code TF-CHARGE-MISSING} (lines 1071-1086)</li>
 * </ul>
 * This is the "identical pattern (7 mandatory elements...)" the RULE-016 card
 * names for F115IPC0, and it requires the 2 elements (CA-Currency, PA-Seq-No)
 * that F115ICH0's own 5-element pattern does not.
 *
 * <p><b>"Last failure wins", not fail-fast</b> (standardized across every
 * table's {@code KeyPolicy} — see {@link KeyPolicy}'s class javadoc): each of
 * the 7 checks above is its own independent {@code Evaluate True}, and the
 * source does not short-circuit between them — all 7 run regardless of
 * whether an earlier one already failed. Z000-Error-Message
 * (F115IHI0.src:1343-1361, shared by every F115I*0 module) overwrites a
 * single R7919999-Message field on every call, so when more than one
 * mandatory field is missing at once, only the LAST failing check's mnemonic
 * survives to the caller. This implementation reproduces that ordering.
 */
public final class PartChargeKeyPolicy implements KeyPolicy<PartChargeKey, PartChargeRow> {

    @Override
    public PartChargeKey resolveKey(PartChargeRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115IPC0.src:948-976 — TF-FIN-INST-MISSING
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0L) {
            if (isBlank(record.finInstNo())) {
                pendingFailure = "TF-FIN-INST-MISSING";
            } else {
                // Resolver itself throws MissingKeyFieldException("TF-FIN-INST-MISSING")
                // if the external number cannot be resolved (N100-Fetch-Fin-Inst-ID).
                resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
            }
        }

        // F115IPC0.src:979-996 — TF-CONTRACT-MISSING
        if (isBlank(record.contractType())) {
            pendingFailure = "TF-CONTRACT-MISSING";
        }

        // F115IPC0.src:998-1015 — TF-CONTRACT-MISSING
        if (record.contractNo() == 0L) {
            pendingFailure = "TF-CONTRACT-MISSING";
        }

        // F115IPC0.src:1017-1034 — TF-PART-AMOUNT-MISSING
        if (isBlank(record.caCurrency())) {
            pendingFailure = "TF-PART-AMOUNT-MISSING";
        }

        // F115IPC0.src:1036-1052 — TF-PART-AMOUNT-MISSING
        if (record.paSeqNo() == 0) {
            pendingFailure = "TF-PART-AMOUNT-MISSING";
        }

        // F115IPC0.src:1054-1069 — TF-ROLE-MISSING
        if (isBlank(record.roleType())) {
            pendingFailure = "TF-ROLE-MISSING";
        }

        // F115IPC0.src:1071-1086 — TF-CHARGE-MISSING
        if (isBlank(record.ctType())) {
            pendingFailure = "TF-CHARGE-MISSING";
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new PartChargeKey(resolvedFinInstId, record.contractType(), record.contractNo(),
                record.caCurrency(), record.paSeqNo(), record.roleType(), record.ctType());
    }

    /** COBOL "Not = Space": blank means null or all-blank/whitespace. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
