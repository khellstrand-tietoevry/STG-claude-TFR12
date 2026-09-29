package com.tfi.tfr12.phase1.trace;

import com.tfi.tfr12.phase1.datetime.MachineTimestamp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CHARACTERIZATION TESTS - NOT VALIDATED AGAINST A LIVE LEGACY RUN.
 *
 * <p>Pins the behavior of legacy/STG/TFR12/src/F791TRAC.src ("GSM Modul for å
 * skrive trace informasjon" - writes a diagnostic trace record to an MQ queue;
 * detects runtime environment CICS/IMS/BATCH). ISO-8859-1/Latin-1 source,
 * columns 8-72 code area, column 7 '*' marks a comment.
 *
 * <p>This module calls three external programs not present in this repository
 * (F7919220 job/environment info, F791CICS EIB accessor, F7919330 verification
 * token, F791MQS0 the actual MQSeries PUT1) so it could not be compiled or run.
 * Every expected value below was hand-traced from F791TRAC.src's control flow
 * (B000-Initialize, B100-Kontroll, D000-Prepare-Data, E000-Trace-Logg,
 * M100-MQ-Put1) and the field widths in copybooks R791DBGH.copy and
 * R791TRAC.copy, not observed from a running program.
 *
 * <p>Implemented by {@link DiagnosticTraceServiceImpl}; since the legacy
 * module could not run locally, these tests remain the only proof of
 * equivalence for it.
 */
class DiagnosticTraceServiceTest {

    private final DiagnosticTraceService service = new DiagnosticTraceServiceImpl();

    private static final RuntimeEnvironmentInfo BATCH_ENV = new RuntimeEnvironmentInfo(
            EnvironmentKind.BATCH, "JOBNAME1", "", "LOADMOD1", "", 0);

    // 2026-09-28T14:07:23.45 -> timestamp16 2026092814072345.
    private static final MachineTimestamp TIMESTAMP = new MachineTimestamp(
            2026, 9, 28, 14, 7, 23, 45,
            "2026-09-28-14.07.23.450000",
            "20260928140723450000",
            2026092814072345L,
            20260928,
            14072345);

    private static TraceRequest validRequest() {
        return new TraceRequest("TESTPGM", "NO", "POINT-A", "AREA-1", null, "hello");
    }

    // =====================================================================
    // Required-field validation (F791TRAC.src B100-Kontroll)
    // =====================================================================
    @Nested
    @DisplayName("required-field validation")
    class ValidationTests {

        @Test
        @DisplayName("blank program is rejected (F791TRAC.src:B101)")
        void writeTrace_blankProgramRejected() {
            TraceRequest request = new TraceRequest("   ", "NO", "POINT-A", "AREA-1", null, "data");

            TraceValidationException ex = assertThrows(TraceValidationException.class,
                    () -> service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123"));
            assertEquals("B101", ex.legacyParagraph());
        }

        @Test
        @DisplayName("blank sysid is rejected (F791TRAC.src:B102)")
        void writeTrace_blankSysidRejected() {
            TraceRequest request = new TraceRequest("TESTPGM", "  ", "POINT-A", "AREA-1", null, "data");

            TraceValidationException ex = assertThrows(TraceValidationException.class,
                    () -> service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123"));
            assertEquals("B102", ex.legacyParagraph());
        }

        @Test
        @DisplayName("blank trace point is rejected (F791TRAC.src:B103)")
        void writeTrace_blankTracePointRejected() {
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "   ", "AREA-1", null, "data");

            TraceValidationException ex = assertThrows(TraceValidationException.class,
                    () -> service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123"));
            assertEquals("B103", ex.legacyParagraph());
        }

        @Test
        @DisplayName("blank trace area is rejected (F791TRAC.src:B104)")
        void writeTrace_blankTraceAreaRejected() {
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "POINT-A", "   ", null, "data");

            TraceValidationException ex = assertThrows(TraceValidationException.class,
                    () -> service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123"));
            assertEquals("B104", ex.legacyParagraph());
        }

        @Test
        @DisplayName("blank trace data is accepted - unlike the four fields above, it is not a required field")
        void writeTrace_blankTraceDataAccepted() {
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "POINT-A", "AREA-1", null, "");

            TraceWriteResult result = service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123");

            assertEquals(TraceHeader.HEADER_LENGTH_BYTES, result.publication().logicalLength());
        }
    }

