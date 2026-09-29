# F5879010 (Customer/Org Master Data) characterization tests

Characterization tests for legacy program `legacy/TFR12/src/F5879010.src`.
Unlike its 9 siblings in `com.tfi.tfr12.phase2.dbi.*`, F5879010 is **not** a
direct DB2 table interface. Its own header comment calls it a "temporary
clutch": it redirects legacy `OA_Konsern` lookups to the newer iHUB-Org DBI
programs `BZXOJOR0` and `BZXOJNA0`, called dynamically
(`legacy/TFR12/src/F5879010.src:44-45`). Those two programs are **external to
this repository** and are never characterized directly here — they are
stood in for by the `IHubOrgGateway` port and its test-only fake.

The source is ISO-8859-1/Latin-1, not UTF-8; column 7 `*` marks a comment,
columns 8-72 are the code area. Every expected value below was hand-traced
from `F5879010.src`, not observed from a running program — F5879010 needs
the real iHUB Org DBIs, which are not available in this environment.

## What's covered

- `OrgUnitAttributes` (`src/main/java/.../orgdata/OrgUnitAttributes.java`) —
  the "extra info" fields `G400-Extract-Attributes`
  (`F5879010.src:602-664`) actually extracts (`GRP`/`SGRP`/`UPDC`/`MRR`,
  matching `R5879010-Ut-Xtra-Info` in
  `legacy/TFR12/copybooks/R5879010.copy:97-101`), plus the validity window.
