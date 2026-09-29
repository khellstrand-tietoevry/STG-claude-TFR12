# Verification: TFR12

Written by `scripts/proof_pack.py` on 2026-09-29 08:44 UTC. Each module's verdict is computed from the evidence files by the fixed rules at the end of this page. No model's opinion is part of it.

**Overall: PARTLY PROVEN** (2 partly proven)


You asked about `F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010`. Every built module is judged from its current evidence files on every run, so no verdict here depends on an earlier run.

Legacy source: `legacy/TFR12` (a link to `/Users/kristofferhellstrand/git/tfi-workspace/STG-claude-TFR12/legacy/STG/TFR12`). Could it run here: no. The source was checked by modification time, and no version-control tool was run.

| Module | Track | Verdict | Tests executed | Failed | P0 rules tested | Same behavior | Fresh inputs |
|---|---|---|---|---|---|---|---|
| F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010 | rewrite | **PARTLY PROVEN** | 160 | 0 | 0 of 1 | not proven | not proven |
| F7919070,F7919100,F7919270,F7918030,F791TRAC | rewrite | **PARTLY PROVEN** | 67 | 0 | 0 of 0 | not proven | not proven |

## F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010: PARTLY PROVEN

Track: rewrite. Folder: `modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010`. Checked 2026-09-29 08:44 UTC.

**What is missing or wrong**

- Rules traced: 1 of 1 P0 rule(s) are not named by any test: RULE-026.
- Same behavior: No equivalence cases were recorded: the new code was never compared with the legacy output.
- Fresh inputs: The legacy could not run here (None of the legacy programs in either built module can execute in this environment. Phase 1 (F7919070,F7919100,F7919270,F7918030,F791TRAC): F7919070/F7919100/F7919270 hit a GnuCOBOL 3.2.0 restriction (REDEFINES field not allowed in PROCEDURE DIVISION USING), F7918030 requires a DB2 precompiler not …), so the proof is trace-based: no fresh-input compari…

**What passed**

- Tests ran: 160 test(s) executed, 0 failed, 1 skipped. Note: 161/162 pass on a clean rebuild (161 executed, 1 deliberately @Disabled: F115ISR0 cursor-qualification gap, documented in TRANSFORMATION_NOTES.md). 2 tests added during this verify pass after a canary exposed a coverage gap; see canary note below.
- Canary: 1 canary run(s) shown by a result file or log; the first (Copied the module to a scratch folder outside modernized/ and analysis/, then force-'fixed' RULE-017's confirmed-preserved gap in SysCodeKeyPolicy (added an unconditional SC-Code validation check that the legacy source's missing End-Evaluate makes unreachable in the real module). A separate, earlie…) made 2 more test(s) fail than t…
- Source untouched: legacy/TFR12 is untouched: no file changed after the analysis started (87 files compared). Checked by modification time, and no version-control tool was run.

| Check | Result | Detail |
|---|---|---|
| Tests ran | pass | 160 test(s) executed, 0 failed, 1 skipped. Note: 161/162 pass on a clean rebuild (161 executed, 1 deliberately @Disabled: F115ISR0 cursor-qualification gap, documented in TRANSFORMATION_NOTES.md). 2 tests added during this verify pass after a canary exposed a coverage gap; see canary note below. |
| Rules traced | not proven | 1 of 1 P0 rule(s) are not named by any test: RULE-026. |
| Same behavior | not proven | No equivalence cases were recorded: the new code was never compared with the legacy output. |
| Fresh inputs | not proven | The legacy could not run here (None of the legacy programs in either built module can execute in this environment. Phase 1 (F7919070,F7919100,F7919270,F7918030,F791TRAC): F7919070/F7919100/F7919270 hit a GnuCOBOL 3.2.0 restriction (REDEFINES field not allowed in PROCEDURE DIVISION USING), F7918030 requires a DB2 precompiler not …), so the proof is trace-based: no fresh-input comparison was possib… |
| Canary | pass | 1 canary run(s) shown by a result file or log; the first (Copied the module to a scratch folder outside modernized/ and analysis/, then force-'fixed' RULE-017's confirmed-preserved gap in SysCodeKeyPolicy (added an unconditional SC-Code validation check that the legacy source's missing End-Evaluate makes unreachable in the real module). A separate, earlie…) made 2 more test(s) fail than the clean… |
| Source untouched | pass | legacy/TFR12 is untouched: no file changed after the analysis started (87 files compared). Checked by modification time, and no version-control tool was run. |

