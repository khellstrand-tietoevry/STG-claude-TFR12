package com.tfi.tfr12.phase3.merge;

import java.util.List;

/**
 * Mirrors {@code R115L070-Data} on output (legacy/STG/TFR12/copybooks/R115L070.copy:63-130):
 * the final charge table plus totals produced by F115L070's J000-Output-Fields
 * (F115L070.src:886-890).
 */
public record ChargeMergeResult(List<ChargeRow> charges, ChargeTotals totals) {
}
