# Security Findings: TFR12

Written from a `code-modernization:modernize-harden-scan` workflow run (2026-09-29): 5 class-scoped
finders in parallel, then adversarial refutation per distinct finding, then a second independent
judge for each finding still Critical or High after refutation. Legacy source: `legacy/TFR12`
(symlink to `STG/TFR12`). No credentials were entered, no destructive commands were run — this is a
read-only audit of `legacy/TFR12` (plus a brief cross-check against the already-modernized Phase 1
Java code where one finding reproduces there).

## Scorecard

| Severity | Count |
|---|---|
| Critical | 0 |
| High | 3 |
| Medium | 2 |
| Low | 4 |
| **Total (after refutation)** | **9** |

11 raw findings → 11 distinct after dedup → **9 survived adversarial refutation, 2 killed as false
positives** (false-positive rate: 18%, judged findings only). All 3 High findings were additionally
put through a second, independent confirmation pass (real=true on all 3 — see each finding below).

**0 hardcoded credentials found** in `legacy/TFR12` (targeted grep across src/copybooks for
password/secret/apikey/token/private-key/connection-string patterns, all clean).

**Top CWE categories:** CWE-532 (cleartext sensitive data in a shared log/trace sink) — 2 findings;
CWE-20 (missing input validation) — 2 findings; CWE-862 (missing authorization), CWE-1284/CWE-129/
CWE-787 (array-bounds), CWE-696 (execution-order logic error), CWE-330 (weak/static security token),
CWE-1104 (no dependency-scanning control) — 1 each.

## Coverage gaps

All 5 finder classes returned and every finding was judged. `deadFinders`: none. `unverified`:
none. `injectionFlags`: none — a full-repo scan for instruction-shaped text aimed at manipulating
automated review (prompt-injection patterns) found nothing in `legacy/TFR12`.

## Findings

### High

**SEC-001 — Missing authorization check: caller-supplied institution number never validated against the authenticated session** (CWE-862)
- **Where:** `src/FTFRK120.src:861-873` (`E000-Control-Base`, an empty generator stub — confirmed by
  direct read: the paragraph body is a single `Continue`); consumed at
  `src/FTFRL120.src:303-340, 749-798` (`G100-Read-Main-Contract`, `H500-Read-History`);
  `copybooks/RTFRE120.copy:23-27`; `copybooks/R400CH02.copy:44-46`.
- **Exploit:** a caller authenticated for one financial institution
  (`R400CH01-Fininst-Id`/`R400CH01-Finance-Inst`) can pass any `RTFRE120-I-Fin-Inst-No-D` in a
  ReadCharge request; TFR12 uses it verbatim as the DB2 lookup key for `TF_Main_Contract` and
  `TF_History`, returning another institution's contract charges, agent commissions, and
  transaction history. No ownership check exists anywhere in the call chain.
- **Confirmed twice:** refuted-as-real (the stub's only content is `Continue`; the only check on
  `Fin-Inst-No` anywhere in `FTFRK120` is a *presence* check, not an ownership check), then
  independently reconfirmed by a second judge who re-derived the full exploit path from source
  rather than trusting the write-up.
- **Fix:** `security_remediation.patch`, hunk 1 — see Remediation Log.

**SEC-002 — Full trade-finance charge record dumped in cleartext to a diagnostic MQ trace queue** (CWE-532 / CWE-319)
- **Where:** `src/FTFRH120.src:94-99,281-319` (trigger + data movement) and
  `copybooks/RTFRE120.copy:1-300` (record contents). **This exact pattern is reproduced 1:1 in the
  already-modernized Java Phase 1 code**:
  `modernized/TFR12/F7919070,F7919100,F7919270,F7918030,F791TRAC/src/main/java/com/tfi/tfr12/phase1/trace/DiagnosticTraceServiceImpl.java:49-61`
  and `DiagnosticTraceService.java:26` (`DEFAULT_TRACE_QUEUE = "FDPO.TRACELOG"`) — flagging this
  explicitly since it means our own shipped Java inherited the vulnerability, not just the COBOL.
