package com.tfi.tfr12.phase2.dbi.maincharge;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;
import com.tfi.tfr12.phase2.dbi.MissingKeyFieldException;

/**
 * RULE-016 for TF_Main_Charge: H100-Check-Qualification, F115ICH0.src:868-996.
 *
 * <p><b>What the legacy source actually gates:</b> H100-Check-Qualification is
 * performed by H000-Update alone (F115ICH0.src:856-866, "Perform
 * H100-Check-Qualification" at line 858) immediately before
 * G100-Move-Input-To-DCL and H200-Update-Row. G000-Insert
 * (F115ICH0.src:783-791) calls G100-Move-Input-To-DCL directly and performs
 * no equivalent check at all — the documented RULE-016 asymmetry. This class
 * implements only the per-field checks; {@link com.tfi.tfr12.phase2.dbi.DbiEngine}
 * decides which actions invoke {@link #validate}.
 *
 * <p><b>Divergence to flag:</b> F000-Select (F115ICH0.src:670-724) and
 * I000-Delete (F115ICH0.src:1026-1033) both call F100-Prepare-Qualification
 * (F115ICH0.src:726-753) instead of H100-Check-Qualification.
 * F100-Prepare-Qualification moves the In- qualification fields straight into
 * the DCL host variables (resolving Fin-Inst-No to Fin-Inst-ID if needed) and
 * performs no blank/zero check and raises none of the TF-*-MISSING errors. So
 * in the real F115ICH0, Select and Delete can run — and simply not find a row
 * (error {@code DB-FOREKOMST-NF-Q}, F115ICH0.src:694-720) — with an
 * incomplete key; only Update is actually protected by RULE-016. The same
 * pattern (Select/Delete -&gt; F100-Prepare-Qualification, only Update -&gt;
 * H100-Check-Qualification) was independently confirmed in F115ICA0.src and
 * F115IPC0.src, so it is not an F115ICH0-specific oddity. This is narrower
 * than {@link com.tfi.tfr12.phase2.dbi.DbiEngine}'s own documented contract
 * ("Key validation... runs before Select, Update and Delete"), which applies
 * this policy uniformly to Select/Update/Delete for every table wired through
 * it. Plugging this policy into {@code DbiEngine} therefore makes the
 * modernized Select/Delete for Main Charge stricter than literal F115ICH0
 * parity would require. That is a deliberate engine-level design choice (out
 * of this module's scope to change), not a claim that F115ICH0's own Select
 * or Delete paragraphs perform this check — flagged here rather than silently
 * assumed.
 *
 * <p>Exact source mapping (mandatory field -&gt; legacy error mnemonic):
 * <ul>
 *   <li>Fin-Inst-ID / Fin-Inst-No -&gt; {@code TF-FIN-INST-MISSING} (lines 870-899)</li>
 *   <li>Contract-Type -&gt; {@code TF-CONTRACT-MISSING} (lines 901-918)</li>
 *   <li>Contract-No -&gt; {@code TF-CONTRACT-MISSING} (lines 920-937)</li>
 *   <li>RP-Role-Type -&gt; {@code TF-PARTY-MISSING} (lines 939-959)</li>
 *   <li>Charge-Type -&gt; {@code TF-CHARGE-MISSING} (lines 961-977)</li>
 *   <li>CH-Seq-No -&gt; {@code TF-CHARGE-MISSING} (lines 979-994)</li>
 * </ul>
 * CA-Currency and PA-Seq-No are intentionally not checked here — see
 * {@link MainChargeKey}'s javadoc.
 *
 * <p><b>"Last failure wins", not fail-fast</b> (standardized across every
 * table's {@code KeyPolicy} — see {@link KeyPolicy}'s class javadoc): each of
 * the 6 checks above is its own independent {@code Evaluate True}, and the
 * source does not short-circuit between them — all 6 run regardless of
 * whether an earlier one already failed. Z000-Error-Message
 * (F115IHI0.src:1343-1361, shared by every F115I*0 module) overwrites a
 * single R7919999-Message field on every call, so when more than one
 * mandatory field is missing at once, only the LAST failing check's mnemonic
 * survives to the caller. This implementation reproduces that ordering.
 */
public final class MainChargeKeyPolicy implements KeyPolicy<MainChargeKey, MainChargeRow> {

    @Override
    public MainChargeKey resolveKey(MainChargeRow record, FinInstResolver resolver) {
        String pendingFailure = null;

        // F115ICH0.src:870-899 — TF-FIN-INST-MISSING
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

        // F115ICH0.src:901-918 — TF-CONTRACT-MISSING
        if (isBlank(record.contractType())) {
            pendingFailure = "TF-CONTRACT-MISSING";
        }

        // F115ICH0.src:920-937 — TF-CONTRACT-MISSING
        if (record.contractNo() == 0L) {
            pendingFailure = "TF-CONTRACT-MISSING";
        }

        // F115ICH0.src:939-959 — TF-PARTY-MISSING
        if (isBlank(record.rpRoleType())) {
            pendingFailure = "TF-PARTY-MISSING";
        }

        // F115ICH0.src:961-977 — TF-CHARGE-MISSING
        if (isBlank(record.chargeType())) {
            pendingFailure = "TF-CHARGE-MISSING";
        }

        // F115ICH0.src:979-994 — TF-CHARGE-MISSING
        if (record.chSeqNo() == 0) {
            pendingFailure = "TF-CHARGE-MISSING";
        }

        if (pendingFailure != null) {
            throw new MissingKeyFieldException(pendingFailure);
        }
        return new MainChargeKey(resolvedFinInstId, record.contractType(), record.contractNo(),
                record.rpRoleType(), record.chargeType(), record.chSeqNo());
    }

    /** COBOL "Not = Space": blank means null or all-blank/whitespace. */
    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
