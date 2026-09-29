package com.tfi.tfr12.phase1.datetime;

/**
 * Input to {@link DateTimeConversionService#convert}, mirroring
 * R7919270-Kvalifisering. Exactly one of the following supplied-field
 * combinations is expected to be non-empty (F7919100... rather F7919270.src
 * D000-Konv-Fra-Input Evaluate at lines 250-317), matching the legacy priority
 * order:
 * <ol>
 *   <li>{@code timestampDb2} alone</li>
 *   <li>{@code timestamp16} alone</li>
 *   <li>{@code date} + {@code timeTms} (+ optional {@code microseconds})</li>
 *   <li>{@code date} + {@code timeTmsh} (+ optional {@code microseconds} - see
 *       the field's javadoc for the surprising overwrite quirk this combination
 *       triggers)</li>
 *   <li>{@code date} alone (time defaults to all-zero)</li>
 * </ol>
 * Supplying no fields at all, or a combination that matches none of the above
 * (e.g. {@code timeTms} together with {@code timestampDb2}), is rejected
 * (F7919270.src D001/D002).
 *
 * @param date          ÅÅÅÅMMDD, or {@code null} if not supplied
 * @param timeTms       HHMMSS (6 digits, no hundredths), or {@code null}
 * @param timeTmsh      HHMMSSHH (8 digits, with hundredths), or {@code null}
 * @param microseconds  optional 6-digit microseconds value paired with either
 *                      {@code timeTms} or {@code timeTmsh}. <b>Quirk (F7919270.src
 *                      D400-Konv-Fra-Dato-TMSH, comment "HLR 20040917 - hundredelene
 *                      fra mikro blir overskrevet med hundredelene fra tid-tmsh"):</b>
 *                      when paired with {@code timeTmsh}, only the LAST 4 digits of
 *                      this 6-digit value survive in the output - the first 2 digits
 *                      (the "hundredths" position) are silently overwritten by the
 *                      hundredths embedded in {@code timeTmsh}. When paired with
 *                      {@code timeTms} instead, all 6 digits pass through unchanged.
 * @param timestamp16   ÅÅÅÅMMDDHHMMSSHH (16 digits), or {@code null}
 * @param timestampDb2  "YYYY-MM-DD-HH.MM.SS.NNNNNN" (26 chars, full 6-digit
 *                      microseconds), or {@code null}/blank
 */
public record TimestampConversionRequest(
        Integer date,
        Integer timeTms,
        Integer timeTmsh,
        Integer microseconds,
        Long timestamp16,
        String timestampDb2) {
}
