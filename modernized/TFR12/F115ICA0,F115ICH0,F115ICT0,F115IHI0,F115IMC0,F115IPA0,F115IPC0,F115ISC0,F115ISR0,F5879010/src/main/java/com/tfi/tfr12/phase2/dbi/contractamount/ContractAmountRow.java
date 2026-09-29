package com.tfi.tfr12.phase2.dbi.contractamount;

import java.math.BigDecimal;

/**
 * Row DTO for TF_Contract_Amount, mirroring the mandatory-key-relevant part
 * of {@code R115ICA0-Data} (legacy/TFR12/copybooks/R115ICA0.copy:134-156)
 * plus a representative sample of the data fields, not the full column list
 * (R115ICA0.copy also has Prev-O-Amount and the Out-Row-No/Out-Total-Rows
 * cursor accumulators, which are cursor/paging concerns, not row data, and
 * are omitted here).
 *
 * <p><b>Simplification vs. the legacy copybook:</b> R115ICA0 carries each key
 * element twice — once in the "data area" (e.g. {@code R115ICA0-Contract-Type})
 * and once in the parallel "qualification area" ({@code R115ICA0-In-Contract-Type})
 * — purely as a wire-format artifact of the old single-copybook request/response
 * transport (R115ICA0.copy:72-124 vs. :134-142). {@link ContractAmountRow}
 * collapses each such pair into one field: whichever value the caller supplied
 * ends up here, and {@link ContractAmountKeyPolicy} validates this single set,
 * matching the fallback the legacy also performs (F115ICA0.src:812-827,
 * 843-846, 862-865, 881-882) when only the qualification-area field was given.
 *
 * @param finInstId        resolved internal Fin-Inst-ID, or 0 if not yet resolved
 *                         (R115ICA0-Fin-Inst-ID / R115ICA0-In-Fin-Inst-ID, Comp-3)
 * @param finInstNo        external Fin-Inst-No, used to resolve {@code finInstId}
 *                         via {@link com.tfi.tfr12.phase2.dbi.FinInstResolver} when
 *                         it is not already known (R115ICA0-Fin-Inst-No /
 *                         R115ICA0-In-Fin-Inst-No, Pic X(18))
 * @param contractType     R115ICA0-Contract-Type / -In-Contract-Type, Pic X(03)
 * @param contractNo       R115ICA0-Contract-No / -In-Contract-No, Pic S9(07) Comp-3
 * @param currency         R115ICA0-Currency / -In-Currency, Pic X(03), ISO 4217
 * @param originalAmount   R115ICA0-Original-Amount, Pic S9(14)V9(03) Comp-3
 * @param remainingAmount  R115ICA0-Remaining-Amount, Pic S9(14)V9(03) Comp-3
 * @param currOriginalAmount current original amount, R115ICA0-Curr-O-Amount,
 *                         Pic S9(14)V9(03) Comp-3
 * @param minPctTolerance  R115ICA0-Min-Pct-Tolerance, Pic S9(04) Comp
 * @param maxPctTolerance  R115ICA0-Max-Pct-Tolerance, Pic S9(04) Comp
 */
public record ContractAmountRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        String currency,
        BigDecimal originalAmount,
        BigDecimal remainingAmount,
        BigDecimal currOriginalAmount,
        int minPctTolerance,
        int maxPctTolerance) {
}
