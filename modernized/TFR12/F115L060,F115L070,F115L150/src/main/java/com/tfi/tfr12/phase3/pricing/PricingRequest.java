package com.tfi.tfr12.phase3.pricing;

import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;
import com.tfi.tfr12.phase2.dbi.syschargetype.SysChargeTypeRow;

/**
 * Input to {@link PopPricingService#computeDefaultCharge(PricingRequest)}, modelled on
 * the linkage/working-storage of F115L150 (copybook {@code R115L150.copy},
 * {@code R115L150-Qualification} group, F115L150.src:34-51/168-183).
 *
 * <p>The underlying DBI reads for {@code TF_Sys_Code} (F115ISC0), {@code TF_Sys_Charge_Type}
 * (F115ICT0), {@code TF_Contract_Amount} (F115ICA0) and {@code TF_Part_Amount} (F115IPA0)
 * are already-translated Phase 2 modules (see {@code ContractAmountRow},
 * {@code SysChargeTypeRow}, {@code PartAmountRow}), so this request carries their
 * already-resolved rows directly rather than re-modelling those DBI calls here. Only the
 * two modules that are genuinely external to this repo slice - {@code F116L030}
 * (currency exchange) and {@code F349IPP0} (product/service price) - are modelled as
 * injected ports ({@link CurrencyExchangeGateway}, {@link ProductPriceGateway}).
 *
 * @param partAmountNo     R115L150-In-Part-Amount-No (copybooks/R115L150.copy:28). {@code <= 0} means
 *                         "no part amount specified" (F115L150.src:154, 430).
 * @param bankNo           R115ISC0-Fin-Inst-No, as resolved by C200-Read-Sys-Code. Only its
 *                         first 4 characters are used, mirroring the COBOL reference
 *                         modification {@code R115ISC0-Fin-Inst-No(1:4)} at F115L150.src:473
 *                         and F115L150.src:546-547 when building every currency-exchange call.
 * @param chargeTypeConfig R115ICT0-Sys-Charge-Type row (read by C300-Read-Sys-Charge-Type).
 *                         Only {@code methodCalc()} is consulted, to test the
 *                         {@code R115ICT0-MethCalc-Pct-Of-PA} condition ('Q') at F115L150.src:430-431.
 * @param contractAmount   R115ICA0-Contract-Amount row (read by C500-Read-Contract-Amount).
 *                         {@code currency()} becomes the output currency (F115L150.src:533);
 *                         {@code currOriginalAmount()} is the RULE-011 percent base
 *                         (F115L150.src:458); {@code remainingAmount()} is the tiered-path
 *                         amount base when no part amount is given (F115L150.src:158-159).
 * @param partAmount       R115IPA0-Part-Amount row (read by C600-Read-Part-Amount), or
 *                         {@code null} when {@code partAmountNo <= 0}. {@code amount()} is
 *                         used both as the RULE-011 "Pct-Of-PA" base (F115L150.src:447,
 *                         D100-Compute-Charge-Amount re-reads it, but the value is the same
 *                         row) and as the tiered-path amount base (F115L150.src:156).
 * @param validDate        R115L150-In-Valid-Date (copybooks/R115L150.copy:29), {@code YYYYMMDD}
 *                         as an {@code int} (same representation as
 *                         {@code MachineTimestamp.date8()}). RULE-001: the caller (F115L060)
 *                         fetches today's machine date once per run and forwards it here so
 *                         C400-Read-1st-Prod-Tjen-Pris (F115L150.src:264-276) qualifies the
 *                         {@code PP_Prod_Tjen_Pris} lookup against the price row valid as of
 *                         today, not the contract's original date.
 */
public record PricingRequest(
        int partAmountNo,
        String bankNo,
        SysChargeTypeRow chargeTypeConfig,
        ContractAmountRow contractAmount,
        PartAmountRow partAmount,
        int validDate) {

    /** F115L150.src:154, 430: a part amount was supplied on the request. */
    public boolean hasPartAmount() {
        return partAmountNo > 0;
    }
}
