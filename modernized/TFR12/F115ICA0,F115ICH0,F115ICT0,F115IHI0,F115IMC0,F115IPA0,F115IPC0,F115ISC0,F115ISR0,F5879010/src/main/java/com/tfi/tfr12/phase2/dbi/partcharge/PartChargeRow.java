package com.tfi.tfr12.phase2.dbi.partcharge;

import java.math.BigDecimal;

/**
 * A TF_Part_Charge row: the R115IPC0-Data group (copybook
 * R115IPC0.copy:157-181) — the 7 natural-key elements pinned by
 * {@link PartChargeKey} plus a representative slice of the non-key data
 * fields carried on every Insert/Update.
 *
 * <p>{@code finInstNo} is the external institution number (R115IPC0-Fin-Inst-No,
 * copybook line 182): the legacy record additionally carries R115IPC0-In-Fin-Inst-No
 * / R115IPC0-In-Fin-Inst-ID as a separate caller-supplied qualification group
 * (copybook lines 121-123), used only to prime the data fields before the
 * H100 check runs (F115IPC0.src:952-967). This modern row collapses that
 * primary/fallback duplication into a single {@code finInstId}/{@code finInstNo}
 * pair — a caller supplies one or the other, not a "loaded row" copy of both —
 * since the duplication in the legacy copybook exists only because one
 * physical COBOL group served both the request and the loaded-row shape.
 *
 * <p>Data fields represented here (of R115IPC0-Currency, -Amount,
 * -Role-Paying-Pty, -Charge-Pct, -Charge-Text; copybook lines 174-179):
 * currency, amount, rolePayingParty, chargePct, chargeText.
 */
public record PartChargeRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        String caCurrency,
        int paSeqNo,
        String roleType,
        String ctType,
        String currency,
        BigDecimal amount,
        String rolePayingParty,
        BigDecimal chargePct,
        String chargeText) {

    public PartChargeKey key() {
        return new PartChargeKey(finInstId, contractType, contractNo, caCurrency, paSeqNo, roleType, ctType);
    }
}
