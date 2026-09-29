package com.tfi.tfr12.phase1.datetime;

/**
 * The eight date representations legacy program F7919100.src converts between.
 * Names mirror the legacy flags (R7919100-In-Konv-Fra-xxx / -In-Til-Formater)
 * verbatim so the mapping back to the COBOL source stays obvious.
 */
public enum DateFormat {
    /** ÅÅÅÅMMDD, e.g. 20260315 for 2026-03-15. */
    AMD9,
    /** DDMMÅÅÅÅ, e.g. 15032026 for 2026-03-15. */
    DMA9,
    /** ÅÅMMDD, 2-digit year, e.g. 260315 for 2026-03-15. */
    AMD7,
    /** DDMMÅÅ, 2-digit year, e.g. 150326 for 2026-03-15. */
    DMA7,
    /** Legacy pseudo-Julian: (year-1900)*360 + (month-1)*30 + day. NOT a true ordinal day. */
    JULIAN,
    /** DB2 ISO date string, e.g. "2026-03-15" (produced on output; ISO/EUR/USA accepted on input). */
    DB2,
    /** ÅÅÅÅDDD, 4-digit year + 3-digit true ordinal day-of-year, e.g. 2026074. */
    A4D3,
    /** ÅÅDDD, 2-digit year + 3-digit true ordinal day-of-year, e.g. 26074. */
    A2D3
}
