package com.tfi.tfr12.phase3.pricing;

import java.math.BigDecimal;

/**
 * Request to {@link CurrencyExchangeGateway}, standing in for one call to the external
 * {@code F116L030} module (copybooks/R116L030.copy), as built by L130-Call-LMOD-VA's
 * callers L100-Convert-Amounts (F115L150.src:539-579) and E000-Process-Multiple-Occ
 * (F115L150.src:471-479).
 *
 * <p>Exactly one of {@code fromBasicCurrency} / an explicit {@code fromCurrency}, and
 * exactly one of {@code toBasicCurrency} / an explicit {@code toCurrency}, is meaningful
 * per call - mirroring the copybook's mutually exclusive 88-level conditions
 * {@code R116L030-In-From-Basic-Cur} / {@code R116L030-In-To-Basic-Cur}
 * (copybooks/R116L030.copy:78,81).
 *
 * @param bankNo             R116L030-In-Fin-Inst-No: the first 4 characters of
 *                           R115ISC0-Fin-Inst-No (F115L150.src:473, 546-547).
 * @param fromAmount         R116L030-In-From-Amount.
 * @param fromCurrency       R116L030-In-From-Currency, or {@code null} when
 *                           {@code fromBasicCurrency} is {@code true}.
 * @param fromBasicCurrency  {@code true} when the call sets R116L030-In-From-Basic-Cur
 *                           (F115L150.src:477-478: converting a contract-currency amount
 *                           into the bank's base currency, ahead of tiered pricing).
 * @param toCurrency         R116L030-In-To-Currency, or {@code null} when
 *                           {@code toBasicCurrency} is {@code true}.
 * @param toBasicCurrency    {@code true} when the call sets R116L030-In-To-Basic-Cur
 *                           (F115L150.src:543-544: converting a base-currency POP amount
 *                           into the contract's currency, RULE-012).
 */
public record CurrencyExchangeRequest(
        String bankNo,
        BigDecimal fromAmount,
        String fromCurrency,
        boolean fromBasicCurrency,
        String toCurrency,
        boolean toBasicCurrency) {
}
