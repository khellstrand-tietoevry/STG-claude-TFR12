# F115ICT0 (TF_Sys_Charge_Type) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115ICT0.src` (table `TF_Sys_Charge_Type`), covering:

- `SysChargeTypeKey` / `SysChargeTypeRow` (`src/main/java/.../dbi/syschargetype/`) -
  the Java contract for the table's natural key and full row, hand-traced
  from `legacy/TFR12/copybooks/R115ICT0.copy`.
- `SysChargeTypeKeyPolicy` - a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<SysChargeTypeRow>`, ported from legacy
  `H100-Check-Qualification` (`F115ICT0.src:738-787`).
- `SysChargeTypeServiceTest` - the tests themselves, plus wiring tests for the
  shared `DbiEngine<SysChargeTypeKey, SysChargeTypeRow>` against an
  in-memory fake `KeyedRepository` (there is no real DB2 access here -
  F115ICT0 needs a DB2 precompiler that isn't available in this
  environment).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program.

## No table-specific RULE-NNN id - and why that's fine

`TF_Sys_Charge_Type` was not flagged as distinctive during rule mining, so
`analysis/TFR12/BUSINESS_RULES.md` has no `RULE-NNN` card naming this table's
own field set. It is, however, explicitly covered by RULE-018's generic
observation: "the same pattern [mandatory-key-before-update] is repeated
independently in every F115I*0 table-interface module, so a fix or change to
key validation must be replicated across all of them"
(`BUSINESS_RULES.md:291`). This suite pins that replication for F115ICT0
specifically, citing exact F115ICT0.src line ranges rather than a rule id
that doesn't exist for this table. Test names use plain descriptive method
names (no `ruleNNN_` prefix) since there is no id to attach.

## What H100-Check-Qualification actually gates in F115ICT0

Before an Update, F115ICT0 must resolve exactly 2 mandatory elements:

| Field | Legacy error mnemonic | Source |
|---|---|---|
| CT-Type | `TF-TYPE-MISSING` | `F115ICT0.src:740-754` |
| Fin-Inst-Id / Fin-Inst-No | `TF-FININST-MISSING` | `F115ICT0.src:756-787` |

falling back to the alternate `-In-` qualification fields and to
`N100-Fetch-Fin-Inst-ID` for the institution id, exactly like the sibling
modules. `G000-Insert` (`F115ICT0.src:655-663`) performs no equivalent check
at all - the same insert/update asymmetry RULE-016/018 document for every
other table. Both halves are pinned directly: the plain-named tests in
`KeyPolicyTests` cover both mandatory fields, and
`insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey` in `EngineWiringTests`
proves insert succeeds with an incomplete record.

**Note the mnemonic spelling: `TF-FININST-MISSING`, not `TF-FIN-INST-MISSING`.**
F115ICT0.src:781 moves the literal `'TF-FININST-MISSING'` (one word, no
hyphen between FIN and INST) - this is *not* the same string as the sibling
Main/Part-Charge/Contract-Amount modules use for the equivalent check. This
is exactly the risk RULE-018's card calls out: each module reimplements the
pattern independently, so even the error text is not guaranteed consistent.

**Regno is deliberately excluded from the mandatory checks (but present in
`SysChargeTypeKey`).** H100-Check-Qualification has exactly 2 `Evaluate True`
blocks - CT-Type and Fin-Inst - and no third block for Regno; no
`TF-REGNO-MISSING`-shaped mnemonic exists anywhere in `F115ICT0.src`. Regno is
still part of `SysChargeTypeKey` because it is part of physical row identity
(`W-DB-Key`, `F115ICT0.src:125-128`, and the comment at 394-397, "Allways
qualify on regno") - see `SysChargeTypeKey`'s javadoc for the full
distinction. `regnoZero_stillNoException_notIndependentlyValidated` pins
this precisely.

**No Min/Max charge-amount fields exist in this copybook's current version.**
Both `F115ICT0.src` (change-log line 53) and `R115ICT0.copy` (change-log line
51, and the commented-out field itself at copybook line 116) record that
`Min-Max` was removed from this table. See `SysChargeTypeRow`'s javadoc.

## Discrepancy flagged: Select and Delete do not run this check in the real module

Reading `F115ICT0.src` end to end (not just the cited 738-787 range) shows
`H100-Check-Qualification` is called from exactly one place: `H000-Update`
(line 728). `F000-Select` (562-607) and `I000-Delete` (817-824) both call
`F100-Prepare-Qualification` (609-627) instead, which moves the `-In-` fields
straight into the DB2 host variables (resolving Fin-Inst-No if needed) and
raises none of the `TF-*-MISSING` errors. The same
Select/Delete-bypass-Update-doesn't pattern was independently confirmed in
`F115ICA0.src`, `F115ICH0.src` and `F115IPC0.src`, so it is a real, repeated
design in the legacy suite, not an F115ICT0 quirk or a misreading.

The shared `DbiEngine` (out of this module's scope to change) applies
`KeyPolicy.validate` uniformly before Select, Update **and** Delete for every
table wired through it. Plugging `SysChargeTypeKeyPolicy` into it therefore
makes the modernized Select/Delete for Sys Charge Type stricter than literal
F115ICT0 parity requires. `EngineWiringTests` still exercises this (the
`engineContract_select_*` / `engineContract_delete_*` tests), because it is a
real property of the shared engine worth pinning - but those tests are named
and commented as pinning the **engine's** contract, not a claim about
F115ICT0's own Select/Delete paragraphs. See the Javadoc on
`SysChargeTypeKeyPolicy` and the class-level Javadoc on
`SysChargeTypeServiceTest` for the full trace. This has not been raised with
the system owner yet - do so before assuming either side (engine or legacy)
is "correct."

`engineContract_getRange_doesNotInvokeKeyPolicy` is the one cursor-path test
that IS literal F115ICT0 parity: D000-Get-1st/E000-Get-Next never call
H100-Check-Qualification either, so `DbiEngine.getRange` correctly skips the
policy too.

## Capabilities: FULL

`C000-Interface`'s dispatch `Evaluate` (`F115ICT0.src:275-311`) has a live,
uncommented `When` branch for every one of Get-1st, Get-Next, Select, Insert,
Update and Delete - unlike `F115ISR0` (Update's condition-name is commented
out, RULE-027) and `F115IHI0` (Select and Update are both commented out,
RULE-028). F115ICT0 uses `Capabilities.FULL`. This was checked directly
against this module's own dispatch, not assumed by analogy to any sibling.

## Flagged COBOL comparison quirk (not modeled literally)

`F115ICT0.src:765` reads `When R115ICT0-Fin-Inst-No Not = +0`, comparing the
PIC X(18) alphanumeric field to the numeric literal `+0`. Per COBOL's
nonnumeric-comparison rule, this is a character-by-character comparison (the
literal's digit string "0" padded with spaces to 18 bytes), so the condition
is literally true even when the field holds blank/spaces, not just when it
holds a real institution number. `SysChargeTypeKeyPolicy` instead treats
blank/null `finInstNo` as absent (`isBlank`), matching the modeling choice
already used for `MainChargeKeyPolicy`/`PartChargeKeyPolicy`'s equivalent
fallback branch. This has NOT been proven byte-for-byte equivalent to the
literal legacy comparison for a genuinely blank `Fin-Inst-No` - see
`SysChargeTypeKeyPolicy`'s javadoc for the full caveat.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=SysChargeTypeServiceTest
```

