# Transformation Notes — Phase 1: Utilities & Diagnostics

**Modules:** `F7919070`, `F7919100`, `F7919270`, `F7918030`, `F791TRAC` (legacy) →
`modernized/TFR12/F7919070,F7919100,F7919270,F7918030,F791TRAC/` (Java 21, Maven module `tfr12-phase1-utilities`)

## Equivalence basis: trace/golden-master, not dual-execution

None of the 5 legacy programs could be compiled or run in this environment:

| Program | Blocker |
|---|---|
| `F7919070`, `F7919100`, `F7919270` | GnuCOBOL 3.2.0 restriction: `REDEFINES` field not allowed in `PROCEDURE DIVISION USING` |
| `F7918030` | Embedded `EXEC SQL` requires a DB2 precompiler, not available here |
| `F791TRAC` | Compiles clean, but calls 3 external programs (`F7919220`, `F791CICS`, `F791MQS0`) not present in this repo slice — cannot actually execute |

**Equivalence for this whole phase rests entirely on the 67 characterization tests**, each derived by hand-tracing the COBOL control flow and copybook field widths (not observed from a running program). There is no dual-execution diff to fall back on. Anyone relying on these tests for downstream work should spot-check the "quirks" below against a real legacy run or a captured production trace before trusting them unconditionally.

## Mapping table

| Legacy | Target |
|---|---|
| `F7919070.src` (machine timestamp) | `MachineTimestampService` / `MachineTimestampServiceImpl` |
| `F7919100.src` (date format conversion; RULE-036, RULE-037) | `DateFormatConversionService` / `DateFormatConversionServiceImpl` |
| `F7919270.src` (date/time/timestamp conversion) | `DateTimeConversionService` / `DateTimeConversionServiceImpl` |
| `F7918030.src` (error message lookup) | `ErrorMessageService` / `ErrorMessageServiceImpl` |
| `F791TRAC.src` (MQ trace write) | `DiagnosticTraceService` / `DiagnosticTraceServiceImpl` |

