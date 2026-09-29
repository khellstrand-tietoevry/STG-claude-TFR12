package com.tfi.tfr12.phase3.merge;

import java.util.List;

/**
 * Mirrors {@code R115L070-Qualification} (legacy/STG/TFR12/copybooks/R115L070.copy:28-59),
 * the full input to F115L070's A000-Main / C000-Interface dispatch (RULE-005,
 * F115L070.src:121-180).
 *
 * @param action                  R115L070-In-Action, Pic X(16); only the literal
 *                                values {@code "ADD"} and {@code "SUBTRACT"} match
 *                                the copybook's 88-levels (R115L070.copy:30-32) -
 *                                anything else reproduces the legacy "When other"
 *                                abend (C001, F115L070.src:172-178,
 *                                message GL-IN-AKSJON-UV)
 * @param tableVersion            R115L070-In-Table-Version
 * @param finInstId               resolved internal Fin-Inst-ID - see
 *                                {@link ChargeMergeContext#finInstId()} for the
 *                                scope note on resolution
 * @param contractType            R115L070-In-Contract-Type
 * @param contractNo              R115L070-In-Contract-No
 * @param paSeqNo                 R115L070-In-PA-Seq-No; 0 = main-contract-level
 *                                only, non-zero = main + part level (RULE-005/034)
 * @param operationType           R115L070-In-Operation-Type, Pic X(20); a value
 *                                whose first 6 characters are {@code "AMEND-"}
 *                                selects the Part_Charge-only path
 *                                (F115L070.src:137-138, 161-162)
 * @param callerSuppliedCharges   only meaningful for {@code action = "SUBTRACT"}:
 *                                the caller must pre-populate the whole charge
 *                                table before calling, since C100-Initialize-Output
 *                                is never performed on the Subtract path
 *                                (F115L070.src:155-180) - this is the end user's
 *                                proposed new charge figures to be reduced by
 *                                what is already stored (RULE-010)
 * @param callerSuppliedTotals    only meaningful for {@code action = "SUBTRACT"}:
 *                                carried through completely unmodified on both
 *                                Subtract branches (neither C100-Initialize-Output
 *                                nor E200-Accumulate-Totals ever runs for Subtract)
 * @param language                see {@link ChargeMergeContext#language()}
 * @param traceOn                 see {@link ChargeMergeContext#traceOn()}
 */
public record ChargeMergeRequest(
        String action,
        ChargeTableVersion tableVersion,
        long finInstId,
        String contractType,
        long contractNo,
        int paSeqNo,
        String operationType,
        List<ChargeRow> callerSuppliedCharges,
        ChargeTotals callerSuppliedTotals,
        String language,
        boolean traceOn) {

    public ChargeMergeContext toContext() {
        return new ChargeMergeContext(finInstId, contractType, contractNo, paSeqNo, language, traceOn);
    }
}
