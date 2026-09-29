# F115ICA0 (TF_Contract_Amount) DBI characterization tests

Characterization tests for the legacy DB2-interface module
`legacy/TFR12/src/F115ICA0.src` (table `TF_Contract_Amount`) — the **pilot**
table for Phase 2's "Data Access Layer" work: 8 more near-identical
`F115I*0` table-interface modules follow this exact pattern, so read the
"Before you replicate this to the other 8 tables" section below first.

Covers:

- `ContractAmountKey` / `ContractAmountRow`
  (`src/main/java/.../dbi/contractamount/`) — the Java contract for the
  table's natural key and row, hand-traced from
  `legacy/TFR12/copybooks/R115ICA0.copy`.
- `ContractAmountKeyPolicy` — a real implementation of the shared
  `com.tfi.tfr12.phase2.dbi.KeyPolicy<ContractAmountRow>`, ported from
  legacy `H100-Check-Qualification` (`F115ICA0.src:806-894`), which is
  RULE-018 in `analysis/TFR12/BUSINESS_RULES.md`.
- `ContractAmountServiceTest` — the tests themselves, plus wiring tests for
  the shared `DbiEngine<ContractAmountKey, ContractAmountRow>` against an
  in-memory fake `KeyedRepository` (there is no real DB2 access here —
  F115ICA0 needs a DB2 precompiler that isn't available in this
  environment).

Every expected value was hand-traced from the COBOL source and copybook,
not observed from a running legacy program. The source is
ISO-8859-1/Latin-1, not UTF-8; column 7 `*` marks a comment, columns 8-72
are the code area.

## RULE-018 — the mandatory key, and where it is (and isn't) enforced

`H100-Check-Qualification` requires four fields before it lets a call
through: Fin-Inst (ID, or No resolved via `F5879010`), Contract-Type,
Contract-No, and Currency (`F115ICA0.src:808-892`). Missing ones map to
error mnemonics: `TF-FIN-INST-MISSING`, `TF-CONTRACT-MISSING` (shared by
both Contract-Type and Contract-No), `TF-CURRENCY-MISSING`.

**Read this before touching `ContractAmountKeyPolicy` or copying this
pattern to the other 8 tables**: in the literal F115ICA0 source,
`H100-Check-Qualification` is invoked from exactly one place —
`H000-Update` (`F115ICA0.src:796`). `F000-Select` and `I000-Delete` both
route through `F100-Prepare-Qualification` (`F115ICA0.src:673-693`)
instead, which copies whatever qualification fields were supplied (with a
Fin-Inst-No→ID resolution) but never checks for a missing field and never
raises a `TF-*-MISSING` error — a missing key on Select just surfaces later
as `DB-FOREKOMST-NF-Q` ("row not found") once DB2 is queried, and a missing
key on Delete is never rejected before the `DELETE` runs. This contradicts
the copybook's own comment ("Contract-Type and -No is mandatory for
Select/Update/Delete", `R115ICA0.copy:89-93,112-113,123`) — the comment
claims a check the control flow doesn't perform outside Update.

The shared `DbiEngine` (already built, not this task's to redesign)
deliberately applies `ContractAmountKeyPolicy` before Select, Update AND
Delete uniformly — see its own Javadoc. That is a **hardening decision for
the modernized system, not a faithful line-for-line reproduction of
F115ICA0's Select/Delete behavior**. `ContractAmountServiceTest` pins both
facts side by side:

- `rule018_updateRejectsIncompleteKey_matchesLegacyHFor000Update` — matches
  literal legacy behavior (Update really does check).
- `rule018_selectRejectsIncompleteKey_widerThanLegacySelect` and
  `rule018_deleteRejectsIncompleteKey_widerThanLegacyDelete` — explicitly
  named and commented as **wider** than legacy, so this widening is visible
  before it gets baked into the other 8 tables rather than silently
  inherited.

**"Last failure wins", not fail-fast**: `F115ICA0.src:808-892` runs its four
mandatory-field checks as four independent `Evaluate True ... End-Evaluate`
blocks with no early exit — a failing check overwrites the single scalar
error-message field and keeps going. With more than one field missing, the
mnemonic actually returned is whichever check failed **last** (Currency,
then Contract-No, then Contract-Type, then Fin-Inst), not the first.
`ContractAmountKeyPolicy` reproduces this ordering deliberately (see its
Javadoc for the full line trace); `LastFailureWinsTests` in
`ContractAmountServiceTest` pins it.

RULE-018 has no reviewer verdict yet in `analysis/TFR12/RULE_REVIEWS.json`
(unlike RULE-017, which was confirmed-as-is by the system owner) — treat it
as read from the code as-is, but it hasn't been through the same explicit
sign-off, and the Select/Delete widening above is worth a second look
before replication.

## Running the tests

```bash
cd "modernized/TFR12/F115ICA0,F115ICH0,F115ICT0,F115IHI0,F115IMC0,F115IPA0,F115IPC0,F115ISC0,F115ISR0,F5879010"
mvn test -Dtest=ContractAmountServiceTest
```

Requires Java 21. All 18 test methods currently pass (3 nested classes:
`KeyPolicyTests` with `LastFailureWinsTests` nested inside it, and
`DbiEngineWiringTests`) — `ContractAmountKeyPolicy` is a real implementation,
not a stub, so the suite is expected to be green today.

Note: running the whole module's `mvn test` (not just `-Dtest=
ContractAmountServiceTest`) may also execute other tables' tests
(`syscode`, `partcharge`, ...) written by other work in this same shared
module; failures there are outside this test class's scope.

## How this proves anything

A green suite that can't fail proves nothing. To confirm these tests
actually pin RULE-018 (rather than passing by accident), the "last failure
wins" deferral in `ContractAmountKeyPolicy`'s Fin-Inst check was temporarily
changed to fail fast (`throw` immediately instead of setting
`pendingFailure`), `mvn test -Dtest=ContractAmountServiceTest` was re-run,
and exactly the 3 tests in `LastFailureWinsTests` went red (expected
`TF-CURRENCY-MISSING` / `TF-CONTRACT-MISSING`, got `TF-FIN-INST-MISSING`).
The change was then reverted and the suite is green again. If you change
`ContractAmountKeyPolicy`, repeat this exercise: break one small thing that
matters, confirm at least one test goes red, then restore it.

## Adding a new case

1. Hand-trace the exact COBOL lines in `F115ICA0.src` (or the copybook) for
   the input/output pair you want to pin. Cite the file and line range in a
   comment or `@DisplayName`.
2. Add the case to `KeyPolicyTests` (single-field checks),
   `LastFailureWinsTests` (multi-field-missing precedence), or
   `DbiEngineWiringTests` (engine wiring), or create a new nested class if
   it covers a different paragraph/branch.
3. If the case pins a numbered rule from `BUSINESS_RULES.md`, put the rule
   id in the test method name (e.g. `rule018_...`) or a comment directly
   above it — `modernize-verify` finds tests by that id, and an unnamed test
   counts for no rule.
4. If the case needs a new field on `ContractAmountKey`/`ContractAmountRow`,
   extend the record and update its Javadoc to cite the copybook line
   backing the new field.
5. Run `mvn test -Dtest=ContractAmountServiceTest` and confirm the new test
   executes (not skipped) and passes against the real
   `ContractAmountKeyPolicy` — and, if it's meant to catch a regression,
   prove it can fail by temporarily breaking the corresponding line in
   `ContractAmountKeyPolicy`.
