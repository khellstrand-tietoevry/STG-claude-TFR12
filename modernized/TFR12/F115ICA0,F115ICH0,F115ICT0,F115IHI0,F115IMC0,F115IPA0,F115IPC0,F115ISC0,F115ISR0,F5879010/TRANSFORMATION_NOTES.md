# Transformation Notes — Phase 2: Data Access Layer

**Modules:** `F115ICA0`, `F115ICH0`, `F115ICT0`, `F115IHI0`, `F115IMC0`, `F115IPA0`, `F115IPC0`, `F115ISC0`, `F115ISR0`, `F5879010` (legacy, ~13,125 raw lines) →
`modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010/` (Java 21, Maven module `tfr12-phase2-data-access`)

## Equivalence basis: trace/golden-master, not dual-execution

All 10 legacy programs fail to compile locally: the 9 DBI modules embed `EXEC SQL` (needs a DB2 precompiler, not available here); `F5879010` hits the same GnuCOBOL `REDEFINES`-in-`USING` restriction as Phase 1's `F7919070`/`F7919270`. **Equivalence rests entirely on 159 characterization tests**, each hand-traced from the COBOL source, same discipline as Phase 1.

## The collapse: one generic engine, not 9 reimplementations

Per `ASSESSMENT.md`'s "collapse, don't port" recommendation, the 9 DBI modules' shared mechanics live in one place:

- `DbiEngine<K, R>` — generic select/cursor/insert/update/delete, each action capability-gated and (for select/update/delete) key-validated
- `KeyPolicy<K, R>` — one real implementation per table, encoding that table's mandatory-field validation AND resolving the fully-resolved key `DbiEngine` uses for the actual repository call (see "Architecture review" below — this replaced an earlier, buggier design)
- `Capabilities` — which actions a table actually supports (`FULL`, `CREATE_DELETE_ONLY` for RULE-027, `APPEND_ONLY` for RULE-028)
- `FinInstResolver` — implemented by `F5879010`'s `OrgMasterDataClient`, used by every table's `KeyPolicy` to resolve an external institution number

9 thin per-table packages (`contractamount`, `maincharge`, `partcharge`, `syscode`, `syscoderel`, `history`, `partamount`, `syschargetype`, `maincontract`) each supply only a `Key`/`Row` record pair and a `KeyPolicy` — no per-table reimplementation of the select/cursor/CRUD mechanics.

## Mapping table

| Legacy | Target |
|---|---|
| `F115ICA0.src` (TF_Contract_Amount) | `dbi.contractamount.*` — pilot table |
| `F115ICH0.src` (TF_Main_Charge) | `dbi.maincharge.*` |
| `F115ICT0.src` (TF_Sys_Charge_Type) | `dbi.syschargetype.*` |
| `F115IHI0.src` (TF_History) | `dbi.history.*` — `Capabilities.APPEND_ONLY` |
| `F115IMC0.src` (TF_Main_Contract) | `dbi.maincontract.*` |
| `F115IPA0.src` (TF_Part_Amount) | `dbi.partamount.*` |
| `F115IPC0.src` (TF_Part_Charge) | `dbi.partcharge.*` |
| `F115ISC0.src` (TF_Sys_Code) | `dbi.syscode.*` — RULE-017 preserved gap |
| `F115ISR0.src` (TF_Sys_Code_Rel) | `dbi.syscoderel.*` — `Capabilities.CREATE_DELETE_ONLY` |
| `F5879010.src` (Customer/Org Master Data) | `orgdata.OrgMasterDataClient` + `IHubOrgGateway` port (external `BZXOJOR0`/`BZXOJNA0` mocked, not in this repo) |

## Business rules implemented

