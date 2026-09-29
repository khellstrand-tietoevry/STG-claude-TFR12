package com.tfi.tfr12.phase3.pricing;

/**
 * RULE-021 (P1, F115L150.src:390-438, validated High confidence): a POP price row must
 * define exactly one of a flat price amount (Prisbelop) or a price percentage
 * (Prisprosent); both non-zero on the same row is invalid configuration.
 *
 * <p>Legacy behaviour (D000-Process-Unique-Occurence, F115L150.src:401-410): when
 * {@code R349IPP0-Prisbelop not = +0} and {@code R349IPP0-Prisprosent not = +0} at the
 * same time, F115L150 builds error message {@code TF-SY-INVALID-CONTENT-TAB-VAR1} with
 * variable {@code VAR1 = 'PP_Prod_Tjen_Pris'} and returns Error (AE) via Z700-Error-AE
 * (F115L150.src:409-410) - it never returns a partially-computed charge for that row.
 */
public class InvalidProductPricingConfigurationException extends RuntimeException {

    private static final String MESSAGE_CODE = "TF-SY-INVALID-CONTENT-TAB-VAR1";
    private static final String VARIABLE_DATA = "PP_Prod_Tjen_Pris";

    public InvalidProductPricingConfigurationException() {
        super(MESSAGE_CODE + ": " + VARIABLE_DATA
                + " has both a non-zero Prisbelop and a non-zero Prisprosent (F115L150.src:401-410)");
    }
}
