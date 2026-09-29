# Transformation Notes — Phase 3: Charge Calculation

**Modules:** `F115L060`, `F115L070`, `F115L150` (legacy, 3,331 raw lines: 1,585 + 1,018 + 728) →
`modernized/TFR12/F115L060,F115L070,F115L150/` (Java 21, Maven module `tfr12-phase3-charge-calculation`)

Depends on Phase 1 (`tfr12-phase1-utilities`) and Phase 2 (`tfr12-phase2-data-access`) as real
Maven dependencies (`mvn install`ed locally), not duplicated types.

## Equivalence basis: trace-based only, not dual-execution

Per explicit instruction for this phase, dual-execution against a live GnuCOBOL run was **not
pursued**, even though (unlike Phase 1/2) all three of these modules compile cleanly with
GnuCOBOL 3.2.0 in this environment — no `REDEFINES`-in-`USING` restriction and no `EXEC SQL`
precompiler dependency. Equivalence rests entirely on **77 characterization tests**, each
hand-traced from the COBOL source with exact line citations, same discipline as Phase 1/2.

## Three independently-owned packages, one module

Each legacy program becomes one package, each with its own `Service`/`ServiceImpl` pair. There
is no shared orchestration class yet — composing all three into the single "list, price, merge"
pipeline F115L070 currently drives is out of scope for this phase (see Follow-ups).

| Legacy | Target | Rules pinned |
|---|---|---|
| `F115L150.src` ("Read POP") | `pricing.PopPricingService`/`Impl` | RULE-001 (pricing-side), RULE-003, RULE-011, RULE-012, RULE-021 |
| `F115L060.src` ("Listing/Computation of sum total of charges") | `listing.ChargeListingService`/`Impl` | RULE-001 (listing-side), RULE-006, RULE-007, RULE-019, RULE-029, RULE-033, RULE-038 |
| `F115L070.src` ("Charge merge/presentation") | `merge.ChargeMergeService`/`Impl` | RULE-002, RULE-005, RULE-008, RULE-009, RULE-010, RULE-014, RULE-020, RULE-034, RULE-035 |

Each `Service` interface is **not** a byte-for-byte translation of its whole legacy file — only
the rules listed above are in scope; row-by-row cursor plumbing (Phase 2's concern) and
top-level batch orchestration are explicitly out of scope, documented in each interface's own
class javadoc.

## Business rules implemented (source verified directly, not just the rule cards)

- **RULE-001** (P0, `F115L060.src:1257-1280`, `F115L150.src:264-276`) — a charge priced via POP
  uses **today's** machine date, not the contract's date. Spans both modules: F115L060 fetches
  the date once (`B000-Initialize`, `F115L060.src:178-188`) and forwards it as
  `PricingRequest.validDate()`; F115L150 forwards it again into
  `ProductPriceQuery.validDate()` for the actual date-qualified `PP_Prod_Tjen_Pris` lookup. Both
  halves are pinned with a test asserting the concrete date value, not just that a call
  happened. **This wiring had a real gap during initial construction — see "Fixed during
  review" below.**
- **RULE-003** (P0, `F115L150.src:463-519`) — more than one POP price row for a charge type is
  a graduated/tiered schedule: each tier's percentage applies only to the slice of the
  (currency-converted) amount within that tier's band, increments are summed. See side-by-side
  below for the exact loop translation, including a non-obvious quirk.
- **RULE-006** (P1, `F115L060.src:1076-1079`) — the charge currency is **relabelled** to the
  contract's currency with **no unit conversion** of the amount (the 1999 change that removed a
  prior FX call is commented out, not deleted, in the source). Per explicit user decision, this
  relabel-only behavior is preserved as correct, not treated as a bug.
- **RULE-007** (P1, `F115L060.src:1059-1062`) — a stored charge amount is only recomputed from
  its percentage when the stored amount is zero and a percentage is present.
- **RULE-011** (P1, `F115L150.src:441-461`) — a percentage-of-contract-amount charge (no part
  amount, or method-of-calc ≠ Pct-Of-PA) uses the contract's **original** amount
  (`Curr-O-Amount`), never the outstanding/remaining amount. The commented-out alternative at
  `F115L150.src:457` (`Remaining-Amount`) is confirmed dead code.
- **RULE-012** (P1, `F115L150.src:539-579`) — flat/min/max amounts from the price table are in
  the bank's base currency and are converted to the contract's currency; a percentage-derived
  charge amount is already contract-currency and is **not** re-converted.
