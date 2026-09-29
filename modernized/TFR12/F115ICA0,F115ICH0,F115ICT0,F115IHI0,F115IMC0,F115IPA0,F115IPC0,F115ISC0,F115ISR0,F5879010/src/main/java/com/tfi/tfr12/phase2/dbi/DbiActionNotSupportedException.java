package com.tfi.tfr12.phase2.dbi;

/**
 * Raised when a table's {@link Capabilities} disables the requested action,
 * mirroring the legacy abend 'GL-IN-AKSJON-UV' (invalid action) that
 * F115ISR0.src (RULE-027, Update disabled) and F115IHI0.src (RULE-028, Select
 * and Update disabled) raise via their C000-Interface dispatcher's "When
 * Other" branch, when the requested action's Evaluate condition-name is
 * commented out / unreachable.
 */
public class DbiActionNotSupportedException extends RuntimeException {
    public DbiActionNotSupportedException(String action) {
        super("Action not supported on this table: " + action + " (legacy: GL-IN-AKSJON-UV)");
    }
}