| Rule | Table | What |
|---|---|---|
| RULE-016 | `maincharge`, `partcharge` | Update requires a fully resolvable composite key; Insert performs no such check (asymmetry pinned by a dedicated test in both) |
| RULE-017 | `syscode` | **Preserved gap** (explicit owner decision, not a fix): missing `End-Evaluate` means SC-Code validation is unreachable whenever Contract-Type resolves normally; also reproduces the "last-write-wins" error-message precedence when both fail |
| RULE-018 | `contractamount` (+ same pattern independently confirmed in every other table) | Mandatory composite natural key, with Fin-Inst-No→Fin-Inst-ID resolution via `FinInstResolver` |
| RULE-027 | `syscoderel` | Update disabled (`Capabilities.CREATE_DELETE_ONLY`); a relationship can only be deleted and re-inserted |
| RULE-028 | `history` | Select and Update disabled (`Capabilities.APPEND_ONLY`); append-only, cursor-readable |
| RULE-029 | `partamount` | **Split fields, per confirmed owner decision**: the legacy's overloaded one-byte field (`Has-Been-Settled`='A' vs `Incom-Swift` Y/N) becomes two independent fields, `hasBeenSettled: boolean` and `incomSwift: IncomSwift{YES,NO,UNSPECIFIED}` |
| RULE-039 | `orgdata` | Org-unit attributes filtered to the version valid as-of an injected `Clock`'s "today" |

