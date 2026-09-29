package com.tfi.tfr12.phase3.listing;

import java.util.Optional;

/**
 * Port standing in for the two {@code F115ICT0} ("TF_Sys_Charge_Type") calls made
 * from {@code L100-Read-Sys-Charge-Type} (legacy/TFR12/src/F115L060.src:1104-1156):
 * an institution-scoped lookup, and - only if that misses - a fallback lookup scoped
 * by registration number (RULE-038).
 *
 * <p>{@code F115ICT0} itself is already translated in Phase 2
 * ({@code com.tfi.tfr12.phase2.dbi.syschargetype}), so this is not a stand-in for an
 * untranslated module the way {@link com.tfi.tfr12.phase3.pricing.ProductPriceGateway}
 * is for {@code F349IPP0}; it exists because F115ICT0's own internal SQL predicate
 * combination when both a Fin-Inst-Id and a non-zero Regno are supplied is a detail of
 * F115ICT0.src (a different file, Phase 2's responsibility), not of F115L060.src. This
 * port abstracts the two lookup strategies RULE-038 actually cares about - institution
 * scoped vs. registration-number scoped - without needing to reproduce F115ICT0's
 * internal query semantics.
 */
public interface SysChargeTypeGateway {

    /**
     * First call in L100-Read-Sys-Charge-Type (F115L060.src:1107-1118): qualifies by
     * charge type and financial institution, with {@code R115ICT0-In-Regno} = 0.
     */
    Optional<SysChargeTypeDetails> selectByFinInst(String chargeType, long finInstId);

    /**
     * Fallback call, only reached when {@link #selectByFinInst} misses
     * (F115L060.src:1129-1140): qualifies by charge type and
     * {@code R115L060-In-Reg-No}.
     */
    Optional<SysChargeTypeDetails> selectByRegno(String chargeType, int regNo);
}
