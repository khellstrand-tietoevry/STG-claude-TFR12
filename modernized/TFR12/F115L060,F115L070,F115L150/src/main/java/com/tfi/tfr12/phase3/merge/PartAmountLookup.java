package com.tfi.tfr12.phase3.merge;

import com.tfi.tfr12.phase2.dbi.partamount.PartAmountRow;

import java.util.Optional;

/**
 * Port standing in for F115L070's call to F115IPA0 at
 * E400-Read-Part-Amount (F115L070.src:773-811), used only inside RULE-002's
 * percent-of-Part-Amount min/max clamp, where {@code R115IPA0-Amount} always
 * supplies the base for the percentage - the "CA" half of the generically
 * named {@code WS-CA-Or-PA-Amount} working field is never actually used on
 * this path (F115L070.src:544-546; the field is shared with a different,
 * CA-based code path elsewhere in the system that F115L070 does not exercise).
 * Reuses Phase 2's {@link PartAmountRow} directly.
 *
 * <p>See {@link ContractAmountLookup}'s javadoc for the same "resolved
 * Fin-Inst-ID, no full key-policy" simplification, which applies here too
 * (F115L070.src:793, R115IPA0-In-Select - also routed through
 * F100-Prepare-Qualification, not H100-Check-Qualification).
 */
public interface PartAmountLookup {

    /**
     * @param currency R115IPA0-In-CA-Currency, moved from the contract amount
     *                 row's own currency just read via {@link ContractAmountLookup}
     *                 (F115L070.src:789, "Move R115ICA0-Currency To R115IPA0-In-CA-Currency")
     * @return the matching TF_Part_Amount row, or empty if none exists - the
     *         legacy Return-Error case (F115L070.src:802-804, paragraph E302)
     */
    Optional<PartAmountRow> findByKey(long finInstId, String contractType, long contractNo, String currency, int paSeqNo);
}