- **RULE-019** (P1, `F115L060.src:253-269`) — contract type classifies into one of 4 families
  (Collection/Import-LC/Export-LC/Guarantee); an unrecognized type aborts (mirrors the legacy
  `Z900-Abend`).
- **RULE-021** (P1, `F115L150.src:401-410`) — a POP price row must define a flat amount or a
  percentage, never both; violating this throws
  `InvalidProductPricingConfigurationException`.
- **RULE-029** (P1, `F115L060.src:604-628`) — a settled part amount suppresses re-reading
  `Main_Charge` only when method-of-calc is Pct-Of-PA and a check was requested. Reuses Phase
  2's `PartAmountRow.hasBeenSettled()`/`incomSwift()` split directly (the user's decision to
  model the legacy overloaded byte as two independent fields), scoped strictly to the per-charge
  decision — the legacy's whole-run settlement-check caching (`SW-PA-Checked`) is not modeled.
- **RULE-033** (P1, `F115L060.src:499-570`) — fee-payer role resolution by (category, contract
  family); unmatched combinations read no charges (there is no "When Other").
- **RULE-038** (P2, `F115L060.src:1104-1156`) — charge-type config lookup falls back from
  institution-scoped to registration-number-scoped only if the first lookup misses.
- **RULE-002, 005, 008, 009, 010, 014, 020, 034, 035** (`F115L070.src`, charge merge) — min/max
  clamping with re-derived percentage, part-amount-scoped totals, settlement summation, totals
  by credit-routing, reverse-amount floor at zero, duplicate-charge detection, main/part
  consistency checking, and paying-party override. See `ChargeMergeServiceTest`'s per-rule
  `@Nested` classes for the individually-cited source ranges (9 rules, 35 tests).

## Side-by-side: RULE-003's tiered-pricing loop (`F115L150.src:483-512`)

```
COBOL (F115L150.src:483-512)                                    |  Java (PopPricingServiceImpl.processMultipleOccurrences)
Perform until R7919999-Return-Error                              |      while (!cursorExhausted && totalAmountBase.compareTo(stored) > 0) {
or   WS-PA-or-CA-Amount-Ex     <=   WS-Stored-Gyldig-Til-Bel      |          PricingTier tier = tiers.get(loadedIndex);
  If R349IPP0-Gyldig-Til-Belop <    WS-PA-or-CA-Amount-Ex        |          BigDecimal difference = tier.gyldigTilBelop().compareTo(totalAmountBase)
    Compute WS-Difference = R349IPP0-Gyldig-Til-Belop            |                  ? scale3(tier.gyldigTilBelop()).subtract(stored)
                           - WS-Stored-Gyldig-Til-Bel            |                  : totalAmountBase.subtract(stored);
  Else                                                            |          difference = truncate3(difference);
    Compute WS-Difference = WS-PA-or-CA-Amount-Ex                |          BigDecimal increment = truncate3(difference.multiply(tier.prisprosent())
                           - WS-Stored-Gyldig-Til-Bel            |                  .divide(ONE_HUNDRED, 10, RoundingMode.DOWN));
  End-If                                                          |          popChargeAmount = popChargeAmount.add(increment);
  Compute WS-Increment = WS-Difference * R349IPP0-Prisprosent    |          stored = scale3(tier.gyldigTilBelop());
                        / 100                                    |          if (loadedIndex + 1 < tiers.size()) {
  Add WS-Increment To WS-POP-Charge-Amount                        |              loadedIndex++;
  Move R349IPP0-Gyldig-Til-Belop To WS-Stored-Gyldig-Til-Bel     |          } else {
  Perform C430-Call-DBI-PP-PP    [Get-Next]                       |              cursorExhausted = true;
End-Perform                                                       |          }
                                                                   |      }
```

