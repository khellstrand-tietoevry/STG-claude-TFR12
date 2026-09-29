package com.tfi.tfr12.phase2.dbi.history;

import com.tfi.tfr12.phase2.dbi.FinInstResolver;
import com.tfi.tfr12.phase2.dbi.KeyPolicy;

/**
 * RULE-028 key-validation policy for TF_History — a deliberate NO-OP.
 *
 * <p><b>Finding: unlike its siblings (F115ICA0 / F115ICH0 / F115IPC0),
 * F115IHI0 has NO reachable H100-style mandatory-key check at all.</b> Do
 * not port the sibling pattern here by analogy; the control flow genuinely
 * differs. Traced directly in legacy/TFR12/src/F115IHI0.src:
 *
 * <ul>
 *   <li>{@code H100-Check-Qualification} IS still defined
 *       (F115IHI0.src:892-1017) and looks, line for line, just like
 *       ContractAmount's/ContractCharge's/PartCharge's own H100 — it checks
 *       Fin-Inst, Contract-Type, Contract-No, CA-Currency, PA-Seq-No and
 *       (uniquely to this table) HI-Seq-No, raising
 *       TF-FIN-INST-MISSING / TF-CONTRACT-MISSING / TF-PART-AMOUNT-MISSING /
 *       TF-TYPE-MISSING as appropriate.</li>
 *   <li>But the ONLY call site for it is
 *       {@code Perform H100-Check-Qualification} at F115IHI0.src:882 — and
 *       that line is itself inside the entirely commented-out
 *       {@code H000-Update} paragraph (F115IHI0.src:880-891, every line
 *       prefixed with the column-7 comment '*'). Confirmed with
 *       {@code grep -n "H100-Check-Qualification"} against the whole file:
 *       the only two hits are the paragraph's own header (line 892) and that
 *       one commented-out call (line 882) — there is no live call anywhere.</li>
 *   <li>{@code C000-Interface}'s dispatcher (F115IHI0.src:309-347) confirms
 *       why: the {@code When R115IHI0-In-Update Perform H000-Update} branch
 *       is itself commented out (F115IHI0.src:330-331), alongside
 *       {@code When R115IHI0-In-Select Perform F000-Select}
 *       (F115IHI0.src:323-325) — both dead per RULE-028. A Select or Update
 *       request instead falls through to {@code When Other}
 *       (F115IHI0.src:336-343), which raises 'GL-IN-AKSJON-UV' and abends
 *       via Z900-Abend, never reaching H100 at all.</li>
 *   <li>{@code I000-Delete} (F115IHI0.src:1045-1052, live code) calls
 *       {@code F100-Prepare-Qualification} (F115IHI0.src:1047) followed by
 *       {@code I200-Delete-Row} (F115IHI0.src:1048) — NOT H100. And
 *       {@code F100-Prepare-Qualification} (F115IHI0.src:758-782) just
 *       copies whatever qualification fields the caller supplied into the
 *       DCL host variables (resolving Fin-Inst-No -> Fin-Inst-ID via
 *       N100-Fetch-Fin-Inst-ID at F115IHI0.src:774-780 if only the external
 *       number was given), with no mandatory-field check and no TF-*-MISSING
 *       error raised for any missing field. A Delete with an all-blank/zero
 *       key is therefore never rejected by F115IHI0 itself before
 *       I200-Delete-Row issues the DELETE (F115IHI0.src:1054-1078) — it can
 *       only fail later, if at all, at the DB2 layer.</li>
 *   <li>{@code G000-Insert} / {@code G100-Move-Input-To-DCL}
 *       (F115IHI0.src:810-852) also performs no key check, matching
 *       RULE-016's general Insert-is-unchecked asymmetry and this
 *       {@link KeyPolicy} interface's own contract (Insert is never routed
 *       through a {@code KeyPolicy} by {@code DbiEngine}).</li>
 * </ul>
 *
 * <p>So: for TF_History, EVERY still-reachable action (Get-1st, Get-Next,
 * Insert, Delete — see {@code Capabilities.APPEND_ONLY}) has no mandatory-key
 * enforcement in the legacy module at all. {@code H100-Check-Qualification}
 * is not "Update-only, like the other tables" — for this table specifically
 * it is unreachable dead code, orphaned by the same RULE-028 change that
 * disabled Update itself. Implementing a non-trivial validate() here (e.g.
 * copying ContractAmountKeyPolicy's four checks) would invent a check this
 * table's legacy interface never performs anywhere live, which the task
 * brief explicitly warns against ("implement HistoryKeyPolicy to match
 * whatever you actually find, not what you'd expect by analogy").
 *
 * <p>{@code DbiEngine} invokes a table's {@link KeyPolicy} before Select,
 * Update AND Delete (see {@code DbiEngine}'s own Javadoc). RULE-028's
 * {@code Capabilities.APPEND_ONLY} disables Select and Update for this table,
 * so those two never reach this policy — but Delete is enabled, and IS
 * routed through here. There is still no mandatory-field check to reproduce
 * (see class Javadoc: {@code F100-Prepare-Qualification} raises no
 * TF-*-MISSING error), but it DOES resolve Fin-Inst-No to an id when only the
 * external number is supplied (F115IHI0.src:774-780), so this method
 * reproduces that resolution for Delete without validating anything.
 */
public final class HistoryKeyPolicy implements KeyPolicy<HistoryKey, HistoryRow> {

    @Override
    public HistoryKey resolveKey(HistoryRow record, FinInstResolver resolver) {
        long resolvedFinInstId = record.finInstId();
        if (resolvedFinInstId == 0 && record.finInstNo() != null && !record.finInstNo().isBlank()) {
            resolvedFinInstId = resolver.resolveFinInstId(record.finInstNo());
        }
        return new HistoryKey(resolvedFinInstId, record.contractType(), record.contractNo(),
                record.caCurrency(), record.paSeqNo(), record.hiSeqNo());
    }
}
