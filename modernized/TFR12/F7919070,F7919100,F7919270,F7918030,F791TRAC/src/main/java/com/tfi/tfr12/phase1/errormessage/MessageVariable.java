package com.tfi.tfr12.phase1.errormessage;

/**
 * A caller-supplied symbolic-variable substitution, mirroring one occurrence of
 * R7918030-In-Variabel-Tabell (In-Variabel-Key / In-Variabel-Data).
 *
 * @param key   the symbolic name, matched against {@code &key#} placeholders in
 *              the message template (case-sensitive, exact match against
 *              R7918030-In-Variabel-Key)
 * @param value the replacement text; trailing spaces AND trailing low-values
 *              are trimmed before substitution (F7918030.src E120-Slutt-Key,
 *              the 200125 SM change to also skip Low-values)
 */
public record MessageVariable(String key, String value) {
}
