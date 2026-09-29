package com.tfi.tfr12.phase1.trace;

import com.tfi.tfr12.phase1.datetime.MachineTimestamp;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Replacement for legacy program F791TRAC.src. See {@link DiagnosticTraceService}
 * and the field-level javadoc on {@link RuntimeEnvironmentInfo}/{@link TracePublication}
 * for the two documented quirks this implementation deliberately reproduces.
 */
public final class DiagnosticTraceServiceImpl implements DiagnosticTraceService {

    @Override
    public TraceWriteResult writeTrace(TraceRequest request, RuntimeEnvironmentInfo environment,
                                        MachineTimestamp timestamp, String verificationToken) {
        if (isBlank(request.program())) {
            throw new TraceValidationException("B101", "Program is required");
        }
        if (isBlank(request.sysid())) {
            throw new TraceValidationException("B102", "Sysid is required");
        }
        if (isBlank(request.tracePoint())) {
            throw new TraceValidationException("B103", "Trace point is required");
        }
        if (isBlank(request.traceArea())) {
            throw new TraceValidationException("B104", "Trace area is required");
        }

        boolean cics = environment.kind() == EnvironmentKind.CICS;
        String miljo = fit(cics ? "CICS" : "BATCH", 8);
        String jobNameField = fit(cics ? environment.cicsId() : environment.jobName(), 8);
        String transId = cics ? fit(environment.cicsTransactionId(), 4) : "    ";
        int taskNumber = cics ? environment.cicsTaskNumber() : 0;

        TraceHeader header = new TraceHeader(
                "R791DBGH",
                1,
                miljo,
                fit(request.program(), 8),
                fit(request.sysid(), 2),
                fit(request.tracePoint(), 32),
                fit(request.traceArea(), 32),
                jobNameField,
                transId,
                taskNumber);

        byte[] headerBytes = headerBytes(header);
        String data = request.traceData() == null ? "" : request.traceData();
        byte[] dataBytes = data.getBytes(StandardCharsets.ISO_8859_1);

        int logicalLength = headerBytes.length + dataBytes.length;
        byte[] full = new byte[logicalLength];
        System.arraycopy(headerBytes, 0, full, 0, headerBytes.length);
        System.arraycopy(dataBytes, 0, full, headerBytes.length, dataBytes.length);
        // F791TRAC.src M100-MQ-Put1 always passes exactly W-Trace-Area(1:32768),
        // regardless of the logical length - silently truncating any overflow.
        byte[] payload = full.length > TracePublication.MAX_MQ_PAYLOAD_BYTES
                ? Arrays.copyOf(full, TracePublication.MAX_MQ_PAYLOAD_BYTES)
                : full;

        String queueName = isBlank(request.traceQueue()) ? DEFAULT_TRACE_QUEUE : request.traceQueue();
        String correlationId = String.format("%016d", timestamp.timestamp16()) + fit(environment.jobName(), 8);

        TracePublication publication = new TracePublication(
                queueName,
                payload,
                logicalLength,
                correlationId,
                1,
                timestamp.timestamp16(),
                "FDPO-TRACE",
                "FDPO-TRACE",
                fit(environment.jobName(), 8),
                fit(environment.loadName(), 8),
                verificationToken,
                true);

        return new TraceWriteResult(header, publication);
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String fit(String s, int width) {
        String v = (s == null) ? "" : s;
        if (v.length() >= width) {
            return v.substring(0, width);
        }
        return v + " ".repeat(width - v.length());
    }

    /** Serializes the 112-byte header per R791DBGH.copy's field layout. */
    private static byte[] headerBytes(TraceHeader h) {
        String versjon = String.format("%02d", h.version());
        String taskno = String.format("%08d", h.taskNumber());
        String all = h.eyecatcher() + versjon + h.miljo() + h.program() + h.sysid()
                + h.tracePoint() + h.traceArea() + h.jobName() + h.transId() + taskno;
        return all.getBytes(StandardCharsets.ISO_8859_1);
    }
}
