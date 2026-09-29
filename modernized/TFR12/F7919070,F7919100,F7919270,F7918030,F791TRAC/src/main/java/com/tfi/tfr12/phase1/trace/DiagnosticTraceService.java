package com.tfi.tfr12.phase1.trace;

import com.tfi.tfr12.phase1.datetime.MachineTimestamp;

/**
 * Contract for the modern replacement of legacy program F791TRAC.src
 * ("GSM Modul for å skrive trace informasjon" - module for writing trace
 * information to an MQ queue).
 *
 * <p>The legacy module depends on three external programs not present in this
 * repository:
 * <ul>
 *   <li>F7919220 - job/environment detection (CICS/IMS/BATCH)</li>
 *   <li>F791CICS - CICS EIB field accessor (transaction id, task number)</li>
 *   <li>F7919330 - security/verification token generation</li>
 *   <li>F791MQS0 - the actual MQSeries PUT1 call</li>
 * </ul>
 * This contract takes the outputs of the first three as explicit parameters
 * (see {@link RuntimeEnvironmentInfo}, {@link MachineTimestamp}, and the
 * {@code verificationToken} parameter) and returns what would have been sent
 * to MQ instead of calling it, so the module is fully characterizable without
 * any of those four dependencies.
 */
public interface DiagnosticTraceService {

    /** R791TRAC's hardcoded default queue when {@link TraceRequest#traceQueue()} is blank. */
    String DEFAULT_TRACE_QUEUE = "FDPO.TRACELOG";

    /**
     * @param request           the trace payload and identifying fields
     * @param environment       the pre-resolved runtime environment (stands in
     *                          for the F7919220/F791CICS calls)
     * @param timestamp         the pre-fetched machine timestamp (stands in for
     *                          the module's internal F7919070 call)
     * @param verificationToken the pre-generated token (stands in for the
     *                          F7919330 call)
     * @return the assembled header and the MQ publication that would result
     * @throws TraceValidationException if program, sysid, tracePoint, or
     *                                   traceArea is blank/spaces
     */
    TraceWriteResult writeTrace(
            TraceRequest request,
            RuntimeEnvironmentInfo environment,
            MachineTimestamp timestamp,
            String verificationToken);
}