    // =====================================================================
    // Environment detection and header assembly
    // =====================================================================
    @Nested
    @DisplayName("environment detection and header assembly")
    class EnvironmentTests {

        @Test
        @DisplayName("BATCH environment: header Miljo=BATCH, Jobbnavn=job name, Transid/Taskno left at their zero-value default")
        void writeTrace_batchEnvironmentHeader() {
            TraceWriteResult result = service.writeTrace(validRequest(), BATCH_ENV, TIMESTAMP, "TOKEN123");

            TraceHeader header = result.header();
            assertEquals("R791DBGH", header.eyecatcher());
            assertEquals(1, header.version());
            assertEquals("BATCH   ", header.miljo());
            assertEquals("TESTPGM ", header.program());
            assertEquals("NO", header.sysid());
            assertEquals("POINT-A" + " ".repeat(25), header.tracePoint());
            assertEquals("AREA-1" + " ".repeat(26), header.traceArea());
            assertEquals("JOBNAME1", header.jobName());
            assertEquals("    ", header.transId());
            assertEquals(0, header.taskNumber());
        }

        @Test
        @DisplayName("QUIRK #1: IMS environment is ALSO reported as Miljo=BATCH in the header, not IMS "
                + "(F791TRAC.src B000-Initialize has no distinct 'When R7919220-Ut-Ims' output value)")
        void writeTrace_imsEnvironmentIsLabeledBatchInHeader() {
            RuntimeEnvironmentInfo imsEnv = new RuntimeEnvironmentInfo(
                    EnvironmentKind.IMS, "JOBNAME2", "", "LOADMOD2", "", 0);

            TraceWriteResult result = service.writeTrace(validRequest(), imsEnv, TIMESTAMP, "TOKEN123");

            assertEquals("BATCH   ", result.header().miljo());
            assertEquals("JOBNAME2", result.header().jobName());
        }

        @Test
        @DisplayName("CICS environment: header Miljo=CICS, Jobbnavn=CICS system id, Transid/Taskno from the EIB")
        void writeTrace_cicsEnvironmentHeader() {
            RuntimeEnvironmentInfo cicsEnv = new RuntimeEnvironmentInfo(
                    EnvironmentKind.CICS, "JOBNAME1", "CICX", "LOADMOD1", "TRN1", 42);

            TraceWriteResult result = service.writeTrace(validRequest(), cicsEnv, TIMESTAMP, "TOKEN123");

            TraceHeader header = result.header();
            assertEquals("CICS    ", header.miljo());
            assertEquals("CICX    ", header.jobName()); // 4-char CICS id, space-padded to 8
            assertEquals("TRN1", header.transId());
            assertEquals(42, header.taskNumber());
        }

        @Test
        @DisplayName("QUIRK #2: under CICS, the MQ envelope's workstation field uses the job name, "
                + "NOT the CICS id that the trace header itself uses for Jobbnavn "
                + "(F791TRAC.src E000-Trace-Logg always moves R7919220-Ut-Jobb-Navn, "
                + "never R7919220-Ut-Cics-Id, into R791MQSH-Arbeidsstasjon)")
        void writeTrace_cicsMqWorkstationUsesJobNameNotCicsId() {
            RuntimeEnvironmentInfo cicsEnv = new RuntimeEnvironmentInfo(
                    EnvironmentKind.CICS, "JOBNAME1", "CICX", "LOADMOD1", "TRN1", 42);

            TraceWriteResult result = service.writeTrace(validRequest(), cicsEnv, TIMESTAMP, "TOKEN123");

            assertEquals("CICX    ", result.header().jobName());
            assertEquals("JOBNAME1", result.publication().workstation());
        }

