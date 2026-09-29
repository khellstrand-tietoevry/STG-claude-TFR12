package com.tfi.tfr12.phase3.pricing;

/**
 * Key used to look up POP price rows via {@link ProductPriceGateway}, standing in for the
 * qualification COBOL builds for {@code F349IPP0} in C400-Read-1st-Prod-Tjen-Pris
 * (F115L150.src:257-279): the caller's financial institution, the product identified via
 * {@code TF_Sys_Code}, the service code from {@code TF_Sys_Charge_Type}
 * (R115ICT0-PPFT-Code) and the pricing valid-date.
 *
 * <p>The exact composite-key plumbing across F115ISC0/F115ICT0 -&gt; F349IPP0
 * (R115ISC0-Product-No/-Product-Seq-No, R115ICT0-PPFT-Code, F115L150.src:264-276) is not
 * one of this module's pinned rules (RULE-003/011/012/021); this query intentionally only
 * carries what a test double needs to select the right fixture, not a byte-exact
 * reproduction of the DBI key layout. {@code validDate} (R115L150-In-Valid-Date,
 * RULE-001) IS carried through, since it is the one qualification field a fixture must be
 * able to filter on to prove RULE-001's "priced as of today" behavior end to end.
 */
public record ProductPriceQuery(
        long finInstId,
        String contractType,
        String chargeType,
        int validDate) {
}
