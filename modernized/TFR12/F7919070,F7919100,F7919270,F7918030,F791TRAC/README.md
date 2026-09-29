# TFR12 Phase 1 - Utilities & Diagnostics: characterization tests

This Maven module holds **characterization tests** for five legacy COBOL
utility/diagnostic programs from `legacy/STG/TFR12/src/`, as the first phase
("Utilities & Diagnostics") of the TFR12 modernization:

| Legacy program      | What it does                                                          | Test class                       |
|----------------------|------------------------------------------------------------------------|-----------------------------------|
| `F7919070.src`       | Fetches the machine timestamp (date/time + DB2 timestamp)             | `DateTimeServiceTest` (nested `MachineTimestampTests`) |
| `F7919100.src`       | Converts a date between AMD9/DMA9/AMD7/DMA7/Julian/DB2/A4D3/A2D3       | `DateTimeServiceTest` (nested `DateFormatConversionTests`, incl. `Rule036Tests`, `Rule037Tests`) |
| `F7919270.src`       | Converts between date/time/timestamp representations (with microseconds) | `DateTimeServiceTest` (nested `DateTimeConversionTests`) |
| `F7918030.src`       | Looks up localized error message text (table `PO_Feilmelding`)        | `ErrorMessageServiceTest`         |
| `F791TRAC.src`       | Writes a diagnostic trace record to an MQ queue; detects CICS/IMS/BATCH | `DiagnosticTraceServiceTest`      |

## Status: contracts only, no implementation yet

This module intentionally ships **no `src/main/java` implementation logic**.
It contains:

- The **contract** each Phase 2 implementation is expected to satisfy: plain
  interfaces and record DTOs under `src/main/java/com/tfi/tfr12/phase1/{datetime,errormessage,trace}/`.
- A `NotYetImplemented*` stub for each interface, whose every method throws
  `UnsupportedOperationException`. These exist purely so the test suite
  compiles and *runs* (rather than being skipped) - a characterization test
  that can't execute is a failure, never a skip.
- The characterization tests themselves, which currently all fail red with
  `UnsupportedOperationException` (or, for `assertThrows` tests, fail because
  the wrong exception type was thrown). That is the **expected, correct state**
  right now: **zero tests pass, because zero behavior has been implemented.**
  See "How this proves anything" below.

None of the five legacy programs could be compiled or executed in this
environment:

- `F7919070`, `F7919100`, `F7919270` hit a GnuCOBOL compiler restriction on
  `REDEFINES` fields in `PROCEDURE DIVISION USING`.
- `F7918030` requires a DB2 precompiler that isn't available here.
- `F791TRAC` calls three external programs (`F7919220`, `F791CICS`,
  `F7919330`, `F791MQS0`) that don't exist in this repository.

Every expected value in these tests was therefore computed by hand-tracing
the COBOL control flow and copybook field widths, **not observed from a
running legacy program**. Each test class says so at the top, and anyone
relying on these numbers for the Phase 2 rewrite should spot-check the more
surprising ones (see "Quirks worth double-checking" below) against a real
legacy run or a captured production trace before trusting them unconditionally.

## Business rules pinned

Two confirmed, P1, currently-unreviewed-by-a-human business rules from
`analysis/STG/TFR12/BUSINESS_RULES.md` are pinned explicitly, by name, in
`DateTimeServiceTest`:

- **RULE-036** (`Rule036Tests`, `F7919100.src:291-336`): a sub-minimum input
  date (in any of the AMD9/DMA9/AMD7/DMA7 input formats) is silently
  normalized to the sentinel "null date" `0001-01-01`, instead of being
  rejected.
- **RULE-037** (`Rule037Tests`, `F7919100.src:951-996`): a 2-digit-year input
  date (AMD7/DMA7) has its century picked by a sliding window - today's date
  plus a years-ahead offset (default 50, overridable 1-99) is the window's
  upper bound; whichever century keeps the date at or before that bound wins.

