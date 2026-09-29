# F115ISR0 (TF_Sys_Code_Rel) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/STG/TFR12/src/F115ISR0.src` (table `TF_Sys_Code_Rel`), covering:

- `SysCodeRelKey` / `SysCodeRelRow` (`src/main/java/.../dbi/syscoderel/`) -
  the Java contract for the table's natural key and full row, hand-traced
  from `legacy/STG/TFR12/copybooks/R115ISR0.copy` and from
  `G100-Move-Input-To-DCL` (Insert) / `F100-Prepare-Qualification`
  (Select/Delete) in `F115ISR0.src`.
- `SysCodeRelKeyPolicy` - a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<SysCodeRelRow>`. Unlike the sibling
  `TF_Sys_Code` table, this one is a deliberate no-op: see "RULE-027" below.
- `SysCodeRelServiceTest` - the tests themselves, plus wiring tests for the
  shared `DbiEngine<SysCodeRelKey, SysCodeRelRow>` against an in-memory fake
  `KeyedRepository` (there is no real DB2 access here - F115ISR0 needs a DB2
  precompiler that isn't available in this environment).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program.

## RULE-027 - read this before touching `Capabilities.CREATE_DELETE_ONLY` or `SysCodeRelKeyPolicy`

`analysis/TFR12/BUSINESS_RULES.md` documents RULE-027: `TF_Sys_Code_Rel`
relationships (e.g. valid Operation-Type-to-Charge-Type mappings, consumed by
`F115L060.src:341-406`) can only be **created or deleted, never updated in
place**.

Confirmed directly in the source for this port (not just from the rule-card
summary):

- `legacy/STG/TFR12/copybooks/R115ISR0.copy:69` - the `88 R115ISR0-In-Update`
  condition-name is commented out (`R115ISR0-In-Insert` at line 68 and
  `R115ISR0-In-Delete` at line 70 are both live).
- `legacy/STG/TFR12/src/F115ISR0.src`, `C000-Interface` (lines 326-362) -
  the `When R115ISR0-In-Update` / `Perform H000-Update` dispatch arm is
  commented out at **lines 345-346**, between the live `Insert` arm
  (342-343) and the live `Delete` arm (348-349). A request with
  `In-Action = 'UPDATE'` therefore falls to `When Other` (351), which raises
  `GL-IN-AKSJON-UV` and calls `Z900-Abend` (358) - the request **abends**, it
  does not return a graceful business error.
- `H000-Update` (933-943), `H100-Check-Qualification` (945-1146) and
  `H200-Update-Row` (1148-1172) all still exist in the source as unreachable
  dead code - nothing in `C000-Interface` ever performs them.

`Capabilities.CREATE_DELETE_ONLY` (`select=true, cursor=true, insert=true,
update=false, delete=true`) was checked against this trace and **matches
exactly** - `rule027_createDeleteOnly_fieldValuesMatchDispatch` pins it.

**Do not re-enable `update` on this table's `Capabilities` without first
getting a new, explicit decision from the system owner to reverse RULE-027's
confirmed verdict.** If you do, `rule027_createDeleteOnly_fieldValuesMatchDispatch`
and `rule027_update_throwsDbiActionNotSupportedException` are expected to go
red - that is correct, not a test bug (see "How this proves anything" below).

### The "change a relationship" workaround

Because there is no Update path, changing an existing relationship (e.g.
re-pointing Operation-Type `OP01` from Charge-Type `CH01` to `CH02`) has to be
expressed as **Delete the old key, then Insert the new one** -
`rule027_changeRelationship_workaround_isDeleteThenInsert` pins exactly this
sequence against the shared `DbiEngine`.

### Select/Delete qualification: no active requirement (and a known gap on cursor)

`F100-Prepare-Qualification` (`F115ISR0.src:798-837`), which qualifies both
Select and Delete, only moves fields (plus an institution-number fallback
lookup) - it has no `Evaluate`/error-message/abend for a missing key field,
unlike the dead `H100`. `SysCodeRelKeyPolicy.validate()` is therefore a
deliberate no-op, confirmed by `KeyPolicyNoActiveRequirement` in the test.

The **cursor** path (Get-1st) is different: `D300-Select-Cursor`
(`F115ISR0.src:468-596`) abends with `GL-CURSOR-UV` unless Fin-Inst-Id is set
**and** From-ST-Type is set. This is a real, active requirement in legacy,
but `DbiEngine.getRange(...)` does not call `KeyPolicy.validate` at all, so
nothing in this Java contract currently enforces it. This is pinned by a
`@Disabled("pending RULE-027 follow-up ...")` test in `EngineWiring` -
**do not delete it**; it is a known, flagged gap, not a mistake. Follow up
with whoever owns `DbiEngine` before removing the annotation.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=SysCodeRelServiceTest
```

Requires Java 21. 10 test methods across 3 nested classes
(`CapabilitiesMatchDispatch`, `KeyPolicyNoActiveRequirement`, `EngineWiring`):
9 execute and pass, 1 is `@Disabled` (the cursor-qualification gap above).
**Equivalence cases executed: 9** (excludes the disabled case; zero executed
would mean nothing was proven).

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests actually
pin RULE-027 (rather than passing by accident), `Capabilities.CREATE_DELETE_ONLY`
was deliberately broken (`update` flipped from `false` to `true`), `mvn test
-Dtest=SysCodeRelServiceTest` was re-run, and exactly the two tests naming
RULE-027 that depend on Update being disabled went red:
`rule027_createDeleteOnly_fieldValuesMatchDispatch` (expected `false`, got
`true`) and `rule027_update_throwsDbiActionNotSupportedException` (expected
`DbiActionNotSupportedException`, nothing was thrown). The change was then
reverted and the suite re-run to confirm it went green again. If you change
`Capabilities.CREATE_DELETE_ONLY` or `SysCodeRelKeyPolicy`, repeat this
exercise: break one small thing that matters, confirm at least one test goes
red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115ISR0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to the matching `@Nested` class
   (`CapabilitiesMatchDispatch`, `KeyPolicyNoActiveRequirement`,
   `EngineWiring`), or create a new one if it covers a different
   paragraph/branch.
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule id
   in the test method name (e.g. `rule027_...`) or a comment directly above
   it - `modernize-verify` finds tests by that id, and an unnamed test counts
   for no rule.
4. If the case needs a new field on `SysCodeRelKey`/`SysCodeRelRow`, extend
   the record and update its Javadoc to cite the copybook/source line backing
   the new field. Note: this table has no non-key data columns at all (see
   `SysCodeRelRow`'s Javadoc) - if you find one, re-verify against the
   copybook before adding it, since that would contradict this module's
   current finding.
5. Run `mvn test` and confirm the new test executes (not skipped) and passes
   - and, if it's meant to catch a regression, prove it can fail by
     temporarily breaking the corresponding line in `SysCodeRelKeyPolicy` or
     `Capabilities`.
