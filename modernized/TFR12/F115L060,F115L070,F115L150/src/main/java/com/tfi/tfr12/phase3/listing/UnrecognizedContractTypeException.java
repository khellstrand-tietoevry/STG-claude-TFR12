package com.tfi.tfr12.phase3.listing;

/**
 * Mirrors the {@code Z900-Abend} fatal path in {@code C300-Set-Contract-Type}
 * (legacy/TFR12/src/F115L060.src:266-268, paragraph {@code C301}) when a contract
 * type does not belong to any of the four recognized families (RULE-019). This is
 * an abend in the legacy program, not a soft validation rejection, so it is modeled
 * here as an unchecked exception rather than an error result.
 */
public class UnrecognizedContractTypeException extends RuntimeException {

    public UnrecognizedContractTypeException(String contractType) {
        super("Unrecognized Contract-Type '" + contractType
                + "' (F115L060.src:253-269, paragraph C300-Set-Contract-Type / C301 abend)");
    }
}