Both rules are marked `P1` (not `P0`) in `BUSINESS_RULES.md` and neither
appears in `analysis/STG/TFR12/RULE_REVIEWS.json` as of this writing - i.e.
neither has been through human review yet. Treat the exact sentinel value and
the exact window semantics as "as read from the code", not yet SME-confirmed.

## Quirks worth double-checking

A few of these tests pin genuinely surprising legacy behavior, called out
explicitly in test names/comments so nobody "fixes" them by accident in
Phase 2:

- `F7919270`: when both `timeTmsh` (hundredths-precision time) and a separate
  6-digit `microseconds` value are supplied together, only the **last 4
  digits** of the microseconds survive - the first 2 digits are silently
  overwritten by the hundredths embedded in `timeTmsh`.
- `F791TRAC`: an `IMS` environment is reported as `"BATCH"` in the trace
  header, not `"IMS"` - the header can't distinguish them even though the
  (external, unavailable) environment-detection call can.
- `F791TRAC`: under CICS, the trace header's job-name field holds the CICS
  system id, but the separate MQ envelope's workstation field always holds
  the actual job name - the two fields diverge under CICS.
- `F791TRAC`: the MQ payload is always truncated to 32768 bytes
  (`W-Trace-Area(1:32768)`), even though the "logical length" field sent
  alongside it is NOT truncated - so trailing trace data can be silently
  dropped from what MQ actually receives while the declared length says
  otherwise.
- `F7918030`: a database/table read failure always produces the **comma**
  variant of the generic fallback text, even under CICS - the CICS-specific
  **period** variant is used only for "message code not found", never for a
  read failure.

## How this proves anything

Per this repository's characterization-testing discipline: a green suite with
zero real assertions proves nothing, and a suite that's green because
everything was skipped proves nothing either. Right now this suite is
correctly **all red** (67 test methods, 67 executed, 0 skipped, 0 passing) -
that is the expected state with no implementation yet.

Once Phase 2 adds real implementations of the five interfaces
(`MachineTimestampService`, `DateFormatConversionService`,
`DateTimeConversionService`, `ErrorMessageService`, `DiagnosticTraceService`)
and wires them in place of the `NotYetImplemented*` stubs:

1. Run `mvn test` and confirm the reported "Tests run" count matches the
   number of `@Test` methods in this module (currently 67 - `grep -c "@Test"`
   across the three test classes) and that failures drop to 0.
2. As a sanity check that the tests can actually fail (not just pass by
   accident), deliberately break one small thing in the new implementation
   (e.g. flip the RULE-037 comparison from `<=` to `<`, or change the
   RULE-036 sentinel from `00010101` to `00000101`) and confirm at least one
   test goes red again, then revert the change.

## Running the tests

```bash
cd modernized/STG/TFR12/F7919070,F7919100,F7919270,F7918030,F791TRAC
mvn test
```

Requires Java 21 and network access the first time (to resolve the JUnit 5
dependency from Maven Central).

## Adding a new case

1. Pick the test class matching the legacy program you're characterizing
   (see the table above), and the `@Nested` group matching the specific
   COBOL paragraph/branch you're pinning (create a new `@Nested` group if the
   branch doesn't fit an existing one).
2. Hand-trace the COBOL source for the exact input -> output pair you want to
   pin. Cite the file and line range in a comment or the `@DisplayName`.
3. If the case pins a numbered business rule from `BUSINESS_RULES.md`, name
   the rule id in the test method name (e.g. `rule036_...`) or in a one-line
   comment immediately above the test - `modernize-verify` finds tests by that
   id, and a test that names no rule counts for no rule.
4. If the case needs a new field on an existing contract (interface/record),
   extend the record/interface in `src/main/java` and update its Javadoc to
   cite the COBOL source backing the new field - do not add real conversion
   logic to `src/main/java` until Phase 2.
5. Run `mvn test` and confirm your new test executes and fails with
   `UnsupportedOperationException` (proving it's wired up and not silently
   skipped) until a real implementation exists.
