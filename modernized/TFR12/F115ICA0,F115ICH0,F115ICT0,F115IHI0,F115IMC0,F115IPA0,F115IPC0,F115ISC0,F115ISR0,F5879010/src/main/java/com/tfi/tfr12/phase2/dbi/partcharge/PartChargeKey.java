package com.tfi.tfr12.phase2.dbi.partcharge;

/**
 * Natural key for TF_Part_Charge, per RULE-016 / RULE-018: the 7 mandatory
 * elements checked by H100-Check-Qualification (F115IPC0.src:946-1086) before
 * a Select/Update/Delete is allowed to proceed —
 * <ol>
 *   <li>Fin-Inst-ID (F115IPC0.src:948-976, error {@code TF-FIN-INST-MISSING})</li>
 *   <li>Contract-Type (F115IPC0.src:979-996, error {@code TF-CONTRACT-MISSING})</li>
 *   <li>Contract-No (F115IPC0.src:998-1015, error {@code TF-CONTRACT-MISSING})</li>
 *   <li>CA-Currency (F115IPC0.src:1017-1034, error {@code TF-PART-AMOUNT-MISSING})</li>
 *   <li>PA-Seq-No (F115IPC0.src:1036-1052, error {@code TF-PART-AMOUNT-MISSING})</li>
 *   <li>Role-Type (F115IPC0.src:1054-1069, error {@code TF-ROLE-MISSING})</li>
 *   <li>CT-Type (F115IPC0.src:1071-1086, error {@code TF-CHARGE-MISSING})</li>
 * </ol>
 *
 * <p>This is exactly the field list the RULE-016 card names for the
 * Part_Charge variant, plus the 2 additional elements (CA-Currency,
 * PA-Seq-No) it calls out as being over and above what F115ICH0 (Main
 * Charge) requires — confirmed against the source rather than assumed from
 * the card summary.
 */
public record PartChargeKey(
        long finInstId,
        String contractType,
        long contractNo,
        String caCurrency,
        int paSeqNo,
        String roleType,
        String ctType) {
}