        @Test
        @DisplayName("program/trace-point/trace-area longer than their field width are truncated, not rejected")
        void writeTrace_overlongFieldsAreTruncated() {
            TraceRequest request = new TraceRequest(
                    "TOOLONGPROGRAMNAME", "NOR", "X".repeat(40), "Y".repeat(40), null, "data");

            TraceWriteResult result = service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123");

            TraceHeader header = result.header();
            assertEquals("TOOLONGP", header.program());   // truncated to 8
            assertEquals("NO", header.sysid());            // truncated to 2
            assertEquals("X".repeat(32), header.tracePoint()); // truncated to 32
            assertEquals("Y".repeat(32), header.traceArea());  // truncated to 32
        }
    }

    // =====================================================================
    // MQ publication assembly
    // =====================================================================
    @Nested
    @DisplayName("MQ publication assembly (F791TRAC.src E000-Trace-Logg / M100-MQ-Put1)")
    class PublicationTests {

        @Test
        @DisplayName("default queue name is FDPO.TRACELOG when no trace queue is supplied")
        void writeTrace_defaultsToStandardQueueName() {
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "POINT-A", "AREA-1", null, "data");

            TraceWriteResult result = service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123");

            assertEquals("FDPO.TRACELOG", result.publication().queueName());
        }

        @Test
        @DisplayName("an explicitly supplied trace queue overrides the default")
        void writeTrace_explicitQueueNameIsUsed() {
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "POINT-A", "AREA-1", "CUSTOM.QUEUE", "data");

            TraceWriteResult result = service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123");

            assertEquals("CUSTOM.QUEUE", result.publication().queueName());
        }

        @Test
        @DisplayName("correlation id is the 16-digit timestamp followed by the 8-char job name")
        void writeTrace_correlationIdIsTimestampPlusJobName() {
            TraceWriteResult result = service.writeTrace(validRequest(), BATCH_ENV, TIMESTAMP, "TOKEN123");

            assertEquals("2026092814072345JOBNAME1", result.publication().correlationId());
        }

        @Test
        @DisplayName("fixed MQ header fields: version 1, FDPO-TRACE sender/receiver, no-syncpoint, propagated token")
        void writeTrace_fixedMqHeaderFields() {
            TraceWriteResult result = service.writeTrace(validRequest(), BATCH_ENV, TIMESTAMP, "TOKEN123");

            TracePublication pub = result.publication();
            assertEquals(1, pub.messageVersion());
            assertEquals("FDPO-TRACE", pub.senderApplication());
            assertEquals("FDPO-TRACE", pub.receiverApplication());
            assertEquals("LOADMOD1", pub.userId());
            assertEquals("TOKEN123", pub.verificationToken());
            assertEquals(2026092814072345L, pub.sentTimestamp16());
            assertTrue(pub.noSyncpoint());
        }

        @Test
        @DisplayName("payload is the 112-byte header immediately followed by the trace data")
        void writeTrace_payloadIsHeaderThenData() {
            TraceWriteResult result = service.writeTrace(validRequest(), BATCH_ENV, TIMESTAMP, "TOKEN123");

            byte[] payload = result.publication().payload();
            assertEquals(TraceHeader.HEADER_LENGTH_BYTES + "hello".length(), payload.length);
            byte[] tail = new byte[5];
            System.arraycopy(payload, TraceHeader.HEADER_LENGTH_BYTES, tail, 0, 5);
            assertEquals("hello", new String(tail, StandardCharsets.ISO_8859_1));
        }

        @Test
        @DisplayName("QUIRK: trace data that would push header+data past 32768 bytes is silently truncated "
                + "in what is actually sent to MQ (F791TRAC.src M100-MQ-Put1 always passes "
                + "exactly W-Trace-Area(1:32768)), even though the reported logical length is untruncated")
        void writeTrace_payloadTruncatedAt32768BytesButLogicalLengthIsNot() {
            String bigData = "X".repeat(32700); // header(112) + 32700 = 32812 > 32768
            TraceRequest request = new TraceRequest("TESTPGM", "NO", "POINT-A", "AREA-1", null, bigData);

            TraceWriteResult result = service.writeTrace(request, BATCH_ENV, TIMESTAMP, "TOKEN123");

            TracePublication pub = result.publication();
            assertEquals(TracePublication.MAX_MQ_PAYLOAD_BYTES, pub.payload().length);
            assertEquals(TraceHeader.HEADER_LENGTH_BYTES + 32700, pub.logicalLength());
        }
    }
}
