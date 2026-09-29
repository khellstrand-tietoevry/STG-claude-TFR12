package com.tfi.tfr12.phase1.datetime;

import java.time.Clock;

/**
 * Contract for the modern replacement of legacy program F7919100.src
 * ("Generell submodul. Benyttes for å konvertere mellom dato-formater.").
 *
 * <p>Encodes, among general format conversion, two confirmed business rules
 * documented in analysis/STG/TFR12/BUSINESS_RULES.md:
 * <ul>
 *   <li><b>RULE-036</b> - a sub-minimum input date (below each format's minimum
 *       representable value) is silently normalized to the sentinel null date
 *       00010101, rather than being rejected (F7919100.src:291-336).</li>
 *   <li><b>RULE-037</b> - a 2-digit-year input date (AMD7/DMA7) has its century
 *       determined by a sliding window: today's date (from F7919070) plus a
 *       years-ahead offset (default 50, overridable 1-99 via
 *       R7919100-In-Aar-Frem-I-Tid) is the window's upper bound; whichever
 *       century keeps the resulting date at or before that bound is chosen
 *       (F7919100.src:951-996).</li>
 * </ul>
 *
 * <p>Because RULE-037's window is anchored on "today", the contract takes an
 * explicit {@link Clock} standing in for the legacy module's internal call to
 * F7919070 (see F7919100.src F000-Glidende-Vindu).
 */
public interface DateFormatConversionService {

    /**
     * Converts a single input date into every requested output format.
     *
     * @param request the source date/format and requested target formats
     * @param clock   the clock supplying "today" for RULE-037's sliding window;
     *                irrelevant for source formats with a 4-digit year (AMD9,
     *                DMA9, JULIAN, DB2, A4D3) or with an A2D3 source (which also
     *                consults the window, see F7919100.src D700-Konv-Fra-A2D3)
     * @return every requested output format, computed from the (possibly
     *         RULE-036-normalized) input date
     * @throws DateConversionException if the resulting AMD9 date fails
     *                                  calendar validation, or an unparseable
     *                                  DB2 date string is supplied
     */
    DateConversionResult convert(DateConversionRequest request, Clock clock);
}