tests executed: 160, failed 0, skipped 1

- unit tests: `mvn -Dmaven.test.failure.ignore=true clean test` (from result files): 160 executed, 0 failed, 1 skipped, 36 result files written 2026-09-29 08:42 UTC. Note: 161/162 pass on a clean rebuild (161 executed, 1 deliberately @Disabled: F115ISR0 cursor-qualification gap, documented in TRANSFORMATION_NOTES.md). 2 tests added during this verify pass after a canary exposed a coverage gap; see canary note below.

### P0 business rules

| Rule | Name | Confidence | Result | Tests | Code | A test that names it |
|---|---|---|---|---|---|---|
| RULE-026 | Contract data-version selection (Actual vs Work) | Medium | code only | 0 | 2 |  |

Inputs left out of the fresh check: Fresh-input dual-execution check (verify Step 4) was not attempted for either module: the legacy side cannot run here at all (see legacy.why above), so no legacy output exists to diff a fresh input a…; Malformed-record inputs were not tried for the same reason (no legacy side to observe tolerating them against).

### Waiting for a person

Claude never ticks these. A person decides them.

- [ ] `F7919070`, `F7919100`, `F7919270`, `F7918030`, `F791TRAC` each pass a dual-execution diff against the legacy module for a representative input set [Phase 1 exit criterion]
- [ ] RULE-036 and RULE-037 proven equivalent [Phase 1 exit criterion]
- [ ] All 9 DBI modules' read behavior reproduced by the one generic repository pattern, each proven against its table's contract test [Phase 2 exit criterion]
- [ ] `F5879010` behavior reproduced against a mocked Org Master Data boundary [Phase 2 exit criterion]
- [ ] RULE-016, RULE-018, RULE-020, RULE-021, RULE-027, RULE-028 proven equivalent [Phase 2 exit criterion]
- [ ] RULE-001, RULE-002, RULE-003 (percentage/tiered charge calculation, min/max clamping) proven equivalent via dual-execution diff [Phase 3 exit criterion]
- [ ] RULE-014 (duplicate/conflicting charge abort) proven equivalent [Phase 3 exit criterion]
- [ ] RULE-011 (percent-of-CA basis) confirmed against the RULE-011 SME question resolution [Phase 3 exit criterion]
- [ ] RULE-004 (min/max default-value recomputation), RULE-015 (single-payer validation incl. ACCEPT-ILC exception), RULE-026 (Actual/Work version selection) proven equivalent [Phase 4 exit criterion]
- [ ] RULE-022, RULE-023, RULE-024, RULE-025, RULE-030, RULE-032 proven equivalent [Phase 4 exit criterion]
- [ ] Shadow-run shows no unexplained diffs against legacy for a full representative traffic sample [Phase 4 exit criterion]
- [ ] RULE-017 suspected defect (`F115ISC0.src:783-813`): a missing `End-Evaluate` silently skips the mandatory Sys-Code key validation on Update. Fix it, or faithfully reproduce the gap? Blocks Phase 2. [open question, section 7]
- [ ] RULE-006 suspected defect (`F115L060.src:1050-1101`): charge currency is relabeled without converting the amount when it differs from the contract currency. Fix it, or preserve the current behavior? Blocks Phase 3. [open question, section 7]
- [ ] RULE-029 overloaded field (`copybooks/R115IPA0.copy:180-183`): one byte carries two unrelated meanings (`Has-Been-Settled` 'A' and `Incom-Swift` Y/N). Clarify intended semantics before modeling it properly in the target. Blocks Phase 2. [open question, section 7]
- [ ] Org Master Data integration: `F5879010`'s real dependency (`BZXOJOR0`/`BZXOJNA0`, the iHUB-Org DBI programs) is outside this repo's scope. Need the real interface contract — or an explicit decision to mock it indefinitely — before Phase 2's Org Master Data Client can be built for real. [open question, section 7]
- [ ] No production telemetry was available for the assessment (`ASSESSMENT.md`, Production Runtime Profile). Confirm whether performance/SLA targets exist for the new service before Phase 4 sign-off. [open question, section 7]
- [ ] Target persistence assumption: this brief assumes the 9 DB2 tables + `PO_Feilmelding` stay on DB2 unchanged (JDBC/JPA access only, no data migration). Confirm, or flag if a data-store migration is actually in scope. [open question, section 7]
- [ ] RULE-009, RULE-010, RULE-031, RULE-033, RULE-035 and the other Medium/Low-confidence P1 rules listed in `BUSINESS_RULES.md`'s SME section were not asked in this review round (only flagged P0s were). Recommend a follow-up `/code-modernization:modernize-review STG/TFR12 p0` → widen to `all` pass, or … [open question, section 7]

