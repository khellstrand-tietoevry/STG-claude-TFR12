package com.tfi.tfr12.phase3.merge;

/**
 * Mirrors {@code R115L060-Qualification} (legacy/STG/TFR12/copybooks/R115L060.copy:23-52)
 * as built by F115L070's C200-Qualify-Read (F115L070.src:195-249) for one of its
 * two calls to F115L060.
 *
 * @param tableVersion  R115L060-In-Table-Version
 * @param finInstId     resolved internal Fin-Inst-ID - see
 *                      {@link ChargeMergeContext#finInstId()} for the
 *                      resolution-scope note
 * @param contractType  R115L060-In-Contract-Type
 * @param contractNo    R115L060-In-Contract-No
 * @param paSeqNo       R115L060-In-PA-Seq-No; 0 = read TF_Main_Charge, non-zero
 *                      = read TF_Part_Charge (R115L060.copy comment lines 6-9)
 * @param operationType R115L060-In-Operation-Type
 * @param checkPaSeqNo  R115L060-In-Check-Pa (R115L060.copy:52); only set to a
 *                      non-zero value by F115L070's "not AMEND" merge branch
 *                      right before reading Main_Charge (F115L070.src:143-146,
 *                      "Feilretting NDL, 19.10.01, må si fra hvilken rate vi er
 *                      på" - "must say which rate we are on"); zero otherwise
 */
public record ChargeListingQuery(
        ChargeTableVersion tableVersion,
        long finInstId,
        String contractType,
        long contractNo,
        int paSeqNo,
        String operationType,
        int checkPaSeqNo) {
}