Requires Java 21. All 15 test methods (2 nested classes: `KeyPolicyTests` [8],
`EngineWiringTests` [7]) currently pass against `SysChargeTypeKeyPolicy` -
equivalence cases executed: 15, none skipped.

If you see spurious "cannot find symbol" compile errors for classes that
clearly exist (`Capabilities`, `KeyedRepository`, etc.), another process is
very likely building the same shared module concurrently and stepping on
`target/classes`. Run `mvn clean test -Dtest=SysChargeTypeServiceTest` again
on its own; it is not a code defect in this package.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin something, `SysChargeTypeKeyPolicy`'s Fin-Inst-Id zero-check was
deliberately flipped (`if (record.finInstId() != 0L)` instead of
`if (record.finInstId() == 0L)`), `mvn test -Dtest=SysChargeTypeServiceTest`
was re-run, and 8 of the 15 tests went red (every test whose row is missing
Fin-Inst-Id, and every test that depends on a complete record passing
validation, since the flipped condition now skips the resolver path when it
should run it, and vice versa). The mutation was then reverted and the suite
re-confirmed green (15/15). If you change `SysChargeTypeKeyPolicy`, repeat
this exercise: break one small thing that matters, confirm at least one test
goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115ICT0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to the matching `@Nested` class (`KeyPolicyTests` for pure
   validation logic, `EngineWiringTests` for action-dispatch behavior), or
   create a new one if it covers a different paragraph/branch.
3. This table has no `RULE-NNN` id of its own - name new tests descriptively
   (as the existing ones are) rather than inventing a rule id. If a future
   rule-mining pass mints an id for `F115ICT0`/`TF_Sys_Charge_Type`, retrofit
   the id into the relevant test names so `modernize-verify` can find them.
   If a case documents engine behavior rather than F115ICT0 parity (as the
   Select/Delete wiring tests do), name it accordingly (`engineContract_...`).
4. If the case needs a new field on `SysChargeTypeKey`/`SysChargeTypeRow`,
   extend the record, update the `withXxx` helper methods at the bottom of
   `SysChargeTypeServiceTest`, and update the Javadoc to cite the copybook
   line backing the new field.
5. Run `mvn test -Dtest=SysChargeTypeServiceTest` and confirm the new test
   executes (not skipped) and passes against the real
   `SysChargeTypeKeyPolicy` - and, if it's meant to catch a regression, prove
   it can fail by temporarily breaking the corresponding line in
   `SysChargeTypeKeyPolicy`.
