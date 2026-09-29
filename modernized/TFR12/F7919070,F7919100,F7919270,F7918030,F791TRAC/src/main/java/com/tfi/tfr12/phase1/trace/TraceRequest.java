package com.tfi.tfr12.phase1.trace;

/**
 * Input to {@link DiagnosticTraceService#writeTrace}, mirroring
 * R791TRAC-Kvalifisering.
 *
 * @param program     R791TRAC-In-Program (max 8 chars); required, blank/spaces rejected (B101)
 * @param sysid       R791TRAC-In-Sysid (max 2 chars); required, blank/spaces rejected (B102)
 * @param tracePoint  R791TRAC-In-Trace-Point (max 32 chars); required, blank/spaces rejected (B103)
 * @param traceArea   R791TRAC-In-Trace-Area (max 32 chars); required, blank/spaces rejected (B104)
 * @param traceQueue  R791TRAC-In-Trace-Queue (max 48 chars); optional - blank/spaces
 *                    means "use the default queue" ({@link DiagnosticTraceService#DEFAULT_TRACE_QUEUE})
 * @param traceData   R791TRAC-In-Trace-Data (max 32768 chars); the free-text
 *                    payload appended after the fixed trace header. Unlike the
 *                    four fields above, this one is NOT validated as required.
 */
public record TraceRequest(
        String program,
        String sysid,
        String tracePoint,
        String traceArea,
        String traceQueue,
        String traceData) {
}
