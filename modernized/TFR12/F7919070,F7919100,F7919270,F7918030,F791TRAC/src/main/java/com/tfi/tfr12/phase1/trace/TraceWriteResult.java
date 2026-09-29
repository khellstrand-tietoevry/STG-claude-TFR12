package com.tfi.tfr12.phase1.trace;

/**
 * Everything {@link DiagnosticTraceService#writeTrace} produces, returned so
 * tests can assert on it without a real MQ broker.
 *
 * @param header      the assembled 112-byte trace header
 * @param publication the single MQ PUT1 call that would have been issued
 */
public record TraceWriteResult(TraceHeader header, TracePublication publication) {
}
