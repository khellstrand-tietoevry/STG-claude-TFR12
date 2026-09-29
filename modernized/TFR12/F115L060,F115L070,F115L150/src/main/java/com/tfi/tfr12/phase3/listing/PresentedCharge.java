package com.tfi.tfr12.phase3.listing;

import java.math.BigDecimal;

/**
 * One row of {@code R115L060-Charge-Element} (legacy/STG/TFR12/copybooks/R115L060.copy:66-98)
 * as populated for the fields {@link ChargeListingService#applyAdditionalInfo} owns -
 * the output of {@code L000-Additional-Info} plus the {@code R115L060-Charge-Amount} /
 * {@code -Charge-Currency} / {@code -Maximum-Charge} / {@code -Minimum-Charge} moves in
 * {@code J100-Iter-Output} (legacy/TFR12/src/F115L060.src:981-992).
 *
 * <p>Deliberately NOT modeled here (out of this task's 7 pinned rules, see
 * {@link ChargeListingService} class javadoc): {@code R115L060-Charge-Description},
 * {@code -Charge-Method-Calc}, {@code -Charge-Credit}, {@code -PPFT-Code},
 * {@code -GL-Account}, {@code -Branch-Code} (straight copies from
 * {@link SysChargeTypeDetails}, not computed by any of the 7 rules) and the running
 * totals from {@code L300-Accumulate-Totals} (RULE-009 territory).
 *
 * @param chargeType     {@code R115L060-Charge-Type}
 * @param roleType       {@code R115L060-Charge-Role-Type}
 * @param chargeAmount   {@code WS-Charge-Amount} after RULE-007's conditional recompute,
 *                       2 decimals
 * @param convertedAmount {@code R115L060-Charge-Amount} (= {@code WS-Converted-Amount}),
 *                       RULE-006: always numerically equal to {@code chargeAmount}
 *                       widened to 3 decimals, never FX-converted
 * @param currency       {@code R115L060-Charge-Currency} (= {@code WS-Currency}), RULE-006:
 *                       relabeled to the contract's currency when it differed, unconverted
 * @param percent        {@code R115L060-Charge-Percentage}
 * @param chargeText     {@code R115L060-Charge-Text}
 * @param maximumCharge  {@code R115L060-Maximum-Charge} (= {@code R115L150-Maximum-Charge}),
 *                       RULE-001: zero when POP lookup was skipped
 * @param minimumCharge  {@code R115L060-Minimum-Charge} (= {@code R115L150-Minimum-Charge}),
 *                       RULE-001: zero when POP lookup was skipped
 */
public record PresentedCharge(
        String chargeType,
        String roleType,
        BigDecimal chargeAmount,
        BigDecimal convertedAmount,
        String currency,
        BigDecimal percent,
        String chargeText,
        BigDecimal maximumCharge,
        BigDecimal minimumCharge) {
}
