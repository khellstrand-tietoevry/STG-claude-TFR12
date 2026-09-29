package com.tfi.tfr12.phase3.merge;

/**
 * Mirrors {@code R115L060-Charge-Method-Calc} / {@code R115L070-Charge-Method-Calc}
 * (Pic X(01), legacy/STG/TFR12/copybooks/R115L060.copy:68-73 and
 * R115L070.copy:75-80) and its five 88-level condition names.
 *
 * <p><b>QUIRK pinned deliberately, not simplified away:</b> the copybook's own
 * 88-levels give {@code 'C'} TWO simultaneous meanings —
 * {@code 88 MethCalc-Canceling Value 'C'} AND {@code 88 MethCalc-Settlement
 * Value 'S', 'C'}. A charge row stored with method code {@code 'C'} therefore
 * satisfies {@link #isCanceling()} AND {@link #isSettlement()} at the same
 * time. F115L070 never tests {@code MethCalc-Canceling} anywhere in its own
 * source (only Pct-Of-CA/Pct-Of-PA/Settlement/Part-Amount are evaluated, e.g.
 * F115L070.src:498-502, 621-624), so in practice a {@code 'C'}-coded charge is
 * always processed as a Settlement charge (summed, RULE-008) by this module —
 * "Canceling" is a distinction with no behavioural difference here.
 */
public record ChargeMethodOfCalculation(char code) {

    public static final ChargeMethodOfCalculation PART_AMOUNT = new ChargeMethodOfCalculation('A');
    public static final ChargeMethodOfCalculation CANCELING = new ChargeMethodOfCalculation('C');
    public static final ChargeMethodOfCalculation SETTLEMENT = new ChargeMethodOfCalculation('S');
    public static final ChargeMethodOfCalculation PCT_OF_CA = new ChargeMethodOfCalculation('P');
    public static final ChargeMethodOfCalculation PCT_OF_PA = new ChargeMethodOfCalculation('Q');
    /** Blank/uninitialized occurrence, e.g. a table row beyond No-Tab-Occ. */
    public static final ChargeMethodOfCalculation NONE = new ChargeMethodOfCalculation(' ');

    /** R115L060-MethCalc-Part-Amount, Value 'A'. */
    public boolean isPartAmount() {
        return code == 'A';
    }

    /** R115L060-MethCalc-Canceling, Value 'C' - see class javadoc for the shared-code quirk. */
    public boolean isCanceling() {
        return code == 'C';
    }

    /** R115L060-MethCalc-Settlement, Value 'S', 'C'. */
    public boolean isSettlement() {
        return code == 'S' || code == 'C';
    }

    /** R115L060-MethCalc-Pct-Of-CA, Value 'P'. */
    public boolean isPctOfCA() {
        return code == 'P';
    }

    /** R115L060-MethCalc-Pct-Of-PA, Value 'Q'. */
    public boolean isPctOfPA() {
        return code == 'Q';
    }
}
