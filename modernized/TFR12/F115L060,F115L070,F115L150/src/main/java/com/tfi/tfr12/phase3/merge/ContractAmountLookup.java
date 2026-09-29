package com.tfi.tfr12.phase3.merge;

import com.tfi.tfr12.phase2.dbi.contractamount.ContractAmountRow;

import java.util.Optional;

/**
 * Port standing in for F115L070's call to F115ICA0 at
 * E300-Read-Contract-Amount (F115L070.src:737-770), used only inside RULE-002's
 * percent-of-Part-Amount min/max clamp. Reuses Phase 2's
 * {@link ContractAmountRow} directly, per the modernization brief, rather than
 * re-modelling TF_Contract_Amount.
 *
 * <p><b>Deliberate simplification vs. the real F115ICA0 call:</b> the legacy
 * call is a "Get-1st" (R115ICA0-In-Get-1st, F115L070.src:741) qualified only by
 * Fin-Inst-No/Contract-Type/Contract-No (F115L070.src:743-751) - no Currency -
 * so it goes through F100-Prepare-Qualification, not the full mandatory-key
 * H100-Check-Qualification (see {@code ContractAmountKeyPolicy}'s own "Scope
 * note"). Modelling that distinction - and the Fin-Inst-No -> Fin-Inst-ID
 * resolution it would also need - is Phase 2's job (already characterized by
 * {@code ContractAmountServiceTest}); this port instead takes an already-resolved
 * {@code finInstId} and returns whichever row (if any) F115L060/F115ICA0 would
 * have found first.
 */
public interface ContractAmountLookup {

    /**
     * @return the first TF_Contract_Amount row for this Fin-Inst/Contract-Type/
     *         Contract-No, or empty if none exists - the legacy Return-Error
     *         case (F115L070.src:762-764, paragraph E302)
     */
    Optional<ContractAmountRow> findFirstByContract(long finInstId, String contractType, long contractNo);
}