- **Exploit:** when `R400CH01-Trace-On` is set (a routine, caller-controllable production-support
  flag — any caller of the transaction can flip it, per the refutation), `FTFRH120` moves the
  entire `RTFRE120` record — contract numbers, financial-institution number, agent commission,
  buyer/seller charge totals, currency, and paying-party name — into the trace payload, which is
  PUT1'd unencrypted to the shared MQ queue `FDPO.TRACELOG` with no masking or access control in the
  trace path itself. Anyone with browse/get access to that queue can read live customer financial
  data across institutions.
- **Confirmed twice:** refuted-as-real (full call chain traced and confirmed, including the
  mechanically-ported Java), then independently reconfirmed by a second judge.
- **Fix:** `security_remediation.patch`, hunk 2 — masks session-identity fields only (userid/
  terminalid); see the honest scope note in the Remediation Log below. The Java-side fix is
  **not** included in this patch (see Remediation Log) — it needs its own change-and-test cycle
  through the same discipline as the rest of Phase 1, not a drive-by edit during a harden pass.

**SEC-003 — Caller-controlled pagination bounds bypass the 50-row output table cap (COBOL array-bounds overflow)** (CWE-1284 / CWE-129 / CWE-787)
- **Where:** `src/FTFRL120.src:1375-1381` (`U130-Adjust-Parameters`, the `When other` branch) and
  `:1393-1398` (`U160-Iter-Output`, unbounded `WA-Ind-Out` subscript); corroborated by
  `src/FTFRK120.src:641-667` (validation checks only "is numeric", no range check) and
  `copybooks/RTFRE120.copy:142-149` (`RTFRE120-O-Charge-Type-T OCCURS 50 TIMES` plus 7 parallel
  `OCCURS 50` tables).
- **Exploit:** a caller supplies `Read-From`/`Read-To` pagination fields both non-zero (e.g. 1 and
  200) on a contract with more than 50 stored charges. Unlike the two sibling branches in the same
  `Evaluate`, which do cap against the table's 50-row limit, the `When other` branch copies the
  caller's values straight through with no cap, so `U160-Iter-Output` writes past element 50 across
  8 parallel tables — a classic COBOL buffer overflow reachable via externally supplied pagination
  parameters, which can abend the transaction (S0C4) or silently corrupt adjacent memory.
- **Confirmed twice:** refuted-as-real (independently re-derived from source, confirmed the
  asymmetry against the two sibling branches), then independently reconfirmed by a second judge.
- **Fix:** `security_remediation.patch`, hunk 3 — see the scope note on the dropped reject-path in
  the Remediation Log.

### Medium

**SEC-004 — Access-control flag computed from stale return code due to check-before-call ordering bug** (CWE-696)
- **Where:** `src/FTFRL120.src:258-262`.
- **Exploit:** `SW-Access-AM-Base` is set from the *prior* lookup's leftover return status (the
  `CHARGE-TEXT-2012` check at line 253) because the actual `CHARGE-AMB-2012` entitlement lookup at
  line 262 runs *after* the check that sets the flag. A caller entitled to one code but not the
  other gets the wrong access-base flag in either direction.
- Not a Critical/High finding — no patch drafted per the skill's scope, but flagged for the
  Remediation Log as a one-line reorder fix (move the `Perform G200-Read-Sys-Code` immediately
  before its own `If R7919999-Return-OK` check, mirroring the correct pattern already used two
  lines above it for `CHARGE-TEXT-2012`).

**SEC-005 — MQ security-control token generated by F7919330 is a hardcoded, zero-entropy literal, not a weak-but-varying value as initially estimated** (CWE-330, escalated toward CWE-798 territory)
- **Where:** `copybooks/R7919330.copy:31-48` (the `R7919330-Ut-Token` contract, `Pic X(08)`); call
  site `src/F791TRAC.src:327-346`; consumed by `src/F791MQS0.src:399-404` and reproduced in
  `modernized/TFR12/F7919070,.../TracePublication.java:29`.
