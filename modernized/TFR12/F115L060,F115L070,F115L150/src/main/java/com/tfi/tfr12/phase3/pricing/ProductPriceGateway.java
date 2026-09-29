package com.tfi.tfr12.phase3.pricing;

import java.util.List;

/**
 * Port standing in for the external {@code F349IPP0} module ({@code PP_Prod_Tjen_Pris} DBI,
 * copybooks/R349IPP0.copy), which is not translated as part of this repo slice.
 *
 * <p>Legacy F115L150 opens a cursor on this table with one {@code GET-1ST} call
 * (C400-Read-1st-Prod-Tjen-Pris, F115L150.src:257-279) followed by zero or more
 * {@code GET-NEXT} calls (C430-Call-DBI-PP-PP, invoked again from
 * E000-Process-Multiple-Occ, F115L150.src:510) until the cursor is exhausted
 * (R7919999-Return-Error). {@link #findPriceTiers(ProductPriceQuery)} collapses that whole
 * cursor into a single ordered list: index 0 is the row the GET-1ST call would return, and
 * subsequent indices are what successive GET-NEXT calls would return, in the same order.
 *
 * <p>An empty list corresponds to R349IPP0-Ut-Ak-Rader-Totalt = 0 (F115L150.src:145-148:
 * no matching price row at all).
 */
public interface ProductPriceGateway {

    List<PricingTier> findPriceTiers(ProductPriceQuery query);
}