### What this does not prove

- It does not prove behavior on inputs nobody tried: the equivalence cases and the fresh inputs are samples, not the whole input space.
- The legacy could not run here, so the comparison rests on recorded outputs and traces. It is only as good as that recording.
- It does not cover speed, capacity, security, concurrency, or anything the tests and cases do not exercise.
- It traces only the rules that were extracted: behavior nobody wrote down as a rule has no rule to trace.
- A test that names a rule shows the rule is mentioned, not that the test is a good one. The canary shows only that some test can fail.
- It does not replace the review and sign-off of the people who own the system.

## F7919070,F7919100,F7919270,F7918030,F791TRAC: PARTLY PROVEN

Track: rewrite. Folder: `modernized/TFR12/F7919070,F7919100,F7919270,F7918030,F791TRAC`. Checked 2026-09-29 08:44 UTC.

**What is missing or wrong**

- Same behavior: No equivalence cases were recorded: the new code was never compared with the legacy output.
- Fresh inputs: The legacy could not run here (None of the legacy programs in either built module can execute in this environment. Phase 1 (F7919070,F7919100,F7919270,F7918030,F791TRAC): F7919070/F7919100/F7919270 hit a GnuCOBOL 3.2.0 restriction (REDEFINES field not allowed in PROCEDURE DIVISION USING), F7918030 requires a DB2 precompiler not …), so the proof is trace-based: no fresh-input compari…

**What passed**

