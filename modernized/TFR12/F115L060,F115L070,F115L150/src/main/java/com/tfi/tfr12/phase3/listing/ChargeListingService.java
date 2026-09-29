package com.tfi.tfr12.phase3.listing;

import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;
import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;

import java.util.List;

/**
 * Contract for the modern replacement of legacy program F115L060.src
 * ("Listing/Computation of sum total of charges" - presents figures exactly as
 * they appear from one and only one of TF_Main_Charge/TF_Part_Charge,
 * R115L060.copy:1-14), covering the 7 rules pinned by {@code ChargeListingServiceTest}:
 * RULE-001, RULE-006, RULE-007, RULE-019, RULE-029, RULE-033, RULE-038.
 *
 * <p>Paragraph mapping:
 * <ul>
 *   <li>{@code C300-Set-Contract-Type} (F115L060.src:253-269) -&gt;
 *       {@link #classifyContractType} - RULE-019.</li>
 *   <li>{@code D000-Read-Permissible-Charges} role-resolution Evaluate
 *       (F115L060.src:499-570) -&gt; {@link #resolveRoleTypes} - RULE-033.</li>
 *   <li>{@code L100-Read-Sys-Charge-Type} (F115L060.src:1104-1156) -&gt;
 *       {@link #resolveChargeTypeConfig} - RULE-038.</li>
 *   <li>{@code D100-Read-By-Paying-Pty} settlement check
 *       (F115L060.src:604-628) -&gt; {@link #shouldReadMainCharge} - RULE-029.</li>
 *   <li>{@code L000-Additional-Info} / {@code N000-Compute-Charge-Amount}
 *       (F115L060.src:1050-1101, 1341-1381) -&gt; {@link #applyAdditionalInfo} -
 *       RULE-006, RULE-007, RULE-001 (the F115L060-owned half; see the RULE-001
 *       boundary note below).</li>
 * </ul>
 *
 * <p><b>Scope note:</b> {@code ChargeListingServiceImpl} is NOT a byte-for-byte
 * translation of all 1586 lines of F115L060.src - only the 7 rules above are in
 * scope for this module; the row-by-row TF_Main_Charge/TF_Part_Charge cursor
 * reads themselves (E120-Read-Next-TF-CH, F120-Process-TF-PC) and the top-level
 * orchestration that ties every paragraph together into the single "Call
 * F115L060" boundary F115L070 invokes (F115L070.src:254, 276) are out of scope
 * here.
 *
 * <p><b>RULE-001:</b> the "priced as of today, not the contract date" behavior
 * (F115L060.src:1257-1280) spans two modules - F115L060 fetches the machine date
 * once per run (B000-Initialize, F115L060.src:178-188) and forwards it into the
 * POP lookup (F115L060.src:1278, {@code PricingRequest.validDate()}); the actual
 * qualification of {@code PP_Prod_Tjen_Pris} rows against that date happens in
 * F115L150 (behind {@code com.tfi.tfr12.phase3.pricing.PopPricingService}, see
 * {@code ProductPriceQuery.validDate()}). This interface pins the F115L060-owned
 * half: that the date is fetched (only when POP lookup is enabled) and forwarded
 * unconditionally to the pricing request.
 */
public interface ChargeListingService {

    /**
     * C300-Set-Contract-Type (F115L060.src:253-269).
     *
     * @throws UnrecognizedContractTypeException for a contract type outside the
     *         4 recognized families (Z900-Abend, paragraph C301)
     */
    ContractFamily classifyContractType(String contractType);

    /**
     * D000-Read-Permissible-Charges role-resolution Evaluate (F115L060.src:499-570).
     *
     * @return the concrete role type(s) to search TF_Main_Charge/TF_Part_Charge
     *         for, or an empty list if this (category, family) combination has
     *         no match (there is no "When Other" in the legacy Evaluate)
     */
    List<String> resolveRoleTypes(ChargeRoleCategory category, ContractFamily family);

    /**
     * L100-Read-Sys-Charge-Type (F115L060.src:1104-1156): institution-scoped
     * lookup, falling back to a registration-number-scoped lookup only if the
     * first misses.
     *
     * @throws ChargeTypeNotConfiguredException if neither lookup finds a row
     *         (Z700-Error-AE, error 'L103')
     */
    SysChargeTypeDetails resolveChargeTypeConfig(String chargeType, long finInstId, int regNo);

    /**
     * D100-Read-By-Paying-Pty settlement check (F115L060.src:604-628, RULE-029).
     *
     * @param methodCalc               the charge type's R115ICT0-Method-Calc
     * @param checkPaSeqNo             R115L060-In-Check-Pa
     * @param partAmountCheckErrored   whether the Part-Amount lookup used to
     *                                 test Has-Been-Settled itself returned
     *                                 Return-Error
     * @param partAmountHasBeenSettled R115IPA0-Has-Been-Settled on that lookup's result
     * @return {@code false} only when method = Pct-Of-PA, a check was
     *         requested, the lookup did NOT error, AND the part amount has
     *         already been settled - every other combination still reads
     *         Main_Charge
     */
    boolean shouldReadMainCharge(String methodCalc, int checkPaSeqNo,
                                  boolean partAmountCheckErrored, boolean partAmountHasBeenSettled);

    /**
     * L000-Additional-Info / N000-Compute-Charge-Amount (F115L060.src:1050-1101,
     * 1341-1381): RULE-007's conditional amount recompute, RULE-006's
     * currency-relabel-without-conversion, and RULE-001's POP min/max lookup
     * (see class javadoc for the RULE-001 boundary note).
     *
     * @param paSeqNo the caller's PA-Seq-No; 0 means "no part amount", in which
     *                case {@code partAmount} may be {@code null}
     * @param bankNo  the institution's external bank number, forwarded to the
     *                POP pricing request unchanged
     */
    PresentedCharge applyAdditionalInfo(ChargeLine raw, ContractAmountRow contractAmount,
                                         SysChargeTypeDetails chargeTypeConfig, int paSeqNo,
                                         PartAmountRow partAmount, String bankNo);
}