**Non-obvious quirk, pinned not "fixed":** the loop condition is tested **before** each
iteration (so zero iterations is possible), and `C430-Call-DBI-PP-PP` (Get-Next) is called at
the *end* of every iteration's body — including the one that satisfies the exit condition. On
`Get-Next` failure (cursor exhausted), `R349IPP0` keeps whatever row was **last successfully
read**, it is never re-initialized. `J000-Output-Fields` (which reads `Maksimumpris`/
`Minimumpris` for the result's max/min caps) always runs against *whatever row is currently
loaded when the loop exits* — which is the last tier actually read, not necessarily the last
tier that contributed a charge increment, since the final Get-Next of a successful iteration
still advances the loaded row before the loop condition is re-tested. `loadedIndex` in the Java
translation deliberately does not advance past `tiers.size() - 1` to model "Get-Next keeps the
last successfully read row" exactly, and a dedicated boundary test
(`rule003_amountNotAboveFirstThreshold_zeroIterations_maxMinComeFromFirstTier`) pins that the
zero-iteration case still reads max/min from the first (only loaded) tier.

## Deviations found and flagged (verified against source, not assumed)

- **RULE-020 / RULE-002 quirks in F115L070 (preserved byte-for-byte, per explicit
  characterization-testing mandate):**
  - `D100-Check-Consistency` (RULE-020)'s Main/Part-Charge mismatch error substitutes the
    **literal hard-coded string `'F115L070'`** (the module's own name) as `VAR1` in the built
    error message, instead of the actual mismatching charge type or role. An operator reading
    the resulting abend text can never tell which charge type broke. Preserved and pinned by a
    dedicated test in `ChargeMergeServiceTest.Rule020MainPartConsistency`.
  - RULE-002's initial amount-from-percentage `Compute` (`F115L070.src:553-558`) uses `ROUNDED`,
    but **both** percent-from-amount recomputations after a min or max clamp
    (`F115L070.src:576-580`, `602-606`) **omit** `ROUNDED` — COBOL truncates there instead of
    rounding — and neither carries an `ON SIZE ERROR` clause, so a zero Part-Amount combined
    with a below-minimum fallback amount would divide by zero in production. Preserved and
    pinned by `rule002_percentRecompute_truncatesRatherThanRounds_QUIRK` and the canary
    described below (flipping `RoundingMode.DOWN` to `HALF_UP` turned this test red).
- **RULE-021's abend propagation:** F115L150's `Z900-Abend`-equivalent condition (both a flat
  amount and a percentage defined on the same POP row) throws
  `InvalidProductPricingConfigurationException`, an unchecked `RuntimeException`. Nothing in
  `listing` or `merge` catches it — it propagates all the way to whatever eventually calls
  `applyAdditionalInfo`, matching legacy's own abend-and-halt semantics (the batch run stops
  rather than silently continuing with bad pricing data). This is intentional, not an
  oversight — confirmed during architecture review.

## What was NOT migrated

- The row-by-row `TF_Main_Charge`/`TF_Part_Charge` cursor reads themselves
  (`E120-Read-Next-TF-CH`, `F120-Process-TF-PC` in F115L060) and the top-level paragraph
  orchestration that ties every paragraph of each program into its single external call
  boundary — out of scope for this phase, which targets only the 20 rules listed above.
  `F115L060`'s dead branch in `N000-Compute-Charge-Amount` (a method-of-calc other than
  `P`/`Q` reaching that paragraph, which the source's own comment says "should never occur")
  is deliberately modeled as pure/stateless rather than reproducing the legacy's cross-call
  staleness of `WS-CA-Or-PA-Amount`.
- `ChargeRow.withPercentage(BigDecimal)` (merge module) was written but never called by
  `applyD230`/`applyD260`/`mergeRow` — removed as dead code during architecture review rather
  than kept "for completeness."

## Fixed during review

**RULE-001 cross-module wiring gap (fixed by hand, not by a subagent).** During initial
construction, `ChargeListingServiceImpl.applyAdditionalInfo` fetched the machine timestamp via
`MachineTimestampService` but **discarded the result** instead of forwarding it, and
`PricingRequest`/`ProductPriceQuery` had no field to carry a valid-date at all — meaning
RULE-001's "priced as of today" mechanism was fetched but never actually used by the pricing
lookup. Fixed by adding an `int validDate` field (YYYYMMDD, matching
`MachineTimestamp.date8()`) to both `PricingRequest` and `ProductPriceQuery`, threading it from
`ChargeListingServiceImpl` through `PopPricingServiceImpl.toQuery`, and adding a regression test
on each side of the boundary that asserts the concrete date value. **Canaried:** hardcoding the
forwarded date to `0` in `ChargeListingServiceImpl` turned
`Rule001PopPricedTodayBoundary.rule001_readPopEnabled_fetchesMachineDateAndCallsPopExactlyOnce`
red; reverted and reconfirmed green.

## Architecture review (code-modernization:architecture-critic)

Reviewed the full module, the hand-written `validDate` fix specifically, and the listing
package's interface/impl consistency (see "Process incident" below). No blocking correctness
issue found in the `validDate` wiring — it is correctly threaded and legacy-equivalent for the
case that matters. Findings applied or left open:

- **Applied:** removed dead `ChargeRow.withPercentage(BigDecimal)` (see "What was NOT
  migrated").
- **Applied:** corrected `src/test/README.md`'s RULE-001 section, which still described the
  now-fixed gap as open, and its stale PopPricingServiceTest test count (14 → 15 after adding
  the RULE-001 pricing-side test).
- **Left open (Medium), tracked as a follow-up below:** `validDate` is fetched once per
  `applyAdditionalInfo` call (once per charge line) rather than once per batch run as legacy's
  `B000-Initialize` does. Functionally identical almost always; only differs for a long-running
  batch that straddles midnight, where legacy would price every charge against the start-of-run
  date and this implementation would silently switch mid-run. Not currently test-pinned as an
  accepted deviation (no test invokes `applyAdditionalInfo` twice with an advancing clock).
- **Left open (nit), expected scope for this phase:** every external port
  (`SysChargeTypeGateway`, `ProductPriceGateway`, `CurrencyExchangeGateway`,
  `ContractAmountLookup`, `PartAmountLookup`, `ChargeListingPort`) has only a test-fake
  implementation — no production adapter and no composition root wiring the three packages'
  services together into F115L070's actual pipeline yet.

## Process incident: cross-package file collision (self-corrected, independently verified)

During concurrent construction of the three packages, the `merge`-module agent briefly and
accidentally overwrote two files it had been told not to touch:
`listing/ChargeListingService.java` and a spurious `listing/ChargeListingRequest.java` it
introduced. It caught this immediately, reconstructed `ChargeListingService.java` by inference
from the untouched `ChargeListingServiceImpl.java`/`ChargeListingServiceTest.java`/
`PresentedCharge.java`, deleted the extraneous file, and redesigned its own module to depend
only on ports defined inside `com.tfi.tfr12.phase3.merge` itself so it never needs to touch
`listing` again. This was independently verified twice: once by running the full module test
suite clean (77/77, unchanged from the pre-incident baseline plus 1 new RULE-001 test), and
again by the architecture-critic reading `ChargeListingService.java`'s javadoc line-by-line
against `ChargeListingServiceImpl.java` — every documented contract matched the implementation
exactly, no mismatch found.

## Proof of equivalence

- 77 characterization tests, 0 failures, 0 skipped, run via
  `mvn -f "modernized/TFR12/F115L060,F115L070,F115L150/pom.xml" clean test`.
- **Canary 1 (RULE-002 quirk):** flipped `RoundingMode.DOWN` to `HALF_UP` in the
  percent-recompute path → `rule002_percentRecompute_truncatesRatherThanRounds_QUIRK` failed
  (expected 3.325, got 3.326); reverted, reconfirmed green.
- **Canary 2 (RULE-029):** flipped the settlement-suppression comparison
  (`!partAmountHasBeenSettled` → `partAmountHasBeenSettled`) → 2 tests failed; reverted,
  reconfirmed green.
- **Canary 3 (RULE-001 fix):** hardcoded the forwarded valid-date to `0` → 1 test failed
  (`Rule001PopPricedTodayBoundary`); reverted, reconfirmed green.
- Dual-execution against live GnuCOBOL was explicitly out of scope for this phase (see
  "Equivalence basis" above), despite all three legacy programs compiling cleanly here —
  proof for this module is trace-based only.

## Follow-ups for dependent modules / Phase 4

1. **`validDate` per-call vs. per-run fetch** (Medium, architecture review): if Phase 4's
   orchestration processes multiple charge lines per batch run, fetch the machine date once at
   the top of that run and pass it down explicitly, rather than having each
   `applyAdditionalInfo` call fetch its own — or add an explicit test pinning the current
   per-call behavior as an accepted deviation if it's left as-is.
2. **Composition root needed.** No production adapter exists yet for any of this module's 6
   ports. Phase 4 (or a dedicated wiring task) needs to implement
   `SysChargeTypeGateway`/`ProductPriceGateway`/`CurrencyExchangeGateway`/
   `ContractAmountLookup`/`PartAmountLookup`/`ChargeListingPort` against real Phase 2
   repositories and the still-untranslated `F116L030`/`F349IPP0`, and wire `pricing` →
   `listing` → `merge` into the single pipeline F115L070 currently drives end to end.
3. **RULE-020's unhelpful error message** and **RULE-002's unguarded truncation/divide-by-zero
   risk** are preserved legacy behavior, not defects introduced here — if a future phase
   revisits F115L070 for a genuine bug fix (not just modernization), these are the two most
   operationally-relevant candidates, but changing them was explicitly out of scope for this
   characterization-only pass.
