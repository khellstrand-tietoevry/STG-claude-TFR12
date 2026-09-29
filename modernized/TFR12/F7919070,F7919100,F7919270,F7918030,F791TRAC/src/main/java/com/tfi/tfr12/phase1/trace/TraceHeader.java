package com.tfi.tfr12.phase1.trace;

/**
 * The fixed 112-byte trace header prepended to every trace record, mirroring
 * R791DBGH-Trace-Info-Hdr (copybook R791DBGH.copy):
 * Eyecatcher(8) + Versjon(2) + Miljo(8) + Program(8) + Sysid(2) +
 * Trace-Point(32) + Trace-Area(32) + Jobbnavn(8) + Transid(4) + Taskno(8) = 112 bytes.
 *
 * <p>Every text field below is expected to be exactly its declared PIC-clause
 * width: right-padded with spaces if the source value was shorter, and
 * truncated if longer (standard COBOL alphanumeric MOVE semantics) - a
 * conforming implementation must reproduce this padding/truncation exactly.
 *
 * @param eyecatcher  always "R791DBGH" (8 chars) - set via the 88-level
 *                    R791DBGH-Eyecatcher-Val
 * @param version     always 01 (2-digit, R791DBGH-Versjon)
 * @param miljo       "CICS" or "BATCH" (8 chars, space-padded) - see
 *                    {@link RuntimeEnvironmentInfo} for the IMS-becomes-BATCH quirk
 * @param program     8 chars, space-padded/truncated from {@link TraceRequest#program()}
 * @param sysid       2 chars, space-padded/truncated from {@link TraceRequest#sysid()}
 * @param tracePoint  32 chars, space-padded/truncated from {@link TraceRequest#tracePoint()}
 * @param traceArea   32 chars, space-padded/truncated from {@link TraceRequest#traceArea()}
 * @param jobName     8 chars, space-padded/truncated; CICS-id for CICS, job name otherwise
 * @param transId     4 chars, space-padded; blank ("    ") for IMS/BATCH (never set for those)
 * @param taskNumber  8-digit zero-filled number; 0 for IMS/BATCH (never set for those)
 */
public record TraceHeader(
        String eyecatcher,
        int version,
        String miljo,
        String program,
        String sysid,
        String tracePoint,
        String traceArea,
        String jobName,
        String transId,
        int taskNumber) {

    public static final int HEADER_LENGTH_BYTES = 112;
}
