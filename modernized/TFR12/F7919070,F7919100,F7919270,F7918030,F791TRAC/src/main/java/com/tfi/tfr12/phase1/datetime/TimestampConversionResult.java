package com.tfi.tfr12.phase1.datetime;

/**
 * Output of {@link DateTimeConversionService#convert}, mirroring
 * R7919270-Resultat: every field is always populated on a successful
 * conversion (F7919270.src E000-Konv-Til-Output runs unconditionally, unlike
 * F7919100's per-format opt-in).
 *
 * @param date          ÅÅÅÅMMDD
 * @param timeTms       HHMMSS
 * @param timeTmsh      HHMMSSHH
 * @param microseconds  6-digit microseconds (see {@link TimestampConversionRequest#microseconds()}
 *                      for the TMSH-overwrite quirk this may reflect)
 * @param timestamp16   ÅÅÅÅMMDDHHMMSSHH
 * @param timestampDb2  "YYYY-MM-DD-HH.MM.SS.NNNNNN"
 */
public record TimestampConversionResult(
        int date,
        int timeTms,
        int timeTmsh,
        int microseconds,
        long timestamp16,
        String timestampDb2) {
}
