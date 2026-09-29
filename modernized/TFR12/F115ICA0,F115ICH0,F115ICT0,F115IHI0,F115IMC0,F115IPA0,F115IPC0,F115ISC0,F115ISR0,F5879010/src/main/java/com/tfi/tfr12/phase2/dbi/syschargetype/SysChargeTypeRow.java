package com.tfi.tfr12.phase2.dbi.syschargetype;

import java.math.BigDecimal;

/**
 * A TF_Sys_Charge_Type row: the {@code R115ICT0-Data} group
 * (copybook R115ICT0.copy:93-133) — the 3-element physical key pinned by
 * {@link SysChargeTypeKey} plus a representative slice of the non-key data
 * fields carried on every Insert/Update (G100-Move-Input-To-DCL,
 * F115ICT0.src:665-696; J100-Move-DCL-To-Output, F115ICT0.src:858-883).
 *
 * <p>{@code finInstNo} is the external institution number
 * (R115ICT0-Fin-Inst-No, copybook line 101): the legacy record additionally
 * carries R115ICT0-In-Fin-Inst-No / R115ICT0-In-Fin-Inst-Id / R115ICT0-In-CT-Type
 * / R115ICT0-In-Regno as a separate caller-supplied qualification group
 * (copybook lines 79-83), used only as a fallback source for the direct
 * fields before H100-Check-Qualification runs (F115ICT0.src:738-787). This
 * modern row collapses that primary/fallback duplication into a single field
 * per element, following the same convention used for
 * {@code MainChargeRow}/{@code PartChargeRow} in this codebase.
 *
 * <p>Data fields represented here (of the fuller set at copybook lines
 * 103-133: Description, Method-Calc, Sort, Credit, PPFT-Code, GL-Coll-Acc,
 * GL-ELC-Acc, GL-ILC-Acc, Branch-Code, Split-Pct, Read-POP, Paying-Pty,
 * GL-E-Coll-Acc, GL-GAR-Acc): {@code description}, {@code methodCalc}
 * (R115ICT0-Method-Calc, PIC X(01), 88-values {@code A}=Part-Amount,
 * {@code C}=Canceling, {@code S}=Settlement, {@code P}=Pct-Of-CA,
 * {@code Q}=Pct-Of-PA — copybook lines 104-109), {@code credit}
 * (R115ICT0-Credit, PIC X(01), 88-values {@code A}=Agent, {@code T}=Other-Bank,
 * {@code W}=Own-Bank — copybook lines 112-115), {@code glCollAcc}
 * (R115ICT0-GL-Coll-Acc, PIC S9(11) COMP-3 — copybook line 118) and
 * {@code splitPct} (R115ICT0-Split-Pct, PIC S9(03)V9(2) COMP-3 — copybook
 * line 122).
 *
 * <p><b>No Min/Max charge-amount fields exist in this copybook.</b> The
 * change-log at the top of both F115ICT0.src (lines 53, "Removed
 * Paying_Pty, Min_Max, added Sort", dated 050101) and R115ICT0.copy (line 51)
 * records that Min-Max was removed from this table version; the field is
 * still visible, commented out, at copybook line 116
 * ("{@code 10  R115ICT0-Min-Max Pic S9(11) Comp-3.}"), immediately followed
 * by the still-live {@code R115ICT0-PPFT-Code} at line 117. So unlike the
 * task's suggested example, min/max charge amounts are not part of this
 * table's current row and are not modeled.
 */
public record SysChargeTypeRow(
        String ctType,
        long finInstId,
        String finInstNo,
        int regno,
        String description,
        String methodCalc,
        String credit,
        long glCollAcc,
        BigDecimal splitPct) {

    public SysChargeTypeKey key() {
        return new SysChargeTypeKey(ctType, finInstId, regno);
    }
}
