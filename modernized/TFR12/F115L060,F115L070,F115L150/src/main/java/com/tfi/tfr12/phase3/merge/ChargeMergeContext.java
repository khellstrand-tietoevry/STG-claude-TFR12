package com.tfi.tfr12.phase3.merge;

/**
 * The contract-identity and error-reporting context RULE-002's min/max clamp
 * (F115L070.src:534-616, E300-Read-Contract-Amount / E400-Read-Part-Amount)
 * and the D1xx/D2xx/C001 abend paths (Z000-Error-Message, F115L070.src:954-972)
 * need, factored out of {@link ChargeMergeRequest} so
 * {@link ChargeMergeService#mergeMainAndPart} can be exercised directly
 * without building a full request.
 *
 * @param finInstId    resolved internal Fin-Inst-ID (R115L070-In-Fin-Inst-ID /
 *                     R115X000-Fin-Inst-ID); resolution from the external
 *                     Fin-Inst-No is a Phase 2 concern (already characterized
 *                     by {@code ContractAmountServiceTest} et al.) and is
 *                     deliberately out of scope here - callers are expected to
 *                     supply the resolved id
 * @param contractType R115L070-In-Contract-Type, Pic X(03)
 * @param contractNo   R115L070-In-Contract-No, Pic S9(07) Comp-3
 * @param paSeqNo      R115L070-In-PA-Seq-No, Pic S9(04) Comp
 * @param language     R115X000-FI-Language-639-1, used by Z000-Error-Message
 *                     (F115L070.src:960-961) when resolving abend message text
 * @param traceOn      R115X000-Trace-On (F115L070.src:963-965); when true,
 *                     Z000-Error-Message asks for the message to also be
 *                     logged - a side effect this port does not model, kept
 *                     here only for fidelity of the request shape
 */
public record ChargeMergeContext(
        long finInstId,
        String contractType,
        long contractNo,
        int paSeqNo,
        String language,
        boolean traceOn) {

    /** Convenience factory for tests that only care about the merge math, not error-message resolution. */
    public static ChargeMergeContext of(long finInstId, String contractType, long contractNo, int paSeqNo) {
        return new ChargeMergeContext(finInstId, contractType, contractNo, paSeqNo, "EN", false);
    }
}
