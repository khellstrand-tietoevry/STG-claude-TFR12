package com.tfi.tfr12.phase3.merge;

import java.util.List;

/**
 * Contract for the modern replacement of legacy program F115L070.src
 * ("Lists charges" - merges/presents charge data computed from TF_Main_Charge
 * and TF_Part_Charge per each charge type's Method_Of_Calculation,
 * F115L070.src:1-25).
 *
 * <p>Three entry points, mirroring three independently-testable pieces of the
 * legacy control flow rather than one monolithic method:
 * <ul>
 *   <li>{@link #process} - the full A000-Main / C000-Interface dispatch
 *       (RULE-005/034 scope decisions, F115L070.src:121-180);</li>
 *   <li>{@link #mergeMainAndPart} - D000-Pre-Process-Charges +
 *       E000-Process-Charges in isolation (RULE-002/008/009/014/020/035,
 *       F115L070.src:294-735), reached only from the "Add, PA-Seq-No != 0,
 *       not AMEND" branch of {@code process};</li>
 *   <li>{@link #reverseStoredCharges} - F000-Process-New-Charges +
 *       F100-Modify-Input-Values in isolation (RULE-010, F115L070.src:813-884),
 *       reached only from the "Subtract, PA-Seq-No != 0, not AMEND" branch of
 *       {@code process}.</li>
 * </ul>
 */
public interface ChargeMergeService {

    /**
     * The full C000-Interface dispatch (F115L070.src:121-180).
     *
     * @throws ChargeMergeAbendException for an unrecognized
     *         {@link ChargeMergeRequest#action()} (paragraph C001) or any
     *         data-integrity failure surfaced by {@link #mergeMainAndPart}
     * @throws ChargeMergeErrorException if a downstream lookup used by
     *         {@link #mergeMainAndPart} reports "not found"
     */
    ChargeMergeResult process(ChargeMergeRequest request);

    /**
     * D000-Pre-Process-Charges' SW-Read-Main-Part branch plus
     * E000-Process-Charges (F115L070.src:294-735): consistency/paying-party
     * reconciliation between Main_Charge and Part_Charge (RULE-014, RULE-020,
     * RULE-035), per-row merge by Method-of-Calculation with min/max clamping
     * (RULE-002, RULE-008), and totals accumulation (RULE-009).
     *
     * @param mainCharges TF_Main_Charge as read with PA-Seq-No forced to 0
     *                    (F115L070.src:239-244), plus that table's own totals
     *                    (only {@code totalAgentCommission} is consumed, RULE-009)
     * @param partCharges TF_Part_Charge as read with the caller's PA-Seq-No
     *                    (F115L070.src:239-241), plus that table's own totals
     * @param context     contract identity (for the RULE-002 clamp's
     *                    Contract-Amount/Part-Amount lookups) and
     *                    error-reporting fields
     * @throws ChargeMergeAbendException for RULE-014 (paragraphs D201/D202) or
     *         RULE-020 (paragraph D101) violations
     * @throws ChargeMergeErrorException if the RULE-002 clamp's Contract-Amount
     *         or Part-Amount lookup finds no row (paragraph E302)
     */
    ChargeMergeResult mergeMainAndPart(ChargeListingSnapshot mainCharges, ChargeListingSnapshot partCharges,
                                        ChargeMergeContext context);

    /**
     * F000-Process-New-Charges / F100-Modify-Input-Values (F115L070.src:813-884,
     * RULE-010): for each caller-supplied input row (terminated by the first
     * blank Charge-Type or the end of the list, F115L070.src:818-822) that
     * matches a stored Main_Charge row by Charge-Type + Role-Type, a
     * Settlement/Part-Amount method charge has the stored amount subtracted
     * from the input amount, floored at zero; a Pct-Of-CA/Pct-Of-PA method
     * charge, or an input row with no matching stored charge, is left
     * unchanged.
     *
     * @param inputCharges      the end user's proposed new charge figures
     * @param storedMainCharges TF_Main_Charge as read with PA-Seq-No = 0
     */
    List<ChargeRow> reverseStoredCharges(List<ChargeRow> inputCharges, List<ChargeRow> storedMainCharges);
}
