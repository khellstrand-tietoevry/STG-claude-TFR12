# F115ICH0 (TF_Main_Charge) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115ICH0.src` (table `TF_Main_Charge`), covering:

- `MainChargeKey` / `MainChargeRow` (`src/main/java/.../dbi/maincharge/`) -
  the Java contract for the table's natural key and full row, hand-traced
  from `legacy/TFR12/copybooks/R115ICH0.copy`.
- `MainChargeKeyPolicy` - a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<MainChargeRow>`, ported from legacy
  `H100-Check-Qualification` (`F115ICH0.src:868-996`).
- `MainChargeServiceTest` - the tests themselves, plus wiring tests for the
  shared `DbiEngine<MainChargeKey, MainChargeRow>` against an in-memory fake
  `KeyedRepository` (there is no real DB2 access here - F115ICH0 needs a DB2
  precompiler that isn't available in this environment).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program.

## RULE-016 - what H100-Check-Qualification actually gates

`analysis/TFR12/BUSINESS_RULES.md` documents RULE-016: before an Update,
F115ICH0 must fully resolve a 6-element key (Fin-Inst-ID, Contract-Type,
Contract-No, RP-Role-Type, Charge-Type, CH-Seq-No), falling back to alternate
`-In-` qualification fields and to `N100-Fetch-Fin-Inst-ID` for the
institution id, and rejects with `TF-FIN-INST-MISSING`,
`TF-CONTRACT-MISSING`, `TF-PARTY-MISSING` or `TF-CHARGE-MISSING` otherwise.
`G000-Insert` (`F115ICH0.src:783-826`) performs no equivalent check at all -
the asymmetry RULE-016 documents. Both halves are pinned directly:
`rule016_*` tests in `KeyPolicyTests` cover every mandatory field, and
`rule016_insert_doesNotInvokeKeyPolicy_evenWithIncompleteKey` in
`EngineWiringTests` proves insert succeeds with an incomplete record.

**CA-Currency and PA-Seq-No are deliberately excluded from `MainChargeKey`.**
They are part of the table's physical Related-Party foreign-key group
(copybook lines 169-172) and do get copied into the row whenever the
RP-Role-Type branch fires (`F115ICH0.src:945-950`), but `H100-Check-Qualification`
never independently tests either one for blank/zero, unlike the identical
7-element pattern in Part Charge (`F115IPC0.src:946-1086`, which does
validate its 2 extra elements). `rule016_caCurrencyAndPaSeqNoBlank_*` pins
this precisely.

## Discrepancy flagged: Select and Delete do not run this check in the real module

Reading `F115ICH0.src` end to end (not just the cited 868-996 range) shows
`H100-Check-Qualification` is called from exactly one place:
`H000-Update` (line 858). `F000-Select` (670-724) and `I000-Delete`
(1026-1033) both call `F100-Prepare-Qualification` (726-753) instead, which
moves the `-In-` fields straight into the DB2 host variables (resolving
Fin-Inst-No if needed) and raises none of the `TF-*-MISSING` errors. The same
Select/Delete-bypass-Update-doesn't pattern was independently confirmed in
`F115ICA0.src` and `F115IPC0.src`, so it is a real, repeated design in the
legacy suite, not an F115ICH0 quirk or a misreading.

The shared `DbiEngine` (out of this module's scope to change) applies
`KeyPolicy.validate` uniformly before Select, Update **and** Delete for every
table wired through it. Plugging `MainChargeKeyPolicy` into it therefore
makes the modernized Select/Delete for Main Charge stricter than literal
F115ICH0 parity requires. `EngineWiringTests` still exercises this (the
`engineContract_select_*` / `engineContract_delete_*` tests), because it is a
real property of the shared engine worth pinning - but those tests are named
and commented as pinning the **engine's** contract, not a RULE-016 claim
about F115ICH0's own Select/Delete paragraphs. See the Javadoc on
`MainChargeKeyPolicy` and the class-level Javadoc on `MainChargeServiceTest`
for the full trace. This has not been raised with the system owner yet -
do so before assuming either side (engine or legacy) is "correct."

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=MainChargeServiceTest
```

Requires Java 21. All 17 test methods (2 nested classes: `KeyPolicyTests`,
`EngineWiringTests`) currently pass against `MainChargeKeyPolicy` - this
module, like `PartChargeKeyPolicy` and `SysCodeKeyPolicy`, ships a real (not
stubbed) implementation, so the suite is expected to be green today.

If you see spurious "cannot find symbol" compile errors for classes that
clearly exist (`Capabilities`, `KeyedRepository`, etc.), another process is
very likely building the same shared module concurrently and stepping on
`target/classes`. Run `mvn clean test -Dtest=MainChargeServiceTest` again on
its own; it is not a code defect in this package.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin something, `MainChargeKeyPolicy`'s RP-Role-Type check was
deliberately flipped (`if (!isBlank(...))` instead of `if (isBlank(...))`),
`mvn test -Dtest=MainChargeServiceTest` was re-run, and 9 of the 17 tests
went red (both the RP-Role-Type-specific tests and every test that depends on
a complete record passing validation, since the flipped condition now throws
on *present* RP-Role-Type instead of *absent*). The mutation was then
reverted and the suite re-confirmed green (17/17). If you change
`MainChargeKeyPolicy`, repeat this exercise: break one small thing that
matters, confirm at least one test goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115ICH0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to the matching `@Nested` class (`KeyPolicyTests` for pure
   validation logic, `EngineWiringTests` for action-dispatch behavior), or
   create a new one if it covers a different paragraph/branch.
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule id
   in the test method name (e.g. `rule016_...`) or a comment directly above
   it - `modernize-verify` finds tests by that id, and an unnamed test counts
   for no rule. If a case documents engine behavior rather than F115ICH0
   parity (as the Select/Delete wiring tests do), name it accordingly
   (`engineContract_...`) instead of claiming a rule id it doesn't support.
4. If the case needs a new field on `MainChargeKey`/`MainChargeRow`, extend
   the record, update the `withXxx` helper methods at the bottom of
   `MainChargeServiceTest`, and update the Javadoc to cite the copybook line
   backing the new field.
5. Run `mvn test -Dtest=MainChargeServiceTest` and confirm the new test
   executes (not skipped) and passes against the real `MainChargeKeyPolicy` -
   and, if it's meant to catch a regression, prove it can fail by temporarily
   breaking the corresponding line in `MainChargeKeyPolicy`.