- Tests ran: 67 test(s) executed, 0 failed, 0 skipped. Note: 67/67 pass on a clean rebuild, independently re-run for this verify pass (not reused from modernize-transform).
- Rules traced: No rule this module answers for is rated P0 (2 rule(s) counted).
- Canary: 1 canary run(s) shown by a result file or log; the first (Copied the module to a scratch folder outside modernized/ and analysis/, then inverted ErrorMessageServiceImpl's organization-fallback condition from 'if (orgId != 0)' to 'if (orgId == 0)' (line ~26), which controls whether the org=0/same-language fallback attempt is added to the lookup chain.) made 1 more test(s) fail than the cle…
- Source untouched: legacy/TFR12 is untouched: no file changed after the analysis started (87 files compared). Checked by modification time, and no version-control tool was run.

| Check | Result | Detail |
|---|---|---|
| Tests ran | pass | 67 test(s) executed, 0 failed, 0 skipped. Note: 67/67 pass on a clean rebuild, independently re-run for this verify pass (not reused from modernize-transform). |
| Rules traced | pass | No rule this module answers for is rated P0 (2 rule(s) counted). |
| Same behavior | not proven | No equivalence cases were recorded: the new code was never compared with the legacy output. |
| Fresh inputs | not proven | The legacy could not run here (None of the legacy programs in either built module can execute in this environment. Phase 1 (F7919070,F7919100,F7919270,F7918030,F791TRAC): F7919070/F7919100/F7919270 hit a GnuCOBOL 3.2.0 restriction (REDEFINES field not allowed in PROCEDURE DIVISION USING), F7918030 requires a DB2 precompiler not …), so the proof is trace-based: no fresh-input comparison was possib… |
| Canary | pass | 1 canary run(s) shown by a result file or log; the first (Copied the module to a scratch folder outside modernized/ and analysis/, then inverted ErrorMessageServiceImpl's organization-fallback condition from 'if (orgId != 0)' to 'if (orgId == 0)' (line ~26), which controls whether the org=0/same-language fallback attempt is added to the lookup chain.) made 1 more test(s) fail than the clean run (… |
| Source untouched | pass | legacy/TFR12 is untouched: no file changed after the analysis started (87 files compared). Checked by modification time, and no version-control tool was run. |

tests executed: 67, failed 0, skipped 0

- unit tests: `mvn -Dmaven.test.failure.ignore=true clean test` (from result files): 67 executed, 0 failed, 0 skipped, 14 result files written 2026-09-29 07:18 UTC. Note: 67/67 pass on a clean rebuild, independently re-run for this verify pass (not reused from modernize-transform).

Inputs left out of the fresh check: Fresh-input dual-execution check (verify Step 4) was not attempted for either module: the legacy side cannot run here at all (see legacy.why above), so no legacy output exists to diff a fresh input a…; Malformed-record inputs were not tried for the same reason (no legacy side to observe tolerating them against).

### Waiting for a person

Claude never ticks these. A person decides them.

- [ ] `F7919070`, `F7919100`, `F7919270`, `F7918030`, `F791TRAC` each pass a dual-execution diff against the legacy module for a representative input set [Phase 1 exit criterion]
- [ ] RULE-036 and RULE-037 proven equivalent [Phase 1 exit criterion]
- [ ] All 9 DBI modules' read behavior reproduced by the one generic repository pattern, each proven against its table's contract test [Phase 2 exit criterion]
- [ ] `F5879010` behavior reproduced against a mocked Org Master Data boundary [Phase 2 exit criterion]
- [ ] RULE-016, RULE-018, RULE-020, RULE-021, RULE-027, RULE-028 proven equivalent [Phase 2 exit criterion]
- [ ] RULE-001, RULE-002, RULE-003 (percentage/tiered charge calculation, min/max clamping) proven equivalent via dual-execution diff [Phase 3 exit criterion]
- [ ] RULE-014 (duplicate/conflicting charge abort) proven equivalent [Phase 3 exit criterion]
- [ ] RULE-011 (percent-of-CA basis) confirmed against the RULE-011 SME question resolution [Phase 3 exit criterion]
- [ ] RULE-004 (min/max default-value recomputation), RULE-015 (single-payer validation incl. ACCEPT-ILC exception), RULE-026 (Actual/Work version selection) proven equivalent [Phase 4 exit criterion]
- [ ] RULE-022, RULE-023, RULE-024, RULE-025, RULE-030, RULE-032 proven equivalent [Phase 4 exit criterion]
- [ ] Shadow-run shows no unexplained diffs against legacy for a full representative traffic sample [Phase 4 exit criterion]
- [ ] RULE-017 suspected defect (`F115ISC0.src:783-813`): a missing `End-Evaluate` silently skips the mandatory Sys-Code key validation on Update. Fix it, or faithfully reproduce the gap? Blocks Phase 2. [open question, section 7]
- [ ] RULE-006 suspected defect (`F115L060.src:1050-1101`): charge currency is relabeled without converting the amount when it differs from the contract currency. Fix it, or preserve the current behavior? Blocks Phase 3. [open question, section 7]
- [ ] RULE-029 overloaded field (`copybooks/R115IPA0.copy:180-183`): one byte carries two unrelated meanings (`Has-Been-Settled` 'A' and `Incom-Swift` Y/N). Clarify intended semantics before modeling it properly in the target. Blocks Phase 2. [open question, section 7]
- [ ] Org Master Data integration: `F5879010`'s real dependency (`BZXOJOR0`/`BZXOJNA0`, the iHUB-Org DBI programs) is outside this repo's scope. Need the real interface contract — or an explicit decision to mock it indefinitely — before Phase 2's Org Master Data Client can be built for real. [open question, section 7]
- [ ] No production telemetry was available for the assessment (`ASSESSMENT.md`, Production Runtime Profile). Confirm whether performance/SLA targets exist for the new service before Phase 4 sign-off. [open question, section 7]
- [ ] Target persistence assumption: this brief assumes the 9 DB2 tables + `PO_Feilmelding` stay on DB2 unchanged (JDBC/JPA access only, no data migration). Confirm, or flag if a data-store migration is actually in scope. [open question, section 7]
- [ ] RULE-009, RULE-010, RULE-031, RULE-033, RULE-035 and the other Medium/Low-confidence P1 rules listed in `BUSINESS_RULES.md`'s SME section were not asked in this review round (only flagged P0s were). Recommend a follow-up `/code-modernization:modernize-review STG/TFR12 p0` → widen to `all` pass, or … [open question, section 7]

### What this does not prove

- It does not prove behavior on inputs nobody tried: the equivalence cases and the fresh inputs are samples, not the whole input space.
- The legacy could not run here, so the comparison rests on recorded outputs and traces. It is only as good as that recording.
- It does not cover speed, capacity, security, concurrency, or anything the tests and cases do not exercise.
- It traces only the rules that were extracted: behavior nobody wrote down as a rule has no rule to trace.
- A test that names a rule shows the rule is mentioned, not that the test is a good one. The canary shows only that some test can fail.
- It does not replace the review and sign-off of the people who own the system.

## Sign-off

A person fills this in. Claude leaves it blank.

| | |
|---|---|
| Name | ________________ |
| Role | ________________ |
| Date | __________ |
| Decision | accept / accept with conditions / reject |

## How each verdict is computed

PROVEN needs every check to pass: the six below, and for an uplift three more (7 to 9). NOT PROVEN when any check fails. PARTLY PROVEN when nothing failed but a check could not pass.

1. Tests ran: at least one test executed in a fresh run, none failed, none was skipped without a reason, and every result file is newer than the code. The counts must be read by this script from result files or a saved raw runner log; counts only typed in cannot reach PROVEN.
2. Rules traced (rewrite and reimagine): every P0 rule the module answers for is backed by a test that ran and passed: its id is in the test or class name of a result this script parsed, or a test file names it on a line not marked skipped or pending and that file's class ran and passed. A rule named only by skipped, pending or failing tests is named, not run; one only the notes name is claimed. Neither is tested.
3. Same behavior: the development cases, judged again by compare.py, executed at least one case and none differs or is missing (a difference a person approved is allowed and listed); for an uplift, no regression, no new failure, no missing test and no drop in executed tests against BASELINE.md.
4. Fresh inputs: at least 10 new inputs, none with the same output as a development case, ran on the legacy and the new code with no difference. Needed whenever the legacy could run here; when it could not, the proof is trace-based and the best verdict is PARTLY PROVEN.
5. Canary: a deliberate one-line break whose own result file or saved runner log shows failing tests. A line in the notes is a claim.
6. Source untouched: no file under legacy/<system> is newer than the analysis (PREFLIGHT.md), by a plain file walk; no version-control tool is run.
7. Baseline measured (uplift): the old version's numbers in BASELINE.md come from per-test result files, a per-test results file or a raw runner log that this script parsed (in analysis/<system>/baseline/, or on a Recorded: or Machine-readable: line of BASELINE.md), and the typed table agrees with them. A table typed by hand, or sources that disagree, cannot reach PROVEN.
8. Tests kept (uplift): no test file of the untouched legacy tree is missing from the working copy, no more than 25% of them changed, and the legacy tree has test files to compare against (walked by file, no process is run). The list is for a person to review; weakened assertions cannot be detected, only that files changed.
9. Deltas covered (uplift): every Behavioral-silent delta in DELTA_CATALOG.md has its site's file named, as a whole word, by some test file of the working copy. A name is not proof that the test exercises the change.
Open questions, unticked criteria and the sign-off are for a person. They never change the verdict.
A folder that holds only test code and the files that build it is test tooling: it is listed, not judged, and not counted.
