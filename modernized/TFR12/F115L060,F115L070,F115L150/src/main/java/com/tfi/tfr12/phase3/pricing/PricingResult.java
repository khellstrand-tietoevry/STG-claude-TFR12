package com.tfi.tfr12.phase3.pricing;

import java.math.BigDecimal;

/**
 * Output of {@link PopPricingService#computeDefaultCharge(PricingRequest)}, modelled on
 * {@code R115L150-Default-Charge} / {@code R115L150-Max-Min-Charge}
 * (copybooks/R115L150.copy:46-53), populated by J000-Output-Fields (F115L150.src:521-537).
 *
 * <p>When the POP lookup returns zero rows (R349IPP0-Ut-Ak-Rader-Totalt = 0), J000 is never
 * performed (F115L150.src:146-148) so the caller-visible record is left exactly as
 * Initialized by C100-Initialize-Output: all numeric amounts zero and {@code currency}
 * blank. {@link PopPricingServiceImpl} represents that blank COBOL alphanumeric as
 * {@code ""} rather than {@code null}.
 *
 * @param percentComm   R115L150-Percent-Comm (WS-Percent-Comm), F115L150.src:531.
 * @param chargeAmount  R115L150-Charge-Amount (WS-Charge-Amount), F115L150.src:532.
 * @param currency      R115L150-Currency (= R115ICA0-Currency, the contract's currency),
 *                      F115L150.src:533.
 * @param maximumCharge R115L150-Maximum-Charge (WS-Max-Charge), F115L150.src:534.
 * @param minimumCharge R115L150-Minimum-Charge (WS-Min-Charge), F115L150.src:535.
 */
public record PricingResult(
        BigDecimal percentComm,
        BigDecimal chargeAmount,
        String currency,
        BigDecimal maximumCharge,
        BigDecimal minimumCharge) {
}
