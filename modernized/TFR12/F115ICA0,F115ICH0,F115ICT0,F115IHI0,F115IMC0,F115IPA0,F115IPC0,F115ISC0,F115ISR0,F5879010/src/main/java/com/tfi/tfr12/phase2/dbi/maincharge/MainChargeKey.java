package com.tfi.tfr12.phase2.dbi.maincharge;

/**
 * Natural key for TF_Main_Charge, per RULE-016: the 6 mandatory elements
 * independently checked by H100-Check-Qualification (F115ICH0.src:868-996)
 * before an Update is allowed to proceed —
 * <ol>
 *   <li>Fin-Inst-ID (F115ICH0.src:870-899, error {@code TF-FIN-INST-MISSING})</li>
 *   <li>Contract-Type (F115ICH0.src:901-918, error {@code TF-CONTRACT-MISSING})</li>
 *   <li>Contract-No (F115ICH0.src:920-937, error {@code TF-CONTRACT-MISSING})</li>
 *   <li>RP-Role-Type (F115ICH0.src:939-959, error {@code TF-PARTY-MISSING})</li>
 *   <li>Charge-Type (F115ICH0.src:961-977, error {@code TF-CHARGE-MISSING})</li>
 *   <li>CH-Seq-No (F115ICH0.src:979-994, error {@code TF-CHARGE-MISSING})</li>
 * </ol>
 *
 * <p>This is exactly the 6-element list the RULE-016 card names for the
 * Main_Charge variant ("Fin-Inst-ID..., Contract-Type, Contract-No,
 * RP-Role-Type, Charge-Type, CH-Seq-No") — confirmed against the source
 * rather than assumed from the card summary.
 *
 * <p><b>Deliberately excluded:</b> CA-Currency and PA-Seq-No. The table's
 * physical Related-Party foreign-key group (copybook R115ICH0.copy:169-172)
 * carries these two fields alongside RP-Role-Type, and the RP-Role-Type
 * qualification branch does copy all three input fields together
 * (F115ICH0.src:945-950) — but H100-Check-Qualification never independently
 * tests CA-Currency or PA-Seq-No for blank/zero and never raises an error
 * mnemonic for either one on its own. They ride along with RP-Role-Type
 * instead of gating the update themselves, so they are modeled as ordinary
 * fields on {@link MainChargeRow}, not as elements of this RULE-016 key. This
 * is the asymmetry the RULE-016 card calls out when it says Part_Charge
 * (F115IPC0) "requires 2 additional key elements (CA-Currency, PA-Seq-No)
 * beyond Main Charge."
 */
public record MainChargeKey(
        long finInstId,
        String contractType,
        long contractNo,
        String rpRoleType,
        String chargeType,
        int chSeqNo) {
}
