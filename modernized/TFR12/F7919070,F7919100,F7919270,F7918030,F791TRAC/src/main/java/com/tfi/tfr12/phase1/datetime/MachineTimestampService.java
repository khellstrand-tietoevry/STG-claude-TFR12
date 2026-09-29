package com.tfi.tfr12.phase1.datetime;

import java.time.Clock;

/**
 * Contract for the modern replacement of legacy program F7919070.src
 * ("Generell submodul. Benyttes for å hente timestamp - maskindato/klokke.").
 *
 * <p>The legacy program has no input; it reads the mainframe wall clock via two
 * external calls (F7919071 - assembler, F7919072 - COBOL, used to support
 * date-override in test environments) and reconciles them to protect against a
 * midnight rollover race between the two reads. Because neither of those calls
 * can be executed in this environment, and because a modern implementation
 * should be deterministically testable, the contract takes an explicit
 * {@link Clock} instead of reading a real system clock directly. Implementers
 * are expected to source the real wall clock from {@link Clock#systemDefaultZone()}
 * (or equivalent) in production and inject a fixed clock in tests.
 */
public interface MachineTimestampService {

    /**
     * Fetches the current timestamp, broken down into every representation the
     * legacy R7919070-Timestamp output group provides.
     *
     * @param clock the clock to read "now" from (stands in for the legacy
     *              F7919071/F7919072 calls)
     * @return the fully populated timestamp in every legacy output format
     */
    MachineTimestamp fetchTimestamp(Clock clock);
}