Side-by-side, legacy `F115ISC0.src:783-815` vs. `SysCodeKeyPolicy.validate` (RULE-017's preserved gap):

```
Evaluate True                                              |  // Contract-Type + RULE-017 gap: F115ISC0.src:783-815
  When R115ISC0-Contr-Type  Not = Space                    |  if (isBlank(record.contrType())) {
    Continue                                                |      // Contract-Type's own "When other" (790-796) is
  When R115ISC0-In-Contr-Type Not = Space                   |      // reached, which is exactly the branch the SC-Code
    Move ... To R115ISC0-Contr-Type                         |      // Evaluate (799-813) is nested inside of.
  When other                                                |      if (isBlank(record.scCode())) {
    Move 'TF-CONTRTYP-MISSING' To ...                        |          throw new MissingKeyFieldException("TF-CODE-MISSING");
    Perform Z000-Error-Message                               |      }
    Set Return-Error To True                                 |      throw new MissingKeyFieldException("TF-CONTRTYP-MISSING");
                                                              |  }
Evaluate True                    <- starts here, still       |  // RULE-017 (confirmed-preserved defect): Contract-Type
  When R115ISC0-SC-Code Not = Space   inside the Contract-    |  // resolved normally, so the nested SC-Code Evaluate is
    Continue                          Type "When other" arm,  |  // UNREACHABLE here. SC-Code is NOT validated, no matter
  When R115ISC0-In-SC-Code Not = Space  because 783's own      |  // how blank it is.
    Move ... To R115ISC0-SC-Code        End-Evaluate is       |
  When other                            MISSING before this   |
    Move 'TF-CODE-MISSING' To ...        Evaluate begins.      |
    Perform Z000-Error-Message                                |
    Set Return-Error To True                                  |
End-Evaluate                     <- closes SC-Code (799),      |
.                                    NOT Contract-Type (783)   |
```

The Java reproduces the legacy accident exactly: SC-Code is checked only in the branch reachable through Contract-Type's own failure, never independently.

## Deviations found and flagged (verified against source, not assumed)

Independently confirmed by 5 different agents reading 5 different modules: **the mandatory-key validation (RULE-016/018) is invoked only from Update in the legacy source — never from Select or Delete**, contradicting how the RULE-016/018 cards describe it ("Before a Select/Update/Delete..."). `F000-Select`/`I000-Delete` route through a different paragraph (`F100-Prepare-Qualification`) that performs no such check. `DbiEngine` deliberately applies `KeyPolicy` uniformly to Select/Update/Delete anyway — a real, intentional widening of validation coverage for the new system, not a literal port. Every affected table's tests name this explicitly (`..._widerThanLegacySelect`/`..._widerThanLegacyDelete` or `engineContract_*` rather than `rule016_*`/`rule018_*`) so the deviation is visible, not silently inherited. **This is worth flagging to the system owner**: it means the modernized system will reject some Select/Delete calls the legacy system accepts.

Other confirmed discrepancies, all flagged in code comments and each table's own README:
- **`F115IMC0`** requires a 4-way Fin-Inst fallback (data-area ID/No × qualification-area ID/No) that other tables don't have — collapsed to the same 2-branch shape in Java, documented in `MainContractRow`'s javadoc.
- **`F115ICT0`** spells its institution-missing mnemonic `TF-FININST-MISSING` (no hyphen between FIN and INST) — every sibling table uses `TF-FIN-INST-MISSING`. A genuine cross-module naming inconsistency in the legacy system, reproduced exactly, not "fixed" to be consistent.
- **`F115ISR0`**'s cursor path (`D300-Select-Cursor`) requires Fin-Inst-Id + From-ST-Type or the legacy abends `GL-CURSOR-UV` — but `DbiEngine.getRange()` doesn't route through `KeyPolicy` at all, so this requirement is currently **unenforced** in the Java contract. Pinned by one `@Disabled` test (`SysCodeRelServiceTest`) rather than silently dropped or faked green. **Follow-up needed**: either extend `DbiEngine.getRange()` to accept an optional cursor-qualification check, or accept this gap explicitly before cutover.
- **`F115IHI0`**: `H100-Check-Qualification` still exists in source but its only call site is inside the already-dead `H000-Update` paragraph — so unlike every other table, there is genuinely **no reachable key validation at all** for this table (not even Update-only). `HistoryKeyPolicy.validate()` is a documented no-op, not an oversight.
- **`F5879010`**: the `WC-Use-Copy-Yes`/`F200-Select-On-Copy` internal cache-then-DB2-fallback branch (calling a separate module `F587901T`, not in this repo) was collapsed into a single `IHubOrgGateway.resolveOrgIdByExternalRef` port — only the net resolve-or-fail contract is characterized, not that internal caching distinction.

## What was NOT migrated

- `F5879010`'s real target (`BZXOJOR0`/`BZXOJNA0`, the iHUB-Org DBI programs) is outside this repo's scope — `IHubOrgGateway` is a port with no real implementation yet (brief's open question, Phase 2 entry criteria).
- `F587901T` (F5879010's internal-cache fallback module) — not in this repo, collapsed as noted above.
- No dead code was found worth dropping beyond what's already noted as unreachable (F115ISR0's/F115IHI0's disabled paragraphs) — those are preserved as documented capability restrictions, not deleted, since `Capabilities` needs to know they're disabled.

## Architecture review (code-modernization:architecture-critic)

Two HIGH-severity findings, both fixed before shipping:

1. **The Fin-Inst-No→Fin-Inst-ID resolution result was silently discarded.** The original `DbiEngine.selectOne`/`delete` took a separate caller-supplied `K key` alongside the validated `R keyRecord`, and only ever used the caller's `key` for the repository call — the id `KeyPolicy` resolved from an external Fin-Inst-No was thrown away instead of reaching the actual lookup. **Fixed** by redesigning `KeyPolicy<K, R>.resolveKey(R, FinInstResolver)` to return the fully-resolved key itself; `DbiEngine` now uses exactly that returned key for every repository call, for all three of select/update/delete. All 9 `KeyPolicy` implementations and their tests were updated to the new contract.
2. **Inconsistent multi-field-missing precedence across the 9 `KeyPolicy`s.** Three tables (`contractamount`, `partamount`, `maincontract`) modeled the legacy's actual "last failure wins" behavior (no short-circuit between independent `Evaluate` blocks); three others (`maincharge`, `partcharge`, `syschargetype`) instead short-circuited on the first missing field, with a javadoc caveat admitting the difference was unproven — a real behavioral inconsistency introduced by 9 parallel subagents each making an independent judgment call. **Fixed** by standardizing every `KeyPolicy` on "last failure wins" (matching what the legacy source actually does), including `syscode`'s RULE-017 nested-gap logic. Two tests that had asserted the old first-fail behavior (`MainChargeServiceTest`, `SysChargeTypeServiceTest`) were corrected to assert the standardized last-failure-wins error code instead.

Lower-severity findings, deferred (not blocking this phase): inconsistent `key()` convenience-method availability across Row records (4 of 9 have it); a stale javadoc comment in `HistoryKeyPolicy` (fixed as part of the resolveKey refactor above); duplicated `isBlank` helpers across policies; no defensive handling of malformed numeric input in `OrgMasterDataClientImpl` (low priority — `IHubOrgGateway` has no real implementation yet); no logging anywhere in the module (reasonable to defer until Spring wiring).

## Proof of equivalence

- **161 test methods, 160 executed, 0 failed, 1 deliberately `@Disabled`** (the F115ISR0 cursor-qualification gap above) — clean rebuild, independently re-verified by me after all 9 subagents completed, again after the architecture-review fixes, and again during `modernize-verify`.
- **Canary (run three times — after the initial build, after the architecture-review refactor, and again during independent verify):** force-"fixed" RULE-017's preserved gap in `SysCodeKeyPolicy` (added unconditional SC-Code validation) → exactly 2 tests failed (`Rule017Asymmetry`, `EngineWiring`) every time, nothing else → reverted, suite green again every time. Confirms the RULE-017 tests genuinely pin the preserved-defect behavior across every round of changes.
- **A second, independent canary run during `modernize-verify`** (reverting the architecture-review's own resolved-key fix in `ContractAmountKeyPolicy`, in a scratch copy) exposed a real test-coverage gap: **no existing test actually exercised the Fin-Inst-No→ID resolution end-to-end through `DbiEngine`** — every wiring test used an already-resolved id, so the canary passed even with the fix reverted. Closed by adding `rule018_selectResolvesFinInstNoAndUsesResolvedIdForRepositoryLookup`, which does exercise that path and correctly fails if the fix is reverted.
- That same investigation surfaced a **second, narrower gap, now explicitly flagged** (not silently dropped): `DbiEngine.update(R)` still passes the original, unresolved record straight to the repository — unlike `selectOne`/`delete` (which use the resolved `K`), `update` has no generic way to inject the resolved Fin-Inst-ID back into an arbitrary `R`. In the real legacy module, `H100-Check-Qualification` mutates the same working-storage field that the subsequent `H200-Update-Row` persists, so the legacy DOES persist the resolved id — this Java port does not yet, for Update specifically. Narrow impact (only triggers when a caller updates using the external Fin-Inst-No alone, never the internal id). Pinned by `rule018_updateDoesNotPersistTheResolvedFinInstId_knownGap`, which documents the current (gap) behavior rather than hiding it.
- Per-table canaries were also run by each subagent during their own work (reported individually); the phase-wide canaries above are the independent verification steps.

## Follow-ups for dependent modules

- **Before cutover**: decide on `F115ISR0`'s unenforced cursor-qualification gap (extend `DbiEngine.getRange()`, or accept the gap).
- **Before cutover**: fix `DbiEngine.update()` to persist the resolved Fin-Inst-ID (see "Proof of equivalence" above) — likely needs `KeyPolicy` to expose a way to apply a resolved id back onto `R`, not just return `K`. Replicate the `rule018_selectResolvesFinInstNoAndUsesResolvedIdForRepositoryLookup`-style regression test to the other 8 tables once fixed.
- **Before cutover**: `IHubOrgGateway` needs a real implementation — the actual iHUB-Org interface contract (brief's open question).
- Phase 3 (Charge Calculation) will consume `contractamount`, `maincharge`, `partcharge`, `partamount`, `syschargetype`, `syscode`, `syscoderel` directly — the `DbiEngine`/`KeyPolicy` shape should be stable for it to build on.
- Phase 4 (Orchestration) will need `maincontract`'s data for the Actual/Work version-selection logic (RULE-026) — that switching logic itself lives in `FTFRL120`, out of scope here, but the `MainContractRow`/`MainContractKey` shape is now fixed for it to use.
