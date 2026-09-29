# TFR12 Phase 3 tests

This directory holds characterization tests for three independently-owned packages of
the "Charge Calculation" phase:

- `com.tfi.tfr12.phase3.pricing` (`PopPricingServiceTest`, module F115L150) - see below.
- `com.tfi.tfr12.phase3.listing` (`ChargeListingServiceTest`, module F115L060) - see
  "ChargeListingService (F115L060)" further down.
- `com.tfi.tfr12.phase3.merge` (`ChargeMergeServiceTest`, module F115L070) - see
  "ChargeMergeService (F115L070)" further down.

## PopPricingService (F115L150)

### What this covers

`com.tfi.tfr12.phase3.pricing.PopPricingServiceTest` is a **characterization test suite**
for `PopPricingServiceImpl`, the Java translation of legacy COBOL program `F115L150`
("Read POP", `legacy/STG/TFR12/src/F115L150.src`). It computes a default/negotiated charge,
its percentage, and its min/max caps from the `PP_Prod_Tjen_Pris` product/price table.

Per explicit instruction for this phase, these tests are **hand-traced against the COBOL
source**, not dual-executed against a live GnuCOBOL run. Every test method cites the exact
`F115L150.src` line numbers it pins, so a reviewer can re-derive the expected value by
reading the same lines. The legacy code is the oracle: where the business-rules card in
`analysis/TFR12/BUSINESS_RULES.md` and the source code could be read two ways, the source
(re-verified directly, line-by-line) wins.

Five business rules from `analysis/TFR12/BUSINESS_RULES.md` are pinned here, one `@Nested`
class each:

| Rule | Area | Test class |
|---|---|---|
| RULE-001 (P0) | Caller's valid-date reaches the product-price lookup key unchanged - the F115L150-owned half; see "ChargeListingService (F115L060)" below for the F115L060-owned half | `Rule001ValidDateForwardedToProductPriceLookup` |
| RULE-003 (P0) | Graduated/tiered percentage pricing across amount bands | `Rule003GraduatedTieredPricing` |
| RULE-011 (P1) | Percent-of-contract-amount uses the ORIGINAL amount, not outstanding | `Rule011PercentBasisIsOriginalAmount` |
| RULE-012 (P1) | POP pricing amounts are converted from base currency to contract currency | `Rule012CurrencyConversion` |
| RULE-021 (P1) | A POP price row must define a flat amount or a percentage, not both | `Rule021FlatAmountXorPercentage` |

There is also one supplementary, unnamed-rule test (`zeroPriceRows_...`) covering the
"no POP row found" branch of `C000-Interface`, included for completeness since it is
adjacent to the four pinned rules but is not itself one of them.

### Running the tests

```bash
mvn -f "modernized/TFR12/F115L060,F115L070,F115L150/pom.xml" -Dtest=PopPricingServiceTest test
```

Or, to run the whole module's test suite:

```bash
mvn -f "modernized/TFR12/F115L060,F115L070,F115L150/pom.xml" test
```

A run that reports `equivalence cases executed: 0` (i.e. every test skipped) proves
nothing; expect **15 executed, 0 skipped** as of this writing. If you add cases, update
that count in your own head/notes - there is no dual-run harness to auto-report it in this
phase.

### Two ports are faked, nothing else

`F115L150` calls out to two COBOL modules that are genuinely external to this repo slice
(not translated as part of Phase 1/2/3): `F116L030` (currency exchange) and `F349IPP0`
(product/service price). These are modelled as injected ports:

- `ProductPriceGateway` / `ProductPriceQuery` (stands in for `F349IPP0`'s Get-1st/Get-Next
  cursor over `PP_Prod_Tjen_Pris`, collapsed into a single ordered `List<PricingTier>`).
- `CurrencyExchangeGateway` / `CurrencyExchangeRequest` (stands in for one `F116L030` call).

