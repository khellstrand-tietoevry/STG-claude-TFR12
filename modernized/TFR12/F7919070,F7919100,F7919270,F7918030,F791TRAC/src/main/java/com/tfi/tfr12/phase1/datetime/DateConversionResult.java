package com.tfi.tfr12.phase1.datetime;

/**
 * Output of {@link DateFormatConversionService#convert}, mirroring
 * R7919100-Resultat. Every field that was not in the caller's requested
 * {@code targetFormats} is populated with the legacy "unset" value anyway,
 * since AMD9 is always computed as the pivot format (F7919100.src
 * E000-Konv-Til-Output always sets -Ut-Dato-AMD9 unconditionally, then
 * populates the others only if requested).
 *
 * @param amd9    8-digit ÅÅÅÅMMDD, or the RULE-036 sentinel "00010101"
 * @param dma9    8-digit DDMMÅÅÅÅ, populated only if requested
 * @param amd7    6-digit ÅÅMMDD, populated only if requested
 * @param dma7    6-digit DDMMÅÅ, populated only if requested
 * @param julian  legacy pseudo-Julian day number, populated only if requested
 * @param db2     "YYYY-MM-DD" ISO string, populated only if requested
 * @param a4d3    7-digit ÅÅÅÅDDD (true ordinal day), populated only if requested
 * @param a2d3    5-digit ÅÅDDD (true ordinal day), populated only if requested
 */
public record DateConversionResult(
        String amd9,
        String dma9,
        String amd7,
        String dma7,
        Integer julian,
        String db2,
        String a4d3,
        String a2d3) {

    /** RULE-036: the sentinel "null date" substituted for any sub-minimum input date. */
    public static final String SENTINEL_NULL_DATE_AMD9 = "00010101";
}
