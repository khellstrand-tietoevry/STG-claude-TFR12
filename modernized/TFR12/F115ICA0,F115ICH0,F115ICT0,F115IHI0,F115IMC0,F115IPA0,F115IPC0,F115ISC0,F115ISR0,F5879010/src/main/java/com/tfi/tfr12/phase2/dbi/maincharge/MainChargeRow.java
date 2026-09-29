package com.tfi.tfr12.phase2.dbi.maincharge;

import java.math.BigDecimal;

/**
 * A TF_Main_Charge row: the R115ICH0-Data group (copybook
 * R115ICH0.copy:161-184) — the 6 RULE-016 key elements pinned by
 * {@link MainChargeKey}, the 2 additional physical Related-Party key fields
 * (caCurrency, paSeqNo) that ride along with rpRoleType without their own
 * mandatory check, and a representative slice of the non-key data fields
 * carried on every Insert/Update.
 *
 * <p>{@code finInstNo} is the external institution number
 * (R115ICH0-Fin-Inst-No, copybook line 178): the legacy record additionally
 * carries R115ICH0-In-Fin-Inst-No / R115ICH0-In-Fin-Inst-ID and In- variants
 * of every other key field as a separate caller-supplied qualification group
 * (copybook lines 123-151), used only as a fallback source for the direct
 * fields before H100-Check-Qualification runs (F115ICH0.src:868-996). This
 * modern row collapses that primary/fallback duplication into a single field
 * per element — a caller supplies one value per key element, not a "loaded
 * row" copy plus an input-side alternate — since the duplication in the
 * legacy copybook exists only because one physical COBOL group served both
 * the request qualification and the loaded-row shape. The same collapse is
 * used for {@code PartChargeRow} in this codebase.
 *
 * <p>Data fields represented here (of R115ICH0-Charge-Amount, -Currency,
 * -Charge-Pct, -Charge-Text; copybook lines 180-184): chargeAmount, currency,
 * chargePct, chargeText.
 */
public record MainChargeRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        String caCurrency,
        int paSeqNo,
        String rpRoleType,
        String chargeType,
        int chSeqNo,
        BigDecimal chargeAmount,
        String currency,
        BigDecimal chargePct,
        String chargeText) {

    public MainChargeKey key() {
        return new MainChargeKey(finInstId, contractType, contractNo, rpRoleType, chargeType, chSeqNo);
    }
}
