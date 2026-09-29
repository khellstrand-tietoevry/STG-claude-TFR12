# Modernization Assessment — STG/TFR12

**Date:** 2026-09-28
**Scope:** `legacy/STG/TFR12/` — 21 COBOL programs (`src/`) + 66 copybooks (`copybooks/`), 87 files, 31,140 total lines.

## Executive Summary

TFR12 is a mainframe COBOL "ReadCharge" service (function `TFR12/01`) inside a larger trade-finance
system (STG_TRADEFINANCE), built on a proprietary "Servo" code-generation framework with a
three-tier H/K/L convention (online front door → input control → business logic). It reads and
computes trade-finance charge amounts across 9 DB2 tables via a near-identical family of DBI
(database-interface) wrapper programs. At ~31K lines with dense truth-table business logic and
~10K lines of copy-pasted DB access boilerplate, it is a medium-sized, moderately risky unit: the
core defect risk is concentrated in one module (`FTFRL120.src`), and one genuine
authorization gap was found (a caller-supplied institution key is never checked against the
authenticated session). **Recommended pattern: Refactor** — translate to Java 21/Spring Boot
module-by-module with behavioral parity testing, which is already the direction this workspace has
taken (see `cob2jav`'s `translate-for-tfi-rest-service` pipeline), rather than a from-scratch
rearchitecture.

## System Inventory

- **21 COBOL programs** (`.src`, 23,030 lines) — entry points `FTFRH120`, `FTFRK120`, `FTFRL120`
  (Servo H/K/L triplet), plus 18 support modules.
- **66 copybooks** (`.copy`, 8,110 lines) — record layouts, DB2/MQ/CICS linkage areas, shared
  error-handling structures.
- **Dialect/style:** mixed-case COBOL keywords (`Identification Division.`, `Perform`, `If`),
  non-ASCII (Norwegian) comments and author names throughout — several tooling defaults (plain
  `grep`, `file`, `iconv`) misdetect these files as binary; all scans in this assessment used
  `grep -a` / byte-safe reads.
- **No local build manifest, JCL, CI definition, or test suite** exists in this extract — it is a
  flat COBOL source/copybook drop, consistent with being sliced out of a much larger mainframe
  repository for translation purposes only.
- **Analysis tooling:** `scc` and `cloc` are not installed in this environment; LOC and the
  relative-size index below were computed with `find`/`wc`/`awk`, and complexity was ranked by
  COBOL decision-keyword density (`If`/`Evaluate`/`Perform`) rather than `scc`'s per-file metric.

## Architecture at a Glance

Seven functional domains (diagram: [`ARCHITECTURE.mmd`](ARCHITECTURE.mmd)):

| Domain | Files | Responsibility |
|---|---|---|
| **D1 — Servo Orchestration** | `FTFRH120`, `FTFRK120`, `FTFRL120` | The H/K/L service envelope for "ReadCharge": H sequences K (input validation) then L (business logic) and drives error/trace calls. |
| **D2 — Charge Calculation & Listing** | `F115L060`, `F115L070`, `F115L150` | Lists raw charge rows, computes presentable amounts per method-of-calculation, reads percent-of-principal figures. |
| **D3 — Charge/Contract DBI Layer** | `F115ICA0`, `F115ICH0`, `F115ICT0`, `F115IHI0`, `F115IMC0`, `F115IPA0`, `F115IPC0`, `F115ISC0`, `F115ISR0` | Nine near-identical "database interface" wrappers, one per DB2 table (`TF_Contract_Amount`, `TF_Main_Charge`, `TF_Sys_Charge_Type`, `TF_History`, `TF_Main_Contract`, `TF_Part_Amount`, `TF_Part_Charge`, `TF_Sys_Code`, `TF_Sys_Code_Rel`). |
| **D4 — Customer/Org Master Data** | `F5879010` | Self-described "temporary clutch" wrapper redirecting legacy `OA_Konsern` lookups to the newer iHUB-Org DBI programs. |
| **D5 — Date & Time Utilities** | `F7919070`, `F7919100`, `F7919270` | Generic timestamp/date-format conversion, used by D2–D4 and D7. |
| **D6 — GSM Error & Abend Handling** | `F7918030` | Central error-message/abend builder called by every business module. |
| **D7 — GSM Trace/MQ/CICS Diagnostics** | `F791TRAC` | Publishes trace/debug records via MQSeries and reads CICS execution-interface data. |

The extract calls out to ~25 external programs with no local source (per-table SQL access pairs,
version-conversion helpers, currency/product reference data, the iHUB-Org DBI family, and the GSM
trace/CICS/MQ infrastructure) — expected, since TFR12 is one slice of a larger shared-module
library. No `EXEC CICS` statements exist locally; CICS/MQ access is fully encapsulated in two
external wrapper programs (`F791CICS`, `F791MQS0`). Full call-site citations are in the structural
analysis this document was built from.

## Production Runtime Profile

No telemetry available — no APM connector, batch logs, or runtime exports were supplied for this
assessment. Revisit this section if production trace/log exports become available.

## Technical Debt (top 10, by remediation value)

1. **Order-of-operations bug in access-flag logic** — `SW-Access-AM-Base` is set from the leftover
   return status of the *previous* entitlement lookup (`CHARGE-TEXT-2012`) because the check
   precedes the call meant to populate it for `CHARGE-AMB-2012`.
   `src/FTFRL120.src:258-262` (compare correct ordering at :252-256). A 1:1 port would silently
   reproduce a wrong authorization result — needs SME confirmation before "fixing."
2. **~10,700 lines of duplicated DB-interface boilerplate** across the 9 D3 modules
   (`F115ICA0/ICH0/ICT0/IHI0/IMC0/IPA0/IPC0/ISC0`, each 1,143–1,476 lines) — an identical generated
   CRUD skeleton per DB2 table. This is the single highest-value collapse opportunity: one generic
   repository/DAO pattern in Java replaces all nine.
3. **Undocumented triple-boolean truth-table business rules** governing charge-row
   suppression/overwrite (`I330/I345/I360-...-Row-Suppression` in `src/FTFRL120.src:1038-1173`),
   with only sparse inline comments as the design record — needs an SME walkthrough and
   truth-table-driven tests before translation.
4. **25+ years of dead/commented-out logic left in place** instead of removed — `G300-Read-Event-Info`/
   `G400-Compute-Periods` (`src/FTFRL120.src:376-447`) and two "temporarily removed" commission
   rules dated 1999/2001 (`:1274-1304`). Needs an explicit SME decision: resurrect or drop.
5. **Hardcoded MQ configuration** — trace queue name `'FDPO.TRACELOG'` and app identifier
   `'FDPO-TRACE'` baked into logic (`src/F791TRAC.src:322-323,357`); must become externalized
   config in the rewrite.
6. **Buffer-size/slice mismatch causing latent silent truncation** — `W-Trace-Area` is declared
   `PIC X(40000)` but the MQ `Put1` call always slices a fixed `32768` bytes regardless of the
   computed real length (`src/F791TRAC.src:26,299-302,402`).
7. **Swallowed error path** — a failed system/product-code lookup is handled with a bare `Continue`,
   no logging or distinction from "not found" (`src/FTFRL120.src:363-364`) — the same lookup that
   drives the buggy access flags in #1.
8. **Generator-owned framework code with no generator present in this repo** — `FTFRH120` carries
   "THIS MODULE MUST NOT BE MODIFIED BY HUMAN HANDS" (`src/FTFRH120.src:10`); `FTFRK120`/`FTFRL120`
   are fenced with `<META>` tags from an unspecified "Servo" generator. A translator must
   distinguish templated plumbing from the bespoke logic in the user sections between fences.
9. **Magic constant capping diagnostic call-stack depth** — `R7919999-MODULE-TAB` hardcoded to
   `OCCURS 20 TIMES` (`copybooks/R7919999.copy:19`); abend call-chain recording silently stops
   past 20 nested modules with no error raised.
10. **Undocumented external field-numbering scheme** — output fields annotated "Item 310", "Item
    368", etc. (`src/FTFRL120.src:1195-1246`) with no in-repo mapping to the requirements/tracking
    system they reference — needed for field-by-field sign-off on the rewrite.

**Credential scan:** no hardcoded credentials, passwords, or connection strings found in any
`.src`/`.copy` file. The only token-like mechanism (`F7919330`/`R7919330.copy`) computes a runtime
verification token from userid/terminal-id via a called subroutine — no static secret value is
present, so no `SECRETS.local.md` was created.

## Security Findings

| ID | CWE | Description | Location | Severity |
|---|---|---|---|---|
| SEC-001 | CWE-862 / CWE-639 | Caller-supplied financial-institution key (`RTFRE120-I-Fin-Inst-No-D`) is checked only for presence, then used directly as the DB lookup key for the contract's charges — never compared against the authenticated institution in the session header. The generator's dedicated extension point for this check (`E000-Control-Base`) is left as an empty `Continue` stub, and no downstream DBI module checks institution entitlement either. | `src/FTFRK120.src:861-873`, `src/FTFRL120.src:303-340`, `copybooks/RTFRE120.copy:23-27` | **High** |
| SEC-002 | CWE-696 / CWE-863 | Entitlement flag `SW-Access-AM-Base` is set from the wrong (previous-lookup) return status due to check-before-call ordering — same root cause as Technical Debt #1, with a security consequence: a feature-access decision can be granted/denied using the wrong permission code. | `src/FTFRL120.src:252-262` | Medium |
| SEC-003 | CWE-532 | When trace is enabled, the full session header (userid, terminal ID) and business payload (contract numbers, charge amounts, currencies, paying-party data) are copied unmasked/unencrypted to an MQ trace queue. | `src/FTFRH120.src:272-325`, `src/F791TRAC.src:260-306,348-362` | Low–Medium |
| SEC-004 | CWE-20 | Caller-supplied `Contract-Type` and `Operation-Type` fields are checked only for presence, never against a permitted-value whitelist, even though the generated framework has a dedicated section for exactly this (`C300-Permitted-Values`, which whitelists other fields but not these two). Downstream `Evaluate...When Other` fallbacks limit blast radius. | `src/FTFRK120.src:589-668` | Low |

**Checked and found benign:** the sole executable SQL statement (`src/F7918030.src:503-523`) uses
fully parameterized host variables — no SQL injection surface; no `EXEC CICS` exists locally; all
`GO TO` usage is old-style forward jumps to an error exit, not unsafe state bypass; TFR12 is
read-only so no partial-commit/transaction-inconsistency risk was found; no cryptographic routines
are present.

## Documentation Gaps (top 5)

1. **The Servo/GSM code-generation convention itself** — which `<META>`-fenced sections are
   generated plumbing versus which "user" paragraphs hold bespoke logic is nowhere documented in
   this repo; a newcomer (or a translation tool) has to infer it from banner comments.
2. **The external STG/GSM shared-module library this slice depends on** — ~25 called programs
   (per-table SQL pairs, `F116L030`, `F349IPP0`, `F1781910`, the iHUB-Org DBI family, GSM trace/CICS/MQ
   infrastructure) have no manifest describing what they do or where they live.
3. **The charge-row suppression/overwrite business rules** (`I330/I345/I360` in `FTFRL120.src`) —
   the core "which charge row wins" logic for the whole function, recorded only in sparse inline
   comments.
4. **Institution-entitlement check design intent** — the generator left an explicit extension point
   (`E000-Control-Base`) for this; nothing records what check was supposed to go there (see
   SEC-001).
5. **The external "Item NNN" field-traceability numbering** used throughout `FTFRL120.src`'s output
   assembly, with no in-repo mapping to the system it traces into.

## Relative Scale

- **31,140 total lines** across 87 files (23,030 in `.src`, 8,110 in `.copy`); **18,368 lines**
  excluding comments/blanks (KSLOC ≈ 18.4).
- **Relative-size index** (`2.94 × KSLOC^1.10`, scc's formula, computed manually — `scc`/`cloc` not
  installed): **≈72** using code-only KSLOC (18.4), or **≈129** using total-line KSLOC (31.1).
  **This index is a relative size measure for ranking systems against each other — it is not a
  timeline, a schedule, or a cost estimate.**
- Most complex files by decision-keyword density (`If`/`Evaluate`/`Perform`, comments excluded):
  `F115IMC0` (225), `F115ISR0` (213), `F115L060` (209), `F115IPC0` (207), `F115IPA0` (205) — the D3
  DBI layer and D2 calculation modules dominate complexity, consistent with the duplication finding
  above.

## Recommended Modernization Pattern: Refactor

TFR12 is a well-bounded, behaviorally-critical unit (financial charge calculation) with dense but
comment-poor business logic, and it must keep behaving identically. This argues against a
from-scratch rearchitecture (`Rebuild`/`Rearchitect`) and against a same-technology `Uplift` (there
is no newer COBOL platform target here). It fits **Refactor**: rewrite module-by-module in the
target technology while proving behavioral parity at each step. (Target stack is an open decision
for this repo — see `MODERNIZATION_BRIEF.md` once written; this assessment does not assume any
particular target or reuse another repo's translation pipeline.) Two things should shape that
effort directly:

- **Collapse, don't port, the D3 DBI layer.** The 9 near-identical database-interface modules
  (Technical Debt #2) should become one generic repository pattern, not 9 separately translated
  classes — this is the single biggest scope/complexity reduction available.
- **Resolve the open questions before translating, not during.** Findings #1/#3/#4/#7 (the
  access-flag ordering bug, the suppression truth tables, the 25-year-old dead code, and the
  swallowed error path) all need an SME decision on intended behavior; translating them as-is would
  either silently propagate a bug or require an undocumented judgment call mid-translation.

**Next step:** `/code-modernization:modernize-map STG/TFR12`
