package com.tfi.tfr12.phase3.pricing;

/**
 * Java translation of legacy COBOL program {@code F115L150} ("Read POP"), which computes a
 * default/negotiated (POP = "party specific price") charge, its percentage, and its
 * min/max caps, from the {@code PP_Prod_Tjen_Pris} product/price table.
 *
 * <p>Source: {@code legacy/STG/TFR12/src/F115L150.src}. See {@link PopPricingServiceImpl}
 * for the paragraph-by-paragraph mapping and the rules pinned (RULE-003, RULE-011,
 * RULE-012, RULE-021).
 */
public interface PopPricingService {

    /**
     * Equivalent to a full call of F115L150 (A000-Main -&gt; C000-Interface), for the happy
     * path where the POP lookup key is already resolved into {@code request}.
     *
     * @throws InvalidProductPricingConfigurationException RULE-021: a single matching POP
     *         row defines both a flat amount and a percentage.
     */
    PricingResult computeDefaultCharge(PricingRequest request);
}
