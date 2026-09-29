package com.tfi.tfr12.phase2.dbi.partamount;

/**
 * The natural (resolved) key of a TF_Part_Amount row, per
 * {@code R115IPA0-Key} (legacy/TFR12/copybooks/R115IPA0.copy:157-164) plus the
 * currency and part-amount-sequence-number elements that F115IPA0 also treats
 * as mandatory for Update (F115IPA0.src:973-1008; see
 * {@link PartAmountKeyPolicy}).
 *
 * <p>{@code finInstId} here is always the resolved internal id — never the
 * external Fin-Inst-No — matching what {@link PartAmountKeyPolicy} guarantees
 * before a {@code KeyedRepository} lookup is attempted.
 *
 * @param finInstId    resolved internal Fin-Inst-ID (R115IPA0-Fin-Inst-ID,
 *                     Pic S9(18) Comp-3)
 * @param contractType R115IPA0-Contract-Type, Pic X(03)
 * @param contractNo   R115IPA0-Contract-No, Pic S9(07) Comp-3
 * @param currency     R115IPA0-CA-Currency, Pic X(03), ISO 4217
 * @param paSeqNo      R115IPA0-PA-Seq-No, Pic S9(04) Comp — the part-amount
 *                     sequence number, created by the interface on Insert
 *                     (copybooks/R115IPA0.copy:96, "PA-Seq-No will be created
 *                     by the interface")
 */
public record PartAmountKey(long finInstId, String contractType, long contractNo, String currency, int paSeqNo) {
}