Everything else F115L150 reads (`TF_Sys_Code`, `TF_Sys_Charge_Type`, `TF_Contract_Amount`,
`TF_Part_Amount`) is **already translated** in Phase 2 (`SysCodeRow`, `SysChargeTypeRow`,
`ContractAmountRow`, `PartAmountRow`), so `PricingRequest` carries those already-resolved
rows directly instead of re-modelling the DBI calls.

The test doubles (`FakeProductPriceGateway`, `FakeCurrencyExchangeGateway`) are private
static nested classes at the bottom of `PopPricingServiceTest`. `FakeCurrencyExchangeGateway`
applies a configurable multiplier per direction (`toBasicMultiplier` / `fromBasicMultiplier`)
so a test can prove a conversion genuinely happened (multiplier != 1) or prove one did
**not** happen (assert `fx.calls()` is empty / has the expected size).

No credential-shaped literals appear anywhere in these fixtures; bank numbers, currencies
and amounts are all synthetic.

### Adding a new case

1. Read the exact `F115L150.src` lines for the behaviour you're pinning (byte-safe,
   ISO-8859-1, columns 8-72 are code) - do not trust a paraphrase, including this README or
   the business-rules card, over the source.
2. If it pins one of RULE-003/011/012/021, add a `@Test` inside the matching `@Nested`
   class; name it `ruleNNN_<condition>_<expectedOutcome>` and put the exact source line
   range in a comment above the assertions. If it pins a different `RULE-NNN` from
   `analysis/TFR12/BUSINESS_RULES.md`, add a new `@Nested` class the same way.
3. Build the fixture with the small `tier(...)`, `contractAmount(...)`, `chargeType(...)`,
   `partAmount(...)`, `amt(...)`/`pct(...)`/`dec(...)` helpers at the bottom of the file
   rather than constructing `PricingTier`/`ContractAmountRow`/etc. positional constructors
   inline - it keeps the COBOL field mapping in one place.
4. Compute the expected result BY HAND from the COBOL (not by running the new
   implementation and copying its output) - that is what makes this a characterization
   test rather than a tautology.
5. Run the suite. Then, once, deliberately break the implementation in the one place your
   new test is supposed to protect and confirm the test goes red; revert the break. If
   nothing goes red, the test is not pinning what you think it is - strengthen the
   assertion (e.g. pick fixture values that make the buggy and correct behaviour diverge,
   the way `rule011_noPartAmount_usesOriginalAmount_notRemainingAmount` deliberately makes
   `remainingAmount` differ from `currOriginalAmount`).

## ChargeListingService (F115L060)

### What this covers

`com.tfi.tfr12.phase3.listing.ChargeListingServiceTest` is a **characterization test
suite** for `ChargeListingServiceImpl`, the Java translation (of the 7 rules in scope
for this task) of legacy COBOL program `F115L060`
("Listing/Computation of sum total of charges", `legacy/TFR12/src/F115L060.src`).

Per explicit instruction for this phase, these tests are **hand-traced against the
COBOL source**, not dual-executed against a live GnuCOBOL run. Every test method cites
the exact `F115L060.src` line range it pins.

`ChargeListingServiceImpl` is NOT a byte-for-byte translation of all 1586 lines of
F115L060 - it exposes exactly the 7 pinned rules below, each as its own method mirroring
one legacy paragraph, rather than one monolithic "list all charges" call that would also
re-implement the `TF_Sys_Code_Rel` / `TF_Main_Charge` / `TF_Part_Charge` cursor plumbing
(Phase 2's responsibility) and the running-totals accumulation (RULE-002/005/008/009/010,
out of scope here). See `ChargeListingService`'s class javadoc for the full scope note,
including **RULE-001**: F115L060 fetches the machine date once (only when POP lookup is
enabled for the charge type) and forwards it unchanged into
`com.tfi.tfr12.phase3.pricing.PricingRequest.validDate()`, which `PopPricingServiceImpl`
forwards again into `ProductPriceQuery.validDate()` for the actual date-qualified lookup
(see "PopPricingService (F115L150)" above). Both halves of this boundary are pinned by a
test that asserts the concrete date value, not just that a call happened.

