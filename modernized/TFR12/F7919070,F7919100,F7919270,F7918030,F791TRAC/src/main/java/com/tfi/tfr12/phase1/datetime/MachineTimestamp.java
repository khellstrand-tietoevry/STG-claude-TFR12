package com.tfi.tfr12.phase1.datetime;

/**
 * Modern replacement for the R7919070-Resultat output group produced by legacy
 * program F7919070.src.
 *
 * <p>Field-for-field mapping back to the COBOL copybook R7919070.copy:
 * <ul>
 *   <li>{@code year}/{@code month}/{@code day}/{@code hour}/{@code minute}/{@code second} -
 *       the individual components embedded in R7919070-Ut-Timestamp (PIC 9(16),
 *       laid out YYYYMMDDHHMMSSHH).</li>
 *   <li>{@code hundredths} - the two-digit "hundredths of a second" component. The
 *       legacy code derives this by truncating (NOT rounding) a 6-digit microsecond
 *       value to its first two digits (see F7919070.src C300-Formater-TMS,
 *       {@code Move W-TMS-20(1:16) To R7919070-UT-TMS-16}). A value of 456789
 *       microseconds yields hundredths = 45, not 46 or 46 (no rounding).</li>
 *   <li>{@code db2Timestamp} - R7919070-UT-TMS-DB2, format
 *       {@code YYYY-MM-DD-HH.MM.SS.NNNNNN} (full 6-digit microseconds, X(26)).</li>
 *   <li>{@code timestamp20} - R7919070-UT-TMS-20, a 20-character numeric string
 *       {@code YYYYMMDDHHMMSSNNNNNN} (full 6-digit microseconds).</li>
 *   <li>{@code timestamp16} - R7919070-UT-TMS-16 / R7919070-UT-TIMESTAMP, the
 *       16-digit {@code YYYYMMDDHHMMSSHH} value (truncated hundredths, not full
 *       microseconds).</li>
 *   <li>{@code date8} - R7919070-UT-DATO, {@code YYYYMMDD}.</li>
 *   <li>{@code time8} - R7919070-UT-TID, {@code HHMMSSHH} (truncated hundredths).</li>
 * </ul>
 */
public record MachineTimestamp(
        int year,
        int month,
        int day,
        int hour,
        int minute,
        int second,
        int hundredths,
        String db2Timestamp,
        String timestamp20,
        long timestamp16,
        int date8,
        int time8) {
}
