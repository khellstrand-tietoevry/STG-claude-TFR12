package com.tfi.tfr12.phase2.dbi.contractamount;

/**
 * The natural (resolved) key of a TF_Contract_Amount row, per
 * {@code R115ICA0-Key} (legacy/TFR12/copybooks/R115ICA0.copy:136-142) plus the
 * currency element that F115ICA0 also treats as mandatory for
 * Select/Update/Delete (R115ICA0.copy:142, F115ICA0.src:877-892).
 *
 * <p>{@code finInstId} here is always the resolved internal id — never the
 * external Fin-Inst-No — matching what {@link ContractAmountKeyPolicy}
 * guarantees before a {@code KeyedRepository} lookup is attempted (RULE-018).
 *
 * @param finInstId   resolved internal Fin-Inst-ID (R115ICA0-Fin-Inst-ID,
 *                    Pic S9(18) Comp-3)
 * @param contractType R115ICA0-Contract-Type, Pic X(03)
 * @param contractNo  R115ICA0-Contract-No, Pic S9(07) Comp-3
 * @param currency    R115ICA0-Currency, Pic X(03), ISO 4217
 */
public record ContractAmountKey(long finInstId, String contractType, long contractNo, String currency) {
}