| Rule | Area | Test class |
|---|---|---|
| RULE-001 (P0) | POP priced "as of today" - the F115L060-owned half (date fetch and forwarding) | `Rule001PopPricedTodayBoundary` |
| RULE-006 (P1) | Charge currency re-labelled to contract currency, amount NOT converted | `Rule006CurrencyRelabelOnly` |
| RULE-007 (P1) | Percentage-based charge amount derived from Contract or Part Amount | `Rule007PercentBasedChargeAmount` |
| RULE-019 (P1) | Contract-type classification for GL account and applicability rules | `Rule019ContractTypeClassification` |
| RULE-029 (P1) | Settled part-amount suppresses recalculation of percentage-based charge | `Rule029SettledPartAmountSuppression` |
| RULE-033 (P1) | Fee-payer role determined by contract type and charge role category | `Rule033RoleTypeResolution` |
| RULE-038 (P2) | Charge-type config falls back from institution-specific to registration-number | `Rule038ChargeTypeConfigFallback` |

### Running the tests

```bash
mvn -f "modernized/TFR12/F115L060,F115L070,F115L150/pom.xml" -Dtest=ChargeListingServiceTest test
```

Expect **27 executed, 0 skipped** as of this writing. A run reporting 0 executed proves
nothing - if you see that, something is wrong with the harness, not the legacy code.

### What's faked