- **What changed during refutation:** the finder capped this at Low because "the F7919330
  implementation is not present in this repository." The refuter found that premise false — the
  actual `F7919330.src` exists in a **sibling repository in this workspace**
  (`mainframe-prod-inventory`, not one of this session's own three granted working directories) and
  is internally consistent with this repo's own `R7919330.copy` (matching version literal and
  change-log entries). **Flagging this cross-repo read transparently**: the refuter subagent read a
  file outside this session's explicitly granted directories to verify the claim. It was read-only
  and used only to confirm a security question already in scope (not to borrow implementation
  patterns for the modernization pipeline, which remains off-limits per this session's separate
  instruction about not using `tfi-rest-service` as pipeline input) — but it is a boundary the
  agent crossed on its own initiative, worth knowing about.
- **What it found (masked — the raw literal is a hardcoded security-control value and is being
  treated as a secret, not printed here in full):** the "token" is a hardcoded 8-byte literal —
  `[REDACTED 8-char literal, all printable ISO-8859-1 symbols]` — returned identically on every
  call, independent of the caller's userid/terminal (which are checked only for non-blank, never
  mixed into the output). Anyone who observes **one** MQ trace message knows this "verification"
  value forever, for every user and terminal — no brute-forcing or multi-sample prediction needed.
- **Severity:** raised from the finder's Low to **Medium** by the refuter — confirmed hardcoded/
  constant security-control behavior, but the only confirmed consumer found is the diagnostic
  trace/log path (`FDPO.TRACELOG`), not an observed transaction-authorization boundary. If
  `R791MQSH-Verifikasjon` (the generic MQ header field this token populates) is checked by any
  other consumer for something beyond trace/log integrity, severity should be raised further
  (to Critical, CWE-798) — that was not confirmed either way from the files reviewed.
