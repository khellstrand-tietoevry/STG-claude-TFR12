package com.tfi.tfr12.phase3.merge;

import java.util.List;

/**
 * The result of one F115L070-src:254/276 {@code Call 'F115L060'} - "figures
 * exactly as they appear from one and only one of the two database tables"
 * (legacy/STG/TFR12/copybooks/R115L060.copy:11-14): either TF_Main_Charge
 * (when the qualifying PA-Seq-No is zero) or TF_Part_Charge (when it is
 * non-zero), never both. Mirrors {@code R115L060-Data}
 * (R115L060.copy:56-129): the charge rows plus that same table's own totals.
 *
 * <p>{@code com.tfi.tfr12.phase3.listing} (F115L060's own module, owned by a
 * sibling work item) does not yet expose this call-boundary shape - its
 * {@code ChargeListingService} instead exposes F115L060's individual internal
 * paragraphs (RULE-001/006/007/019/029/033/038). Rather than add the missing
 * orchestration method to that sibling-owned package (out of this module's
 * strict scope, {@code com.tfi.tfr12.phase3.merge} only), this type and
 * {@link ChargeListingPort} model the "Call F115L060" boundary entirely on
 * this (F115L070) side, exactly as F115L070.src itself sees it.
 */
public record ChargeListingSnapshot(List<ChargeRow> charges, ChargeTotals totals) {
}
