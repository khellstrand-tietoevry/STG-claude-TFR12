package com.tfi.tfr12.phase3.listing;

/**
 * Mirrors the {@code Z700-Error-AE} soft-reject path ({@code 'L103'}) raised by
 * {@code L100-Read-Sys-Charge-Type} (legacy/TFR12/src/F115L060.src:1147-1149) when
 * NEITHER the institution-scoped nor the registration-number-scoped
 * {@code F115ICT0} lookup finds a row for the charge type (RULE-038). Unlike
 * {@link UnrecognizedContractTypeException} (an abend), the legacy paragraph reaches
 * {@code Z700-Error-AE} here, i.e. a caller-visible rejection rather than a program
 * abend - modeled as a distinct, separately-named exception type for that reason.
 */
public class ChargeTypeNotConfiguredException extends RuntimeException {

    public ChargeTypeNotConfiguredException(String chargeType, int regNo) {
        super("No TF_Sys_Charge_Type configuration for charge type '" + chargeType
                + "' by institution or by registration number " + regNo
                + " (F115L060.src:1104-1156, paragraph L100-Read-Sys-Charge-Type, error 'L103')");
    }
}
