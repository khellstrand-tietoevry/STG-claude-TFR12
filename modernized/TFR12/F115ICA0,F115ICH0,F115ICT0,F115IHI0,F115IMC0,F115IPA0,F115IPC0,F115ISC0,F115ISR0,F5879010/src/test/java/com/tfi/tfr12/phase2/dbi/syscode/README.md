# F115ISC0 (TF_Sys_Code) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115ISC0.src` (table `TF_Sys_Code`), covering:

- `SysCodeKey` / `SysCodeRow` (`src/main/java/.../dbi/syscode/`) - the
  Java contract for the table's natural key and full row, hand-traced from
  `legacy/TFR12/copybooks/R115ISC0.copy`.
- `SysCodeKeyPolicy` - a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<SysCodeRow>`, ported from legacy
  `H100-Check-Qualification` (`F115ISC0.src:739-815`).
- `SysCodeServiceTest` - the tests themselves, plus wiring tests for the
  shared `DbiEngine<SysCodeKey, SysCodeRow>` against an in-memory fake
  `KeyedRepository` (there is no real DB2 access here - F115ISC0 needs a DB2
  precompiler that isn't available in this environment, see
  `TRANSFORMATION_NOTES.md` in the sibling Phase 1 module for the same
  constraint on other programs).

Every expected value was hand-traced from the COBOL source and copybook, not
observed from a running legacy program.

## RULE-017 - read this before touching `SysCodeKeyPolicy`

`analysis/TFR12/BUSINESS_RULES.md` documents RULE-017: a missing
`End-Evaluate` after the Contract-Type qualification block in
`H100-Check-Qualification` (expected around `F115ISC0.src:796`) means the
mandatory SC-Code (system code) check at `F115ISC0.src:799-813` is
**unreachable** whenever Contract-Type resolves through a non-error branch -
the normal case. ST-Type (741-755) and Institution (757-781) do NOT have this
problem; each correctly closes with its own `End-Evaluate`.

**This was confirmed with the system owner and the explicit decision was to
preserve the gap, not fix it.** `SysCodeKeyPolicy` reproduces it exactly:
SC-Code is validated only when Contract-Type itself fails to resolve. The
`Rule017Asymmetry` nested test class in `SysCodeServiceTest` pins this
precisely, including the secondary "which error message wins" effect of the
same missing `End-Evaluate` (see the class Javadoc on `SysCodeKeyPolicy` for
the full line-by-line trace).

**Do not "fix" this in `SysCodeKeyPolicy` without first getting a new,
explicit decision from the system owner to reverse RULE-017's confirmed
verdict.** If you do change it, `rule017_contrTypeResolvesNormally_blankScCode_isNotValidated`
and the matching `EngineWiring` test are expected to go red - that is
correct, not a test bug.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=SysCodeServiceTest
```

Requires Java 21. All 19 test methods (5 nested classes: `StTypeValidation`,
`InstitutionValidation`, `Rule017Asymmetry`, `EngineWiring`, plus the outer
class) currently pass against `SysCodeKeyPolicy` - this module, unlike the
Phase 1 module, ships a real (not stubbed) implementation, so the suite is
expected to be green today.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin RULE-017 (rather than passing by accident), the gap was
deliberately "fixed" (SC-Code validated unconditionally whenever
Contract-Type is missing OR blank), `mvn test` was re-run, and exactly the
two tests naming RULE-017
(`rule017_contrTypeResolvesNormally_blankScCode_isNotValidated` and
`rule017_updateWithBlankScCode_stillReachesRepository`) went red with
`MissingKeyFieldException: TF-CODE-MISSING`. The fix was then reverted. If
you change `SysCodeKeyPolicy`, repeat this exercise: break one small thing
that matters, confirm at least one test goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115ISC0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to the matching `@Nested` class (`StTypeValidation`,
   `InstitutionValidation`, `Rule017Asymmetry`), or create a new one if it
   covers a different paragraph/branch.
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule id
   in the test method name (e.g. `rule017_...`) or a comment directly above
   it - `modernize-verify` finds tests by that id, and an unnamed test counts
   for no rule.
4. If the case needs a new field on `SysCodeKey`/`SysCodeRow`, extend the
   record and update its Javadoc to cite the copybook line backing the new
   field.
5. Run `mvn test` and confirm the new test executes (not skipped) and passes
   against the real `SysCodeKeyPolicy` - and, if it's meant to catch a
   regression, prove it can fail by temporarily breaking the corresponding
   line in `SysCodeKeyPolicy`.
