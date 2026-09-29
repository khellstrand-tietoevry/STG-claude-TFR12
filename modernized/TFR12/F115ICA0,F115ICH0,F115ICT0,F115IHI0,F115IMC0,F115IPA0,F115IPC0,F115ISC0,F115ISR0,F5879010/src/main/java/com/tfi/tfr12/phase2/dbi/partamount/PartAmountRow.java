package com.tfi.tfr12.phase2.dbi.partamount;

import java.math.BigDecimal;

/**
 * Row DTO for TF_Part_Amount, mirroring the mandatory-key-relevant part of
 * {@code R115IPA0-Data} (legacy/TFR12/copybooks/R115IPA0.copy:155-186) plus a
 * representative sample of the data fields, not the full column list
 * (R115IPA0.copy also has Register-Date, Prev-P-Amount, Accept-Date and the
 * Out-Row-No/Out-Total-Rows cursor accumulators, which are omitted here as
 * cursor/paging or lower-priority audit concerns, not core row identity).
 *
 * <p><b>Simplification vs. the legacy copybook (wire-format duplication):</b>
 * R115IPA0 carries each key element twice — once in the "data area" (e.g.
 * {@code R115IPA0-Contract-Type}) and once in the parallel "qualification
 * area" ({@code R115IPA0-In-Contract-Type}) — purely as an artifact of the old
 * single-copybook request/response transport (R115IPA0.copy:116-151 vs.
 * :157-166). {@link PartAmountRow} collapses each such pair into one field:
 * whichever value the caller supplied ends up here, and
 * {@link PartAmountKeyPolicy} validates this single set, matching the
 * fallback the legacy also performs (F115IPA0.src:904-1008) when only the
 * qualification-area field was given.
 *
 * <p><b>RULE-029 — split overloaded byte:</b> the legacy copybook packs two
 * unrelated flags into one byte, {@code R115IPA0-Incom-Swift}
 * (copybooks/R115IPA0.copy:180-183): an incoming-SWIFT Y/N indicator
 * ({@code Incom-Yes}='Y' / {@code Incom-No}='N') and an unrelated
 * settlement-lifecycle flag ({@code Has-Been-Settled}='A', consumed by
 * F115L060.src:604-628). The system owner confirmed this should be modeled as
 * two independent, separately-typed fields in the new domain model instead of
 * one shared byte: {@link #hasBeenSettled()} (boolean) and
 * {@link #incomSwift()} ({@link IncomSwift}). Unlike the legacy byte — which
 * can hold only one of {@code 'Y'}/{@code 'N'}/{@code 'A'}/space at a time —
 * this row can represent {@code hasBeenSettled=true} together with any
 * {@link IncomSwift} value, a combination the legacy encoding could never
 * cleanly express.
 *
 * @param finInstId       resolved internal Fin-Inst-ID, or 0 if not yet
 *                        resolved (R115IPA0-Fin-Inst-ID / -In-Fin-Inst-ID,
 *                        Pic S9(18) Comp-3)
 * @param finInstNo       external Fin-Inst-No, used to resolve
 *                        {@code finInstId} via
 *                        {@link com.tfi.tfr12.phase2.dbi.FinInstResolver} when
 *                        it is not already known (R115IPA0-Fin-Inst-No /
 *                        -In-Fin-Inst-No, Pic X(18))
 * @param contractType    R115IPA0-Contract-Type / -In-Contract-Type, Pic X(03)
 * @param contractNo      R115IPA0-Contract-No / -In-Contract-No, Pic S9(07) Comp-3
 * @param currency        R115IPA0-CA-Currency / -In-CA-Currency, Pic X(03), ISO 4217
 * @param paSeqNo         R115IPA0-PA-Seq-No / -In-PA-Seq-No, Pic S9(04) Comp
 * @param hasBeenSettled  settlement-lifecycle half of the legacy overloaded
 *                        byte (RULE-029): {@code true} when the legacy byte
 *                        would have been {@code 'A'} ({@code Has-Been-Settled})
 * @param incomSwift      incoming-SWIFT half of the legacy overloaded byte
 *                        (RULE-029), independent of {@code hasBeenSettled}
 * @param amount          R115IPA0-Amount (TFPA-Part-Amount), Pic S9(14)V9(03) Comp-3
 * @param status          R115IPA0-Status (TFPA-Status), Pic X(12)
 * @param userId          R115IPA0-User-ID (TFPA-User-ID), Pic X(08)
 * @param ansvar          availability flag, R115IPA0-Availability
 *                        (copybooks/R115IPA0.copy:184-186): {@code true} =
 *                        {@code R115IPA0-Ansvar} ('Y'), {@code false} =
 *                        {@code R115IPA0-Uten-Ansvar} (space)
 */
public record PartAmountRow(
        long finInstId,
        String finInstNo,
        String contractType,
        long contractNo,
        String currency,
        int paSeqNo,
        boolean hasBeenSettled,
        IncomSwift incomSwift,
        BigDecimal amount,
        String status,
        String userId,
        boolean ansvar) {

    /**
     * Decodes the legacy overloaded byte's settlement half. {@code 'A'} means
     * settled; anything else (including {@code 'Y'}, {@code 'N'} and space)
     * means not settled, per {@code R115IPA0-Has-Been-Settled}'s single
     * matching value (copybooks/R115IPA0.copy:183).
     */
    public static boolean hasBeenSettledFromLegacyByte(char rawByte) {
        return rawByte == 'A';
    }
}
