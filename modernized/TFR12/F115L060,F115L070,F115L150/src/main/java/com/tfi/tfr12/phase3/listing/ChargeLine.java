package com.tfi.tfr12.phase3.listing;

import java.math.BigDecimal;

/**
 * One raw charge occurrence exactly as read from {@code TF_Main_Charge}
 * (E120-Read-Next-TF-CH, legacy/TFR12/src/F115L060.src:704-709) or
 * {@code TF_Part_Charge} (F120-Process-TF-PC, F115L060.src:786-791), before
 * {@code L000-Additional-Info} runs. Field names follow the {@code WS-Charge} group
 * (F115L060.src:69-80).
 *
 * @param chargeType  {@code WS-Charge-Type}, Pic X(20)
 * @param roleType    {@code WS-Role-Type}, Pic X(20) (the paying-party role type this
 *                    occurrence was read under - see RULE-033)
 * @param chargeAmount {@code WS-Charge-Amount}, Pic S9(14)V9(02) Comp-3 (2 decimals)
 * @param currency    {@code WS-Currency}, Pic X(03)
 * @param percent     {@code WS-Percent}, Pic S9(03)V9(03) Comp-3 (3 decimals)
 * @param chargeText  {@code WS-Charge-Text}, Pic X(30)
 */
public record ChargeLine(
        String chargeType,
        String roleType,
        BigDecimal chargeAmount,
        String currency,
        BigDecimal percent,
        String chargeText) {
}
