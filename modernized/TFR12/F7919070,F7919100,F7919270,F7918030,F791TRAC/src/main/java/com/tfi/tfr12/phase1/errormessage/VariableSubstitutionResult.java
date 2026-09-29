package com.tfi.tfr12.phase1.errormessage;

/**
 * Result of expanding {@code &key#} symbolic variables inside a message
 * template, mirroring F7918030.src E100-Bytt-Ut-Variabler.
 *
 * @param text          the template with every resolvable {@code &key#} replaced
 *                       by its value; unresolved keys are left in the output
 *                       text as a literal {@code &key} followed by a literal
 *                       {@code #} (the trailing "grisehale" - "pig's tail" -
 *                       ampersand is preserved as-is, see F7918030.src E130-Falsk-Key)
 * @param hadUnresolvedVariable true if at least one {@code &key#} placeholder
 *                       could not be matched against the supplied variable
 *                       table, or had empty (all-space/low-values) variable
 *                       data - the legacy module logs this case via
 *                       E190-Feil-I-Variabler
 */
public record VariableSubstitutionResult(String text, boolean hadUnresolvedVariable) {
}
