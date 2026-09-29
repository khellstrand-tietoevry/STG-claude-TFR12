package com.tfi.tfr12.phase3.pricing;

import java.math.BigDecimal;

/**
 * Port standing in for the external {@code F116L030} currency-exchange module
 * (copybooks/R116L030.copy), which is not translated as part of this repo slice.
 *
 * <p>Legacy F115L150 calls it via L130-Call-LMOD-VA (F115L150.src:581-600), which the
 * calling paragraphs (L100-Convert-Amounts, E000-Process-Multiple-Occ) treat as always
 * succeeding with {@code Return-OK} on the happy path exercised by these characterization
 * tests; {@code Return-Error}/{@code Return-Abend} handling in F115L150 (F115L150.src:592-599)
 * is out of scope for the 4 pinned rules and is not modelled here.
 *
 * @return R116L030-To-Amount for the given request.
 */
public interface CurrencyExchangeGateway {

    BigDecimal exchange(CurrencyExchangeRequest request);
}
