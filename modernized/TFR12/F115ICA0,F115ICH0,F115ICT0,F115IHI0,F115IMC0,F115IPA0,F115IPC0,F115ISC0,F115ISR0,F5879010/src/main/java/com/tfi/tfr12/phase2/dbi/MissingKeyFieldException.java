package com.tfi.tfr12.phase2.dbi;

/**
 * Raised when a mandatory natural-key field cannot be resolved before a
 * Select/Update/Delete, mirroring the per-table H100-Check-Qualification
 * error mnemonics (RULE-016, RULE-018): TF-FIN-INST-MISSING,
 * TF-CONTRACT-MISSING, TF-CURRENCY-MISSING, TF-PART-AMOUNT-MISSING,
 * TF-PARTY-MISSING / TF-ROLE-MISSING, TF-CHARGE-MISSING.
 */
public class MissingKeyFieldException extends RuntimeException {
    private final String legacyErrorCode;

    public MissingKeyFieldException(String legacyErrorCode) {
        super("Mandatory key field unresolved: " + legacyErrorCode);
        this.legacyErrorCode = legacyErrorCode;
    }

    /** e.g. "TF-FIN-INST-MISSING". */
    public String legacyErrorCode() {
        return legacyErrorCode;
    }
}