- `SysChargeTypeGateway` (this module's own port, not a stand-in for an untranslated
  module - `F115ICT0` is already translated in Phase 2): models the two lookup
  strategies RULE-038's fallback needs (institution-scoped, then registration-number
  scoped) without reproducing `F115ICT0`'s own internal SQL predicate combination
  (a different file, Phase 2's concern). Faked by `FakeSysChargeTypeGateway`.
- `PopPricingService` / `PricingRequest` / `PricingResult` (`com.tfi.tfr12.phase3.pricing`,
  built independently for module F115L150 - not this test suite's file to validate).
  Faked by `FakePopPricingService`, which records the exact `PricingRequest` it was
  called with so a test can assert what `ChargeListingServiceImpl` sent it.
- `MachineTimestampService` (Phase 1, `com.tfi.tfr12.phase1.datetime`): faked by
  `FakeMachineTimestampService`, which returns a fixed `date8` and counts calls - used to
  pin both that the date is fetched exactly when POP would be called (and not otherwise),
  and that the fetched value reaches `PricingRequest.validDate()` unchanged (see RULE-001
  above). Note this fetch happens once per `applyAdditionalInfo` call (i.e. once per
  charge line), not once per run as legacy's `B000-Initialize` does - a narrow,
  currently-undocumented-elsewhere divergence that only matters for a batch run that
  straddles midnight; see `TRANSFORMATION_NOTES.md` follow-ups.

Everything else (`ContractAmountRow`, `SysChargeTypeRow`, `PartAmountRow`, `IncomSwift`)
is a real Phase 2 DBI row type, constructed directly by the fixture helpers at the
bottom of `ChargeListingServiceTest` (`contractAmount(...)`, `chargeTypeDetails(...)`,
`partAmountRow(...)`) - not re-modelled.

No credential-shaped literals appear anywhere in these fixtures; bank numbers,
currencies and amounts are all synthetic.

### Adding a new case

1. Read the exact `F115L060.src` lines for the behaviour you're pinning (byte-safe,
   ISO-8859-1, columns 8-72 are code) - do not trust a paraphrase, including this README
   or the business-rules card, over the source.
2. If it pins one of RULE-001/006/007/019/029/033/038, add a `@Test` inside the matching
   `@Nested` class; name it `ruleNNN_<condition>_<expectedOutcome>` and put the exact
   source line range in a comment above the assertions. If it pins a different
   `RULE-NNN` from `analysis/TFR12/BUSINESS_RULES.md`, add a new `@Nested` class the
   same way, and add a new method to `ChargeListingService` if the current 7-method
   contract doesn't already expose the paragraph you need.
3. Compute the expected result BY HAND from the COBOL (not by running the new
   implementation and copying its output).
4. Run the suite. Then, once, deliberately break the implementation in the one place
   your new test is supposed to protect and confirm the test goes red; revert the break.
   If nothing goes red, strengthen the assertion (e.g. pick fixture values that make the
   buggy and correct behaviour diverge, the way `rule007_roundingIsHalfUp_notTruncated`
   deliberately uses a percentage/base pair whose exact product ends in `5` at the third
   decimal, so a truncating implementation and a `HALF_UP` one disagree).

## ChargeMergeService (F115L070)

### What this covers

`com.tfi.tfr12.phase3.merge.ChargeMergeServiceTest` is a **characterization test suite**
for `ChargeMergeServiceImpl`, the Java translation of legacy COBOL program `F115L070`
("Lists charges" - merges/presents charge data computed from `TF_Main_Charge` and
`TF_Part_Charge`, `legacy/TFR12/src/F115L070.src`).

Per explicit instruction for this phase, these tests are **hand-traced against the
COBOL source**, not dual-executed against a live GnuCOBOL run. Every test method cites
the exact `F115L070.src` line range it pins.

`ChargeMergeService` has three entry points mirroring three independently-testable
pieces of the legacy control flow, not one monolithic method: `process` (the full
`C000-Interface` dispatch), `mergeMainAndPart` (`D000`/`E000` - the consistency,
paying-party and per-row merge/accumulate logic, reachable in isolation without
building a full request), and `reverseStoredCharges` (`F000`/`F100`).

| Rule | Priority | Area | Test class |
|---|---|---|---|
| RULE-002 | P0 | Charge amount from % of Part Amount, clamped to min/max | `Rule002MinMaxClamp` |
| RULE-005 | P1 | Charge computation scope follows whether a part amount is specified | `Rule005ScopeFollowsPartAmount` |
| RULE-008 | P1 | Settlement/part-amount charges are summed, not recalculated | `Rule008SettlementSummed` |
| RULE-009 | P1 | Buyer/seller charge totals split by credit routing | `Rule009TotalsByCreditRouting` |
| RULE-010 | P1 | Subtracting already-stored charges when reversing input, floored at zero | `Rule010ReverseFloorsAtZero` |
| RULE-014 | P0 | Duplicate/conflicting charge detection between Main and Part | `Rule014DuplicateChargeDetection` |
| RULE-020 | P1 | Consistency between main and part charge records | `Rule020MainPartConsistency` |
| RULE-034 | P1 | Charge data scope depends on whether a part-amount is specified | `Rule034ScopeFollowsPartAmountData` |
| RULE-035 | P1 | Part-charge paying party overrides main-charge paying party | `Rule035PayingPartyOverride` |

RULE-005 and RULE-034 both concern "scope follows PA-Seq-No" but at two different
paragraphs: RULE-005 pins `C000-Interface`'s branch dispatch (which of Main/Part gets
read at all), RULE-034 pins `C200-Qualify-Read`'s qualification-record construction
(Main's PA-Seq-No forced to 0, the `In-Check-Pa` "which rate are we on" fix) - kept as
separate `@Nested` classes so each cites its own line range rather than conflating them.

### Running the tests

```bash
mvn -f "modernized/TFR12/F115L060,F115L070,F115L150/pom.xml" -Dtest=ChargeMergeServiceTest test
```

Expect **35 executed, 0 skipped** as of this writing (equivalence cases executed: 35).
A run reporting 0 executed proves nothing - if you see that, something is wrong with the
harness, not the legacy code.

### Scope decisions and what's faked

- **`ChargeListingPort` / `ChargeListingQuery` / `ChargeListingSnapshot`** (this module's
  own port, in `com.tfi.tfr12.phase3.merge` itself, NOT in `com.tfi.tfr12.phase3.listing`):
  stands in for F115L070's two calls to F115L060 (`C300-Read-Main-Charge`,
  `C400-Read-Part-Charge`). It intentionally lives on this side of the boundary because
  F115L060's own module (a sibling work item) exposes its internal paragraphs
  (`classifyContractType`, `resolveRoleTypes`, etc. - RULE-001/006/007/019/029/033/038)
  rather than a single "read the whole charge table" call; adding that orchestration
  method to the sibling-owned package would be out of this module's strict scope
  (`com.tfi.tfr12.phase3.merge` only). Faked by `RecordingChargeListingPort`, which
  records every `ChargeListingQuery` it receives so a test can assert which one(s) were
  issued (RULE-005/034) - most other tests instead call `mergeMainAndPart`/
  `reverseStoredCharges` directly and never touch this port at all.