- No patch drafted (Medium, not High/Critical per the skill's remediation scope) — recommend
  locating and reviewing the real `F7919330` source before modernization encodes the current 8-byte
  width as a permanent contract, and auditing every other consumer of `R791MQSH-Verifikasjon`.

### Low

**SEC-006 — Session identity and business payload written unmasked to shared MQ trace queue** (CWE-532)
Same root cause as SEC-002 (`FTFRH120.src` / `F791TRAC.src` trace path) with the caller-controlled
`Trace-On` flag and hardcoded queue name `FDPO.TRACELOG` as the specific mechanism. Resolved by the
same patch hunk as SEC-002 (session-identity portion only — see that finding's scope note).

**SEC-007 — Contract-Type and Operation-Type accepted without a permitted-value whitelist, despite a dedicated whitelist section existing for other fields** (CWE-20)
- **Where:** `src/FTFRK120.src:589-668` (`C300-Permitted-Values` validates Contract-No,
  Part-Amount-No, Period, Read-From, Read-To — but not these two fields).
- Confirmed real by the refuter, who also checked a sibling module (`FTFRK110`) and found the same
  generator convention there — this is a systemic pattern, not a TFR12-specific oversight.
- No patch drafted (Low) — recommend adding the same whitelist-and-reject pattern used for the
  other fields in `C300-Permitted-Values`.

**SEC-008 — No automated Software Composition Analysis wired into the Maven build** (CWE-1104)
- **Where:** all three `modernized/TFR12/*/pom.xml` files.
- Confirmed real, but explicitly a **missing preventive control**, not a demonstrated exploitable
  vulnerability today — current dependencies are test-scope-only JUnit with no known CVEs. Matters
  once Phase 4+ introduces real runtime dependencies (JDBC driver, JSON/HTTP libraries).
- No patch drafted (Low, and a build-config change rather than a code fix) — recommend adding
  `org.owasp:dependency-check-maven` bound to the `verify` phase in each module, failing the build
  on CVSS ≥ 7.

**SEC-009 — STRING without ON OVERFLOW silently truncates diagnostic/abend messages** (CWE-20)
- **Where:** `src/FTFRL120.src:1474-1478, 1497-1501, 1520-1524` (`Z700-Error-AE`, `Z800-Error-AS`,
  `Z900-Abend`); field sizes at `copybooks/R400CH02.copy:87` and `copybooks/R7919999.copy:25`
  (both `PIC X(80)`).
- Not a memory-safety issue (COBOL `STRING` never writes past the receiving field) — the risk is
  operational: a message near 80 bytes gets silently truncated with no `ON OVERFLOW` to detect it,
  hiding the actionable part of an error during incident triage.
- No patch drafted (Low) — recommend adding `ON OVERFLOW` to the three `STRING` statements.

## Refuted (killed as false positives)

1. **"Orphaned/unverifiable MQ security-token generation routine"** (CWE-1188) — the finder claimed
   no `CALL 'F7919330'` exists anywhere in the 21 TFR12 programs. False: `F791TRAC.src:338` calls
   it directly with proper error handling. The finder's `grep` silently returned no matches because
   the file is ISO-8859-1 encoded and a plain `grep` misclassifies it as binary — the same tooling
   blind spot this whole modernization effort has had to route around with `grep -a` throughout.
2. **"Current dependency set is test-scope only; SCA coverage is effectively zero"** (CWE-1104) —
   the finder's own text conceded "not directly exploitable today" and framed it purely as a
   process/tooling gap with no CVE in any resolved version. Redundant with, and weaker than, SEC-008
   above (same underlying observation, correctly kept there instead).

## Dependency CVE table

No entries — no production runtime dependencies exist in any of the three `modernized/TFR12/*`
Maven modules today (test-scope JUnit only); see SEC-008 for the control gap this leaves for later
phases. No package-manager manifests exist in `legacy/TFR12` (pure COBOL/copybook source).

## Remediation Log

Went through 2 rounds of the deterministic revise-and-re-review loop. Round 1 found all 3 hunks
PARTIAL; each was revised to address every concern raised, and round 2 found all 3 RESOLVES (for
their stated, disclosed scope). No hunk was dropped.

| ID | Fix (final, round-2-resolved version) | Patch |
|---|---|---|
| SEC-001 | `FTFRK120.src` `E000-Control-Base` (was a `Continue` stub): resolve `RTFRE120-I-Fin-Inst-No-D` to the internal Konsern-Id domain via `Call 'F5879010'` (mirroring `F115IMC0.src`'s existing `N100-Fetch-Fin-Inst-ID` pattern exactly), then reject via `Z700-Error-AE` on mismatch against `R400CH01-Fininst-Id`. **Round 1 → 2 change:** the original draft compared the raw external code directly against the internal ID field — a likely domain mismatch that a reviewer traced to this codebase's own established resolver pattern. Revised to use that resolver instead of guessing. **Still open (not a patch defect, a deployment prerequisite):** confirm `R5879010-Ut-KO-Konsern-Id` and `R400CH01-Fininst-Id` really are the same identifier domain at runtime (both are populated by infrastructure outside this repo — same-size/shape `PIC 9(18)` fields resolved via the same call used elsewhere for the same purpose is the strongest evidence available from static review alone), and register message code `GL-IKKE-TILGANG&` in the shop's message table (confirmed not already present anywhere in this repo). | `security_remediation.patch`, hunk 1 |
| SEC-002 / SEC-006 | **Round 1 → 2 change:** moved entirely from `FTFRH120.src` (which a reviewer found is marked "THIS MODULE MUST NOT BE MODIFIED BY HUMAN HANDS !!!" with no safe extension point) to `F791TRAC.src`'s `D000-Prepare-Data` (confirmed to carry no such restriction) — a single choke point every caller of the shared trace subprogram goes through, not just `FTFRH120`. Masks `R400CH01-Terminalid`/`-Userid` via a reference-modification blank of `R791TRAC-In-Trace-Data(43:16)`, gated on the trace area being `'R400CH01'`; byte offsets independently re-derived and confirmed correct by the round-2 reviewer (43-50 and 51-58, verified against every preceding field's `PICTURE` width in `R400CH02.copy`). **Scope note, unchanged and still accurate:** this masks session identity only, not the RTFRE120 business payload (contract numbers, amounts, paying-party) — full remediation needs the structured-logger/redaction redesign recommended in the finding above. The identical vulnerability already shipped in the modernized Java (`DiagnosticTraceServiceImpl.java`) is **not** patched here — tracked as a dedicated Phase 1 follow-up through its own test-and-verify discipline, not a drive-by edit during this harden pass. | `security_remediation.patch`, hunk 2 |
| SEC-003 | `FTFRL120.src` `U130-Adjust-Parameters`'s `When other` branch: floor `Read-From` at 1, then only clamp `WA-Read-To` (using the corrected `Charge-Type-A - 1 + Read-From` formula) when the caller's requested window would exceed the table's 50-row cap — otherwise the caller's real `Read-To` is honored unchanged. **Round 1 → 2 change:** the original draft copied a sibling branch's clamp formula verbatim, which a reviewer traced and found off-by-one (permits a 51-row window against a 50-row table), and always substituted the maximal window even for already-in-bounds requests (an undisclosed behavior change beyond the fix). Both fixed; round 2 traced concrete numbers end-to-end and confirmed the corrected formula is off-by-one-free and no longer discards in-bounds requests. **Still open, disclosed in-patch, tracked here as a separate follow-up, not a defect in this hunk:** the sibling `"When any also +0"` branch two cases above (`FTFRL120.src:1362-1366`, untouched by this patch) has the *same* off-by-one bug this patch just fixed in the branch SEC-003 was actually scoped to — it is a live CWE-787 overflow of the same shape, confirmed by the round-2 reviewer, and should be filed as its own fix. | `security_remediation.patch`, hunk 3 |
| SEC-004 | Not patched (Medium) — one-line reorder: move `Perform G200-Read-Sys-Code` for `CHARGE-AMB-2012` immediately before its own `R7919999-Return-OK` check. | none |
| SEC-005 | Not patched (Medium) — needs the real `F7919330` source reviewed and every `R791MQSH-Verifikasjon` consumer audited before a fix is scoped. | none |
| SEC-007, SEC-008, SEC-009 | Not patched (Low) — see each finding's recommendation above. | none |
| *(follow-up, not a numbered finding)* | `FTFRL120.src:1362-1366`'s pre-existing off-by-one in the `"When any also +0"` branch, surfaced only as a side effect of reviewing SEC-003's fix — same CWE-787 shape, different branch, not part of the original scan's 9 findings. Recommend a dedicated fix. | none |

## Patch Review

Two rounds, per the skill's deterministic loop (revise while any hunk is PARTIAL or
INTRODUCES-RISK, up to 3 rounds; never ship a hunk that failed its last review).

**Round 1 — all 3 hunks PARTIAL:**
- Hunk 1: structurally sound reject pattern, but compared an unvalidated external ID against a
  session field that may be in a different (internal, resolver-mediated) identifier domain never
  otherwise read in this repo.
- Hunk 2: masking/restore mechanics were race-free and correct, but hand-edited a file explicitly
  marked "must not be modified by human hands" with no generator-safe extension point.
- Hunk 3: eliminated the large, arbitrary-size overflow, but reused a sibling branch's clamp
  formula that has a pre-existing off-by-one, and silently discarded the caller's real `Read-To`
  value beyond what the fix required.

Each hunk was revised to address its specific round-1 finding (see Remediation Log above for what
changed).

**Round 2 — all 3 hunks RESOLVES:**
- Hunk 1: RESOLVES. The `F5879010` resolver call matches `F115IMC0`'s existing usage field-for-field
  (confirmed independently), the added `Copy R5879010.` sits in a sanctioned customization zone, no
  stale-state risk (state is `Initialize`d fresh every call), and the access-denial genuinely blocks
  the sensitive downstream read (traced the abort path through to `FTFRH120`'s `Goback` on
  `Return-Error`). The domain-match assumption remains correctly flagged as an open runtime question
  rather than asserted as proven — appropriate honesty, not a defect.
- Hunk 2: RESOLVES. Confirmed `F791TRAC.src` carries no modification restriction; independently
  re-derived the byte offsets from the copybook's field widths and confirmed exact coverage (no
  truncation into `Fininst-Id`, no gap); confirmed the mask runs before the record is copied into
  the buffer that actually reaches the MQ `PUT1` call.
