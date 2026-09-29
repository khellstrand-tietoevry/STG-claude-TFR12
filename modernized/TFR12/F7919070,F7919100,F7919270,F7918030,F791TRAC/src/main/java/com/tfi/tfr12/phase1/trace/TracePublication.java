package com.tfi.tfr12.phase1.trace;

/**
 * The single MQ PUT1 call F791TRAC.src M100-MQ-Put1 issues, captured for
 * assertion instead of actually calling MQSeries (no MQ broker is available in
 * this environment; program F791MQS0 is external and out of scope for Phase 1).
 *
 * @param queueName          the target queue (R791MQS0-In-QueueName); defaults to
 *                           {@link DiagnosticTraceService#DEFAULT_TRACE_QUEUE}
 *                           when {@link TraceRequest#traceQueue()} is blank
 * @param payload            the bytes actually sent to MQ: the 112-byte header
 *                           followed by the trace data, <b>truncated to a
 *                           maximum of 32768 bytes total</b> - F791TRAC.src
 *                           M100-MQ-Put1 always passes exactly
 *                           {@code W-Trace-Area(1:32768)} regardless of the
 *                           logical message length, so any trace data that
 *                           would push header+data past 32768 bytes is silently
 *                           dropped from what MQ actually receives
 * @param logicalLength      the full, untruncated header+data length
 *                           (R791MQS0-In-Put-LL, computed from
 *                           R7919310-Ut-Lengde + header length) - note this can
 *                           legitimately exceed {@code payload.length} per the
 *                           32768-byte transmission cap above; that mismatch is
 *                           a faithfully-reproduced legacy quirk, not a defect
 *                           to "fix" in this contract
 * @param correlationId      24 chars: the 16-digit machine timestamp
 *                           (R7919070-Ut-Timestamp, as digit characters) followed
 *                           immediately by the 8-char job name, left-padded/
 *                           truncated to width (R791MQS0-In-Correlid, built by
 *                           first moving the timestamp in, then overwriting
 *                           bytes 17-24 with the job name)
 * @param messageVersion     always 1 (R791MQSH-Melding-Ver)
 * @param sentTimestamp16    the 16-digit machine timestamp used to build both
 *                           the header and the correlation id (R791MQSH-Sendt-Timestamp)
 * @param senderApplication  always "FDPO-TRACE" (R791MQSH-Avsender-Appl)
 * @param receiverApplication always "FDPO-TRACE" (R791MQSH-Mottaker-Appl)
 * @param workstation        the job name (R791MQSH-Arbeidsstasjon = R7919220-Ut-Jobb-Navn)
 * @param userId             the load module name (R791MQSH-Brukerid = R7919220-Ut-Load-Navn)
 * @param verificationToken  the token returned by F7919330 (R791MQSH-Verifikasjon)
 * @param noSyncpoint        always true - the write happens outside syncpoint (R791MQS0-In-No-Syncpoint)
 */
public record TracePublication(
        String queueName,
        byte[] payload,
        int logicalLength,
        String correlationId,
        int messageVersion,
        long sentTimestamp16,
        String senderApplication,
        String receiverApplication,
        String workstation,
        String userId,
        String verificationToken,
        boolean noSyncpoint) {

    public static final int MAX_MQ_PAYLOAD_BYTES = 32768;
}