External dependencies not migrated (out of this repo's scope, taken as explicit parameters instead — see each contract's javadoc): `F7919071`/`F7919072` (wall clock reads, replaced by injected `Clock`), `F7919220` (environment detection, replaced by `RuntimeEnvironmentInfo` parameter), `F791CICS` (EIB accessor, folded into `RuntimeEnvironmentInfo`), `F7919330` (verification token, taken as a parameter), `F791MQS0` (the actual MQ PUT1 call — this phase returns the `TracePublication` that *would* be sent, per `DiagnosticTraceService`'s javadoc, rather than calling MQ).

## Business rules implemented

- **RULE-036** (sub-minimum input date → sentinel `0001-01-01`): `DateFormatConversionServiceImpl.parseSource` / `sentinel()`. 5 tests (`Rule036Tests`).
- **RULE-037** (sliding-window century determination): `DateFormatConversionServiceImpl.resolveCentury`. 7 tests (`Rule037Tests`).

Side-by-side, legacy `F7919100.src:951-996` (`F000-Glidende-Vindu`) vs. `DateFormatConversionServiceImpl.resolveCentury` (lines 155-166):

```
094000 F000-Glidende-Vindu               Section.                        |  private int resolveCentury(int rawYear2, int month, int day, Clock clock, Integer yearsAheadOverride) {
094200* Vi skal benytte "glidende vindu" for aa avgjoere hvilket aarhundre|      int yearsAhead = (yearsAheadOverride == null || yearsAheadOverride == 0) ? 50 : ...
094300* datoen befinner seg i.                                           |      LocalDate today = LocalDate.now(clock);
...  (F000 first calls F7919070 for "today" if not already cached)       |      LocalDate windowUpperBound = today.plusYears(yearsAhead);
096200* Dersom intet er angitt benyttes +/- 50 aar fra dagens dato.      |      int currentCenturyBase = (today.getYear() / 100) * 100;
096400 Evaluate R7919100-In-Aar-Frem-I-Tid In R7919100-Konverter-Dato    |      int candidateYear = currentCenturyBase + rawYear2;
096600   When 01 Thru 99                                                 |      LocalDate candidate = LocalDate.of(candidateYear, month, day);
096700     Add R7919100-In-Aar-Frem-I-Tid ... To W-SVD-Aar               |
097000   When other                                                      |
097100     Add 50 To W-SVD-Aar                                           |
097700 Move W-SVD-Aar-Hundre To W-S-AMD9-Aar-Hundre                      |
098200 If W-Splitt-AMD9 > W-Splitt-Vindus-Dato                           |      if (candidate.isAfter(windowUpperBound)) {
098300     Subtract 1 From W-S-AMD9-Aar-Hundre                           |          candidateYear -= 100;
098400 End-If                                                            |      }
                                                                          |      return candidateYear;
```

The Java version collapses the COBOL's split "build the window upper bound, then separately move the century digits, then compare" into direct `LocalDate` arithmetic and comparison, but the decision itself — window = today + N years (default 50, overridable 1-99), century chosen so the resulting date is at or before that window — is unchanged and covered by the canary below.

## Deviations and inferred behavior (not directly characterized by a test)

- **DB2 date source, blank input**: a blank/empty `DateFormatConversionRequest` DB2 source value is treated as the RULE-036 sentinel, by analogy with the rule's "no date supplied → null date" theme. Not exercised by any test — flag for SME confirmation if a caller ever legitimately passes a blank DB2 date.
- **JULIAN, A4D3, A2D3 as *source* formats**: implemented as the natural inverse of their documented output formulas, but no test exercises them as inputs (only as outputs, from an AMD9 source). Treat as unverified until a follow-up characterization pass adds input-side tests.
- **MQ trace publication (`F791MQS0`) is not actually called.** Per the brief's Phase 1 risk mitigation, this was the intended pilot scope — `DiagnosticTraceService` returns the `TracePublication` that would be sent, which a later phase (wiring the real Spring/MQ integration) will need to actually publish.

## What was NOT migrated

- Nothing was dropped as dead code in this phase — all 5 modules' observable behavior (as characterized) is implemented.
- The COBOL "cache the fetched timestamp across calls within one run" optimization (`F7919100.src:948 If Not R7919070-Versjon`) was not reproduced — `MachineTimestampServiceImpl` is stateless and takes an explicit `Clock` per call. This is a deliberate simplification (the caching was a mainframe CPU-cost optimization, not an observable behavior) and has no test coverage requirement since it doesn't change output.

## Proof of equivalence

- **67/67 characterization tests pass** (`mvn test`, clean build): `DateTimeServiceTest` (35), `ErrorMessageServiceTest` (16), `DiagnosticTraceServiceTest` (16).
- **Canary:** flipped `resolveCentury`'s window comparison from `candidate.isAfter(windowUpperBound)` to `candidate.isBefore(windowUpperBound)` → **7 tests failed** (exactly `Rule037Tests`, no others) → reverted, suite green again on a clean rebuild. This confirms the RULE-037 tests genuinely pin the sliding-window behavior rather than passing vacuously.
- Equivalence cases executed: **67** (one COBOL behavior per test; no case was skipped).

## Architecture review (code-modernization:architecture-critic)

One HIGH-severity finding, fixed before shipping: malformed numeric input (a
non-numeric date string, a truncated DB2 timestamp) was escaping as a raw
`NumberFormatException`/`IndexOutOfBoundsException` instead of the documented
`DateConversionException`, in `DateFormatConversionServiceImpl` and
`DateTimeConversionServiceImpl`. Both parsing paths now catch and rewrap those
exceptions; 67/67 tests still pass after the fix.

Lower-severity findings, deferred (not blocking this phase):

- **Medium** — `DateTimeConversionServiceImpl.convert`'s 5-branch boolean-flag
  cascade (`hasDate`/`hasTms`/.../`hasDb2`) would be more robust as a sealed
  hierarchy of the 5 legal input shapes with an exhaustive `switch`, so the
  compiler enforces "exactly one combination is legal" instead of five
  hand-written negated conditions. Worth revisiting if a 6th input combination
  is ever added.
- **Medium** — the `AMD9`/`DMA9`/`AMD7`/`DMA7` parsing branches in
  `DateFormatConversionServiceImpl.parseSource` repeat the same "parse fixed
  substrings, sentinel if below minimum" shape four times; a shared helper
  would reduce copy-paste-drift risk.
- **Medium** — `MachineTimestampServiceImpl` builds `timestamp16`/`date8`/`time8`
  by formatting to a string and immediately re-parsing it back to a number;
  direct integer arithmetic would be clearer and avoid a needless parse step.
- **Minor** — `DateFormatConversionServiceImpl.sentinel()` re-manufactures the
  same `ParsedDate(1,1,1)` on every call instead of reusing a named constant.

## Follow-ups for dependent modules

- Phase 2 (Data Access Layer) will need a real `MessageRepository` implementation (currently only characterized against fakes) and should reuse `ErrorMessageService` as-is.
- Whoever wires the real MQ integration needs `F791MQS0`'s actual PUT1 semantics beyond what `TracePublication` captures (connection/channel setup, error handling on a failed PUT) — out of scope here since `F791MQS0` is external to this repo.
- The JULIAN/A4D3/A2D3-as-source gap above should get characterization tests before any caller relies on those paths.