- **`ContractAmountLookup` / `PartAmountLookup`**: stand in for F115L070's direct calls
  to F115ICA0/F115IPA0 (`E300-Read-Contract-Amount`, `E400-Read-Part-Amount`), used only
  inside RULE-002's min/max clamp. Reuse Phase 2's `ContractAmountRow`/`PartAmountRow`
  directly rather than re-modelling those tables. Configured via `TestHarness`'s
  `withContractAmount`/`withPartAmount`; a `TestHarness` with neither configured throws
  if either is actually called, which several RULE-002 tests rely on to prove the clamp
  path is *not* entered when it shouldn't be (e.g.
  `rule002_partChargeAlreadyNonZero_mainIgnoredEntirely_noClamp`).
- **`ErrorMessageService` / `MessageRepository`** (Phase 1, `com.tfi.tfr12.phase1.errormessage`):
  faked with a trivial always-resolves stand-in (`FakeErrorMessageService`) and an
  always-empty repository (`FakeMessageRepository`) - `ChargeMergeServiceImpl` only uses
  these to resolve human-readable abend text for `Z000-Error-Message`'s 4 call sites
  (C001/D101/D201/D202); the legacy message-code/paragraph/variable-data pinned by the
  RULE-014/020 tests do not depend on the resolved text itself.

No credential-shaped literals appear anywhere in these fixtures; institution ids,
contract numbers, currencies and amounts are all synthetic.

### Adding a new case

1. Read the exact `F115L070.src` lines for the behaviour you're pinning (byte-safe,
   ISO-8859-1, columns 8-72 are code) - do not trust a paraphrase, including this README
   or the business-rules card, over the source.
2. If it pins one of RULE-002/005/008/009/010/014/020/034/035, add a `@Test` inside the
   matching `@Nested` class; name it `ruleNNN_<condition>_<expectedOutcome>` and put the
   exact source line range in a comment above the assertions. If it pins a different
   `RULE-NNN` from `analysis/TFR12/BUSINESS_RULES.md`, add a new `@Nested` class the
   same way.
3. Build fixtures with the `row(...)`, `snapshot(...)`, `context(...)`, `addRequest(...)`,
   `contractAmountRow(...)`, `partAmountRow(...)` helpers at the bottom of the file
   rather than constructing `ChargeRow`/`ContractAmountRow`/etc. positional constructors
   inline - it keeps the COBOL field mapping in one place. Use `assertAmount(...)`, not
   `assertEquals`, to compare `BigDecimal` amounts/percentages (it compares by value via
   `compareTo`, not by scale, matching how COBOL Comp-3 comparisons work).
4. Compute the expected result BY HAND from the COBOL (not by running the new
   implementation and copying its output).
5. Run the suite. Then, once, deliberately break the implementation in the one place
   your new test is supposed to protect and confirm the test goes red; revert the break.
   If nothing goes red, strengthen the assertion (e.g. pick fixture values that make the
   buggy and correct behaviour diverge, the way
   `rule002_percentRecompute_truncatesRatherThanRounds_QUIRK` deliberately picks a
   min-charge/base pair whose exact quotient's 4th decimal digit is `5` or more, so a
   truncating implementation and a `HALF_UP` one disagree).
