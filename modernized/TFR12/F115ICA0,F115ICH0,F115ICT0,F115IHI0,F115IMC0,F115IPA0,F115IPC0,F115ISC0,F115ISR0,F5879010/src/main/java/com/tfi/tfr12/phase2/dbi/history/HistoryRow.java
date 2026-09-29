package com.tfi.tfr12.phase2.dbi.history;

/**
 * Row DTO for TF_History, mirroring the key-relevant and a representative
 * sample of the data-relevant part of {@code R115IHI0-Data}
 * (legacy/TFR12/copybooks/R115IHI0.copy:130-175). Not the full column list:
 * R115IHI0-Text also carries two documented REDEFINES
 * ({@code R115IHI0-Agent-Text} / {@code R115IHI0-SilentC-Text},
 * R115IHI0.copy:153-171) that overlay application-specific sub-structures on
 * the same 150-byte text buffer for particular {@code Operation-Type}
 * values — those overlays are a display/parsing concern for callers of this
 * table, not additional stored columns, so they are omitted here. The
 * cursor accumulators ({@code R115IHI0-Out-Row-No},
 * {@code R115IHI0-Out-Total-Rows}, R115IHI0.copy:184-186) are paging
 * metadata populated by {@code J100-Move-DCL-To-Output}
 * (F115IHI0.src:1108-1109), not row data, and are likewise omitted.
 *
 * <p>{@code finInstNo} is carried alongside the resolved {@code finInstId}
 * because G100-Move-Input-To-DCL (the Insert path) accepts either: if
 * {@code finInstId} is 0 and {@code finInstNo} is non-blank, it is resolved
 * via {@link com.tfi.tfr12.phase2.dbi.FinInstResolver}
 * (F115IHI0.src:825-831, mirroring N100-Fetch-Fin-Inst-ID).
 *
 * @param finInstId     resolved internal Fin-Inst-ID, or 0 if not yet resolved
 *                      (R115IHI0-Fin-Inst-ID, Comp-3)
 * @param finInstNo     external Fin-Inst-No, used to resolve {@code finInstId}
 *                      when it is not already known (R115IHI0-Fin-Inst-No,
 *                      Pic X(18), R115IHI0.copy:174)
 * @param contractType  R115IHI0-Contract-Type, Pic X(03)
 * @param contractNo    R115IHI0-Contract-No, Pic S9(07) Comp-3
 * @param caCurrency    R115IHI0-CA-Currency, Pic X(03), ISO 4217
 * @param paSeqNo       R115IHI0-PA-Seq-No, Pic S9(04) Comp
 * @param hiSeqNo       R115IHI0-HI-Seq-No, Pic S9(04) Comp — 0 on an Insert
 *                      request (assigned by DB2/the DCL and returned by
 *                      G200-Insert-Row, F115IHI0.src:865-867), non-zero once
 *                      read back from the cursor or after Insert completes
 * @param operationType R115IHI0-Operation-Type, Pic X(20) — what kind of
 *                      history event this row records (R115IHI0.copy:144)
 * @param userId        R115IHI0-User-Id, Pic X(07) — who performed the
 *                      recorded operation (R115IHI0.copy:146)
 * @param timestamp     R115IHI0-Timestamp, Pic X(26) — when it happened
 *                      (R115IHI0.copy:147)
 * @param textLength    R115IHI0-Text-Length, Pic S9(04) Comp — length in use
 *                      within the 150-byte text buffer (R115IHI0.copy:149)
 * @param text          R115IHI0-Text, Pic X(150) — free-text/overlay payload
 *                      (R115IHI0.copy:151)
 */
public record HistoryRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        String caCurrency,
        int paSeqNo,
        int hiSeqNo,
        String operationType,
        String userId,
        String timestamp,
        int textLength,
        String text) {
}
