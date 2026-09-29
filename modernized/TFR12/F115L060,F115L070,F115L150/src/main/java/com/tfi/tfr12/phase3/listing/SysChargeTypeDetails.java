package com.tfi.tfr12.phase3.listing;

import com.tfi.tfr12.phase2.dbi.syschargetype.SysChargeTypeRow;

/**
 * The subset of a {@code TF_Sys_Charge_Type} row that {@code F115L060} reads which
 * is NOT already modeled by Phase 2's {@link SysChargeTypeRow} (which only carries
 * a "representative slice" of {@code R115ICT0-Data} - see its own javadoc). Rather
 * than re-model the fields {@link SysChargeTypeRow} already has, this record wraps
 * one and adds exactly the extra {@code R115ICT0} fields {@code F115L060} needs:
 * {@code R115ICT0-Paying-Pty} (role category, RULE-033), {@code R115ICT0-Read-POP}
 * (RULE-001), and the three additional GL-account fields
 * (RULE-019: {@code R115ICT0-GL-ILC-Acc}, {@code -GL-ELC-Acc}, {@code -GL-GAR-Acc};
 * {@code R115ICT0-GL-Coll-Acc} is already on {@link SysChargeTypeRow}) - all four
 * confirmed present at legacy/STG/TFR12/copybooks/R115ICT0.copy:118-133.
 *
 * @param row            the already-translated Phase 2 row (ctType, finInstId,
 *                        methodCalc, credit, glCollAcc, etc.)
 * @param payingPty      {@code R115ICT0-Paying-Pty} (copybook lines 126-131), consumed
 *                        by RULE-033
 * @param readPopEnabled {@code true} unless {@code R115ICT0-Read-POP} = {@code 'N'}
 *                        (copybook lines 123-125: {@code R115ICT0-Read-POP-Yes} is
 *                        the DEFAULT value, a space, not an explicit 'Y') - RULE-001
 * @param glIlcAcc       {@code R115ICT0-GL-ILC-Acc} (copybook line 120)
 * @param glElcAcc       {@code R115ICT0-GL-ELC-Acc} (copybook line 119)
 * @param glGarAcc       {@code R115ICT0-GL-GAR-Acc} (copybook line 133)
 */
public record SysChargeTypeDetails(
        SysChargeTypeRow row,
        ChargeRoleCategory payingPty,
        boolean readPopEnabled,
        long glIlcAcc,
        long glElcAcc,
        long glGarAcc) {

    public String ctType() {
        return row.ctType();
    }

    public String methodCalc() {
        return row.methodCalc();
    }

    public long glCollAcc() {
        return row.glCollAcc();
    }
}
