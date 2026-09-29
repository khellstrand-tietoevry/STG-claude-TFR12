package com.tfi.tfr12.phase2.dbi.history;

/**
 * The natural (resolved) key of a TF_History row, per {@code R115IHI0-Key}
 * (legacy/TFR12/copybooks/R115IHI0.copy:132-142) — the same six-field tuple
 * that F115IHI0's own {@code L200-Find-DCL} / {@code L300-Remove-DCL} use to
 * identify a row in its in-memory row cache
 * (legacy/TFR12/src/F115IHI0.src:1182-1193, 1203-1214) and that
 * {@code J100-Move-DCL-To-Output} reads a row back into
 * (F115IHI0.src:1088-1093).
 *
 * <p>Unlike the other {@code F115I*0} tables, TF_History includes its own
 * per-row sequence number ({@code hiSeqNo}, "EH Sequence Number" per
 * R115IHI0.copy:88) in the natural key: history is an append-only audit log
 * keyed by contract/part-amount PLUS a sequence number that distinguishes
 * multiple history entries for the same contract/part-amount
 * (R115IHI0.copy:120, TFEH-SEQ-NO in F115IHI0.src:772, 839, 867, 1093, 1146).
 *
 * <p>{@code finInstId} here is always the resolved internal id — never the
 * external Fin-Inst-No — matching the field F115IHI0 actually qualifies its
 * DB2 access and its row cache on (F115IHI0.src:488, 764, 1136-1137).
 *
 * @param finInstId    resolved internal Fin-Inst-ID (R115IHI0-Fin-Inst-ID /
 *                     FK-OAKO-FinInst-ID, Pic S9(18) Comp-3)
 * @param contractType R115IHI0-Contract-Type / FK-TFMC-Contr-Type, Pic X(03)
 * @param contractNo   R115IHI0-Contract-No / FK-TFMC-Contr-No, Pic S9(07) Comp-3
 * @param caCurrency   R115IHI0-CA-Currency / FK-TFCA-Currency, Pic X(03), ISO 4217
 * @param paSeqNo      R115IHI0-PA-Seq-No / FK-TFPA-Seq-No, Pic S9(04) Comp
 * @param hiSeqNo      R115IHI0-HI-Seq-No / TFEH-SEQ-NO, Pic S9(04) Comp — the
 *                     history row's own append sequence number, assigned by
 *                     G200-Insert-Row on a successful Insert
 *                     (F115IHI0.src:865-867: "Move TFEH-SEQ-NO To
 *                     R115IHI0-HI-Seq-No", i.e. DB2/the DCL supplies it back)
 */
public record HistoryKey(
        long finInstId,
        String contractType,
        long contractNo,
        String caCurrency,
        int paSeqNo,
        int hiSeqNo) {
}
