package com.tfi.tfr12.phase3.merge;

import java.math.BigDecimal;
import java.util.Set;

/**
 * One occurrence of {@code R115L060-Charge-Element} / {@code R115L070-Charge-Element}
 * (Occurs 50, legacy/STG/TFR12/copybooks/R115L060.copy:66-98 and
 * R115L070.copy:73-99). Per DATA_OBJECTS.md:191, the two copybooks' Charge-Element
 * groups are byte-for-byte identical in shape - R115L070's copy simply carries
 * merged/adjusted figures instead of raw-as-stored ones - so this single record
 * models both the "as read from F115L060" and the "as merged by F115L070" row.
 *
 * @param chargeType          Pic X(20)
 * @param methodOfCalculation Pic X(01), see {@link ChargeMethodOfCalculation}
 * @param roleType            Pic X(05); see {@link #isSeller()} / {@link #isBuyer()}
 *                            for the 88-level value sets (R115L060.copy:74-80)
 * @param amount              Pic S9(14)V9(3) Comp-3
 * @param currency            Pic X(03)
 * @param percentage          Pic 9(03)V9(03) Comp-3 - UNSIGNED in the copybook
 * @param maximumCharge       Pic S9(14)V9(03) Comp-3; 0 means "no cap" (RULE-002)
 * @param minimumCharge       Pic S9(14)V9(03) Comp-3
 * @param credit              Pic X(01), see {@link ChargeCreditRoute}
 * @param description         Pic X(80), carried through unchanged by merge/reversal
 * @param ppftCode            Pic X(06), carried through unchanged
 * @param glAccount           Pic S9(11) Comp-3, carried through unchanged
 * @param branchCode          Pic S9(05) Comp-3, carried through unchanged
 * @param text                Pic X(30), carried through unchanged
 */
public record ChargeRow(
        String chargeType,
        ChargeMethodOfCalculation methodOfCalculation,
        String roleType,
        BigDecimal amount,
        String currency,
        BigDecimal percentage,
        BigDecimal maximumCharge,
        BigDecimal minimumCharge,
        ChargeCreditRoute credit,
        String description,
        String ppftCode,
        long glAccount,
        long branchCode,
        String text) {

    /** R115L060-Seller, Value 'DRWER', 'BENEF' (R115L060.copy:75-76). */
    private static final Set<String> SELLER_ROLE_TYPES = Set.of("DRWER", "BENEF");

    /** R115L060-Buyer, Value 'DRWEE', 'ISSUI', 'CUSTM', spaces (R115L060.copy:77-80). */
    private static final Set<String> BUYER_ROLE_TYPES = Set.of("DRWEE", "ISSUI", "CUSTM");

    public static final BigDecimal ZERO_AMOUNT = BigDecimal.ZERO.setScale(3);
    public static final BigDecimal ZERO_PERCENT = BigDecimal.ZERO.setScale(3);

    /**
     * A blank/uninitialized table occurrence - what COBOL would read from a
     * fixed {@code Occurs 50} array slot beyond {@code No-Tab-Occ}. Used to
     * reproduce the legacy's "just reads zeros/spaces" behaviour when this
     * Java port's variable-length {@link java.util.List} is indexed past its
     * end (e.g. Main_Charge shorter than Part_Charge), instead of throwing an
     * index-out-of-bounds exception the legacy never would.
     */
    public static final ChargeRow BLANK = new ChargeRow(
            "", ChargeMethodOfCalculation.NONE, "", ZERO_AMOUNT, "", ZERO_PERCENT,
            ZERO_AMOUNT, ZERO_AMOUNT, ChargeCreditRoute.NONE, "", "", 0L, 0L, "");

    public boolean isSeller() {
        return SELLER_ROLE_TYPES.contains(roleType);
    }

    /** True for DRWEE/ISSUI/CUSTM or blank (R115L060.copy:77-80 includes 5 spaces). */
    public boolean isBuyer() {
        return BUYER_ROLE_TYPES.contains(roleType) || roleType == null || roleType.isBlank();
    }

    public ChargeRow withAmount(BigDecimal newAmount) {
        return new ChargeRow(chargeType, methodOfCalculation, roleType, newAmount, currency, percentage,
                maximumCharge, minimumCharge, credit, description, ppftCode, glAccount, branchCode, text);
    }

    public ChargeRow withAmountAndPercentage(BigDecimal newAmount, BigDecimal newPercentage) {
        return new ChargeRow(chargeType, methodOfCalculation, roleType, newAmount, currency, newPercentage,
                maximumCharge, minimumCharge, credit, description, ppftCode, glAccount, branchCode, text);
    }
}