- `OrgMasterDataClient` — extends the shared
  `com.tfi.tfr12.phase2.dbi.FinInstResolver`, since RULE-018's documented
  edge case ("If only Fin-Inst-No is given, N100-Fetch-Fin-Inst-ID calls
  F5879010 to resolve it") makes F5879010 that interface's real
  implementation.
- `IHubOrgGateway` — the port standing in for `BZXOJOR0`/`BZXOJNA0`.
- `OrgMasterDataClientImpl` — the real implementation: RULE-039's
  as-of-today attribute filtering and the Fin-Inst-No resolution
  orchestration, both calling `IHubOrgGateway`.
- `OrgMasterDataClientTest` — the tests, against a `FakeIHubOrgGateway` test
  double (no real DB2/iHUB access exists or is attempted here).

No credential-like literal appears in `F5879010.src`; nothing here needed
substitution.

## RULE-039 — organization unit attributes are looked up as of today only

`G100-Get-Search-Date` (`F5879010.src:537-553`) fetches the run date once
(via `F7918110`), and `G200-Get-1st-Attribute` (`F5879010.src:557-574`)
qualifies the `BZXOJNA0` lookup with **both**
`BZXOJNA0-In-ORNA-Val-From-Dt` and `-Val-To-Dt` set to that *same* date
(`F5879010.src:565-566`) — there is no caller-supplied as-of date anywhere in
`R5879010-Konsern`. If the lookup finds nothing (no attribute version's
validity window covers today), `G000-Get-Attributes`
(`F5879010.src:525-535`) skips `G400-Extract-Attributes` entirely and quietly
resets the error state — the caller sees no error, just absent attributes.

Because the actual date-window matching happens inside `BZXOJNA0`/DB2 (an
external black box), `OrgMasterDataClientImpl.lookupAttributes` is
characterized as always deriving **today** from the injected `Clock` and
passing it as the single as-of qualification to `IHubOrgGateway`
(collapsing the legacy's "same value in both bounds" into one parameter);
`FakeIHubOrgGateway` in the test plays the role BZXOJNA0/DB2 would play,
matching a stored version only if today falls within its own
`[validFrom, validTo]` window. `OrgMasterDataClientTest` pins:

- a single version covering today is returned, with `validFrom`/`validTo`
  in the result forced to today (`rule039_singleValidVersionCoveringToday_isReturned`).
- with several versions on file (past, current, future), only the one
  covering today is returned
  (`rule039_multipleVersionsWithDifferentWindows_onlyTheOneCoveringTodayIsReturned`).
- no version valid for today → `Optional.empty()`, not an exception
  (`rule039_noVersionValidForToday_returnsEmpty`,
  `rule039_noAttributeVersionsAtAll_returnsEmpty`).
- two different fixed clocks against the same stored data pick two
  different versions, proving this is a real as-of-date filter and not a
  hardcoded/lucky match (`rule039_differentRunDates_selectDifferentVersions_deterministicWithFixedClock`).
- an attribute key outside `GRP`/`SGRP`/`UPDC`/`MRR` is silently ignored,
  matching the `Evaluate WT-Data-Key` having no `When Other` arm
  (`F5879010.src:644-661`).

RULE-039 has no reviewer verdict yet in `analysis/TFR12/RULE_REVIEWS.json` —
treat it as read from the code as-is (Confidence: Medium in
`BUSINESS_RULES.md`, flagged for SME confirmation on whether "no historical
or future-dated query" is intentional).

## RULE-018's edge case — Fin-Inst-No → internal ID resolution

`F100-Prepare-OROR-Qual`'s select-by-No branch (`F5879010.src:339-356`)
qualifies `BZXOJOR0` on the external organization reference
(`BZXOJOR0-In-OROR-Ext-Org-Ref`, `FK-TY-Int-Code = 'FIIN'`) and
`F900-Populate-Output` (`F5879010.src:486`) returns the resolved internal
Org-ID as `R5879010-Ut-KO-Konsern-Id`. This is the paragraph every sibling
DBI table's `KeyPolicy` relies on via `FinInstResolver.resolveFinInstId`
when a record only carries the external Fin-Inst-No. `OrgMasterDataClientTest`
pins:

- a resolvable number returns the internal ID
  (`rule018_resolvableFinInstNo_returnsInternalId`).
- an unresolvable number throws `MissingKeyFieldException("TF-FIN-INST-MISSING")`,
  matching the select-by-No lookup returning `R791ERRX-State-Error`
  (`F5879010.src:436-439`)
  (`rule018_unresolvableFinInstNo_throwsMissingKeyFieldException`).
- distinct numbers resolve to distinct IDs
  (`rule018_distinctFinInstNos_resolveToDistinctIds`).

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=OrgMasterDataClientTest
```

Requires Java 21. All 9 test methods currently pass (2 nested classes:
`Rule039AsOfTodayFiltering` and `FinInstResolutionTests`) —
`OrgMasterDataClientImpl` is a real implementation, not a stub, so the suite
is expected to be green today. Equivalence cases executed: 9.

Note: running the whole module's `mvn test` (not just `-Dtest=
OrgMasterDataClientTest`) also executes the 9 sibling `F115I*0` DBI tables'
tests written by other work in this same shared module; failures there are
outside this test class's scope.

## How this proves anything

A green suite that can't fail proves nothing. Two things were temporarily
broken in `OrgMasterDataClientImpl` and confirmed to turn tests red:

1. `LocalDate.now(clock)` → `LocalDate.now(clock).plusDays(1)` in
   `lookupAttributes` — `rule039_singleValidVersionCoveringToday_isReturned`
   and `rule039_unrecognizedAttributeKey_isSilentlyIgnored` went red (wrong
   date passed to the gateway, so the version that should match no longer
   does).
2. `.orElseThrow(...)` → `.orElse(0L)` in `resolveFinInstId` — 
   `rule018_unresolvableFinInstNo_throwsMissingKeyFieldException` went red
   (silently returned 0 instead of throwing).

Both changes were reverted immediately after confirming the failure; the
suite is green again. If you change `OrgMasterDataClientImpl`, repeat this
exercise: break one small thing that matters, confirm at least one test
goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F5879010.src` for the
   input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to `Rule039AsOfTodayFiltering` or `FinInstResolutionTests`,
   or create a new nested class if it covers different logic in the module
   (e.g. the `Get-1st`/`Get-Next`/`Select` action dispatch in `C000-Interface`,
   not yet characterized here since it is outside RULE-018/RULE-039's scope).
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule
   id in the test method name (e.g. `rule039_...`) or a comment directly
   above it — `modernize-verify` finds tests by that id, and an unnamed test
   counts for no rule.
4. Extend `FakeIHubOrgGateway` if the case needs new gateway behavior (e.g. a
   new stored attribute version or organization mapping) rather than adding
   special cases to `OrgMasterDataClientImpl` itself — the fake plays the
   role of the external BZXOJOR0/BZXOJNA0 programs, whose real internals are
   out of scope.
5. Run `mvn test -Dtest=OrgMasterDataClientTest` and confirm the new test
   executes (not skipped) and passes against the real
   `OrgMasterDataClientImpl` — and, if it's meant to catch a regression,
   prove it can fail by temporarily breaking the corresponding line in
   `OrgMasterDataClientImpl`.
