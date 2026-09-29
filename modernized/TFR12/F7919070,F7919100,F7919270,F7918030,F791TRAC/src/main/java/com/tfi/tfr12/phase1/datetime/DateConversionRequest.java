package com.tfi.tfr12.phase1.datetime;

import java.util.EnumSet;
import java.util.Set;

/**
 * Input to {@link DateFormatConversionService#convert}, modeling a single
 * populated input field from R7919100-Konverter-Dato plus the caller's
 * requested output formats.
 *
 * <p>Scope note: the legacy module also supports an "auto-detect input format"
 * mode (F7919100.src D000-Konv-Fra-Input, the final {@code Evaluate True / When
 * Other} branch at lines 383-518) that infers the source format purely from
 * which numeric ranges the various -In-Dato-xxx fields fall into when no
 * explicit -In-Konv-Fra-xxx switch is set. This contract intentionally requires
 * the caller to state {@link #sourceFormat()} explicitly and does not
 * characterize the auto-detect branch.
 *
 * @param sourceFormat        which of the eight formats {@link #sourceValue()} is in
 * @param sourceValue         the input date, textual and zero-padded to the
 *                             natural width of {@code sourceFormat}
 *                             (AMD9/DMA9 = 8 digits, AMD7/DMA7 = 6 digits,
 *                             JULIAN = up to 5 digits, DB2 = "YYYY-MM-DD",
 *                             A4D3 = 7 digits, A2D3 = 5 digits), or a blank/empty
 *                             string for DB2 to represent COBOL Spaces
 * @param targetFormats        formats to populate in the result; the legacy default
 *                             (no -In-Til-Formater bits set) is AMD9+DMA9+AMD7+DMA7+JULIAN+DB2
 *                             (A4D3/A2D3 excluded from the default; see F7919100.src
 *                             B000-Initialize)
 * @param yearsAheadOverride   overrides the default 50-year "sliding window" used
 *                             to pick a century for 2-digit-year input formats
 *                             (AMD7/DMA7); corresponds to R7919100-In-Aar-Frem-I-Tid,
 *                             valid range 1-99, {@code null} or any other value means
 *                             "use the default of 50" (RULE-037)
 */
public record DateConversionRequest(
        DateFormat sourceFormat,
        String sourceValue,
        Set<DateFormat> targetFormats,
        Integer yearsAheadOverride) {

    public static final Set<DateFormat> DEFAULT_TARGET_FORMATS =
            EnumSet.of(DateFormat.AMD9, DateFormat.DMA9, DateFormat.AMD7,
                    DateFormat.DMA7, DateFormat.JULIAN, DateFormat.DB2);
}