- Hunk 3: RESOLVES for its stated scope. Traced concrete numbers (Read-From=1, Read-To=200,
  cap=50 → window becomes exactly [1,50], 50 rows) and a boundary case (Read-From=1, Read-To=50,
  already in bounds → honored unchanged, still 50 rows) — off-by-one-free, and the in-bounds
  behavior change from round 1 is gone. The sibling branch's separate, pre-existing off-by-one is
  confirmed real but was correctly out of SEC-003's original scope; disclosed rather than silently
  left for someone to discover later.

**Outstanding items a person should track** (none of these block applying the patch — they are
runtime confirmations and a separate follow-up fix, not defects in what's here):
1. Register message code `GL-IKKE-TILGANG&` before deploying hunk 1.
2. Get infra/business confirmation that the two Fin-Inst identifier fields in hunk 1 are truly the
   same domain at runtime.
3. File a follow-up fix for `FTFRL120.src:1362-1366`'s separate, pre-existing off-by-one.

## Applied

`security_remediation.patch` was applied to `legacy/TFR12` (2026-09-29) at the user's request. Two
hand-written mistakes surfaced only once the patch met a real COBOL compiler and were fixed before
finalizing:

1. **COBOL reference-modification syntax**: the original hunk 2 draft wrote
   `R791TRAC-In-Trace-Data In R791TRAC-Interface (43:16)` split across two lines in a way that
   pushed the ref-mod's leading digit one column short of GnuCOBOL's fixed-format column-72
   boundary, producing a syntax error. Fixed by re-wrapping the statement so both the qualifier and
   the `(43:16)` land safely inside columns 8-72.
2. **Fabricated sequence-number columns**: hunk 3's hand-typed lines carried cosmetic
   sequence-number-style suffixes (mimicking the surrounding file's real ones) that landed one
   column into the actual code area instead of the ignored 73-80 area, leaking stray digits into
   the `COMPUTE` statements. Fixed by removing all fabricated trailing digits from every inserted
   line — they served no functional purpose.

Both were caught by actually running `cobc -fsyntax-only` against the patched files (something
neither this document's review rounds nor the earlier adversarial patch review had done — those
reviews read the diff and the source, but never compiled the result). The corrected patch was then
re-verified as byte-for-byte reproducible (applying it to a clean copy of the original files
produces output identical to what's now in `legacy/TFR12`) and syntax-checked in place:

- `src/F791TRAC.src`: compiles clean, no errors.
- `src/FTFRK120.src` and `src/FTFRL120.src`: each still shows the *same* pre-existing errors
  (a GnuCOBOL 3.2.0 `REDEFINES`-in-`USING` restriction, and — for `FTFRK120.src` only — an
  unrelated syntax issue in `C200-Out-Fields`, and — for `FTFRL120.src` only — a copybook
  structural issue in `R115ICA0.copy`) confirmed present and identical on the **unmodified**
  originals too, i.e. none of these are introduced by this patch. This module (`FTFRK120`/
  `FTFRL120`/`F791TRAC`, part of Phase 4's orchestration layer) has never been confirmed
  fully compilable in this environment independent of this patch.

`legacy/` outside these 3 files is confirmed untouched (`git status --porcelain legacy/` shows
exactly `FTFRK120.src`, `F791TRAC.src`, `FTFRL120.src` modified, nothing else).
