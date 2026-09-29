package com.tfi.tfr12.phase3.pricing;

import java.math.BigDecimal;

/**
 * One occurrence of {@code PP_Prod_Tjen_Pris} (product/service price), as read by
 * the external {@code F349IPP0} DBI module (copybook {@code R349IPP0.copy}).
 *
 * <p>F115L150 only reads this table (Get-1st / Get-Next); it never inserts, updates
 * or deletes it, so only the fields actually consumed by F115L150's pricing logic
 * (D000-Process-Unique-Occurence, D100/D200-Compute-Charge-Amount and
 * E000-Process-Multiple-Occ) are modelled here. Field names mirror the COBOL
 * (Norwegian) names 1:1 so a reviewer can cross-check against the copybook.
 *
 * <p>Legacy PICTUREs (src/F115L150.src via copybooks/R349IPP0.copy:161-182):
 * <ul>
 *   <li>{@code prisbelop}       - R349IPP0-Prisbelop        Pic S9(7)V9(2) Comp-3 (flat price, bank base currency)</li>
 *   <li>{@code prisprosent}     - R349IPP0-Prisprosent      Pic S9(2)V9(3) Comp-3 (percentage)</li>
 *   <li>{@code maksimumpris}    - R349IPP0-Maksimumpris     Pic S9(9)      Comp   (max charge cap, base currency, no decimals)</li>
 *   <li>{@code minimumpris}     - R349IPP0-Minimumpris      Pic S9(9)      Comp   (min charge cap, base currency, no decimals)</li>
 *   <li>{@code ovrePrisbelop}   - R349IPP0-Ovre-Prisbelop   Pic S9(7)V9(2) Comp-3 (upper band, used ONLY for the flat-amount branch, F115L150.src:415-416)</li>
 *   <li>{@code nedrePrisbelop}  - R349IPP0-Nedre-Prisbelop  Pic S9(7)V9(2) Comp-3 (lower band, used ONLY for the flat-amount branch, F115L150.src:417-418)</li>
 *   <li>{@code gyldigTilBelop}  - R349IPP0-Gyldig-Til-Belop Pic S9(9)      Comp   (tier validity upper threshold, base currency, used ONLY in the
 *       tiered/multiple-occurrence path, F115L150.src:486,508)</li>
 * </ul>
 *
 * <p>Note {@code maksimumpris}/{@code minimumpris} (used in the "both zero" and
 * "percent only" branches of D000, and always in E000) are a DIFFERENT pair of
 * fields from {@code ovrePrisbelop}/{@code nedrePrisbelop} (used only in the
 * flat-amount branch of D000) - see RULE-021's edge cases and F115L150.src:392-435.
 */
public record PricingTier(
        BigDecimal prisbelop,
        BigDecimal prisprosent,
        BigDecimal maksimumpris,
        BigDecimal minimumpris,
        BigDecimal ovrePrisbelop,
        BigDecimal nedrePrisbelop,
        BigDecimal gyldigTilBelop) {
}
